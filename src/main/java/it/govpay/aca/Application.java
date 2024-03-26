package it.govpay.aca;

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
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import it.govpay.aca.costanti.Costanti;

@SpringBootApplication
@EnableScheduling
public class Application extends SpringBootServletInitializer {

	private Logger log = LoggerFactory.getLogger(Application.class);
	
	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
	
	@Autowired
	JobLauncher jobLauncher;

	@Autowired
	@Qualifier(Costanti.SEND_PENDENZE_ACA_JOBNAME)
	private Job pendenzaSenderJob;
	
	public void runSendPendenzeAcaJob() throws JobExecutionAlreadyRunningException, JobRestartException, JobInstanceAlreadyCompleteException, JobParametersInvalidException  {
		JobParameters params = new JobParametersBuilder()
				.addString(Costanti.GOVPAY_ACA_JOB_ID, Costanti.SEND_PENDENZE_ACA_JOBNAME)
				.addString(Costanti.GOVPAY_ACA_JOB_PARAMETER_WHEN,  OffsetDateTime.now().toString())
				.toJobParameters();
		jobLauncher.run(pendenzaSenderJob, params);
	}
	
	@Scheduled(fixedDelayString = "${scheduler.acaSenderJob.fixedDelayString:600000}", initialDelayString = "${scheduler.initialDelayString:1}")
	public void verifyMessagesJob() throws JobExecutionAlreadyRunningException, JobRestartException, JobInstanceAlreadyCompleteException, JobParametersInvalidException  {
		this.log.info("Running scheduled {}", Costanti.SEND_PENDENZE_ACA_JOBNAME);
		this.runSendPendenzeAcaJob();
	}
}
