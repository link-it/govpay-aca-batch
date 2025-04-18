package it.govpay.gpd.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;

import it.govpay.gpd.Application;
import it.govpay.gpd.client.api.DebtPositionActionsApiApi;
import it.govpay.gpd.client.api.DebtPositionsApiApi;
import it.govpay.gpd.client.api.impl.ApiClient;
import it.govpay.gpd.client.beans.PaymentPositionModel;
import it.govpay.gpd.client.beans.PaymentPositionModelBaseResponse;
import it.govpay.gpd.client.beans.PaymentPositionModelBaseResponse.StatusEnum;
import it.govpay.gpd.client.beans.ProblemJson;
import it.govpay.gpd.entity.VersamentoGpdEntity.StatoVersamento;
import it.govpay.gpd.gde.client.EventiApi;
import it.govpay.gpd.test.entity.VersamentoFullEntity;
import it.govpay.gpd.test.utils.GpdUtils;
import it.govpay.gpd.test.utils.PaymentPositionModelUtils;
import it.govpay.gpd.test.utils.VersamentoUtils;


@SpringBootTest(classes = Application.class)
@EnableAutoConfiguration
@DisplayName("Test Invio GPD")
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
@SpringBatchTest
class UC_2_AggiornamentiTest extends UC_00_BaseTest {

	@Autowired
	@MockitoBean(name = "gpdApi")
	DebtPositionsApiApi gpdApi;
	
	@Autowired
	@MockitoBean(name = "gpdActionsApi")
	DebtPositionActionsApiApi gpdActionsApi;
	
	@Autowired
	@MockitoBean
	EventiApi gdeApi;

	@Autowired
	private JobLauncherTestUtils jobLauncherTestUtils;

	@Autowired
	private JobRepositoryTestUtils jobRepositoryTestUtils;

	@Autowired
	@Qualifier(value = "gpdSenderJob")
	private Job job;

	@Value("${it.govpay.gpd.batch.client.baseUrl}")
	String gpdBaseUrl;
	
	@Value("${it.govpay.gpd.batch.dbreader.sogliaTemporaleRicercaPendenze.numeroGiorni:7}")
	private Integer numeroGiorni;
	
	HttpResponse<InputStream> mockHttpResponseOk;
	

	private void initailizeJobLauncherTestUtils() {
		jobLauncherTestUtils.setJob(job);
	}
	
	@AfterEach
    void tearDown() {
        jobRepositoryTestUtils.removeJobExecutions();
    }

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		this.cleanDB();
		
		Mockito.lenient()
		.when(gpdApi.getApiClient()).thenAnswer(new Answer<ApiClient>() {

			@Override
			public ApiClient answer(InvocationOnMock invocation) throws Throwable {
				ApiClient apiClient = new ApiClient();
				apiClient.setBasePath(gpdBaseUrl);
				return apiClient;
			}
		});
		
		Mockito.lenient()
		.when(gpdActionsApi.getApiClient()).thenAnswer(new Answer<ApiClient>() {

			@Override
			public ApiClient answer(InvocationOnMock invocation) throws Throwable {
				ApiClient apiClient = new ApiClient();
				apiClient.setBasePath(gpdBaseUrl);
				return apiClient;
			}
		});
		
		// Creazione del mock della HttpResponse
		mockHttpResponseOk = Mockito.mock(HttpResponse.class);

		// Configurazione del comportamento del mock
		Mockito.lenient().when(mockHttpResponseOk.statusCode()).thenReturn(200);
		Mockito.lenient().when(mockHttpResponseOk.body()).thenReturn(new ByteArrayInputStream("".getBytes()));
	}

	@Test
	void TC_01_SendTest_CambiStatoOk() throws Exception {
		try {

			// caricamento presso GPD versamento in stato non eseguito
			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
						}
					});
			
			Mockito.lenient()
			.when(gpdApi.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModelBaseResponse>>() {
						@Override
						public ResponseEntity<PaymentPositionModelBaseResponse> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseGetPositionOk(invocation, StatusEnum.VALID);
						}
					});
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});

			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(0, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			// caricamento presso GPD versamento in stato eseguito
			versamentoGpdEntity.setStatoVersamento(StatoVersamento.ESEGUITO);
			versamentoGpdEntity.setDataUltimaComunicazioneAca(versamentoGpdEntity.getDataUltimaModificaAca().minusMinutes(2));
			this.versamentoFullRepository.save(versamentoGpdEntity);
			this.checkVersamentoFullEntity(versamentoGpdEntity);
			
			// versamenti eseguiti non devono essere inviati
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(0, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
		} finally {
			this.cleanDB();
		}
	}
	
	@Test
	void TC_02_SendTest_DataValiditaOk() throws Exception {
		try {

			// caricamento presso GPD versamento in stato non eseguito
			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			versamentoGpdEntity.setDataValidita(OffsetDateTime.now().plusMonths(1));
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
						}
					});
			
			Mockito.lenient()
			.when(gpdApi.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModelBaseResponse>>() {
						@Override
						public ResponseEntity<PaymentPositionModelBaseResponse> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseGetPositionOk(invocation, StatusEnum.DRAFT);
						}
					});
			
			Mockito.lenient()
			.when(gpdActionsApi.publishPositionWithHttpInfo(any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponsePublishPositionOk(invocation);
						}
					});
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});

			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(0, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
		} finally {
			this.cleanDB();
		}
	}
	
	@Test
	void TC_03_SendTest_DataScadenzaOk() throws Exception {
		try {

			// caricamento presso GPD versamento in stato non eseguito
			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			versamentoGpdEntity.setDataScadenza(OffsetDateTime.now().plusMonths(1));
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
						}
					});
			
			Mockito.lenient()
			.when(gpdApi.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModelBaseResponse>>() {
						@Override
						public ResponseEntity<PaymentPositionModelBaseResponse> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseGetPositionOk(invocation, StatusEnum.DRAFT);
						}
					});
			
			Mockito.lenient()
			.when(gpdActionsApi.publishPositionWithHttpInfo(any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponsePublishPositionOk(invocation);
						}
					});
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});

			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(0, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
		} finally {
			this.cleanDB();
		}
	}
	
	@Test
	void TC_04_SendTest_UpdateValidityDateOk() throws Exception {
		try {

			// caricamento presso GPD versamento in stato non eseguito
			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			versamentoGpdEntity.setDataScadenza(OffsetDateTime.now().plusMonths(1));
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
						}
					});
			
			Mockito.lenient()
			.when(gpdApi.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModelBaseResponse>>() {
						@Override
						public ResponseEntity<PaymentPositionModelBaseResponse> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseGetPositionOk(invocation, StatusEnum.DRAFT);
						}
					});
			
			Mockito.lenient()
			.when(gpdActionsApi.publishPositionWithHttpInfo(any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponsePublishPositionOk(invocation);
						}
					});
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});

			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(0, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			// update pendenza e nuovo giro batch
			versamentoGpdEntity = this.versamentoFullRepository.findById(versamentoGpdEntity.getId()).get();
			versamentoGpdEntity.setDataUltimaComunicazioneAca(versamentoGpdEntity.getDataUltimaModificaAca().minusMinutes(1));
			this.versamentoFullRepository.save(versamentoGpdEntity);
			this.checkVersamentoFullEntity(versamentoGpdEntity);
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			Mockito.lenient()
			.when(gpdApi.updatePositionWithHttpInfo(any(), any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseUpdatePaymentPositionModelOk(invocation);
						}
					});
			
			Mockito.lenient()
			.when(gpdActionsApi.publishPositionWithHttpInfo(any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponsePublishPositionOk(invocation);
						}
					});
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});
			
			initailizeJobLauncherTestUtils();
			jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(0, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
		} finally {
			this.cleanDB();
		}
	}
	
	@Test
	void TC_05_SendTest_UpdateNoValidityDateOk() throws Exception {
		try {

			// caricamento presso GPD versamento in stato non eseguito
			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
						}
					});
			
			Mockito.lenient()
			.when(gpdApi.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModelBaseResponse>>() {
						@Override
						public ResponseEntity<PaymentPositionModelBaseResponse> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseGetPositionOk(invocation, StatusEnum.VALID);
						}
					});
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});

			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(0, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			// update pendenza e nuovo giro batch
			versamentoGpdEntity = this.versamentoFullRepository.findById(versamentoGpdEntity.getId()).get();
			versamentoGpdEntity.setDataUltimaComunicazioneAca(versamentoGpdEntity.getDataUltimaModificaAca().minusMinutes(1));
			this.versamentoFullRepository.save(versamentoGpdEntity);
			this.checkVersamentoFullEntity(versamentoGpdEntity);
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			Mockito.lenient()
			.when(gpdApi.updatePositionWithHttpInfo(any(), any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseUpdatePaymentPositionModelOk(invocation);
						}
					});
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});
			
			initailizeJobLauncherTestUtils();
			jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(0, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
		} finally {
			this.cleanDB();
		}
	}
	
	@Test
	void TC_06_SendTest_AnnullamentoOk() throws Exception {
		try {

			// caricamento presso GPD versamento in stato non eseguito
			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			versamentoGpdEntity.setDataScadenza(OffsetDateTime.now().plusMonths(1));
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
						}
					});
			
			Mockito.lenient()
			.when(gpdApi.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModelBaseResponse>>() {
						@Override
						public ResponseEntity<PaymentPositionModelBaseResponse> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseGetPositionOk(invocation, StatusEnum.DRAFT);
						}
					});
			
			Mockito.lenient()
			.when(gpdActionsApi.publishPositionWithHttpInfo(any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponsePublishPositionOk(invocation);
						}
					});
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});

			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(0, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			// update pendenza e nuovo giro batch
			versamentoGpdEntity = this.versamentoFullRepository.findById(versamentoGpdEntity.getId()).get();
			versamentoGpdEntity.setStatoVersamento(StatoVersamento.ANNULLATO);
			versamentoGpdEntity.setDataUltimaComunicazioneAca(versamentoGpdEntity.getDataUltimaModificaAca().minusMinutes(1));
			this.versamentoFullRepository.save(versamentoGpdEntity);
			this.checkVersamentoFullEntity(versamentoGpdEntity);
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			
			Mockito.lenient()
			.when(gpdApi.updatePositionWithHttpInfo(any(), any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseUpdatePaymentPositionModelOk(invocation);
						}
					});
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});
			
			initailizeJobLauncherTestUtils();
			jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(0, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
		} finally {
			this.cleanDB();
		}
	}
	
	@Test
	void TC_07_SendTest_AnnullamentoKo() throws Exception {
		try {

			// caricamento presso GPD versamento in stato non eseguito
			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			versamentoGpdEntity.setDataScadenza(OffsetDateTime.now().plusMonths(1));
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
						}
					});
			
			Mockito.lenient()
			.when(gpdApi.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModelBaseResponse>>() {
						@Override
						public ResponseEntity<PaymentPositionModelBaseResponse> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseGetPositionOk(invocation, StatusEnum.DRAFT);
						}
					});
			
			Mockito.lenient()
			.when(gpdActionsApi.publishPositionWithHttpInfo(any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponsePublishPositionOk(invocation);
						}
					});
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});

			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(0, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			// update pendenza e nuovo giro batch
			versamentoGpdEntity = this.versamentoFullRepository.findById(versamentoGpdEntity.getId()).get();
			versamentoGpdEntity.setStatoVersamento(StatoVersamento.ANNULLATO);
			versamentoGpdEntity.setDataUltimaComunicazioneAca(versamentoGpdEntity.getDataUltimaModificaAca().minusMinutes(1));
			this.versamentoFullRepository.save(versamentoGpdEntity);
			this.checkVersamentoFullEntity(versamentoGpdEntity);
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			Mockito.lenient()
			.when(gpdApi.updatePositionWithHttpInfo(any(), any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<ProblemJson>>() {
						@Override
						public ResponseEntity<ProblemJson> answer(InvocationOnMock invocation) throws Throwable {
							return GpdUtils.creaResponseUpdateKo(invocation, HttpStatus.SERVICE_UNAVAILABLE);
						}
					});
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});
			
			initailizeJobLauncherTestUtils();
			jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
		} finally {
			this.cleanDB();
		}
	}
	
	@Test
	void TC_08_SendTest_PublishKo() throws Exception {
		try {

			// caricamento presso GPD versamento in stato non eseguito
			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			versamentoGpdEntity.setDataScadenza(OffsetDateTime.now().plusMonths(1));
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
						}
					});
			
			Mockito.lenient()
			.when(gpdApi.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModelBaseResponse>>() {
						@Override
						public ResponseEntity<PaymentPositionModelBaseResponse> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseGetPositionOk(invocation, StatusEnum.DRAFT);
						}
					});
			
			Mockito.lenient()
			.when(gpdActionsApi.publishPositionWithHttpInfo(any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<ProblemJson>>() {
						@Override
						public ResponseEntity<ProblemJson> answer(InvocationOnMock invocation) throws Throwable {
							return GpdUtils.creaResponseKo(invocation, HttpStatus.CONFLICT);
						}
					});
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});

			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
		} finally {
			this.cleanDB();
		}
	}
	
	@Test
	void TC_09_SendTest_Update_NotFound() throws Exception {
		try {

			// caricamento presso GPD versamento in stato non eseguito
			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
						}
					});
			
			Mockito.lenient()
			.when(gpdApi.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModelBaseResponse>>() {
						@Override
						public ResponseEntity<PaymentPositionModelBaseResponse> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseGetPositionOk(invocation, StatusEnum.VALID);
						}
					});
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});

			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(0, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			// update pendenza e nuovo giro batch
			versamentoGpdEntity = this.versamentoFullRepository.findById(versamentoGpdEntity.getId()).get();
			versamentoGpdEntity.setDataUltimaComunicazioneAca(versamentoGpdEntity.getDataUltimaModificaAca().minusMinutes(1));
			this.versamentoFullRepository.save(versamentoGpdEntity);
			this.checkVersamentoFullEntity(versamentoGpdEntity);
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			Mockito.lenient()
			.when(gpdApi.updatePositionWithHttpInfo(any(), any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<ProblemJson>>() {
						@Override
						public ResponseEntity<ProblemJson> answer(InvocationOnMock invocation) throws Throwable {
							return GpdUtils.creaResponseUpdateKo(invocation, HttpStatus.NOT_FOUND);
						}
					});
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});
			
			initailizeJobLauncherTestUtils();
			jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(0, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
		} finally {
			this.cleanDB();
		}
	}
	
	@Test
	void TC_10_SendTest_Update_Conflict() throws Exception {
		try {

			// caricamento presso GPD versamento in stato non eseguito
			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
						}
					});
			
			Mockito.lenient()
			.when(gpdApi.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModelBaseResponse>>() {
						@Override
						public ResponseEntity<PaymentPositionModelBaseResponse> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseGetPositionOk(invocation, StatusEnum.VALID);
						}
					});
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});

			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(0, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			// update pendenza e nuovo giro batch
			versamentoGpdEntity = this.versamentoFullRepository.findById(versamentoGpdEntity.getId()).get();
			versamentoGpdEntity.setDataUltimaComunicazioneAca(versamentoGpdEntity.getDataUltimaModificaAca().minusMinutes(1));
			this.versamentoFullRepository.save(versamentoGpdEntity);
			this.checkVersamentoFullEntity(versamentoGpdEntity);
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			Mockito.lenient()
			.when(gpdApi.updatePositionWithHttpInfo(any(), any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<ProblemJson>>() {
						@Override
						public ResponseEntity<ProblemJson> answer(InvocationOnMock invocation) throws Throwable {
							return GpdUtils.creaResponseUpdateKo(invocation, HttpStatus.CONFLICT);
						}
					});
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});
			
			initailizeJobLauncherTestUtils();
			jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(0, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
		} finally {
			this.cleanDB();
		}
	}
	
	@Test
	void TC_11_SendTest_Update_ServiceUnavailable() throws Exception {
		try {

			// caricamento presso GPD versamento in stato non eseguito
			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
						}
					});
			
			Mockito.lenient()
			.when(gpdApi.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModelBaseResponse>>() {
						@Override
						public ResponseEntity<PaymentPositionModelBaseResponse> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseGetPositionOk(invocation, StatusEnum.VALID);
						}
					});
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});

			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(0, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			// update pendenza e nuovo giro batch
			versamentoGpdEntity = this.versamentoFullRepository.findById(versamentoGpdEntity.getId()).get();
			versamentoGpdEntity.setDataUltimaComunicazioneAca(versamentoGpdEntity.getDataUltimaModificaAca().minusMinutes(1));
			this.versamentoFullRepository.save(versamentoGpdEntity);
			this.checkVersamentoFullEntity(versamentoGpdEntity);
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			Mockito.lenient()
			.when(gpdApi.updatePositionWithHttpInfo(any(), any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<ProblemJson>>() {
						@Override
						public ResponseEntity<ProblemJson> answer(InvocationOnMock invocation) throws Throwable {
							return GpdUtils.creaResponseUpdateKo(invocation, HttpStatus.SERVICE_UNAVAILABLE);
						}
					});
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});
			
			initailizeJobLauncherTestUtils();
			jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
		} finally {
			this.cleanDB();
		}
	}
	
	@Test
	void TC_12_SendTest_Update_NotFound_WithException() throws Exception {
		try {

			// caricamento presso GPD versamento in stato non eseguito
			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
						}
					});
			
			Mockito.lenient()
			.when(gpdApi.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModelBaseResponse>>() {
						@Override
						public ResponseEntity<PaymentPositionModelBaseResponse> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseGetPositionOk(invocation, StatusEnum.VALID);
						}
					});
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});

			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(0, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			// update pendenza e nuovo giro batch
			versamentoGpdEntity = this.versamentoFullRepository.findById(versamentoGpdEntity.getId()).get();
			versamentoGpdEntity.setDataUltimaComunicazioneAca(versamentoGpdEntity.getDataUltimaModificaAca().minusMinutes(1));
			this.versamentoFullRepository.save(versamentoGpdEntity);
			this.checkVersamentoFullEntity(versamentoGpdEntity);
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			Mockito.lenient()
			.when(gpdApi.updatePositionWithHttpInfo(any(), any(), any(), any(), any()
					)).thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found", new HttpHeaders(GpdUtils.getHeadersProblem("TransationID")), new byte[0], Charset.defaultCharset()));
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});
			
			initailizeJobLauncherTestUtils();
			jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(0, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
		} finally {
			this.cleanDB();
		}
	}
	
	@Test
	void TC_13_SendTest_Update_Conflict_WithException() throws Exception {
		try {

			// caricamento presso GPD versamento in stato non eseguito
			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
						}
					});
			
			Mockito.lenient()
			.when(gpdApi.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModelBaseResponse>>() {
						@Override
						public ResponseEntity<PaymentPositionModelBaseResponse> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseGetPositionOk(invocation, StatusEnum.VALID);
						}
					});
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});

			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(0, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			// update pendenza e nuovo giro batch
			versamentoGpdEntity = this.versamentoFullRepository.findById(versamentoGpdEntity.getId()).get();
			versamentoGpdEntity.setDataUltimaComunicazioneAca(versamentoGpdEntity.getDataUltimaModificaAca().minusMinutes(1));
			this.versamentoFullRepository.save(versamentoGpdEntity);
			this.checkVersamentoFullEntity(versamentoGpdEntity);
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			Mockito.lenient()
			.when(gpdApi.updatePositionWithHttpInfo(any(), any(), any(), any(), any()
					))
			.thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT, "Conflict"));
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});
			
			initailizeJobLauncherTestUtils();
			jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(0, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
		} finally {
			this.cleanDB();
		}
	}
	
	@Test
	void TC_14_SendTest_Update_ServiceUnavailable_WithException() throws Exception {
		try {

			// caricamento presso GPD versamento in stato non eseguito
			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
						}
					});
			
			Mockito.lenient()
			.when(gpdApi.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModelBaseResponse>>() {
						@Override
						public ResponseEntity<PaymentPositionModelBaseResponse> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseGetPositionOk(invocation, StatusEnum.VALID);
						}
					});
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});

			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(0, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			// update pendenza e nuovo giro batch
			versamentoGpdEntity = this.versamentoFullRepository.findById(versamentoGpdEntity.getId()).get();
			versamentoGpdEntity.setDataUltimaComunicazioneAca(versamentoGpdEntity.getDataUltimaModificaAca().minusMinutes(1));
			this.versamentoFullRepository.save(versamentoGpdEntity);
			this.checkVersamentoFullEntity(versamentoGpdEntity);
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			Mockito.lenient()
			.when(gpdApi.updatePositionWithHttpInfo(any(), any(), any(), any(), any()
					)).thenThrow(HttpClientErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", new HttpHeaders(GpdUtils.getHeadersProblem("TransationID")), objectMapper.writeValueAsBytes(GpdUtils.createProblem503()), StandardCharsets.UTF_8));
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});
			
			initailizeJobLauncherTestUtils();
			jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
			
			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
		} finally {
			this.cleanDB();
		}
	}
	
	@Test
	void TC_16_SendTest_Create_Forbidden() throws Exception {
		try {

			// creazione versamento da spedire
			this.creaVersamentoNonEseguitoDefinitoConIbanAppoggio();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<ProblemJson>>() {
						@Override
						public ResponseEntity<ProblemJson> answer(InvocationOnMock invocation) throws Throwable {
							return GpdUtils.creaResponseKo(invocation, HttpStatus.FORBIDDEN);
						}
					});
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});

			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());

			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.cleanDB();
		}
	}
	
	@Test
	void TC_15_SendTest_Create_Forbidden_WithException() throws Exception {
		try {

			// creazione versamento da spedire
			this.creaVersamentoNonEseguitoDefinitoConIbanAppoggio();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", new HttpHeaders(GpdUtils.getHeadersProblem("TransationID")), 
							objectMapper.writeValueAsBytes(GpdUtils.createProblem403()), StandardCharsets.UTF_8));

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});

			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());

			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.cleanDB();
		}
	}
	
	@Test
	void TC_17_SendTest_Get_NotFound_WithException() throws Exception {
		try {

			// caricamento presso GPD versamento in stato non eseguito
			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			versamentoGpdEntity = this.versamentoFullRepository.findById(versamentoGpdEntity.getId()).get();
			versamentoGpdEntity.setDataScadenza(versamentoGpdEntity.getDataCreazione().plusMonths(1));
			this.versamentoFullRepository.save(versamentoGpdEntity);
			this.checkVersamentoFullEntity(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							return PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
						}
					});
			
			Mockito.lenient()
			.when(gpdApi.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()
					)).thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found", new HttpHeaders(GpdUtils.getHeadersProblem("TransationID")), 
							objectMapper.writeValueAsBytes(GpdUtils.createProblem404()), Charset.defaultCharset()));
			
			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(mockHttpResponseOk);
						}
					});

			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());
			
			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
			
		} finally {
			this.cleanDB();
		}
	}
}
