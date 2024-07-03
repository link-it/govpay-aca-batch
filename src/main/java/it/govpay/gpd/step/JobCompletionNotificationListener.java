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
		LocalDateTime endTime = jobExecution.getEndTime();
		String format = endTime != null ? endTime.format(formatter) : "-";
		logger.info("Completata esecuzione del Job {}, {} Status: {}", jobName, format, jobExecution.getStatus());
	}
}