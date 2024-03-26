package it.govpay.aca.costanti;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Costanti {

	public static final String SEND_PENDENZE_ACA_STEPNAME = "spedizionePendenzeStep";
	public static final String SEND_PENDENZE_ACA_JOBNAME= "acaSenderJob";
	
	public static final String MSG_SENDER_TASK_EXECUTOR_NAME = "spring_batch_msgsender"; 
	
	public static final String GOVPAY_ACA_JOB_ID = "GovPay_ACA_JobID";
	public static final String GOVPAY_ACA_JOB_PARAMETER_WHEN = "When";
}