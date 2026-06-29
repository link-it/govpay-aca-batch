package it.govpay.gpd.config;

import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobRestartException;
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

	private JobOperator jobOperator;

	private PreventConcurrentJobLauncher preventConcurrentJobLauncher;

	private Job pendenzaSenderJob;

	@Value("${it.govpay.gpd.batch.clusterId:GovPay-ACA-Batch}")
	private String clusterId;

	public ScheduledJobRunner(JobOperator jobOperator, PreventConcurrentJobLauncher preventConcurrentJobLauncher, @Qualifier(Costanti.SEND_PENDENZE_GPD_JOBNAME) Job pendenzaSenderJob) {
		this.jobOperator = jobOperator;
		this.preventConcurrentJobLauncher = preventConcurrentJobLauncher;
		this.pendenzaSenderJob = pendenzaSenderJob;
	}

	private void runSendPendenzeGpdJob() throws JobExecutionAlreadyRunningException, JobRestartException, JobInstanceAlreadyCompleteException, InvalidJobParametersException {
		JobParameters params = new JobParametersBuilder()
				.addString(Costanti.GOVPAY_GPD_JOB_ID, Costanti.SEND_PENDENZE_GPD_JOBNAME)
				.addString(Costanti.GOVPAY_GPD_JOB_PARAMETER_WHEN, OffsetDateTime.now().toString())
				.addString(Costanti.GOVPAY_GPD_JOB_PARAMETER_CLUSTER_ID, this.clusterId)
				.toJobParameters();
		jobOperator.start(pendenzaSenderJob, params);
	}

	@Scheduled(fixedDelayString = "${scheduler.gpdSenderJob.fixedDelayString:600000}", initialDelayString = "${scheduler.initialDelayString:1}")
	public void runBatchPendenzeJob() throws JobExecutionAlreadyRunningException, JobRestartException, JobInstanceAlreadyCompleteException, InvalidJobParametersException {
		log.info("Esecuzione schedulata di {}", Costanti.SEND_PENDENZE_GPD_JOBNAME);

		JobExecution currentRunningJobExecution = this.preventConcurrentJobLauncher.getCurrentRunningJobExecution(Costanti.SEND_PENDENZE_GPD_JOBNAME);

		if (currentRunningJobExecution != null) {
			// Verifica se il job è stale (bloccato o in stato anomalo)
			if (this.preventConcurrentJobLauncher.isJobExecutionStale(currentRunningJobExecution)) {
				log.warn("JobExecution {} rilevata come STALE. Procedo con abbandono e riavvio.",
					currentRunningJobExecution.getId());

				// Abbandona il job stale
				if (this.preventConcurrentJobLauncher.abandonStaleJobExecution(currentRunningJobExecution)) {
					log.info("Job stale abbandonato con successo. Avvio nuova esecuzione.");
					runSendPendenzeGpdJob();
				} else {
					log.error("Impossibile abbandonare il job stale. Uscita.");
				}
				return;
			}

			// Job in esecuzione normale - estrai il clusterid dell'esecuzione corrente
			String runningClusterId = this.preventConcurrentJobLauncher.getClusterIdFromExecution(currentRunningJobExecution);

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
