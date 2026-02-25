package it.govpay.gpd.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import it.govpay.gpd.Application;
import it.govpay.gpd.client.api.DebtPositionActionsApiApi;
import it.govpay.gpd.client.api.DebtPositionsApiApi;
import it.govpay.gpd.client.beans.PaymentPositionModel;
import it.govpay.gpd.client.beans.PaymentPositionModelBaseResponse;
import it.govpay.gpd.client.beans.PaymentPositionModelBaseResponse.StatusEnum;
import it.govpay.gpd.client.beans.ProblemJson;
import it.govpay.gpd.service.GpdApiService;
import it.govpay.gpd.test.utils.GpdUtils;
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

	@MockitoBean
	GpdApiService gpdApiService;

	@Autowired
	private JobLauncherTestUtils jobLauncherTestUtils;

	@Autowired
	private JobRepositoryTestUtils jobRepositoryTestUtils;

	@Autowired
	@Qualifier(value = "gpdSenderJob")
	private Job job;

	@Value("${it.govpay.gpd.batch.dbreader.sogliaTemporaleRicercaPendenze.numeroGiorni:7}")
	private Integer numeroGiorni;

	DebtPositionsApiApi gpdApi;
	DebtPositionActionsApiApi gpdActionsApi;

	private void initailizeJobLauncherTestUtils() {
		jobLauncherTestUtils.setJob(job);
	}

	@AfterEach
    void tearDown() {
        jobRepositoryTestUtils.removeJobExecutions();
    }

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		this.cleanDB();

		gpdApi = Mockito.mock(DebtPositionsApiApi.class);
		gpdActionsApi = Mockito.mock(DebtPositionActionsApiApi.class);

		Mockito.lenient().when(gpdApiService.getGpdApi(any())).thenReturn(gpdApi);
		Mockito.lenient().when(gpdApiService.getGpdActionsApi(any())).thenReturn(gpdActionsApi);
		Mockito.lenient().when(gpdApiService.getGpdBasePath(any())).thenReturn("http://fakehost:8080/");
	}

	@Test
	void TC_01_SendTest_GpdOk() throws Exception {
		try {

			// creazione versamento da spedire
			this.creaVersamentoNonEseguitoRifermentoConTributoIbanAppoggio();

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
	void TC_02_SendTest_GpdKO() throws Exception {
		try {

			// creazione versamento da spedire
			this.creaVersamentoNonEseguito();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<ProblemJson>>() {
						@Override
						public ResponseEntity<ProblemJson> answer(InvocationOnMock invocation) throws Throwable {
							return GpdUtils.creaResponseKo(invocation, HttpStatus.SERVICE_UNAVAILABLE);
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
	void TC_03_SendTest_Gde400BadRequest_WithProblem() throws Exception {
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
	void TC_04_SendTest_Gde400BadRequest_WithException() throws Exception {
		try {
			// creazione versamento da spedire
			this.creaVersamentoNonEseguito();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()))
			.thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
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
	void TC_05_SendTest_Gde503ServiceUnavailable_WithProblem() throws Exception {
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
	void TC_06_SendTest_Gde503ServiceUnavailable_WithException() throws Exception {
		try {
			// creazione versamento da spedire
			this.creaVersamentoNonEseguito();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()))
			.thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
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
	void TC_07_SendTest_Gde401Unauthorized_WithProblem() throws Exception {
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
	void TC_08_SendTest_Gde401Unauthorized_WithException() throws Exception {
		try {
			// creazione versamento da spedire
			this.creaVersamentoNonEseguito();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()))
			.thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
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
	void TC_09_SendTest_Gde403Forbidden_WithProblem() throws Exception {
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
	void TC_10_SendTest_Gde403Forbidden_WithException() throws Exception {
		try {
			// creazione versamento da spedire
			this.creaVersamentoNonEseguito();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()))
			.thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
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
	void TC_11_SendTest_Gde404NotFound_WithProblem() throws Exception {
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
	void TC_12_SendTest_Gde404NotFound_WithException() throws Exception {
		try {
			// creazione versamento da spedire
			this.creaVersamentoNonEseguito();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()))
			.thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
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
	void TC_13_SendTest_Gde409Conflict_WithProblem() throws Exception {
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
	void TC_14_SendTest_Gde409Conflict_WithException() throws Exception {
		try {
			// creazione versamento da spedire
			this.creaVersamentoNonEseguito();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()))
			.thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
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
	void TC_15_SendTest_Gde429TooManyRequests_WithProblem() throws Exception {
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
	void TC_16_SendTest_Gde429TooManyRequests_WithException() throws Exception {
		try {
			// creazione versamento da spedire
			this.creaVersamentoNonEseguito();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()))
			.thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
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
	void TC_17_SendTest_GpdKO_400BadRequest_WithProblem() throws Exception {
		try {

			// creazione versamento da spedire
			this.creaVersamentoNonEseguito();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<ProblemJson>>() {
						@Override
						public ResponseEntity<ProblemJson> answer(InvocationOnMock invocation) throws Throwable {
							return GpdUtils.creaResponseKo(invocation, HttpStatus.BAD_REQUEST);
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
	void TC_18_SendTest_GpdKO_400BadRequest_WithException() throws Exception {
		try {

			// creazione versamento da spedire
			this.creaVersamentoNonEseguito();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					))
					.thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request"));

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
	void TC_19_SendTest_GpdKO_404NotFound_WithProblem() throws Exception {
		try {

			// creazione versamento da spedire
			this.creaVersamentoNonEseguito();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<ProblemJson>>() {
						@Override
						public ResponseEntity<ProblemJson> answer(InvocationOnMock invocation) throws Throwable {
							return GpdUtils.creaResponseKo(invocation, HttpStatus.NOT_FOUND);
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
	void TC_20_SendTest_GpdKO_404NotFound_WithException() throws Exception {
		try {

			// creazione versamento da spedire
			this.creaVersamentoNonEseguito();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					))
					.thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found"));

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
	void TC_21_SendTest_GpdKO_409Conflict_WithProblem() throws Exception {
		try {

			// creazione versamento da spedire
			this.creaVersamentoNonEseguito();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<ProblemJson>>() {
						@Override
						public ResponseEntity<ProblemJson> answer(InvocationOnMock invocation) throws Throwable {
							return GpdUtils.creaResponseKo(invocation, HttpStatus.CONFLICT);
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
	void TC_22_SendTest_GpdKO_409Conflict_WithException() throws Exception {
		try {

			// creazione versamento da spedire
			this.creaVersamentoNonEseguito();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					))
					.thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT, "Conflict"));

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
	void TC_23_SendTest_GpdKO_500InternalServerError_WithProblem() throws Exception {
		try {

			// creazione versamento da spedire
			this.creaVersamentoNonEseguito();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<ProblemJson>>() {
						@Override
						public ResponseEntity<ProblemJson> answer(InvocationOnMock invocation) throws Throwable {
							return GpdUtils.creaResponseKo(invocation, HttpStatus.INTERNAL_SERVER_ERROR);
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
	void TC_24_SendTest_GpdKO_500InternalServerError_WithException() throws Exception {
		try {

			// creazione versamento da spedire
			this.creaVersamentoNonEseguito();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					))
					.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error"));

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
}
