package it.govpay.gpd.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;

import java.time.OffsetDateTime;

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
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import it.govpay.gpd.Application;
import it.govpay.gpd.client.api.DebtPositionActionsApiApi;
import it.govpay.gpd.client.api.DebtPositionsApiApi;
import it.govpay.gpd.client.beans.PaymentPositionModel;
import it.govpay.gpd.client.beans.PaymentPositionModelBaseResponse;
import it.govpay.gpd.client.beans.PaymentPositionModelBaseResponse.StatusEnum;
import it.govpay.gpd.service.GpdApiService;
import it.govpay.gpd.test.entity.VersamentoFullEntity;
import it.govpay.gpd.test.utils.PaymentPositionModelUtils;
import it.govpay.gpd.test.utils.VersamentoUtils;

/**
 * Pendenze con data di pubblicazione futura (Issue #65, link-it/govpay#914): il core posticipa
 * data_ultima_modifica_aca alla data di pubblicazione, e il batch non le invia prima di quella data.
 * Alla data le invia senza bisogno di altre modifiche alla pendenza.
 */
@SpringBootTest(classes = Application.class)
@EnableAutoConfiguration
@DisplayName("Test data di pubblicazione")
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
@SpringBatchTest
class UC_6_DataPubblicazioneTest extends UC_00_BaseTest {

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

	@AfterEach
	void tearDown() {
		jobRepositoryTestUtils.removeJobExecutions();
	}

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		this.cleanDB();

		gpdApi = Mockito.mock(DebtPositionsApiApi.class);
		gpdActionsApi = Mockito.mock(DebtPositionActionsApiApi.class);

		Mockito.lenient().when(gpdApiService.getGpdApi(any())).thenReturn(gpdApi);
		Mockito.lenient().when(gpdApiService.getGpdActionsApi(any())).thenReturn(gpdActionsApi);
		Mockito.lenient().when(gpdApiService.getGpdBasePath(any())).thenReturn("http://fakehost:8080/");

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

		jobLauncherTestUtils.setJob(job);
	}

	private VersamentoFullEntity creaVersamentoConDataUltimaModificaAca(OffsetDateTime dataUltimaModificaAca) {
		VersamentoFullEntity versamento = this.creaVersamentoNonEseguito();
		versamento.setDataUltimaModificaAca(dataUltimaModificaAca);
		return this.versamentoFullRepository.save(versamento);
	}

	@Test
	void TC_01_NonInviataPrimaDellaDataDiPubblicazione() throws Exception {
		try {
			VersamentoFullEntity versamento = this.creaVersamentoConDataUltimaModificaAca(OffsetDateTime.now().plusDays(1));

			assertEquals(0, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));

			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Mockito.verify(gpdApi, Mockito.never()).createPositionWithHttpInfo(any(), any(), any(), any());
			assertNull(this.versamentoFullRepository.findById(versamento.getId()).get().getDataUltimaComunicazioneAca());
		} finally {
			this.cleanDB();
		}
	}

	@Test
	void TC_02_InviataAllaDataDiPubblicazioneSenzaAltreModifiche() throws Exception {
		try {
			VersamentoFullEntity versamento = this.creaVersamentoConDataUltimaModificaAca(OffsetDateTime.now().plusDays(1));

			JobExecution jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
			Mockito.verify(gpdApi, Mockito.never()).createPositionWithHttpInfo(any(), any(), any(), any());

			// la data di pubblicazione e' raggiunta: la data di ultima modifica e' ora nel passato
			versamento = this.versamentoFullRepository.findById(versamento.getId()).get();
			versamento.setDataUltimaModificaAca(OffsetDateTime.now().minusMinutes(1));
			this.versamentoFullRepository.save(versamento);

			assertEquals(1, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));

			jobExecution = jobLauncherTestUtils.launchJob();
			assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

			Mockito.verify(gpdApi, Mockito.times(1)).createPositionWithHttpInfo(any(), any(), any(), any());
			assertEquals(0, VersamentoUtils.countVersamentiDaSpedire(this.versamentoGpdRepository, this.numeroGiorni));
			assertNotNull(this.versamentoFullRepository.findById(versamento.getId()).get().getDataUltimaComunicazioneAca());
		} finally {
			this.cleanDB();
		}
	}
}
