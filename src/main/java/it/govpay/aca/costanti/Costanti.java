package it.govpay.aca.costanti;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

public class Costanti {
	
	private Costanti () {}

	public static final String SEND_PENDENZE_ACA_STEPNAME = "spedizionePendenzeStep";
	public static final String SEND_PENDENZE_ACA_JOBNAME= "acaSenderJob";
	
	public static final String MSG_SENDER_TASK_EXECUTOR_NAME = "spring_batch_msgsender"; 
	
	public static final String GOVPAY_ACA_JOB_ID = "GovPay_ACA_JobID";
	public static final String GOVPAY_ACA_JOB_PARAMETER_WHEN = "When";
	
	public static final String DEFAULT_TIME_ZONE = "Europe/Rome";
	
	
	/* Costanti relative all'evento di invio all'aca da impostare per il salvataggio dell'evento */
	public static final String GOVPAY_GDE_NOME_EVENTO_PA_CREATE_POSITION = "paCreatePosition";
	public static final String GOVPAY_GDE_PATH_SERVIZIO_PA_CREATE_POSITION = "/paCreatePosition";
	public static final String GOVPAY_GDE_HEADER_ACCEPT_PA_CREATE_POSITION = MediaType.APPLICATION_JSON_VALUE;
	public static final String GOVPAY_GDE_HEADER_CONTENT_TYPE_PA_CREATE_POSITION = MediaType.APPLICATION_JSON_VALUE;
	public static final String GOVPAY_GDE_METHOD_PA_CREATE_POSITION = HttpMethod.POST.name();
	public static final String GOVPAY_GDE_CLUSTER_ID = "govpay-aca-batch";
	
	public static final String MSG_PAYLOAD_NON_SERIALIZZABILE = "Payload non serializzabile";
}