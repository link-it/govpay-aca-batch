package it.govpay.gpd.config;

import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import it.govpay.gpd.costanti.Costanti;

@Component
@Profile("cron")
public class CronJobRunner implements CommandLineRunner, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(CronJobRunner.class);

    private ApplicationContext context;

    private PreventConcurrentJobLauncher preventConcurrentJobLauncher;

    private JobLauncher jobLauncher;

    private Job pendenzaSenderJob;

    @Value("${it.govpay.gpd.batch.clusterId:GovPay-ACA-Batch}")
	private String clusterId;

    public CronJobRunner(JobLauncher jobLauncher, PreventConcurrentJobLauncher preventConcurrentJobLauncher, @Qualifier(Costanti.SEND_PENDENZE_GPD_JOBNAME) Job pendenzaSenderJob) {
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

    @Override
    public void run(String... args) throws Exception {
        log.info("Avvio {} da command line", Costanti.SEND_PENDENZE_GPD_JOBNAME);

        JobExecution currentRunningJobExecution = this.preventConcurrentJobLauncher.getCurrentRunningJobExecution(Costanti.SEND_PENDENZE_GPD_JOBNAME);

		if (currentRunningJobExecution != null) {
			// Verifica se il job è stale (bloccato o in stato anomalo)
			if (this.preventConcurrentJobLauncher.isJobExecutionStale(currentRunningJobExecution)) {
				log.warn("JobExecution {} rilevata come STALE. Procedo con abbandono e riavvio.",
					currentRunningJobExecution.getId());

				// Abbandona il job stale
				boolean abandoned = this.preventConcurrentJobLauncher.abandonStaleJobExecution(currentRunningJobExecution);

				if (abandoned) {
					log.info("Job stale abbandonato con successo. Avvio nuova esecuzione.");
					runSendPendenzeGpdJob();
					log.info("{} completato.", Costanti.SEND_PENDENZE_GPD_JOBNAME);
				} else {
					log.error("Impossibile abbandonare il job stale. Uscita.");
				}
				// Terminazione dell'applicazione
				int exitCode = SpringApplication.exit(context, () -> abandoned ? 0 : 1);
				System.exit(exitCode);
				return;
			}

			// Job in esecuzione normale - estrai il clusterid dell'esecuzione corrente
			String runningClusterId = this.preventConcurrentJobLauncher.getClusterIdFromExecution(currentRunningJobExecution);

            if (runningClusterId != null && !runningClusterId.equals(this.clusterId)) {
                log.info("Il job {} è in esecuzione su un altro nodo ({}). Uscita.", Costanti.SEND_PENDENZE_GPD_JOBNAME, runningClusterId);
            } else {
            	log.warn("Il job {} è ancora in esecuzione sul nodo corrente ({}). Uscita.", Costanti.SEND_PENDENZE_GPD_JOBNAME, runningClusterId);
            }
            // Terminazione dell'applicazione
            int exitCode = SpringApplication.exit(context, () -> 0);
            System.exit(exitCode);
			return;
		}

        runSendPendenzeGpdJob();
        log.info("{} completato.", Costanti.SEND_PENDENZE_GPD_JOBNAME);
        // Terminazione dell'applicazione
        int exitCode = SpringApplication.exit(context, () -> 0);
        System.exit(exitCode);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }
}
