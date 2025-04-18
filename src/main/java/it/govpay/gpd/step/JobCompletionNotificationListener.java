package it.govpay.gpd.step;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

@Component
public class JobCompletionNotificationListener implements JobExecutionListener {

	private static final Logger logger = LoggerFactory.getLogger(JobCompletionNotificationListener.class);

	private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");


	@Override
	public void beforeJob(JobExecution jobExecution) {
		String jobName = jobExecution.getJobInstance().getJobName();
		LocalDateTime startTime = jobExecution.getStartTime();
		String format =  startTime != null ? startTime.format(formatter) : "-";
		logger.info("Iniziata esecuzione Job: {}, {}", jobName, format);
	}

	@Override
	public void afterJob(JobExecution jobExecution) {
		String jobName = jobExecution.getJobInstance().getJobName();
		LocalDateTime startTime = jobExecution.getStartTime();
		LocalDateTime endTime = jobExecution.getEndTime();
		String formatEndTime = endTime != null ? endTime.format(formatter) : "--";
		String durata = "--";
		if (startTime != null && endTime != null) {
			durata = String.valueOf(java.time.Duration.between(startTime, endTime).toSeconds());
		}
		logger.info("Completata esecuzione del Job [{}] alle ore [{}] Status: [{}], durata esecuzione [{}s]", jobName, formatEndTime, jobExecution.getStatus(), durata);
	}
}