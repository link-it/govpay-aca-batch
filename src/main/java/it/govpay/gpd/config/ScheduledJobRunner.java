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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
    
    @Scheduled(fixedDelayString = "${scheduler.gpdSenderJob.fixedDelayString:600000}", initialDelayString = "${scheduler.initialDelayString:1}")
    public void runBatchPendenzeJob() throws Exception {
        log.info("Esecuzione schedulata di {}", Costanti.SEND_PENDENZE_GPD_JOBNAME);
        runSendPendenzeGpdJob();
    }
}
