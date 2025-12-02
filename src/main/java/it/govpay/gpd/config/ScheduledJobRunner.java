package it.govpay.gpd.config;

import java.time.OffsetDateTime;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import it.govpay.gpd.costanti.Costanti;

@Component
@Profile("default")
@EnableScheduling
public class ScheduledJobRunner {

	private static final Logger log = LoggerFactory.getLogger(ScheduledJobRunner.class);

	private JobLauncher jobLauncher;

	private PreventConcurrentJobLauncher preventConcurrentJobLauncher;

	private Job pendenzaSenderJob;

	@Value("${it.govpay.gpd.batch.clusterId:GovPay-ACA-Batch}")
	private String clusterId;

	public ScheduledJobRunner(JobLauncher jobLauncher, PreventConcurrentJobLauncher preventConcurrentJobLauncher, @Qualifier(Costanti.SEND_PENDENZE_GPD_JOBNAME) Job pendenzaSenderJob) {
		this.jobLauncher = jobLauncher;
		this.preventConcurrentJobLauncher = preventConcurrentJobLauncher;
		this.pendenzaSenderJob = pendenzaSenderJob;
	}

	private void runSendPendenzeGpdJob() throws JobExecutionAlreadyRunningException, JobRestartException, JobInstanceAlreadyCompleteException, JobParametersInvalidException {
		JobParameters params = new JobParametersBuilder()
				.addString(Costanti.GOVPAY_GPD_JOB_ID, Costanti.SEND_PENDENZE_GPD_JOBNAME)
				.addString(Costanti.GOVPAY_GPD_JOB_PARAMETER_WHEN, OffsetDateTime.now().toString())
				.addString(Costanti.GOVPAY_GPD_JOB_PARAMETER_CLUSTER_ID, this.clusterId)
				.toJobParameters();
		jobLauncher.run(pendenzaSenderJob, params);
	}

	@Scheduled(fixedDelayString = "${scheduler.gpdSenderJob.fixedDelayString:600000}", initialDelayString = "${scheduler.initialDelayString:1}")
	public void runBatchPendenzeJob() throws JobExecutionAlreadyRunningException, JobRestartException, JobInstanceAlreadyCompleteException, JobParametersInvalidException {
		log.info("Esecuzione schedulata di {}", Costanti.SEND_PENDENZE_GPD_JOBNAME);

		JobExecution currentRunningJobExecution = this.preventConcurrentJobLauncher.getCurrentRunningJobExecution(Costanti.SEND_PENDENZE_GPD_JOBNAME);

		if (currentRunningJobExecution != null) {
			// Verifica se il job è stale (bloccato o in stato anomalo)
			boolean isStale = this.preventConcurrentJobLauncher.isJobExecutionStale(currentRunningJobExecution);

			if (isStale) {
				log.warn("JobExecution {} rilevata come STALE. Procedo con abbandono e riavvio.",
					currentRunningJobExecution.getId());

				// Abbandona il job stale
				boolean abandoned = this.preventConcurrentJobLauncher.abandonStaleJobExecution(currentRunningJobExecution);

				if (abandoned) {
					log.info("Job stale abbandonato con successo. Avvio nuova esecuzione.");
					// Procedi con l'avvio di una nuova esecuzione
					runSendPendenzeGpdJob();
				} else {
					log.error("Impossibile abbandonare il job stale. Uscita.");
				}
				return;
			}

			// Job in esecuzione normale - estrai il clusterid dell'esecuzione corrente
			Map<String, JobParameter<?>> runningParams = currentRunningJobExecution.getJobParameters().getParameters();
			String runningClusterId = runningParams.containsKey(Costanti.GOVPAY_GPD_JOB_PARAMETER_CLUSTER_ID) ? runningParams.get(Costanti.GOVPAY_GPD_JOB_PARAMETER_CLUSTER_ID).getValue().toString() : null;

			if (runningClusterId != null && !runningClusterId.equals(this.clusterId)) {
				log.info("Il job {} è in esecuzione su un altro nodo ({}). Uscita.", Costanti.SEND_PENDENZE_GPD_JOBNAME, runningClusterId);
			} else {
				log.warn("Il job {} è ancora in esecuzione sul nodo corrente ({}). Uscita.", Costanti.SEND_PENDENZE_GPD_JOBNAME, runningClusterId);
			}
			return;
		}

		runSendPendenzeGpdJob();
	}
}
