package it.govpay.aca.test;

import static org.mockito.ArgumentMatchers.any;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import org.junit.Assert;
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
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.test.JobLauncherTestUtils;
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

import it.govpay.aca.Application;
import it.govpay.aca.client.api.AcaApi;
import it.govpay.aca.client.api.impl.ApiClient;
import it.govpay.aca.client.beans.ProblemJson;
import it.govpay.aca.client.gde.EventiApi;
import it.govpay.aca.repository.VersamentoAcaRepository;
import it.govpay.aca.repository.VersamentoRepository;
import it.govpay.aca.test.entity.VersamentoFullEntity;
import it.govpay.aca.test.repository.ApplicazioneRepository;
import it.govpay.aca.test.repository.DominioRepository;
import it.govpay.aca.test.repository.VersamentoFullRepository;
import it.govpay.aca.test.utils.AcaUtils;
import it.govpay.aca.test.utils.GdeProblemUtils;
import it.govpay.aca.test.utils.ObjectMapperUtils;
import it.govpay.aca.test.utils.VersamentoUtils;


@SpringBootTest(classes = Application.class)
@EnableAutoConfiguration
@DisplayName("Test Invio ACA e GDE")
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class UC_3_GdeTest {

	@Autowired
	@MockBean(name = "acaApi")
	AcaApi acaApi;

	@Autowired
	@MockBean
	EventiApi gdeApi;

	@Autowired
	@Qualifier(value = "acaSenderJob")
	private Job job;

	@Autowired
	private JobLauncher jobLauncher;

	@Autowired
	private JobRepository jobRepository;

	private JobLauncherTestUtils jobLauncherTestUtils;

	@Autowired
	VersamentoFullRepository versamentoFullRepository;

	@Autowired
	ApplicazioneRepository applicazioneRepository;

	@Autowired
	DominioRepository dominioRepository;

	@Autowired
	VersamentoAcaRepository versamentoAcaRepository;

	@Autowired
	VersamentoRepository versamentoRepository;

	@Value("${it.govpay.aca.batch.client.baseUrl}")
	String acaBaseUrl;
	
	private ObjectMapper mapper = ObjectMapperUtils.createObjectMapper();

	private void initailizeJobLauncherTestUtils() throws Exception {
		this.jobLauncherTestUtils = new JobLauncherTestUtils();
		this.jobLauncherTestUtils.setJobLauncher(jobLauncher);
		this.jobLauncherTestUtils.setJobRepository(jobRepository);
		this.jobLauncherTestUtils.setJob(job);
	}

	HttpResponse<InputStream> gdeMockHttpResponseOk;
	HttpResponse<InputStream> gdeMockHttpResponse503;
	HttpResponse<InputStream> gdeMockHttpResponse400;

	@BeforeEach
	void setUp() throws URISyntaxException, JsonProcessingException {
		MockitoAnnotations.openMocks(this);
		this.versamentoFullRepository.deleteAll();

		Mockito.lenient()
		.when(acaApi.getApiClient()).thenAnswer(new Answer<ApiClient>() {

			@Override
			public ApiClient answer(InvocationOnMock invocation) throws Throwable {
				ApiClient apiClient = new ApiClient();
				apiClient.setBasePath(acaBaseUrl);
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
	void TC_01_SendTest_AcaOk() throws Exception {
		try {

			VersamentoFullEntity versamentoAcaEntity = VersamentoUtils.creaVersamentoNonEseguito(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository);
			this.versamentoFullRepository.save(versamentoAcaEntity);

			Mockito.lenient()
			.when(acaApi.newDebtPositionWithHttpInfo(any()
					)).thenAnswer(new Answer<ResponseEntity<Void>>() {
						@Override
						public ResponseEntity<Void> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<Void> mockResponseEntity = new ResponseEntity<>(AcaUtils.getHeadersCreatedOk(), HttpStatus.CREATED);
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

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			Assert.assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(0, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.versamentoFullRepository.deleteAll();
		}
	}

	@Test
	void TC_02_SendTest_AcaKO() throws Exception {
		try {

			VersamentoFullEntity versamentoAcaEntity = VersamentoUtils.creaVersamentoNonEseguito(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository);
			this.versamentoFullRepository.save(versamentoAcaEntity);

			Mockito.lenient()
			.when(acaApi.newDebtPositionWithHttpInfo(any()
					)).thenAnswer(new Answer<ResponseEntity<ProblemJson>>() {
						@Override
						public ResponseEntity<ProblemJson> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<ProblemJson> mockResponseEntity = new ResponseEntity<>(AcaUtils.createProblem503(), AcaUtils.getHeadersProblem(),  HttpStatus.SERVICE_UNAVAILABLE);

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

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			Assert.assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_03_SendTest_Gde400BadRequest_WithProblem() throws Exception {
		try {

			VersamentoFullEntity versamentoAcaEntity = VersamentoUtils.creaVersamentoNonEseguito(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository);
			this.versamentoFullRepository.save(versamentoAcaEntity);

			Mockito.lenient()
			.when(acaApi.newDebtPositionWithHttpInfo(any()
					)).thenAnswer(new Answer<ResponseEntity<Void>>() {
						@Override
						public ResponseEntity<Void> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<Void> mockResponseEntity = new ResponseEntity<>(AcaUtils.getHeadersCreatedOk(), HttpStatus.CREATED);
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

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			Assert.assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(0, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_04_SendTest_Gde400BadRequest_WithException() throws Exception {
		try {
			VersamentoFullEntity versamentoAcaEntity = VersamentoUtils.creaVersamentoNonEseguito(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository);
			this.versamentoFullRepository.save(versamentoAcaEntity);

			Mockito.lenient()
			.when(acaApi.newDebtPositionWithHttpInfo(any()))
			.thenAnswer(new Answer<ResponseEntity<Void>>() {
				@Override
				public ResponseEntity<Void> answer(InvocationOnMock invocation) throws Throwable {
					ResponseEntity<Void> mockResponseEntity = new ResponseEntity<>(AcaUtils.getHeadersCreatedOk(), HttpStatus.CREATED);
					return mockResponseEntity;
				}
			});

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()))
			.thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.getReasonPhrase()));

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			Assert.assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(0, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_05_SendTest_Gde503ServiceUnavailable_WithProblem() throws Exception {
		try {

			VersamentoFullEntity versamentoAcaEntity = VersamentoUtils.creaVersamentoNonEseguito(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository);
			this.versamentoFullRepository.save(versamentoAcaEntity);

			Mockito.lenient()
			.when(acaApi.newDebtPositionWithHttpInfo(any()
					)).thenAnswer(new Answer<ResponseEntity<Void>>() {
						@Override
						public ResponseEntity<Void> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<Void> mockResponseEntity = new ResponseEntity<>(AcaUtils.getHeadersCreatedOk(), HttpStatus.CREATED);
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

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			Assert.assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(0, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_06_SendTest_Gde503ServiceUnavailable_WithException() throws Exception {
		try {
			VersamentoFullEntity versamentoAcaEntity = VersamentoUtils.creaVersamentoNonEseguito(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository);
			this.versamentoFullRepository.save(versamentoAcaEntity);

			Mockito.lenient()
			.when(acaApi.newDebtPositionWithHttpInfo(any()))
			.thenAnswer(new Answer<ResponseEntity<Void>>() {
				@Override
				public ResponseEntity<Void> answer(InvocationOnMock invocation) throws Throwable {
					ResponseEntity<Void> mockResponseEntity = new ResponseEntity<>(AcaUtils.getHeadersCreatedOk(), HttpStatus.CREATED);
					return mockResponseEntity;
				}
			});

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()))
			.thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase()));

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			Assert.assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(0, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_07_SendTest_Gde401Unauthorized_WithProblem() throws Exception {
		try {

			VersamentoFullEntity versamentoAcaEntity = VersamentoUtils.creaVersamentoNonEseguito(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository);
			this.versamentoFullRepository.save(versamentoAcaEntity);

			Mockito.lenient()
					.when(acaApi.newDebtPositionWithHttpInfo(any()
					)).thenAnswer(new Answer<ResponseEntity<Void>>() {
						@Override
						public ResponseEntity<Void> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<Void> mockResponseEntity = new ResponseEntity<>(AcaUtils.getHeadersCreatedOk(), HttpStatus.CREATED);
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

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			Assert.assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(0, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.versamentoFullRepository.deleteAll();
		}
	}

	@Test
	void TC_08_SendTest_Gde401Unauthorized_WithException() throws Exception {
		try {
			VersamentoFullEntity versamentoAcaEntity = VersamentoUtils.creaVersamentoNonEseguito(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository);
			this.versamentoFullRepository.save(versamentoAcaEntity);

			Mockito.lenient()
			.when(acaApi.newDebtPositionWithHttpInfo(any()))
			.thenAnswer(new Answer<ResponseEntity<Void>>() {
				@Override
				public ResponseEntity<Void> answer(InvocationOnMock invocation) throws Throwable {
					ResponseEntity<Void> mockResponseEntity = new ResponseEntity<>(AcaUtils.getHeadersCreatedOk(), HttpStatus.CREATED);
					return mockResponseEntity;
				}
			});

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()))
			.thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			Assert.assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(0, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_09_SendTest_Gde403Forbidden_WithProblem() throws Exception {
		try {

			VersamentoFullEntity versamentoAcaEntity = VersamentoUtils.creaVersamentoNonEseguito(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository);
			this.versamentoFullRepository.save(versamentoAcaEntity);

			Mockito.lenient()
					.when(acaApi.newDebtPositionWithHttpInfo(any()
					)).thenAnswer(new Answer<ResponseEntity<Void>>() {
						@Override
						public ResponseEntity<Void> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<Void> mockResponseEntity = new ResponseEntity<>(AcaUtils.getHeadersCreatedOk(), HttpStatus.CREATED);
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

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			Assert.assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(0, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.versamentoFullRepository.deleteAll();
		}
	}

	@Test
	void TC_10_SendTest_Gde403Forbidden_WithException() throws Exception {
		try {
			VersamentoFullEntity versamentoAcaEntity = VersamentoUtils.creaVersamentoNonEseguito(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository);
			this.versamentoFullRepository.save(versamentoAcaEntity);

			Mockito.lenient()
			.when(acaApi.newDebtPositionWithHttpInfo(any()))
			.thenAnswer(new Answer<ResponseEntity<Void>>() {
				@Override
				public ResponseEntity<Void> answer(InvocationOnMock invocation) throws Throwable {
					ResponseEntity<Void> mockResponseEntity = new ResponseEntity<>(AcaUtils.getHeadersCreatedOk(), HttpStatus.CREATED);
					return mockResponseEntity;
				}
			});

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()))
			.thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN, "Forbidden"));

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			Assert.assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(0, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_11_SendTest_Gde404NotFound_WithProblem() throws Exception {
		try {

			VersamentoFullEntity versamentoAcaEntity = VersamentoUtils.creaVersamentoNonEseguito(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository);
			this.versamentoFullRepository.save(versamentoAcaEntity);

			Mockito.lenient()
					.when(acaApi.newDebtPositionWithHttpInfo(any()
					)).thenAnswer(new Answer<ResponseEntity<Void>>() {
						@Override
						public ResponseEntity<Void> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<Void> mockResponseEntity = new ResponseEntity<>(AcaUtils.getHeadersCreatedOk(), HttpStatus.CREATED);
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

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			Assert.assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(0, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.versamentoFullRepository.deleteAll();
		}
	}

	@Test
	void TC_12_SendTest_Gde404NotFound_WithException() throws Exception {
		try {
			VersamentoFullEntity versamentoAcaEntity = VersamentoUtils.creaVersamentoNonEseguito(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository);
			this.versamentoFullRepository.save(versamentoAcaEntity);

			Mockito.lenient()
			.when(acaApi.newDebtPositionWithHttpInfo(any()))
			.thenAnswer(new Answer<ResponseEntity<Void>>() {
				@Override
				public ResponseEntity<Void> answer(InvocationOnMock invocation) throws Throwable {
					ResponseEntity<Void> mockResponseEntity = new ResponseEntity<>(AcaUtils.getHeadersCreatedOk(), HttpStatus.CREATED);
					return mockResponseEntity;
				}
			});

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()))
			.thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found"));

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			Assert.assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(0, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_13_SendTest_Gde409Conflict_WithProblem() throws Exception {
		try {

			VersamentoFullEntity versamentoAcaEntity = VersamentoUtils.creaVersamentoNonEseguito(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository);
			this.versamentoFullRepository.save(versamentoAcaEntity);

			Mockito.lenient()
					.when(acaApi.newDebtPositionWithHttpInfo(any()
					)).thenAnswer(new Answer<ResponseEntity<Void>>() {
						@Override
						public ResponseEntity<Void> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<Void> mockResponseEntity = new ResponseEntity<>(AcaUtils.getHeadersCreatedOk(), HttpStatus.CREATED);
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

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			Assert.assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(0, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.versamentoFullRepository.deleteAll();
		}
	}

	@Test
	void TC_14_SendTest_Gde409Conflict_WithException() throws Exception {
		try {
			VersamentoFullEntity versamentoAcaEntity = VersamentoUtils.creaVersamentoNonEseguito(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository);
			this.versamentoFullRepository.save(versamentoAcaEntity);

			Mockito.lenient()
			.when(acaApi.newDebtPositionWithHttpInfo(any()))
			.thenAnswer(new Answer<ResponseEntity<Void>>() {
				@Override
				public ResponseEntity<Void> answer(InvocationOnMock invocation) throws Throwable {
					ResponseEntity<Void> mockResponseEntity = new ResponseEntity<>(AcaUtils.getHeadersCreatedOk(), HttpStatus.CREATED);
					return mockResponseEntity;
				}
			});

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()))
			.thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT, "Conflict"));

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			Assert.assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(0, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_15_SendTest_Gde429TooManyRequests_WithProblem() throws Exception {
		try {

			VersamentoFullEntity versamentoAcaEntity = VersamentoUtils.creaVersamentoNonEseguito(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository);
			this.versamentoFullRepository.save(versamentoAcaEntity);

			Mockito.lenient()
					.when(acaApi.newDebtPositionWithHttpInfo(any()
					)).thenAnswer(new Answer<ResponseEntity<Void>>() {
						@Override
						public ResponseEntity<Void> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<Void> mockResponseEntity = new ResponseEntity<>(AcaUtils.getHeadersCreatedOk(), HttpStatus.CREATED);
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

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			Assert.assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(0, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.versamentoFullRepository.deleteAll();
		}
	}

	@Test
	void TC_16_SendTest_Gde429TooManyRequests_WithException() throws Exception {
		try {
			VersamentoFullEntity versamentoAcaEntity = VersamentoUtils.creaVersamentoNonEseguito(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository);
			this.versamentoFullRepository.save(versamentoAcaEntity);

			Mockito.lenient()
			.when(acaApi.newDebtPositionWithHttpInfo(any()))
			.thenAnswer(new Answer<ResponseEntity<Void>>() {
				@Override
				public ResponseEntity<Void> answer(InvocationOnMock invocation) throws Throwable {
					ResponseEntity<Void> mockResponseEntity = new ResponseEntity<>(AcaUtils.getHeadersCreatedOk(), HttpStatus.CREATED);
					return mockResponseEntity;
				}
			});

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()))
			.thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests"));

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			Assert.assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(0, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.versamentoFullRepository.deleteAll();
		}
	}

	
	@Test
	void TC_17_SendTest_AcaKO_400BadRequest_WithProblem() throws Exception {
		try {

			VersamentoFullEntity versamentoAcaEntity = VersamentoUtils.creaVersamentoNonEseguito(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository);
			this.versamentoFullRepository.save(versamentoAcaEntity);

			Mockito.lenient()
			.when(acaApi.newDebtPositionWithHttpInfo(any()
					)).thenAnswer(new Answer<ResponseEntity<ProblemJson>>() {
						@Override
						public ResponseEntity<ProblemJson> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<ProblemJson> mockResponseEntity = new ResponseEntity<>(AcaUtils.createProblem400(), AcaUtils.getHeadersProblem(),  HttpStatus.BAD_REQUEST);

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

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			Assert.assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_18_SendTest_AcaKO_400BadRequest_WithException() throws Exception {
		try {

			VersamentoFullEntity versamentoAcaEntity = VersamentoUtils.creaVersamentoNonEseguito(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository);
			this.versamentoFullRepository.save(versamentoAcaEntity);

			Mockito.lenient()
			.when(acaApi.newDebtPositionWithHttpInfo(any()
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

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			Assert.assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_19_SendTest_AcaKO_404NotFound_WithProblem() throws Exception {
		try {

			VersamentoFullEntity versamentoAcaEntity = VersamentoUtils.creaVersamentoNonEseguito(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository);
			this.versamentoFullRepository.save(versamentoAcaEntity);

			Mockito.lenient()
			.when(acaApi.newDebtPositionWithHttpInfo(any()
					)).thenAnswer(new Answer<ResponseEntity<ProblemJson>>() {
						@Override
						public ResponseEntity<ProblemJson> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<ProblemJson> mockResponseEntity = new ResponseEntity<>(AcaUtils.createProblem404(), AcaUtils.getHeadersProblem(),  HttpStatus.BAD_REQUEST);

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

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			Assert.assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_20_SendTest_AcaKO_404NotFound_WithException() throws Exception {
		try {

			VersamentoFullEntity versamentoAcaEntity = VersamentoUtils.creaVersamentoNonEseguito(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository);
			this.versamentoFullRepository.save(versamentoAcaEntity);

			Mockito.lenient()
			.when(acaApi.newDebtPositionWithHttpInfo(any()
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

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			Assert.assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_21_SendTest_AcaKO_409Conflict_WithProblem() throws Exception {
		try {

			VersamentoFullEntity versamentoAcaEntity = VersamentoUtils.creaVersamentoNonEseguito(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository);
			this.versamentoFullRepository.save(versamentoAcaEntity);

			Mockito.lenient()
			.when(acaApi.newDebtPositionWithHttpInfo(any()
					)).thenAnswer(new Answer<ResponseEntity<ProblemJson>>() {
						@Override
						public ResponseEntity<ProblemJson> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<ProblemJson> mockResponseEntity = new ResponseEntity<>(AcaUtils.createProblem409(), AcaUtils.getHeadersProblem(),  HttpStatus.BAD_REQUEST);

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

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			Assert.assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_22_SendTest_AcaKO_409Conflict_WithException() throws Exception {
		try {

			VersamentoFullEntity versamentoAcaEntity = VersamentoUtils.creaVersamentoNonEseguito(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository);
			this.versamentoFullRepository.save(versamentoAcaEntity);

			Mockito.lenient()
			.when(acaApi.newDebtPositionWithHttpInfo(any()
					))
					.thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT, "Conflict"));

			Mockito.lenient()
			.when(gdeApi.addEventoWithHttpInfoAsync(any()
					)).thenAnswer(new Answer<CompletableFuture<HttpResponse<InputStream>>>() {
						@Override
						public CompletableFuture<HttpResponse<InputStream>> answer(InvocationOnMock invocation) throws Throwable {
							return CompletableFuture.completedFuture(gdeMockHttpResponseOk);
						}
					});

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			Assert.assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_23_SendTest_AcaKO_500InternalServerError_WithProblem() throws Exception {
		try {

			VersamentoFullEntity versamentoAcaEntity = VersamentoUtils.creaVersamentoNonEseguito(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository);
			this.versamentoFullRepository.save(versamentoAcaEntity);

			Mockito.lenient()
			.when(acaApi.newDebtPositionWithHttpInfo(any()
					)).thenAnswer(new Answer<ResponseEntity<ProblemJson>>() {
						@Override
						public ResponseEntity<ProblemJson> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<ProblemJson> mockResponseEntity = new ResponseEntity<>(AcaUtils.createProblem500(), AcaUtils.getHeadersProblem(),  HttpStatus.BAD_REQUEST);

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

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			Assert.assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.versamentoFullRepository.deleteAll();
		}
	}
	
	@Test
	void TC_24_SendTest_AcaKO_500InternalServerError_WithException() throws Exception {
		try {

			VersamentoFullEntity versamentoAcaEntity = VersamentoUtils.creaVersamentoNonEseguito(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository);
			this.versamentoFullRepository.save(versamentoAcaEntity);

			Mockito.lenient()
			.when(acaApi.newDebtPositionWithHttpInfo(any()
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

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

			initailizeJobLauncherTestUtils();
			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			Assert.assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Assert.assertEquals(1, this.versamentoFullRepository.count());
			Assert.assertEquals(1, this.versamentoAcaRepository.count());
			Assert.assertEquals(1, this.versamentoRepository.count());

		} finally {
			this.versamentoFullRepository.deleteAll();
		}
	}
}
