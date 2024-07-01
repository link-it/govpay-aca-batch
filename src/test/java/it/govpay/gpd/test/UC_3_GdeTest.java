package it.govpay.gpd.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.gpd.Application;
import it.govpay.gpd.client.api.DebtPositionActionsApiApi;
import it.govpay.gpd.client.api.DebtPositionsApiApi;
import it.govpay.gpd.client.api.impl.ApiClient;
import it.govpay.gpd.client.beans.PaymentPositionModel;
import it.govpay.gpd.client.beans.PaymentPositionModelBaseResponse;
import it.govpay.gpd.client.beans.PaymentPositionModelBaseResponse.StatusEnum;
import it.govpay.gpd.client.beans.ProblemJson;
import it.govpay.gpd.gde.client.EventiApi;
import it.govpay.gpd.test.entity.VersamentoFullEntity;
import it.govpay.gpd.test.utils.GdeProblemUtils;
import it.govpay.gpd.test.utils.GpdUtils;
import it.govpay.gpd.test.utils.ObjectMapperUtils;
import it.govpay.gpd.test.utils.PaymentPositionModelUtils;
import it.govpay.gpd.test.utils.VersamentoUtils;


@SpringBootTest(classes = Application.class)
@EnableAutoConfiguration
@DisplayName("Test Invio GPD e GDE")
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
@SpringBatchTest
class UC_3_GdeTest extends UC_00_BaseTest {

	@Autowired
	@MockBean(name = "gpdApi")
	DebtPositionsApiApi gpdApi;
	
	@Autowired
	@MockBean(name = "gpdActionsApi")
	DebtPositionActionsApiApi gpdActionsApi;
	
	@Autowired
	@MockBean
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
	
	private ObjectMapper mapper = ObjectMapperUtils.createObjectMapper();
	
	HttpResponse<InputStream> gdeMockHttpResponseOk;
	HttpResponse<InputStream> gdeMockHttpResponse503;
	HttpResponse<InputStream> gdeMockHttpResponse400;

	private void initailizeJobLauncherTestUtils() throws Exception {
		jobLauncherTestUtils.setJob(job);
	}
	
	@AfterEach
    void tearDown() {
        jobRepositoryTestUtils.removeJobExecutions();
    }

	@BeforeEach
	void setUp() throws URISyntaxException, JsonProcessingException {
		MockitoAnnotations.openMocks(this);
		this.versamentoFullRepository.deleteAll();

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
		gdeMockHttpResponseOk = Mockito.mock(HttpResponse.class);

		// Configurazione del comportamento del mock
		Mockito.lenient().when(gdeMockHttpResponseOk.statusCode()).thenReturn(200);
		Mockito.lenient().when(gdeMockHttpResponseOk.body()).thenReturn(new ByteArrayInputStream("".getBytes()));

		// Creazione del mock della HttpResponse
		gdeMockHttpResponse503 = Mockito.mock(HttpResponse.class);
		
		// Configurazione del comportamento del mock
		Mockito.lenient().when(gdeMockHttpResponse503.statusCode()).thenReturn(503);
		Mockito.lenient().when(gdeMockHttpResponse503.body()).thenReturn(new ByteArrayInputStream(this.mapper.writeValueAsString(GdeProblemUtils.createProblem503()).getBytes()));

		// Creazione del mock della HttpResponse
		gdeMockHttpResponse400 = Mockito.mock(HttpResponse.class);
		
		// Configurazione del comportamento del mock
		Mockito.lenient().when(gdeMockHttpResponse400.statusCode()).thenReturn(400);
		Mockito.lenient().when(gdeMockHttpResponse400.body()).thenReturn(new ByteArrayInputStream(this.mapper.writeValueAsString(GdeProblemUtils.createProblem400()).getBytes()));
	}

	@Test
	void TC_01_SendTest_GpdOk() throws Exception {
		try {

			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<PaymentPositionModel> mockResponseEntity = PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
							return mockResponseEntity;
						}
					});

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(gdeMockHttpResponseOk);
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
			this.versamentoFullRepository.deleteAll();
		}
	}

	@Test
	void TC_02_SendTest_GpdKO() throws Exception {
		try {

			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<ProblemJson>>() {
						@Override
						public ResponseEntity<ProblemJson> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<ProblemJson> mockResponseEntity = GpdUtils.creaResponseKo(invocation, HttpStatus.SERVICE_UNAVAILABLE);

							return mockResponseEntity;
						}
					});

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(gdeMockHttpResponseOk);
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
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_03_SendTest_Gde400BadRequest_WithProblem() throws Exception {
		try {

			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<PaymentPositionModel> mockResponseEntity = PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
							return mockResponseEntity;
						}
					});

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(gdeMockHttpResponse400);
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
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_04_SendTest_Gde400BadRequest_WithException() throws Exception {
		try {
			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()))
			.thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
				@Override
				public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
					ResponseEntity<PaymentPositionModel> mockResponseEntity = PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
					return mockResponseEntity;
				}
			});

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()))
			.thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.getReasonPhrase()));

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
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_05_SendTest_Gde503ServiceUnavailable_WithProblem() throws Exception {
		try {

			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<PaymentPositionModel> mockResponseEntity = PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
							return mockResponseEntity;
						}
					});

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(gdeMockHttpResponse503);
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
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_06_SendTest_Gde503ServiceUnavailable_WithException() throws Exception {
		try {
			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()))
			.thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
				@Override
				public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
					ResponseEntity<PaymentPositionModel> mockResponseEntity = PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
					return mockResponseEntity;
				}
			});

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()))
			.thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase()));

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
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_07_SendTest_Gde401Unauthorized_WithProblem() throws Exception {
		try {

			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
					.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<PaymentPositionModel> mockResponseEntity = PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
							return mockResponseEntity;
						}
					});

			Mockito.lenient()
					.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							HttpResponse<InputStream> mockHttpResponse401 = Mockito.mock(HttpResponse.class);
							Mockito.lenient().when(mockHttpResponse401.statusCode()).thenReturn(401);
							Mockito.lenient().when(mockHttpResponse401.body()).thenReturn(new ByteArrayInputStream(mapper.writeValueAsString(GdeProblemUtils.createProblem401()).getBytes()));

							return CompletableFuture.completedFuture(mockHttpResponse401);
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
			this.versamentoFullRepository.deleteAll();
		}
	}

	@Test
	void TC_08_SendTest_Gde401Unauthorized_WithException() throws Exception {
		try {
			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()))
			.thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
				@Override
				public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
					ResponseEntity<PaymentPositionModel> mockResponseEntity = PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
					return mockResponseEntity;
				}
			});

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()))
			.thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

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
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_09_SendTest_Gde403Forbidden_WithProblem() throws Exception {
		try {

			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
					.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<PaymentPositionModel> mockResponseEntity = PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
							return mockResponseEntity;
						}
					});

			Mockito.lenient()
					.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							HttpResponse<InputStream> mockHttpResponse403 = Mockito.mock(HttpResponse.class);
							Mockito.lenient().when(mockHttpResponse403.statusCode()).thenReturn(403);
							Mockito.lenient().when(mockHttpResponse403.body()).thenReturn(new ByteArrayInputStream(mapper.writeValueAsString(GdeProblemUtils.createProblem403()).getBytes()));

							return CompletableFuture.completedFuture(mockHttpResponse403);
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
			this.versamentoFullRepository.deleteAll();
		}
	}

	@Test
	void TC_10_SendTest_Gde403Forbidden_WithException() throws Exception {
		try {
			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()))
			.thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
				@Override
				public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
					ResponseEntity<PaymentPositionModel> mockResponseEntity = PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
					return mockResponseEntity;
				}
			});

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()))
			.thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN, "Forbidden"));

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
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_11_SendTest_Gde404NotFound_WithProblem() throws Exception {
		try {

			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
					.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<PaymentPositionModel> mockResponseEntity = PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
							return mockResponseEntity;
						}
					});

			Mockito.lenient()
					.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							HttpResponse<InputStream> mockHttpResponse404 = Mockito.mock(HttpResponse.class);
							Mockito.lenient().when(mockHttpResponse404.statusCode()).thenReturn(409);
							Mockito.lenient().when(mockHttpResponse404.body()).thenReturn(new ByteArrayInputStream(mapper.writeValueAsString(GdeProblemUtils.createProblem404()).getBytes()));

							return CompletableFuture.completedFuture(mockHttpResponse404);
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
			this.versamentoFullRepository.deleteAll();
		}
	}

	@Test
	void TC_12_SendTest_Gde404NotFound_WithException() throws Exception {
		try {
			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()))
			.thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
				@Override
				public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
					ResponseEntity<PaymentPositionModel> mockResponseEntity = PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
					return mockResponseEntity;
				}
			});

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()))
			.thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found"));

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
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_13_SendTest_Gde409Conflict_WithProblem() throws Exception {
		try {

			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			this.versamentoFullRepository.save(versamentoGpdEntity);
			
			Mockito.lenient()
					.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<PaymentPositionModel> mockResponseEntity = PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
							return mockResponseEntity;
						}
					});
			
			Mockito.lenient()
					.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							HttpResponse<InputStream> mockHttpResponse409 = Mockito.mock(HttpResponse.class);
							Mockito.lenient().when(mockHttpResponse409.statusCode()).thenReturn(409);
							Mockito.lenient().when(mockHttpResponse409.body()).thenReturn(new ByteArrayInputStream(mapper.writeValueAsString(GdeProblemUtils.createProblem409()).getBytes()));

							return CompletableFuture.completedFuture(mockHttpResponse409);
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
			this.versamentoFullRepository.deleteAll();
		}
	}

	@Test
	void TC_14_SendTest_Gde409Conflict_WithException() throws Exception {
		try {
			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()))
			.thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
				@Override
				public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
					ResponseEntity<PaymentPositionModel> mockResponseEntity = PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
					return mockResponseEntity;
				}
			});

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()))
			.thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT, "Conflict"));

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
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_15_SendTest_Gde429TooManyRequests_WithProblem() throws Exception {
		try {

			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
					.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<PaymentPositionModel> mockResponseEntity = PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
							return mockResponseEntity;
						}
					});

			Mockito.lenient()
					.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							HttpResponse<InputStream> mockHttpResponse403 = Mockito.mock(HttpResponse.class);
							Mockito.lenient().when(mockHttpResponse403.statusCode()).thenReturn(429);
							Mockito.lenient().when(mockHttpResponse403.body()).thenReturn(new ByteArrayInputStream(mapper.writeValueAsString(GdeProblemUtils.createProblem429()).getBytes()));

							return CompletableFuture.completedFuture(mockHttpResponse403);
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
			this.versamentoFullRepository.deleteAll();
		}
	}

	@Test
	void TC_16_SendTest_Gde429TooManyRequests_WithException() throws Exception {
		try {
			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()))
			.thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
				@Override
				public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
					ResponseEntity<PaymentPositionModel> mockResponseEntity = PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
					return mockResponseEntity;
				}
			});

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()))
			.thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests"));

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
			this.versamentoFullRepository.deleteAll();
		}
	}

	
	@Test
	void TC_17_SendTest_GpdKO_400BadRequest_WithProblem() throws Exception {
		try {

			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<ProblemJson>>() {
						@Override
						public ResponseEntity<ProblemJson> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<ProblemJson> mockResponseEntity = GpdUtils.creaResponseKo(invocation, HttpStatus.BAD_REQUEST);

							return mockResponseEntity;
						}
					});

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(gdeMockHttpResponseOk);
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
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_18_SendTest_GpdKO_400BadRequest_WithException() throws Exception {
		try {

			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					))
					.thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request"));

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(gdeMockHttpResponseOk);
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
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_19_SendTest_GpdKO_404NotFound_WithProblem() throws Exception {
		try {

			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<ProblemJson>>() {
						@Override
						public ResponseEntity<ProblemJson> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<ProblemJson> mockResponseEntity = GpdUtils.creaResponseKo(invocation, HttpStatus.NOT_FOUND);

							return mockResponseEntity;
						}
					});

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(gdeMockHttpResponseOk);
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
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_20_SendTest_GpdKO_404NotFound_WithException() throws Exception {
		try {

			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					))
					.thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found"));

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(gdeMockHttpResponseOk);
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
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_21_SendTest_GpdKO_409Conflict_WithProblem() throws Exception {
		try {

			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<ProblemJson>>() {
						@Override
						public ResponseEntity<ProblemJson> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<ProblemJson> mockResponseEntity = GpdUtils.creaResponseKo(invocation, HttpStatus.CONFLICT);

							return mockResponseEntity;
						}
					});
			
			Mockito.lenient()
			.when(gpdApi.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModelBaseResponse>>() {
						@Override
						public ResponseEntity<PaymentPositionModelBaseResponse> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<PaymentPositionModelBaseResponse> mockResponseEntity = PaymentPositionModelUtils.creaResponseGetPositionOk(invocation, StatusEnum.DRAFT);

							return mockResponseEntity;
						}
					});
			
			Mockito.lenient()
			.when(gpdActionsApi.publishPositionWithHttpInfo(any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<PaymentPositionModel> mockResponseEntity = PaymentPositionModelUtils.creaResponsePublishPositionOk(invocation);
							return mockResponseEntity;
						}
					});

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(gdeMockHttpResponseOk);
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
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_22_SendTest_GpdKO_409Conflict_WithException() throws Exception {
		try {

			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					))
					.thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT, "Conflict"));
			
			Mockito.lenient()
			.when(gpdApi.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModelBaseResponse>>() {
						@Override
						public ResponseEntity<PaymentPositionModelBaseResponse> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<PaymentPositionModelBaseResponse> mockResponseEntity = PaymentPositionModelUtils.creaResponseGetPositionOk(invocation, StatusEnum.DRAFT);

							return mockResponseEntity;
						}
					});
			
			Mockito.lenient()
			.when(gpdActionsApi.publishPositionWithHttpInfo(any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<PaymentPositionModel> mockResponseEntity = PaymentPositionModelUtils.creaResponsePublishPositionOk(invocation);
							return mockResponseEntity;
						}
					});

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(gdeMockHttpResponseOk);
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
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_23_SendTest_GpdKO_500InternalServerError_WithProblem() throws Exception {
		try {

			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<ProblemJson>>() {
						@Override
						public ResponseEntity<ProblemJson> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<ProblemJson> mockResponseEntity = GpdUtils.creaResponseKo(invocation, HttpStatus.INTERNAL_SERVER_ERROR);

							return mockResponseEntity;
						}
					});

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(gdeMockHttpResponseOk);
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
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_24_SendTest_GpdKO_500InternalServerError_WithException() throws Exception {
		try {

			VersamentoFullEntity versamentoGpdEntity = this.creaVersamentoNonEseguito();
			this.versamentoFullRepository.save(versamentoGpdEntity);

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					))
					.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error"));

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(gdeMockHttpResponseOk);
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
			this.versamentoFullRepository.deleteAll();
		}
	}
}
