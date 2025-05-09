package it.govpay.gpd.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.List;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import it.govpay.gpd.Application;
import it.govpay.gpd.client.api.DebtPositionActionsApiApi;
import it.govpay.gpd.client.api.DebtPositionsApiApi;
import it.govpay.gpd.client.api.impl.ApiClient;
import it.govpay.gpd.client.beans.PaymentOptionModel;
import it.govpay.gpd.client.beans.PaymentPositionModel;
import it.govpay.gpd.client.beans.PaymentPositionModelBaseResponse;
import it.govpay.gpd.client.beans.PaymentPositionModelBaseResponse.StatusEnum;
import it.govpay.gpd.client.beans.ProblemJson;
import it.govpay.gpd.client.beans.TransferMetadataModel;
import it.govpay.gpd.client.beans.TransferModel;
import it.govpay.gpd.gde.client.EventiApi;
import it.govpay.gpd.test.costanti.Costanti;
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
class UC_1_HappyPathTest extends UC_00_BaseTest {

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
		
		initailizeJobLauncherTestUtils();
	}
	
	@Test
	void TC_01_EmptyRunTest() throws Exception {
		Mockito.lenient()
		.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
				)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
					@Override
					public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
						return PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
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
		JobExecution jobExecution = jobLauncherTestUtils.launchJob();
		assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
	}

	@Test
	void TC_02_SendTestOk() throws Exception {
		try {

			// creazione versamento da spedire
			this.creaVersamentoNonEseguito();
			
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
	void TC_03_SendTest_KO() throws Exception {
		try {

			// creazione versamento da spedire
			this.creaVersamentoNonEseguitoDefinitoConIbanAppoggio();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<ProblemJson>>() {
						@Override
						public ResponseEntity<ProblemJson> answer(InvocationOnMock invocation) throws Throwable {
							return GpdUtils.creaResponseKo(invocation, HttpStatus.SERVICE_UNAVAILABLE);
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
	void TC_04_SendTestPendenzeMultipleOk() throws Exception {
		try {

			// creazione versamento da spedire
			this.creaVersamentoNonEseguito();
			this.creaVersamentoNonEseguitoDefinito();
			this.creaVersamentoNonEseguitoMBT();
			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			versamentoGpdEntity.setDataScadenza(OffsetDateTime.now().plusDays(30));
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
							return PaymentPositionModelUtils.creaResponseGetPositionOk(invocation, StatusEnum.VALID);
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

			assertEquals(4, this.versamentoFullRepository.count());
			assertEquals(4, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(4, this.versamentoRepository.count());

			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			assertEquals(4, this.versamentoFullRepository.count());
			assertEquals(0, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(4, this.versamentoRepository.count());

		} finally {
			this.cleanDB();
		}
	}
	
	@Test
	void TC_05_SendTestPendenzaConRataOk() throws Exception {
		try {

			// creazione versamento da spedire
			this.creaVersamentoNonEseguitoConRata(Costanti.COD_RATA_01);
			
			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							PaymentPositionModel paymentPositionModel = invocation.getArgument(1);
							List<PaymentOptionModel> paymentOption = paymentPositionModel.getPaymentOption();
							assertNotNull(paymentOption);
							assertEquals(true, paymentOption.get(0).isIsPartialPayment());
							
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
	void TC_06_SendTestPendenzaConMetadataOk() throws Exception {
		try {

			// creazione versamento da spedire
			this.creaVersamentoNonEseguitoConMetadata();
			
			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							PaymentPositionModel paymentPositionModel = invocation.getArgument(1);
							List<PaymentOptionModel> paymentOption = paymentPositionModel.getPaymentOption();
							assertNotNull(paymentOption);
							
							PaymentOptionModel paymentOptionModel = paymentOption.get(0);
							
							List<TransferModel> transferList = paymentOptionModel.getTransfer();
							assertNotNull(transferList);
							for (TransferModel transferModel : transferList) {
								List<TransferMetadataModel> transferMetadata = transferModel.getTransferMetadata();
								assertNotNull(transferMetadata);
								assertEquals(1, transferMetadata.size());
								
								TransferMetadataModel transferMetadataModel = transferMetadata.get(0);
								assertEquals("chiave", transferMetadataModel.getKey());
								assertEquals("valore", transferMetadataModel.getValue());
							}
							
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
	void TC_07_SendTestPendenzaMultivoceOk() throws Exception {
		try {

			// creazione versamento da spedire
			this.creaVersamentoNonEseguitoMultivoceDefinito();
			
			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							PaymentPositionModel paymentPositionModel = invocation.getArgument(1);
							List<PaymentOptionModel> paymentOption = paymentPositionModel.getPaymentOption();
							assertNotNull(paymentOption);
							
							PaymentOptionModel paymentOptionModel = paymentOption.get(0);
							
							List<TransferModel> transferList = paymentOptionModel.getTransfer();
							assertNotNull(transferList);
							assertEquals(2, transferList.size());
							
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
	void TC_08_SendTestPendenzaSenzaIUV() throws Exception {
		try {

			// creazione versamento da spedire
			VersamentoFullEntity creaVersamentoNonEseguitoMultivoceDefinito = this.creaVersamentoNonEseguitoMultivoceDefinito();
			
			creaVersamentoNonEseguitoMultivoceDefinito.setIuvVersamento(null);
			
			this.versamentoFullRepository.save(creaVersamentoNonEseguitoMultivoceDefinito);
			
			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							PaymentPositionModel paymentPositionModel = invocation.getArgument(1);
							List<PaymentOptionModel> paymentOption = paymentPositionModel.getPaymentOption();
							assertNotNull(paymentOption);
							
							PaymentOptionModel paymentOptionModel = paymentOption.get(0);
							
							List<TransferModel> transferList = paymentOptionModel.getTransfer();
							assertNotNull(transferList);
							assertEquals(2, transferList.size());
							
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

			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			assertEquals(1, this.versamentoFullRepository.count());
			assertEquals(0, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.cleanDB();
		}
	}
}
