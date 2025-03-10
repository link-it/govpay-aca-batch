package it.govpay.gpd.config;

import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
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
    private JobLauncher jobLauncher;
    
    @Autowired
    @Qualifier(Costanti.SEND_PENDENZE_GPD_JOBNAME)
    private Job pendenzaSenderJob;
    
    private void runSendPendenzeGpdJob() throws JobExecutionAlreadyRunningException, JobRestartException, JobInstanceAlreadyCompleteException, JobParametersInvalidException {
        JobParameters params = new JobParametersBuilder()
                .addString(Costanti.GOVPAY_GPD_JOB_ID, Costanti.SEND_PENDENZE_GPD_JOBNAME)
                .addString(Costanti.GOVPAY_GPD_JOB_PARAMETER_WHEN, OffsetDateTime.now().toString())
                .toJobParameters();
        jobLauncher.run(pendenzaSenderJob, params);
    }
    
    @Override
    public void run(String... args) throws Exception {
        log.info("Avvio {} da command line", Costanti.SEND_PENDENZE_GPD_JOBNAME);
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
