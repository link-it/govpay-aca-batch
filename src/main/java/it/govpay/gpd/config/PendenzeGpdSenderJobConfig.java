package it.govpay.gpd.config;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.ItemProcessListener;
import org.springframework.batch.core.ItemReadListener;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.transaction.PlatformTransactionManager;

import it.govpay.gpd.costanti.Costanti;
import it.govpay.gpd.entity.VersamentoGpdEntity;
import it.govpay.gpd.step.JobCompletionNotificationListener;
import it.govpay.gpd.step.PendenzaWriter;
import it.govpay.gpd.step.SendPendenzaToGpdProcessor;

@Configuration
public class PendenzeGpdSenderJobConfig {
	
	private SimpleDateFormat sdf = new SimpleDateFormat(Costanti.PATTERN_DATA_JSON_YYYY_MM_DD_T_HH_MM_SS_SSS);

	private Logger logger = LoggerFactory.getLogger(PendenzeGpdSenderJobConfig.class);

	@Value("${it.govpay.gpd.batch.jobs.gpdSenderJob.steps.spedizionePendenzaStep.chunk-size:10}")
	private Integer spedizionePendenzaStepChunkSize;

	@Value("${it.govpay.gpd.batch.dbreader.sogliaTemporaleRicercaPendenze.numeroGiorni:7}")
	private Integer numeroGiorni;

	protected TaskExecutor taskExecutor() {
		SimpleAsyncTaskExecutor simpleAsyncTaskExecutor = new SimpleAsyncTaskExecutor(Costanti.MSG_SENDER_TASK_EXECUTOR_NAME);
		simpleAsyncTaskExecutor.setConcurrencyLimit(1); // numero massimo di thread
		return simpleAsyncTaskExecutor;
	}

	@Bean(name = Costanti.SEND_PENDENZE_GPD_JOBNAME)
	public Job spedizionePendenzeGPD(@Qualifier("spedizionePendenzaStep") Step spedizionePendenzaStep, JobRepository jobRepository, JobCompletionNotificationListener listener){
		return new JobBuilder(Costanti.SEND_PENDENZE_GPD_JOBNAME, jobRepository)
				.listener(listener)
				.start(spedizionePendenzaStep)
				.build();
	}

	protected AsyncItemProcessor<VersamentoGpdEntity,VersamentoGpdEntity> asyncProcessor(SendPendenzaToGpdProcessor itemProcessor) {
		AsyncItemProcessor<VersamentoGpdEntity, VersamentoGpdEntity> asyncItemProcessor = new AsyncItemProcessor<>();
		asyncItemProcessor.setTaskExecutor(taskExecutor());
		asyncItemProcessor.setDelegate(itemProcessor);
		return asyncItemProcessor;
	}

	protected AsyncItemWriter<VersamentoGpdEntity> asyncMessageWriter(PendenzaWriter pendenzaItemWriter){
		AsyncItemWriter<VersamentoGpdEntity> asyncItemWriter = new AsyncItemWriter<>();
		asyncItemWriter.setDelegate(pendenzaItemWriter);
		return asyncItemWriter;
	}

	@Bean
	@StepScope
	public JdbcCursorItemReader<VersamentoGpdEntity> jdbcCursorItemReader(DataSource dataSource) {

		JdbcCursorItemReader<VersamentoGpdEntity> reader = new JdbcCursorItemReader<>();
		reader.setDataSource(dataSource);

		logger.debug("Le pendenze da spedire verranno scelte con la seguente query: {}", Costanti.QUERY_RICERCA_PENDENZE_DA_CARICARE_ACA);

		// Imposta la query SQL        
		reader.setSql(Costanti.QUERY_RICERCA_PENDENZE_DA_CARICARE_ACA);

		
		GenericConversionService cs = new DefaultConversionService();
		cs.addConverter(Timestamp.class, OffsetDateTime.class, ts -> {
		    if (ts == null) {
		        return null;
		    }
		    return ts.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
		});

		
		// Imposta il RowMapper per convertire il ResultSet in oggetti VersamentoGpdEntity
		LoggingBeanPropertyRowMapper<VersamentoGpdEntity> rowMapper = new LoggingBeanPropertyRowMapper<>(VersamentoGpdEntity.class);
		rowMapper.setConversionService(cs);
		reader.setRowMapper(rowMapper);

		// Imposta un PreparedStatementSetter per passare i parametri alla query.
		// In questo esempio, impostiamo la data limite come (oggi - numeroGiorni)
		reader.setPreparedStatementSetter(new PreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement ps) throws SQLException {
				Calendar cal = Calendar.getInstance();
				cal.add(Calendar.DAY_OF_MONTH, -numeroGiorni);
				Date dateLimit = new Date(cal.getTimeInMillis());
				logger.debug("Verranno ricercate le pendenze a partire dal: {}", sdf.format(dateLimit));
				ps.setDate(1, dateLimit);
			}
		});

		// Imposta il fetchSize per controllare il numero di record prelevati alla volta.
		reader.setFetchSize(this.spedizionePendenzaStepChunkSize);

		return reader;
	}

	@Bean(name = "spedizionePendenzaStep")
	public Step spedizionePendenzaStep(
			JobRepository jobRepository, PlatformTransactionManager transactionManager,
			JdbcCursorItemReader<VersamentoGpdEntity> pendenzaItemReader,
			SendPendenzaToGpdProcessor pendenzaSendProcessor,
			PendenzaWriter pendenzaItemWriter)  {
		return new StepBuilder(Costanti.SEND_PENDENZE_GPD_STEPNAME, jobRepository)
				.<VersamentoGpdEntity, Future<VersamentoGpdEntity>>chunk(this.spedizionePendenzaStepChunkSize, transactionManager)
				.reader(pendenzaItemReader)
				.processor(asyncProcessor(pendenzaSendProcessor))
				.writer(asyncMessageWriter(pendenzaItemWriter))
				.faultTolerant()
				.listener(new ChunkListener())
				.listener(new StepListener())
				.listener(new MyItemReadListener<>())
				.listener(new MyItemProcessListener<>())
				.listener(new MyItemWriteListener<>())
				.taskExecutor(taskExecutor())
				.build();
	}

	public class ChunkListener implements org.springframework.batch.core.ChunkListener {

		@Override
		public void beforeChunk(ChunkContext context) {
			logger.debug("Inizio del chunk: {}", context);
		}

		@Override
		public void afterChunk(ChunkContext context) {
			logger.debug("Chunk completato con successo: {}", context);
		}

		@Override
		public void afterChunkError(ChunkContext context) {
			logger.error("Errore durante il processamento del chunk: {}", context);
		}
	}

	public class StepListener implements StepExecutionListener {
		@Override
		public ExitStatus afterStep(StepExecution stepExecution)  {
			logger.debug("Completata {}", stepExecution); 
			logger.debug("Jobparams utilizzati {}", stepExecution.getJobParameters());
			long read    = stepExecution.getReadCount();
	        long write   = stepExecution.getWriteCount();
	        long skip    = stepExecution.getSkipCount();
	        logger.info("Step {} - read={}, written={}, skipped={}", stepExecution.getStepName(), read, write, skip);
			return stepExecution.getExitStatus();
		}

		@Override
		public void beforeStep(StepExecution stepExecution) {
			logger.debug("Inizio {}", stepExecution);
		}
	}

	public class MyItemReadListener<T> implements ItemReadListener<T> {

		@Override
		public void beforeRead() {
			logger.debug("Inizio della lettura di un item.");
		}

		@Override
		public void afterRead(T item) {
			if (item instanceof VersamentoGpdEntity versamentoGpdEntity) {
				logger.debug("Pendenza letta: id[{},{}], dataUltimaModificaACA={}", versamentoGpdEntity.getCodApplicazione(), versamentoGpdEntity.getCodVersamentoEnte(),
						versamentoGpdEntity.getDataUltimaModificaAca());
			} else {
				logger.debug("Letto item: {}", item);
			}
		}

		@Override
		public void onReadError(Exception ex) {
			logger.error("Errore durante la lettura di un item: "+ ex.getMessage(), ex);
		}
	}

	public class MyItemWriteListener<T> implements ItemWriteListener<T> {

		@Override
		public void afterWrite(Chunk<? extends T> items) {
			logger.debug("Scrittura completata per {} item(s).", items.size());
		}

		@Override
		public void beforeWrite(Chunk<? extends T> items) {
			logger.debug("Inizio della scrittura di {} item(s).", items.size());
		}

		@Override
		public void onWriteError(Exception exception, Chunk<? extends T> items) {
			logger.error("Errore durante la scrittura degli item(s): " + exception.getMessage(), exception);
		}
	}

	public class MyItemProcessListener<I, O> implements ItemProcessListener<I, O> {

		@Override
		public void beforeProcess(I item) {
			if (item instanceof VersamentoGpdEntity versamentoGpdEntity) {
				logger.debug("Inizio dell'elaborazione della pendenza: id[{},{}]", versamentoGpdEntity.getCodApplicazione(), versamentoGpdEntity.getCodVersamentoEnte());
			} else {
				logger.debug("Inizio dell'elaborazione dell'item: {}", item);
			}
		}

		@Override
		public void afterProcess(I item, O result) {
			if (item instanceof VersamentoGpdEntity versamentoGpdEntity) {
				logger.debug("Elaborazione completata della pendenza: id[{},{}]", versamentoGpdEntity.getCodApplicazione(), versamentoGpdEntity.getCodVersamentoEnte());
			} else {
				logger.debug("Elaborazione completata. Item: {} - Risultato: {}", item, result);
			}
		}

		@Override
		public void onProcessError(I item, Exception e) {
			if (item instanceof VersamentoGpdEntity versamentoGpdEntity) {
				logger.error("Errore durante l'elaborazione della pendenza: id["+versamentoGpdEntity.getCodApplicazione()+","+versamentoGpdEntity.getCodVersamentoEnte()+"]: "+e.getMessage(),e);
			} else {
				logger.error("Errore durante l'elaborazione dell'item: {}", item, e);
			}
		}
	}
	public class LoggingBeanPropertyRowMapper<T> extends BeanPropertyRowMapper<T> {

		public LoggingBeanPropertyRowMapper(Class<T> arg0) {
			super(arg0);
		}

		@Override
		public T mapRow(ResultSet rs, int rowNum) throws SQLException {
			LoggerFactory.getLogger(getClass()).debug("Inizio mapping per rowNum {}. ResultSet metaData: {}", rowNum, rs.getMetaData());
			try {
				T result = super.mapRow(rs, rowNum);
				if (result instanceof VersamentoGpdEntity versamentoGpdEntity) {
					LoggerFactory.getLogger(getClass()).debug("Pendenza letta: id[{},{}], dataUltimaModificaACA={}", versamentoGpdEntity.getCodApplicazione(), versamentoGpdEntity.getCodVersamentoEnte(),
							versamentoGpdEntity.getDataUltimaModificaAca());
				} else {
					LoggerFactory.getLogger(getClass()).trace("Mapping riga {}: {}", rowNum, result);
				}
				return result;
			} catch (SQLException e) {
				logError("Errore nel mapping della row {}: {}", rowNum, e.getMessage(), e);
				throw e;
			}
		}
		
		private void logError(String msg, Object... args) {
			LoggerFactory.getLogger(getClass()).error(msg, args);
		}
	}


}