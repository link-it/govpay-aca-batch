package it.govpay.aca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import it.govpay.aca.config.PendenzeACASenderJobConfig;
import it.govpay.aca.service.AcaBatchService;

@SpringBootApplication
@EnableScheduling
public class Application extends SpringBootServletInitializer {

	private Logger log = LoggerFactory.getLogger(Application.class);
	
	@Autowired
	AcaBatchService acaBatches;
	
	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
	
	@Scheduled(fixedDelayString = "${scheduler.acaSenderJob.fixedDelayString:600000}", initialDelayString = "${scheduler.initialDelayString:1}")
	public void verifyMessagesJob() throws Exception {
		this.log.info("Running scheduled {}", PendenzeACASenderJobConfig.SEND_PENDENZE_ACA_JOBNAME);
		this.acaBatches.runSendPendenzeAcaJob();
	}

//	@Scheduled(cron = "0 */1 * * * ?")
//	public void perform() throws JobExecutionAlreadyRunningException, JobRestartException, JobInstanceAlreadyCompleteException, JobParametersInvalidException  {
//		JobParameters params = new JobParametersBuilder()
//				.addString("JobID", String.valueOf(System.currentTimeMillis()))
//				.toJobParameters();
//		jobLauncher.run(job, params);
//	}
}
