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
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
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
    
    @Autowired
    private PreventConcurrentJobLauncher preventConcurrentJobLauncher;
    
    @Autowired
    private JobLauncher jobLauncher;
    
    @Autowired
    @Qualifier(Costanti.SEND_PENDENZE_GPD_JOBNAME)
    private Job pendenzaSenderJob;
    
    @Value("${it.govpay.gpd.batch.clusterId:GovPay-ACA-Batch}")
	private String clusterId;
    
    private void runSendPendenzeGpdJob() throws JobExecutionAlreadyRunningException, JobRestartException, JobInstanceAlreadyCompleteException, JobParametersInvalidException {
        JobParameters params = new JobParametersBuilder()
                .addString(Costanti.GOVPAY_GPD_JOB_ID, Costanti.SEND_PENDENZE_GPD_JOBNAME)
                .addString(Costanti.GOVPAY_GPD_JOB_PARAMETER_WHEN, OffsetDateTime.now().toString())
                .addString(Costanti.GOVPAY_GDE_CLUSTER_ID, this.clusterId)
                .toJobParameters();
        jobLauncher.run(pendenzaSenderJob, params);
    }
    
    @Override
    public void run(String... args) throws Exception {
        log.info("Avvio {} da command line", Costanti.SEND_PENDENZE_GPD_JOBNAME);
        
        JobExecution currentRunningJobExecution = this.preventConcurrentJobLauncher.getCurrentRunningJobExecution(Costanti.SEND_PENDENZE_GPD_JOBNAME);
        
		if (currentRunningJobExecution != null) {
			// Estrai il clusterid dell'esecuzione corrente
            Map<String, JobParameter<?>> runningParams = currentRunningJobExecution.getJobParameters().getParameters();
            String runningClusterId = runningParams.containsKey(Costanti.GOVPAY_GDE_CLUSTER_ID) ? runningParams.get(Costanti.GOVPAY_GDE_CLUSTER_ID).getValue().toString() : null;

            if (runningClusterId != null && !runningClusterId.equals(this.clusterId)) {
                log.info("Il job {} è in esecuzione su un altro nodo ({}). Uscita.", Costanti.SEND_PENDENZE_GPD_JOBNAME, runningClusterId);
            } else {
            	log.warn("Il job {} è ancora in esecuzione sul nodo corrente ({}). Uscita.", Costanti.SEND_PENDENZE_GPD_JOBNAME, runningClusterId);
            }
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
