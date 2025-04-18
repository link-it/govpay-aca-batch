package it.govpay.gpd.costanti;

import org.springframework.http.MediaType;

public class Costanti {

	private Costanti () {}
	
	public static final String PATTERN_YYYY_MM_DD_T_HH_MM_SS_MILLIS_VARIABILI_XXX = "yyyy-MM-dd'T'HH:mm:ss[.[SSSSSSSSS][SSSSSSSS][SSSSSSS][SSSSSS][SSSSS][SSSS][SSS][SS][S]XXX]";
	public static final String PATTERN_YYYY_MM_DD_T_HH_MM_SS_MILLIS_VARIABILI = "yyyy-MM-dd'T'HH:mm:ss[.[SSSSSSSSS][SSSSSSSS][SSSSSSS][SSSSSS][SSSSS][SSSS][SSS][SS][S]]";
	public static final String PATTERN_TIMESTAMP_3_YYYY_MM_DD_T_HH_MM_SS_SSSXXX = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
	public static final String PATTERN_TIMESTAMP_6_YYYY_MM_DD_T_HH_MM_SS_SSSXXX = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX";
	public static final String PATTERN_DATA_JSON_YYYY_MM_DD_T_HH_MM_SS_SSS = "yyyy-MM-dd'T'HH:mm:ss.SSS";


	public static final String SEND_PENDENZE_GPD_STEPNAME = "spedizionePendenzeStep";
	public static final String SEND_PENDENZE_GPD_JOBNAME= "gpdSenderJob";

	public static final String MSG_SENDER_TASK_EXECUTOR_NAME = "spring_batch_msgsender"; 

	public static final String GOVPAY_GPD_JOB_ID = "GovPay_GPD_JobID";
	public static final String GOVPAY_GPD_JOB_PARAMETER_WHEN = "When";
	public static final String GOVPAY_GPD_JOB_PARAMETER_CLUSTER_ID = "ClusterID";

	public static final String DEFAULT_TIME_ZONE = "Europe/Rome";

	// Paths
	public static final String EVENTI = "/eventi";
	public static final String ORGANIZATIONS_DEBT_POSITIONS = "/organizations/{organizationfiscalcode}/debtpositions";
	public static final String ORGANIZATIONS_DEBT_POSITIONS_IUPD = "/organizations/{organizationfiscalcode}/debtpositions/{iupd}";
	public static final String ORGANIZATIONS_DEBT_POSITIONS_IUPD_PUBLISH = "/organizations/{organizationfiscalcode}/debtpositions/{iupd}/publish";
	public static final String ORGANIZATIONS_DEBT_POSITIONS_IUPD_INVALIDATE = "/organizations/{organizationfiscalcode}/debtpositions/{iupd}/invalidate";

	// Operation IDs
	public static final String GET_ORGANIZATION_DEBT_POSITIONS = "getOrganizationDebtPositions";
	public static final String CREATE_POSITION = "createPosition";
	public static final String GET_ORGANIZATION_DEBT_POSITION_BY_IUPD = "getOrganizationDebtPositionByIUPD";
	public static final String UPDATE_POSITION = "updatePosition";
	public static final String DELETE_POSITION = "deletePosition";
	public static final String PUBLISH_POSITION = "publishPosition";
	public static final String HEALTH_CHECK = "healthCheck";

	public static final String HEADER_X_REQUEST_ID = "X-Request-Id";

	// Parameters
	public static final String ORGANIZATION_FISCAL_CODE = "organizationfiscalcode";
	public static final String IUPD = "iupd";
	public static final String LIMIT = "limit";
	public static final String PAGE = "page";
	public static final String DUE_DATE_FROM = "due_date_from";
	public static final String DUE_DATE_TO = "due_date_to";
	public static final String PAYMENT_DATE_FROM = "payment_date_from";
	public static final String PAYMENT_DATE_TO = "payment_date_to";
	public static final String STATUS = "status";
	public static final String ORDER_BY = "orderby";
	public static final String ORDERING = "ordering";
	public static final String TO_PUBLISH = "toPublish";

	public static final String GOVPAY_GDE_HEADER_ACCEPT = MediaType.APPLICATION_JSON_VALUE;
	public static final String GOVPAY_GDE_HEADER_CONTENT_TYPE = MediaType.APPLICATION_JSON_VALUE;

	public static final String GOVPAY_GDE_CLUSTER_ID = "govpay-gpd-batch";

	public static final String MSG_PAYLOAD_NON_SERIALIZZABILE = "Payload non serializzabile";


	public static final String EC= "EC";
	
	public static final String QUERY_RICERCA_PENDENZE_DA_CARICARE_ACA = "SELECT * FROM v_versamenti_gpd " +
            "WHERE stato_versamento IN ('NON_ESEGUITO','ANNULLATO') " + 
            "AND data_ultima_modifica_aca >= ? " +
            "AND (data_ultima_modifica_aca > data_ultima_comunicazione_aca OR data_ultima_comunicazione_aca IS NULL) " +
            "ORDER BY data_ultima_comunicazione_aca DESC";
}