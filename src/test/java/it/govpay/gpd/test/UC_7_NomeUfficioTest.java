package it.govpay.gpd.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import it.govpay.common.entity.DominioEntity;
import it.govpay.gpd.Application;
import it.govpay.gpd.client.api.DebtPositionsApiApi;
import it.govpay.gpd.client.beans.PaymentPositionModel;
import it.govpay.gpd.client.beans.PaymentPositionModelBaseResponse;
import it.govpay.gpd.client.v3.api.DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi;
import it.govpay.gpd.client.v3.beans.PaymentPositionModelResponseV3;
import it.govpay.gpd.client.v3.beans.PaymentPositionModelV3;
import it.govpay.gpd.entity.VersamentoGpdEntity.StatoVersamento;
import it.govpay.gpd.service.GpdApiService;
import it.govpay.gpd.test.costanti.Costanti;
import it.govpay.gpd.test.entity.DocumentoFullEntity;
import it.govpay.gpd.test.entity.OpzionePagamentoFullEntity;
import it.govpay.gpd.test.entity.PdDebitoreFullEntity;
import it.govpay.gpd.test.entity.UoEntity;
import it.govpay.gpd.test.entity.VersamentoFullEntity;
import it.govpay.gpd.test.repository.DebitoreFullRepository;
import it.govpay.gpd.test.repository.DocumentoFullRepository;
import it.govpay.gpd.test.repository.OpzionePagamentoFullRepository;
import it.govpay.gpd.test.repository.PdDebitoreFullRepository;
import it.govpay.gpd.test.utils.PaymentPositionModelUtils;
import it.govpay.gpd.test.utils.PaymentPositionModelV3Utils;
import it.govpay.gpd.test.utils.PosizioneDebitoriaUtils;
import it.govpay.pendenze.model.StatoOpzionePagamento;
import it.govpay.pendenze.model.TipologiaOpzionePagamento;

/**
 * Nome dell'ufficio ({@code officeName}) nel payload GPD: e' la denominazione dell'unita'
 * operativa quando il suo codice e' diverso da {@code EC}; non e' valorizzato con l'unita'
 * {@code EC} o senza unita' operativa. Lo step legacy legge l'unita' dalla pendenza, lo
 * step v3 dalla posizione debitoria.
 */
@SpringBootTest(classes = Application.class)
@EnableAutoConfiguration
@DisplayName("Test Invio GPD - Nome dell'ufficio")
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
@SpringBatchTest
class UC_7_NomeUfficioTest extends UC_00_BaseTest {

	private static final long ID_UO_EC = 1L;
	private static final long ID_UO_UFFICIO = 100L;
	private static final String DENOMINAZIONE_UFFICIO = "Ufficio Tributi";

	enum UnitaOperativa {
		UFFICIO(ID_UO_UFFICIO, DENOMINAZIONE_UFFICIO),
		ENTE_CREDITORE(ID_UO_EC, null),
		NESSUNA(null, null);

		private final Long id;
		private final String nomeUfficioAtteso;

		UnitaOperativa(Long id, String nomeUfficioAtteso) {
			this.id = id;
			this.nomeUfficioAtteso = nomeUfficioAtteso;
		}
	}

	@MockitoBean
	GpdApiService gpdApiService;

	@Autowired
	private JobLauncherTestUtils jobLauncherTestUtils;

	@Autowired
	private JobRepositoryTestUtils jobRepositoryTestUtils;

	@Autowired
	@Qualifier(value = "gpdSenderJob")
	private Job job;

	@Autowired
	private DocumentoFullRepository documentoFullRepository;

	@Autowired
	private OpzionePagamentoFullRepository opzionePagamentoFullRepository;

	@Autowired
	private DebitoreFullRepository debitoreFullRepository;

	@Autowired
	private PdDebitoreFullRepository pdDebitoreFullRepository;

	private DebtPositionsApiApi gpdApiLegacy;
	private DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi gpdApiV3;

	@BeforeEach
	void setUp() {
		this.cleanDB();

		DominioEntity dominio = dominioRepository.findByCodDominio(Costanti.CODDOMINIO);
		uoRepository.save(UoEntity.builder()
				.id(ID_UO_UFFICIO)
				.codUo(Costanti.CODUO_UFF1)
				.uoDenominazione(DENOMINAZIONE_UFFICIO)
				.dominio(dominio)
				.build());

		gpdApiLegacy = Mockito.mock(DebtPositionsApiApi.class);
		gpdApiV3 = Mockito.mock(DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi.class);

		Mockito.lenient().when(gpdApiService.getGpdApi(any())).thenReturn(gpdApiLegacy);
		Mockito.lenient().when(gpdApiService.getGpdActionsApi(any()))
				.thenReturn(Mockito.mock(it.govpay.gpd.client.api.DebtPositionActionsApiApi.class));
		Mockito.lenient().when(gpdApiService.getGpdBasePath(any())).thenReturn("http://fakehost:8080/");
		Mockito.lenient().when(gpdApiService.getGpdApiV3(any())).thenReturn(gpdApiV3);
		Mockito.lenient().when(gpdApiService.getGpdActionsApiV3(any()))
				.thenReturn(Mockito.mock(it.govpay.gpd.client.v3.api.DebtPositionActionsApiApi.class));
		Mockito.lenient().when(gpdApiService.getGpdBasePathV3(any())).thenReturn("http://fakehost:8080/");

		jobLauncherTestUtils.setJob(job);
	}

	@AfterEach
	void tearDown() {
		jobRepositoryTestUtils.removeJobExecutions();
		this.cleanDB();
	}

	@Override
	public void cleanDB() {
		super.cleanDB();
		this.opzionePagamentoFullRepository.deleteAll();
		this.pdDebitoreFullRepository.deleteAll();
		this.debitoreFullRepository.deleteAll();
		this.documentoFullRepository.deleteAll();
	}

	@ParameterizedTest(name = "{0}")
	@EnumSource(UnitaOperativa.class)
	@DisplayName("Legacy, invio: nome dell'ufficio secondo l'unita' operativa della pendenza")
	void UC_7_01_LegacyInvio(UnitaOperativa unitaOperativa) throws Exception {
		VersamentoFullEntity versamento = creaVersamentoNonEseguitoDefinito();
		versamento.setIdUo(unitaOperativa.id);
		versamentoFullRepository.save(versamento);

		Mockito.lenient().when(gpdApiLegacy.createPositionWithHttpInfo(any(), any(), any(), any()))
				.thenAnswer(PaymentPositionModelUtils::creaResponseCreatePaymentPositionModelOk);
		Mockito.lenient().when(gpdApiLegacy.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()))
				.thenAnswer(invocation -> PaymentPositionModelUtils.creaResponseGetPositionOk(invocation,
						PaymentPositionModelBaseResponse.StatusEnum.VALID));

		eseguiJob();

		ArgumentCaptor<PaymentPositionModel> payload = ArgumentCaptor.forClass(PaymentPositionModel.class);
		verify(gpdApiLegacy).createPositionWithHttpInfo(any(), payload.capture(), any(), any());
		assertEquals(unitaOperativa.nomeUfficioAtteso, payload.getValue().getOfficeName());
	}

	@ParameterizedTest(name = "{0}")
	@EnumSource(UnitaOperativa.class)
	@DisplayName("Legacy, ritiro: nome dell'ufficio secondo l'unita' operativa della pendenza")
	void UC_7_02_LegacyRitiro(UnitaOperativa unitaOperativa) throws Exception {
		VersamentoFullEntity versamento = creaVersamentoNonEseguitoDefinito();
		versamento.setIdUo(unitaOperativa.id);
		versamento.setStatoVersamento(StatoVersamento.ANNULLATO);
		versamento.setDataUltimaComunicazioneAca(versamento.getDataUltimaModificaAca().minusMinutes(1));
		versamentoFullRepository.save(versamento);

		Mockito.lenient().when(gpdApiLegacy.updatePositionWithHttpInfo(any(), any(), any(), any(), any()))
				.thenAnswer(PaymentPositionModelUtils::creaResponseUpdatePaymentPositionModelOk);

		eseguiJob();

		ArgumentCaptor<PaymentPositionModel> payload = ArgumentCaptor.forClass(PaymentPositionModel.class);
		verify(gpdApiLegacy).updatePositionWithHttpInfo(any(), any(), payload.capture(), any(), eq(false));
		assertEquals(unitaOperativa.nomeUfficioAtteso, payload.getValue().getOfficeName());
	}

	@ParameterizedTest(name = "{0}")
	@EnumSource(UnitaOperativa.class)
	@DisplayName("v3, invio: nome dell'ufficio secondo l'unita' operativa della posizione")
	void UC_7_03_V3Invio(UnitaOperativa unitaOperativa) throws Exception {
		creaPosizione(unitaOperativa, StatoOpzionePagamento.DISPONIBILE, StatoVersamento.NON_ESEGUITO, null);

		Mockito.lenient().when(gpdApiV3.createPositionWithHttpInfo(any(), any(), any(), any()))
				.thenAnswer(PaymentPositionModelV3Utils::creaResponseCreateOk);
		Mockito.lenient().when(gpdApiV3.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()))
				.thenAnswer(invocation -> PaymentPositionModelV3Utils.creaResponseGetPositionOk(invocation,
						PaymentPositionModelResponseV3.StatusEnum.VALID));

		eseguiJob();

		ArgumentCaptor<PaymentPositionModelV3> payload = ArgumentCaptor.forClass(PaymentPositionModelV3.class);
		verify(gpdApiV3).createPositionWithHttpInfo(any(), payload.capture(), any(), any());
		assertEquals(unitaOperativa.nomeUfficioAtteso, payload.getValue().getOfficeName());
	}

	@ParameterizedTest(name = "{0}")
	@EnumSource(UnitaOperativa.class)
	@DisplayName("v3, ritiro: nome dell'ufficio secondo l'unita' operativa della posizione")
	void UC_7_04_V3Ritiro(UnitaOperativa unitaOperativa) throws Exception {
		creaPosizione(unitaOperativa, StatoOpzionePagamento.ANNULLATA, StatoVersamento.ANNULLATO,
				OffsetDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MILLIS));

		Mockito.lenient().when(gpdApiV3.updatePositionWithHttpInfo(any(), any(), any(), any(), any()))
				.thenAnswer(PaymentPositionModelV3Utils::creaResponseUpdateOk);

		eseguiJob();

		ArgumentCaptor<PaymentPositionModelV3> payload = ArgumentCaptor.forClass(PaymentPositionModelV3.class);
		verify(gpdApiV3).updatePositionWithHttpInfo(any(), any(), payload.capture(), eq(false), any());
		assertEquals(unitaOperativa.nomeUfficioAtteso, payload.getValue().getOfficeName());
	}

	private void creaPosizione(UnitaOperativa unitaOperativa, StatoOpzionePagamento statoOpzione,
			StatoVersamento statoRata, OffsetDateTime dataUltimaComunicazioneAca) {
		DocumentoFullEntity documento = PosizioneDebitoriaUtils.creaDocumento(documentoFullRepository,
				applicazioneRepository, dominioRepository, "DOC-UO", dataUltimaComunicazioneAca);
		documento.setIdUnitaOperativa(unitaOperativa.id);
		documentoFullRepository.save(documento);

		PdDebitoreFullEntity debitore = PosizioneDebitoriaUtils.creaDebitore(debitoreFullRepository,
				pdDebitoreFullRepository, documento.getId());
		OpzionePagamentoFullEntity opzione = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository,
				debitore.getId(), TipologiaOpzionePagamento.SOLUZIONE_UNICA, statoOpzione);
		PosizioneDebitoriaUtils.creaRata(versamentoFullRepository, applicazioneRepository, dominioRepository,
				opzione.getId(), "DOC-UO", 1, 10.00, statoRata);
	}

	private void eseguiJob() throws Exception {
		JobExecution jobExecution = jobLauncherTestUtils.launchJob();
		assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
	}
}
