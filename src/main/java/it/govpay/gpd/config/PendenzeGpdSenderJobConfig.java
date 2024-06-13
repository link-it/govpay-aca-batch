package it.govpay.gpd.config;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
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
import org.springframework.transaction.PlatformTransactionManager;

import it.govpay.gpd.costanti.Costanti;
import it.govpay.gpd.entity.VersamentoGpdEntity;
import it.govpay.gpd.entity.VersamentoGpdEntity_;
import it.govpay.gpd.repository.VersamentoFilters;
import it.govpay.gpd.repository.VersamentoGpdRepository;
import it.govpay.gpd.step.JobCompletionNotificationListener;
import it.govpay.gpd.step.PendenzaWriter;
import it.govpay.gpd.step.SendPendenzaToGpdProcessor;

@Configuration
//@EnableBatchProcessing
public class PendenzeGpdSenderJobConfig {

	private Logger logger = LoggerFactory.getLogger(PendenzeGpdSenderJobConfig.class);

	@Value("${it.govpay.gpd.batch.jobs.gpdSenderJob.steps.spedizionePendenzaStep.chunk-size:10}")
	private Integer spedizionePendenzaStepChunkSize;

	@Value("${it.govpay.gpd.batch.dbreader.numeroPendenze.limit:100}")
	private Integer limit;
	
	@Value("${it.govpay.gpd.batch.dbreader.sogliaTemporaleRicercaPendenze.numeroGiorni:7}")
	private Integer numeroGiorni;

	protected TaskExecutor taskExecutor() {
		return new SimpleAsyncTaskExecutor(Costanti.MSG_SENDER_TASK_EXECUTOR_NAME);
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
    public RepositoryItemReader<VersamentoGpdEntity> repositoryItemReader(VersamentoGpdRepository versamentoGpdRepository, @Value("#{jobParameters}") Map<String, Object> jobParameters) {
    	
    	Specification<VersamentoGpdEntity> spec = VersamentoFilters.creaFiltriRicercaVersamentiDaSpedire(this.numeroGiorni);
    	
        return new RepositoryItemReaderBuilder<VersamentoGpdEntity>()
                .name("versamentoGpdReader")
                .repository(versamentoGpdRepository)
                .methodName("findAll")
                .arguments(spec)
                .pageSize(this.limit)
                .sorts(Collections.singletonMap(VersamentoGpdEntity_.DATA_ULTIMA_COMUNICAZIONE_ACA, Direction.DESC))
                .build();
    }

	@Bean(name = "spedizionePendenzaStep")
	public Step spedizionePendenzaStep(
			JobRepository jobRepository, PlatformTransactionManager transactionManager,
			RepositoryItemReader<VersamentoGpdEntity> pendenzaItemReader,
			SendPendenzaToGpdProcessor pendenzaSendProcessor,
			PendenzaWriter pendenzaItemWriter)  {
		return new StepBuilder(Costanti.SEND_PENDENZE_GPD_STEPNAME, jobRepository)
				.<VersamentoGpdEntity, Future<VersamentoGpdEntity>>chunk(this.spedizionePendenzaStepChunkSize, transactionManager)
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