package it.govpay.gpd.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

import java.net.URISyntaxException;

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
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.core.JsonProcessingException;

import it.govpay.gpd.Application;
import it.govpay.gpd.client.api.DebtPositionsApiApi;
import it.govpay.gpd.client.api.impl.ApiClient;
import it.govpay.gpd.client.beans.PaymentPositionModel;
import it.govpay.gpd.gde.client.EventiApi;
import it.govpay.gpd.test.utils.PaymentPositionModelUtils;
import it.govpay.gpd.test.utils.VersamentoUtils;


@SpringBootTest(classes = Application.class, properties = {
        "it.govpay.gde.enabled=false"
})
@EnableAutoConfiguration
@DisplayName("Test Invio GPD e GDE")
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
@SpringBatchTest
class UC_5_NoGdeTest extends UC_00_BaseTest {

	@Autowired
	@MockBean(name = "gpdApi")
	DebtPositionsApiApi gpdApi;

	@Autowired
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
	}

	@Test
	void TC_01_SendTest_NoGde() throws Exception {
		try {

			// creazione versamento da spedire
			this.creaVersamentoNonEseguito();

			Mockito.lenient()
			.when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), any()
					)).thenAnswer(new Answer<ResponseEntity<PaymentPositionModel>>() {
						@Override
						public ResponseEntity<PaymentPositionModel> answer(InvocationOnMock invocation) throws Throwable {
							ResponseEntity<PaymentPositionModel> mockResponseEntity = PaymentPositionModelUtils.creaResponseCreatePaymentPositionModelOk(invocation);
							return mockResponseEntity;
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
}
