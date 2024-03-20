package it.govpay.aca.config;

import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

import it.govpay.aca.entity.VersamentoAcaEntity;
import it.govpay.aca.step.PendenzaWriter;
import it.govpay.aca.step.PendenzeReader;
import it.govpay.aca.step.SendPendenzaToAcaProcessor;

@Configuration
@EnableBatchProcessing
public class PendenzeACASenderJobConfig {

	private Logger logger = LoggerFactory.getLogger(PendenzeACASenderJobConfig.class);

	public static final String SEND_PENDENZE_ACA_STEPNAME = "spedizionePendenzeStep";
	public static final String SEND_PENDENZE_ACA_JOBNAME= "acaSenderJob";

	@Value("${it.govpay.aca.batch.jobs.acaSenderJob.steps.spedizionePendenzaStep.chunk-size:10}")
	private Integer spedizionePendenzaStepChunkSize;

	@Autowired
	private JobBuilderFactory jobs;

	@Autowired
	private StepBuilderFactory steps;

	@Autowired
	JobRepository jobRepository;

	public static final String MSG_SENDER_TASK_EXECUTOR_NAME = "spring_batch_msgsender"; 

	protected TaskExecutor taskExecutor() {
		return new SimpleAsyncTaskExecutor(MSG_SENDER_TASK_EXECUTOR_NAME);
	}

	@Bean(name = SEND_PENDENZE_ACA_JOBNAME)
	public Job spedizionePendenzeACA(@Qualifier("spedizionePendenzaStep") Step spedizionePendenzaStep){
		return jobs.get(SEND_PENDENZE_ACA_JOBNAME)
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
	@Qualifier("spedizionePendenzaStep")
	public Step spedizionePendenzaStep(
			PendenzeReader pendenzaItemReader,
			SendPendenzaToAcaProcessor pendenzaSendProcessor,
			PendenzaWriter pendenzaItemWriter)  {
		return steps.get(SEND_PENDENZE_ACA_STEPNAME)
				.<VersamentoAcaEntity, Future<VersamentoAcaEntity>>chunk(this.spedizionePendenzaStepChunkSize)
				.reader(pendenzaItemReader)
				.processor(asyncProcessor(pendenzaSendProcessor))
				.writer(asyncMessageWriter(pendenzaItemWriter))
				.faultTolerant()
				.listener(new StepNotifyListener())
				.build();
	}

	// Listener per recuperare il numero di righe scritte nel file csv nello step notifyStep
	public class StepNotifyListener implements StepExecutionListener {


		@Override
		public ExitStatus afterStep(StepExecution stepExecution)  {
			logger.debug("afterStep {}", stepExecution); 
			//			if(stepExecution.getReadCount()==0) {
			//				throw new RuntimeException("Dati assenti nel csv delle notifiche");
			//			}
			//
			//			stepExecution.getExecutionContext().put("NumRows", stepExecution.getWriteCount());
			return stepExecution.getExitStatus();
		}

		@Override
		public void beforeStep(StepExecution stepExecution) {
			logger.debug("beforeStep {}", stepExecution);
		}

	}

}