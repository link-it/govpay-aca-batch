package it.govpay.aca.service;

import java.util.Date;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import it.govpay.aca.config.PendenzeACASenderJobConfig;

@Service
public class AcaBatchService {

	private static final String CURRENTDATE_STRING = "CurrentDate";
	private static final String GOVPAY_ACA_JOB_ID = "GovPay_ACA_JobID";
	public static final String GOVPAY_ACA_JOB_PARAMETER_WHEN = "When";

	@Autowired
	JobLauncher jobLauncher;
	
	@Autowired
	JobRepository jobRepository;
	
	@Autowired
	JobExplorer jobExplorer;
	
	@Autowired
	JobOperator jobOperator;

	@Autowired
	@Qualifier(PendenzeACASenderJobConfig.SEND_PENDENZE_ACA_JOBNAME)
	private Job pendenzaSenderJob;
	
	public void runSendPendenzeAcaJob() throws JobExecutionAlreadyRunningException, JobRestartException, JobInstanceAlreadyCompleteException, JobParametersInvalidException  {
		JobParameters params = new JobParametersBuilder()
				.addString(GOVPAY_ACA_JOB_ID, PendenzeACASenderJobConfig.SEND_PENDENZE_ACA_JOBNAME)
				.addString(GOVPAY_ACA_JOB_PARAMETER_WHEN,  String.valueOf(System.currentTimeMillis()))
				.addDate(CURRENTDATE_STRING, new Date())
				.toJobParameters();
		jobLauncher.run(pendenzaSenderJob, params);
	}
}
