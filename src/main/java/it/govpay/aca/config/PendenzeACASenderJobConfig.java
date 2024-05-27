package it.govpay.aca.config;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.PartitionHandler;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import it.govpay.aca.costanti.Costanti;
import it.govpay.aca.entity.VersamentoAcaEntity;
import it.govpay.aca.entity.VersamentoAcaEntity_;
import it.govpay.aca.repository.VersamentoAcaRepository;
import it.govpay.aca.repository.VersamentoFilters;
import it.govpay.aca.step.JobCompletionNotificationListener;
import it.govpay.aca.step.PendenzaWriter;
import it.govpay.aca.step.SendPendenzaToAcaProcessor;

@Configuration
//@EnableBatchProcessing
public class PendenzeACASenderJobConfig {

	private Logger logger = LoggerFactory.getLogger(PendenzeACASenderJobConfig.class);

	@Value("${it.govpay.aca.batch.jobs.acaSenderJob.steps.spedizionePendenzaStep.chunk-size:10}")
	private Integer spedizionePendenzaStepChunkSize;

	@Value("${it.govpay.aca.time-zone:Europe/Rome}")
	String timeZone;
	
	@Value("${it.govpay.aca.batch.dbreader.numeroPendenze.limit:100}")
	private Integer limit;
	
	@Value("${it.govpay.aca.batch.dbreader.sogliaTemporaleRicercaPendenze.numeroGiorni:7}")
	private Integer numeroGiorni;

	protected TaskExecutor taskExecutor() {
		return new SimpleAsyncTaskExecutor(Costanti.MSG_SENDER_TASK_EXECUTOR_NAME);
	}
	
	@Bean(name = Costanti.SEND_PENDENZE_ACA_JOBNAME)
	public Job spedizionePendenzeACA(@Qualifier("spedizionePendenzaStep") Step spedizionePendenzaStep, JobRepository jobRepository, JobCompletionNotificationListener listener){
		return new JobBuilder(Costanti.SEND_PENDENZE_ACA_JOBNAME, jobRepository)
				.listener(listener)
				.start(spedizionePendenzaStep)
				.build();
	}

	protected AsyncItemProcessor<VersamentoAcaEntity,VersamentoAcaEntity> asyncProcessor(SendPendenzaToAcaProcessor itemProcessor) {
		AsyncItemProcessor<VersamentoAcaEntity, VersamentoAcaEntity> asyncItemProcessor = new AsyncItemProcessor<>();
		asyncItemProcessor.setTaskExecutor(taskExecutor());
		asyncItemProcessor.setDelegate(itemProcessor);
		return asyncItemProcessor;
	}
	
	protected AsyncItemWriter<VersamentoAcaEntity> asyncMessageWriter(PendenzaWriter pendenzaItemWriter){
		AsyncItemWriter<VersamentoAcaEntity> asyncItemWriter = new AsyncItemWriter<>();
	    asyncItemWriter.setDelegate(pendenzaItemWriter);
	    return asyncItemWriter;
	}
	
    @Bean
    @StepScope
    public RepositoryItemReader<VersamentoAcaEntity> repositoryItemReader(VersamentoAcaRepository versamentoAcaRepository, @Value("#{jobParameters}") Map<String, Object> jobParameters) {
    	
    	Specification<VersamentoAcaEntity> spec = VersamentoFilters.empty();
    	
    	// filtro sulla data esecuzione
    	if(this.numeroGiorni != null) {
    		OffsetDateTime sogliaTemporale = OffsetDateTime.now().minusDays(this.numeroGiorni);
			spec = VersamentoFilters.byDataUltimaModificaAcaDa(sogliaTemporale);
    	}
    	
        return new RepositoryItemReaderBuilder<VersamentoAcaEntity>()
                .name("versamentoAcaReader")
                .repository(versamentoAcaRepository)
                .methodName("findAll")
                .arguments(spec)
                .pageSize(this.limit)
                .sorts(Collections.singletonMap(VersamentoAcaEntity_.DATA_ULTIMA_COMUNICAZIONE_ACA, Direction.DESC))
                .build();
    }

	@Bean(name = "spedizionePendenzaStep")
	public Step spedizionePendenzaStep(
			JobRepository jobRepository, PlatformTransactionManager transactionManager,
			RepositoryItemReader<VersamentoAcaEntity> pendenzaItemReader,
			SendPendenzaToAcaProcessor pendenzaSendProcessor,
			PendenzaWriter pendenzaItemWriter)  {
		return new StepBuilder(Costanti.SEND_PENDENZE_ACA_STEPNAME, jobRepository)
				.<VersamentoAcaEntity, Future<VersamentoAcaEntity>>chunk(this.spedizionePendenzaStepChunkSize, transactionManager)
				.reader(pendenzaItemReader)
				.processor(asyncProcessor(pendenzaSendProcessor))
				.writer(asyncMessageWriter(pendenzaItemWriter))
				.faultTolerant()
				.listener(new StepListener())
				.taskExecutor(taskExecutor())
				.build();
	}

	public class StepListener implements StepExecutionListener {


		@Override
		public ExitStatus afterStep(StepExecution stepExecution)  {
			logger.debug("Completata {}", stepExecution); 
			logger.debug("Jobparams utilizzati {}", stepExecution.getJobParameters());
			return stepExecution.getExitStatus();
		}

		@Override
		public void beforeStep(StepExecution stepExecution) {
			logger.debug("Inizio {}", stepExecution);
		}
	}

}