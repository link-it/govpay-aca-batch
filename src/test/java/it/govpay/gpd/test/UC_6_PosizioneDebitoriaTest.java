package it.govpay.gpd.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;

import it.govpay.gpd.Application;
import it.govpay.gpd.client.api.DebtPositionsApiApi;
import it.govpay.gpd.client.beans.PaymentPositionModel;
import it.govpay.gpd.client.beans.PaymentPositionModelBaseResponse;
import it.govpay.gpd.client.v3.api.DebtPositionActionsApiApi;
import it.govpay.gpd.client.v3.api.DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi;
import it.govpay.gpd.client.v3.beans.PaymentPositionModelResponseV3;
import it.govpay.gpd.client.v3.beans.PaymentPositionModelResponseV3.StatusEnum;
import it.govpay.gpd.client.v3.beans.PaymentPositionModelV3;
import it.govpay.gpd.entity.VersamentoGpdEntity.StatoVersamento;
import it.govpay.gpd.repository.DocumentoRepository;
import it.govpay.gpd.repository.VersamentoRepository;
import it.govpay.gpd.service.GpdApiService;
import it.govpay.gpd.test.entity.DocumentoFullEntity;
import it.govpay.gpd.test.entity.OpzionePagamentoFullEntity;
import it.govpay.gpd.test.entity.VersamentoFullEntity;
import it.govpay.gpd.test.repository.DocumentoFullRepository;
import it.govpay.gpd.test.repository.OpzionePagamentoFullRepository;
import it.govpay.gpd.test.repository.SoggettoDebitoreFullRepository;
import it.govpay.gpd.test.repository.VersamentoFullRepository;
import it.govpay.gpd.test.utils.PaymentPositionModelUtils;
import it.govpay.gpd.test.utils.PaymentPositionModelV3Utils;
import it.govpay.gpd.test.utils.PosizioneDebitoriaUtils;
import it.govpay.pendenze.model.StatoOpzionePagamento;
import it.govpay.pendenze.model.TipologiaOpzionePagamento;
import it.govpay.pendenze.service.CausaleVersamentoCodec;

/**
 * Verifica end-to-end dello step {@code spedizionePosizioneDebitoriaStep} (reader +
 * processor + writer, nello stesso job {@code gpdSenderJob} dello step legacy) sulla
 * tabella di decisione: caricamento, opzione ATTIVATA (skip), annullamento totale.
 */
@SpringBootTest(classes = Application.class)
@EnableAutoConfiguration
@DisplayName("Test Invio GPD - Posizioni debitorie v3")
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
@SpringBatchTest
class UC_6_PosizioneDebitoriaTest extends UC_00_BaseTest {

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
	private SoggettoDebitoreFullRepository soggettoDebitoreFullRepository;

	@Autowired
	private VersamentoFullRepository versamentoFullRepository;

	@Autowired
	private DocumentoRepository documentoRepository;

	@Autowired
	private VersamentoRepository versamentoRepository;

	DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi gpdApiV3;
	DebtPositionActionsApiApi gpdActionsApiV3;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		this.cleanDB();

		gpdApiV3 = Mockito.mock(DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi.class);
		gpdActionsApiV3 = Mockito.mock(DebtPositionActionsApiApi.class);

		Mockito.lenient().when(gpdApiService.getGpdApiV3(any())).thenReturn(gpdApiV3);
		Mockito.lenient().when(gpdApiService.getGpdActionsApiV3(any())).thenReturn(gpdActionsApiV3);
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
		this.soggettoDebitoreFullRepository.deleteAll();
		this.documentoFullRepository.deleteAll();
	}

	@Test
	@DisplayName("Soluzione unica mai comunicata: createPosition, getPosition (VALID), nessuna publish")
	void UC_6_01_SoluzioneUnicaMaiComunicata_Create() throws Exception {
		DocumentoFullEntity documento = PosizioneDebitoriaUtils.creaDocumento(documentoFullRepository, applicazioneRepository, dominioRepository, "DOC-001", null);
		PosizioneDebitoriaUtils.creaSoggettoDebitoreOrdineZero(soggettoDebitoreFullRepository, documento.getId());
		OpzionePagamentoFullEntity opzione = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, documento.getId(),
				TipologiaOpzionePagamento.SOLUZIONE_UNICA, StatoOpzionePagamento.DISPONIBILE);
		PosizioneDebitoriaUtils.creaRata(versamentoFullRepository, applicazioneRepository, dominioRepository,
				opzione.getId(), "DOC-001", 1, 10.00, StatoVersamento.NON_ESEGUITO);

		Mockito.lenient().when(gpdApiV3.createPositionWithHttpInfo(any(), any(), any(), any()))
				.thenAnswer((Answer<ResponseEntity<PaymentPositionModelV3>>) PaymentPositionModelV3Utils::creaResponseCreateOk);
		Mockito.lenient().when(gpdApiV3.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()))
				.thenAnswer((Answer<ResponseEntity<PaymentPositionModelResponseV3>>) invocation -> PaymentPositionModelV3Utils.creaResponseGetPositionOk(invocation, StatusEnum.VALID));

		JobExecution jobExecution = jobLauncherTestUtils.launchJob();
		assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

		DocumentoFullEntity documentoAggiornato = documentoFullRepository.findById(documento.getId()).orElseThrow();
		assertNotNull(documentoAggiornato.getDataUltimaComunicazioneAca());

		VersamentoFullEntity rataAggiornata = versamentoFullRepository.findById(
				versamentoFullRepository.findFirstByOrderByIdDesc().getId()).orElseThrow();
		assertNotNull(rataAggiornata.getDataUltimaComunicazioneAca());

		verify(gpdActionsApiV3, never()).publishPositionWithHttpInfo(any(), any(), any());
	}

	@Test
	@DisplayName("Opzione ATTIVATA: nessun invio, solo marcatura come comunicata")
	void UC_6_02_OpzioneAttivata_Skip() throws Exception {
		DocumentoFullEntity documento = PosizioneDebitoriaUtils.creaDocumento(documentoFullRepository, applicazioneRepository, dominioRepository, "DOC-002", null);
		PosizioneDebitoriaUtils.creaSoggettoDebitoreOrdineZero(soggettoDebitoreFullRepository, documento.getId());
		OpzionePagamentoFullEntity opzione = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, documento.getId(),
				TipologiaOpzionePagamento.SOLUZIONE_UNICA, StatoOpzionePagamento.ATTIVATA);
		PosizioneDebitoriaUtils.creaRata(versamentoFullRepository, applicazioneRepository, dominioRepository,
				opzione.getId(), "DOC-002", 1, 10.00, StatoVersamento.ESEGUITO);

		JobExecution jobExecution = jobLauncherTestUtils.launchJob();
		assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

		DocumentoFullEntity documentoAggiornato = documentoFullRepository.findById(documento.getId()).orElseThrow();
		assertNotNull(documentoAggiornato.getDataUltimaComunicazioneAca());

		verify(gpdApiService, never()).getGpdApiV3(any());
		verify(gpdApiService, never()).getGpdActionsApiV3(any());
	}

	@Test
	@DisplayName("Annullamento totale (tutte ANNULLATA, gia' comunicata): PUT toPublish=false")
	void UC_6_03_AnnullamentoTotale() throws Exception {
		OffsetDateTime comunicataIeri = OffsetDateTime.now().minusDays(1);
		DocumentoFullEntity documento = PosizioneDebitoriaUtils.creaDocumento(documentoFullRepository, applicazioneRepository, dominioRepository, "DOC-003", comunicataIeri);
		PosizioneDebitoriaUtils.creaSoggettoDebitoreOrdineZero(soggettoDebitoreFullRepository, documento.getId());
		OpzionePagamentoFullEntity opzione = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, documento.getId(),
				TipologiaOpzionePagamento.SOLUZIONE_UNICA, StatoOpzionePagamento.ANNULLATA);
		VersamentoFullEntity rata = PosizioneDebitoriaUtils.creaRata(versamentoFullRepository, applicazioneRepository, dominioRepository,
				opzione.getId(), "DOC-003", 1, 10.00, StatoVersamento.ANNULLATO);
		// come il vero writer v3 dopo un invio riuscito: marca anche la rata, non solo il
		// documento - deve restare distinguibile da una comunicazione legacy pre-migrazione
		// (qui avviene dopo la creazione dell'opzione, non prima, come nel caso legacy).
		rata.setDataUltimaComunicazioneAca(OffsetDateTime.now());
		versamentoFullRepository.save(rata);

		Mockito.lenient().when(gpdApiV3.updatePositionWithHttpInfo(any(), any(), any(), any(), any()))
				.thenAnswer((Answer<ResponseEntity<PaymentPositionModelV3>>) PaymentPositionModelV3Utils::creaResponseUpdateOk);

		JobExecution jobExecution = jobLauncherTestUtils.launchJob();
		assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

		DocumentoFullEntity documentoAggiornato = documentoFullRepository.findById(documento.getId()).orElseThrow();
		assertTrue(documentoAggiornato.getDataUltimaComunicazioneAca().isAfter(comunicataIeri));

		verify(gpdApiV3).updatePositionWithHttpInfo(any(), any(), any(), org.mockito.ArgumentMatchers.eq(false), any());
	}

	@Test
	@DisplayName("Aggiornamento con 404 (posizione mai creata sul GPD): recupero tramite creazione")
	void UC_6_04_Aggiornamento404_RecuperaConCreazione() throws Exception {
		OffsetDateTime comunicataIeri = OffsetDateTime.now().minusDays(1);
		DocumentoFullEntity documento = PosizioneDebitoriaUtils.creaDocumento(documentoFullRepository, applicazioneRepository, dominioRepository, "DOC-004", comunicataIeri);
		PosizioneDebitoriaUtils.creaSoggettoDebitoreOrdineZero(soggettoDebitoreFullRepository, documento.getId());
		OpzionePagamentoFullEntity opzione = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, documento.getId(),
				TipologiaOpzionePagamento.SOLUZIONE_UNICA, StatoOpzionePagamento.DISPONIBILE);
		VersamentoFullEntity rata = PosizioneDebitoriaUtils.creaRata(versamentoFullRepository, applicazioneRepository, dominioRepository,
				opzione.getId(), "DOC-004", 1, 10.00, StatoVersamento.NON_ESEGUITO);
		// come il vero writer v3 dopo un invio riuscito: marca anche la rata, non solo il documento.
		rata.setDataUltimaComunicazioneAca(OffsetDateTime.now());
		versamentoFullRepository.save(rata);

		Mockito.lenient().when(gpdApiV3.updatePositionWithHttpInfo(any(), any(), any(), any(), any()))
				.thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found"));
		Mockito.lenient().when(gpdApiV3.createPositionWithHttpInfo(any(), any(), any(), any()))
				.thenAnswer((Answer<ResponseEntity<PaymentPositionModelV3>>) PaymentPositionModelV3Utils::creaResponseCreateOk);
		Mockito.lenient().when(gpdApiV3.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()))
				.thenAnswer((Answer<ResponseEntity<PaymentPositionModelResponseV3>>) invocation -> PaymentPositionModelV3Utils.creaResponseGetPositionOk(invocation, StatusEnum.VALID));

		JobExecution jobExecution = jobLauncherTestUtils.launchJob();
		assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

		verify(gpdApiV3).updatePositionWithHttpInfo(any(), any(), any(), org.mockito.ArgumentMatchers.eq(true), any());
		verify(gpdApiV3).createPositionWithHttpInfo(any(), any(), any(), any());

		DocumentoFullEntity documentoAggiornato = documentoFullRepository.findById(documento.getId()).orElseThrow();
		assertTrue(documentoAggiornato.getDataUltimaComunicazioneAca().isAfter(comunicataIeri));
	}

	@Test
	@DisplayName("Annullamento con 404 (nulla da ritirare): scarto definitivo, nessun recupero tramite creazione")
	void UC_6_05_Annullamento404_NessunRecupero() throws Exception {
		OffsetDateTime comunicataIeri = OffsetDateTime.now().minusDays(1);
		DocumentoFullEntity documento = PosizioneDebitoriaUtils.creaDocumento(documentoFullRepository, applicazioneRepository, dominioRepository, "DOC-005", comunicataIeri);
		PosizioneDebitoriaUtils.creaSoggettoDebitoreOrdineZero(soggettoDebitoreFullRepository, documento.getId());
		OpzionePagamentoFullEntity opzione = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, documento.getId(),
				TipologiaOpzionePagamento.SOLUZIONE_UNICA, StatoOpzionePagamento.ANNULLATA);
		VersamentoFullEntity rata = PosizioneDebitoriaUtils.creaRata(versamentoFullRepository, applicazioneRepository, dominioRepository,
				opzione.getId(), "DOC-005", 1, 10.00, StatoVersamento.ANNULLATO);
		// come il vero writer v3 dopo un invio riuscito: marca anche la rata, non solo il documento.
		rata.setDataUltimaComunicazioneAca(OffsetDateTime.now());
		versamentoFullRepository.save(rata);

		Mockito.lenient().when(gpdApiV3.updatePositionWithHttpInfo(any(), any(), any(), any(), any()))
				.thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found"));

		JobExecution jobExecution = jobLauncherTestUtils.launchJob();
		assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

		verify(gpdApiV3).updatePositionWithHttpInfo(any(), any(), any(), org.mockito.ArgumentMatchers.eq(false), any());
		verify(gpdApiV3, never()).createPositionWithHttpInfo(any(), any(), any(), any());

		DocumentoFullEntity documentoAggiornato = documentoFullRepository.findById(documento.getId()).orElseThrow();
		assertTrue(documentoAggiornato.getDataUltimaComunicazioneAca().isAfter(comunicataIeri));
	}

	@Test
	@DisplayName("Creazione con 409 seguito da lettura con 404: esito rinviato, nessuna marcatura come comunicata")
	void UC_6_06_Creazione409LetturaNonTrovata_Rinviata() throws Exception {
		DocumentoFullEntity documento = PosizioneDebitoriaUtils.creaDocumento(documentoFullRepository, applicazioneRepository, dominioRepository, "DOC-006", null);
		PosizioneDebitoriaUtils.creaSoggettoDebitoreOrdineZero(soggettoDebitoreFullRepository, documento.getId());
		OpzionePagamentoFullEntity opzione = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, documento.getId(),
				TipologiaOpzionePagamento.SOLUZIONE_UNICA, StatoOpzionePagamento.DISPONIBILE);
		PosizioneDebitoriaUtils.creaRata(versamentoFullRepository, applicazioneRepository, dominioRepository,
				opzione.getId(), "DOC-006", 1, 10.00, StatoVersamento.NON_ESEGUITO);

		Mockito.lenient().when(gpdApiV3.createPositionWithHttpInfo(any(), any(), any(), any()))
				.thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT, "Conflict"));
		Mockito.lenient().when(gpdApiV3.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()))
				.thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found"));

		JobExecution jobExecution = jobLauncherTestUtils.launchJob();
		assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

		verify(gpdActionsApiV3, never()).publishPositionWithHttpInfo(any(), any(), any());

		DocumentoFullEntity documentoAggiornato = documentoFullRepository.findById(documento.getId()).orElseThrow();
		assertEquals(null, documentoAggiornato.getDataUltimaComunicazioneAca());
	}

	@Test
	@DisplayName("Creazione con 201 seguita da lettura di verifica fallita (404): esito rinviato, nessuna marcatura come comunicata")
	void UC_6_07_Creazione201LetturaFallita_Rinviata() throws Exception {
		DocumentoFullEntity documento = PosizioneDebitoriaUtils.creaDocumento(documentoFullRepository, applicazioneRepository, dominioRepository, "DOC-007", null);
		PosizioneDebitoriaUtils.creaSoggettoDebitoreOrdineZero(soggettoDebitoreFullRepository, documento.getId());
		OpzionePagamentoFullEntity opzione = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, documento.getId(),
				TipologiaOpzionePagamento.SOLUZIONE_UNICA, StatoOpzionePagamento.DISPONIBILE);
		PosizioneDebitoriaUtils.creaRata(versamentoFullRepository, applicazioneRepository, dominioRepository,
				opzione.getId(), "DOC-007", 1, 10.00, StatoVersamento.NON_ESEGUITO);

		Mockito.lenient().when(gpdApiV3.createPositionWithHttpInfo(any(), any(), any(), any()))
				.thenAnswer((Answer<ResponseEntity<PaymentPositionModelV3>>) PaymentPositionModelV3Utils::creaResponseCreateOk);
		Mockito.lenient().when(gpdApiV3.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()))
				.thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found"));

		JobExecution jobExecution = jobLauncherTestUtils.launchJob();
		assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

		verify(gpdActionsApiV3, never()).publishPositionWithHttpInfo(any(), any(), any());

		// la creazione e' confermata (201), ma la verifica fallisce: non si puo'
		// escludere che sia rimasta DRAFT, non va marcata come comunicata.
		DocumentoFullEntity documentoAggiornato = documentoFullRepository.findById(documento.getId()).orElseThrow();
		assertEquals(null, documentoAggiornato.getDataUltimaComunicazioneAca());
	}

	@Test
	@DisplayName("Rata con causale propria: la installment usa la causale della rata, non la descrizione del documento")
	void UC_6_08_RataConCausalePropria_UsaCausaleRata() throws Exception {
		DocumentoFullEntity documento = PosizioneDebitoriaUtils.creaDocumento(documentoFullRepository, applicazioneRepository, dominioRepository, "DOC-008", null);
		PosizioneDebitoriaUtils.creaSoggettoDebitoreOrdineZero(soggettoDebitoreFullRepository, documento.getId());
		OpzionePagamentoFullEntity opzione = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, documento.getId(),
				TipologiaOpzionePagamento.SOLUZIONE_UNICA, StatoOpzionePagamento.DISPONIBILE);
		VersamentoFullEntity rata = PosizioneDebitoriaUtils.creaRata(versamentoFullRepository, applicazioneRepository, dominioRepository,
				opzione.getId(), "DOC-008", 1, 10.00, StatoVersamento.NON_ESEGUITO);
		rata.setCausaleVersamento(CausaleVersamentoCodec.encode("Causale specifica della rata"));
		versamentoFullRepository.save(rata);

		Mockito.lenient().when(gpdApiV3.createPositionWithHttpInfo(any(), any(), any(), any()))
				.thenAnswer((Answer<ResponseEntity<PaymentPositionModelV3>>) PaymentPositionModelV3Utils::creaResponseCreateOk);
		Mockito.lenient().when(gpdApiV3.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()))
				.thenAnswer((Answer<ResponseEntity<PaymentPositionModelResponseV3>>) invocation -> PaymentPositionModelV3Utils.creaResponseGetPositionOk(invocation, StatusEnum.VALID));

		JobExecution jobExecution = jobLauncherTestUtils.launchJob();
		assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

		ArgumentCaptor<PaymentPositionModelV3> modelCaptor = ArgumentCaptor.forClass(PaymentPositionModelV3.class);
		verify(gpdApiV3).createPositionWithHttpInfo(any(), modelCaptor.capture(), any(), any());

		assertEquals("Causale specifica della rata",
				modelCaptor.getValue().getPaymentOption().get(0).getInstallments().get(0).getDescription());
	}

	@Test
	@DisplayName("Piano rateale con una rata gia' comunicata a v1 prima della migrazione: la posizione resta fuori da v3, la rata non comunicata resta nel legacy")
	void UC_6_09_RataGiaComunicataAV1_RestaNelLegacy() throws Exception {
		DocumentoFullEntity documento = PosizioneDebitoriaUtils.creaDocumento(documentoFullRepository, applicazioneRepository, dominioRepository, "DOC-009", null);
		// flag scritto dalla migrazione (06_opzioni_pagamento_documenti_esistenti.sql):
		// questo documento resta nel legacy indipendentemente da cosa scrivono i due writer.
		documento.setAcaLegacy(true);
		documentoFullRepository.save(documento);
		PosizioneDebitoriaUtils.creaSoggettoDebitoreOrdineZero(soggettoDebitoreFullRepository, documento.getId());
		OpzionePagamentoFullEntity opzione = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, documento.getId(),
				TipologiaOpzionePagamento.PIANO_RATEALE, StatoOpzionePagamento.DISPONIBILE);

		OffsetDateTime comunicataDieciGiorniFa = OffsetDateTime.now().minusDays(10);
		VersamentoFullEntity rata1 = PosizioneDebitoriaUtils.creaRata(versamentoFullRepository, applicazioneRepository, dominioRepository,
				opzione.getId(), "DOC-009-R1", 1, 10.00, StatoVersamento.NON_ESEGUITO);
		// gia' comunicata a v1 e nulla e' cambiato da allora: stessa data su modifica e
		// comunicazione, non deve risultare "da rispedire" al legacy in questo giro.
		rata1.setDataUltimaComunicazioneAca(comunicataDieciGiorniFa);
		rata1.setDataUltimaModificaAca(comunicataDieciGiorniFa);
		versamentoFullRepository.save(rata1);

		VersamentoFullEntity rata2 = PosizioneDebitoriaUtils.creaRata(versamentoFullRepository, applicazioneRepository, dominioRepository,
				opzione.getId(), "DOC-009-R2", 2, 10.00, StatoVersamento.NON_ESEGUITO);

		DebtPositionsApiApi gpdApiLegacy = Mockito.mock(DebtPositionsApiApi.class);
		Mockito.lenient().when(gpdApiService.getGpdApi(any())).thenReturn(gpdApiLegacy);
		Mockito.lenient().when(gpdApiService.getGpdActionsApi(any()))
				.thenReturn(Mockito.mock(it.govpay.gpd.client.api.DebtPositionActionsApiApi.class));
		Mockito.lenient().when(gpdApiService.getGpdBasePath(any())).thenReturn("http://fakehost:8080/");
		Mockito.lenient().when(gpdApiLegacy.createPositionWithHttpInfo(any(), any(), any(), any()))
				.thenAnswer((Answer<ResponseEntity<PaymentPositionModel>>) PaymentPositionModelUtils::creaResponseCreatePaymentPositionModelOk);
		Mockito.lenient().when(gpdApiLegacy.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()))
				.thenAnswer((Answer<ResponseEntity<PaymentPositionModelBaseResponse>>) invocation ->
						PaymentPositionModelUtils.creaResponseGetPositionOk(invocation, PaymentPositionModelBaseResponse.StatusEnum.VALID));

		JobExecution jobExecution = jobLauncherTestUtils.launchJob();
		assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

		// la posizione v3 non viene mai toccata: resta fuori dal reader del nuovo step
		verify(gpdApiService, never()).getGpdApiV3(any());
		verify(gpdApiService, never()).getGpdActionsApiV3(any());
		DocumentoFullEntity documentoAggiornato = documentoFullRepository.findById(documento.getId()).orElseThrow();
		assertEquals(null, documentoAggiornato.getDataUltimaComunicazioneAca());

		// la rata non ancora comunicata viene gestita (di nuovo) dal legacy, come prima della migrazione
		verify(gpdApiLegacy).createPositionWithHttpInfo(any(), any(), any(), any());
		VersamentoFullEntity rata2Aggiornata = versamentoFullRepository.findById(rata2.getId()).orElseThrow();
		assertNotNull(rata2Aggiornata.getDataUltimaComunicazioneAca());

		// rata1 non e' cambiata da quando e' stata comunicata: nessun rinvio inutile
		verify(gpdApiLegacy, never()).updatePositionWithHttpInfo(any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("Rata migrata gia' comunicata a v1 che cambia in seguito: il legacy deve poterla ancora annullare, non solo escluderla")
	void UC_6_10_RataGiaComunicataAV1_AnnullamentoSuccessivoRestaNelLegacy() throws Exception {
		DocumentoFullEntity documento = PosizioneDebitoriaUtils.creaDocumento(documentoFullRepository, applicazioneRepository, dominioRepository, "DOC-010", null);
		documento.setAcaLegacy(true);
		documentoFullRepository.save(documento);
		PosizioneDebitoriaUtils.creaSoggettoDebitoreOrdineZero(soggettoDebitoreFullRepository, documento.getId());
		OpzionePagamentoFullEntity opzione = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, documento.getId(),
				TipologiaOpzionePagamento.PIANO_RATEALE, StatoOpzionePagamento.DISPONIBILE);

		VersamentoFullEntity rata = PosizioneDebitoriaUtils.creaRata(versamentoFullRepository, applicazioneRepository, dominioRepository,
				opzione.getId(), "DOC-010-R1", 1, 10.00, StatoVersamento.ANNULLATO);
		// comunicata a v1 10 giorni fa, poi annullata oggi: deve ancora passare per il
		// legacy (stesso IUPD con cui era stata creata), non per v3.
		rata.setDataUltimaComunicazioneAca(OffsetDateTime.now().minusDays(10));
		rata.setDataUltimaModificaAca(OffsetDateTime.now());
		versamentoFullRepository.save(rata);

		DebtPositionsApiApi gpdApiLegacy = Mockito.mock(DebtPositionsApiApi.class);
		Mockito.lenient().when(gpdApiService.getGpdApi(any())).thenReturn(gpdApiLegacy);
		Mockito.lenient().when(gpdApiService.getGpdActionsApi(any()))
				.thenReturn(Mockito.mock(it.govpay.gpd.client.api.DebtPositionActionsApiApi.class));
		Mockito.lenient().when(gpdApiService.getGpdBasePath(any())).thenReturn("http://fakehost:8080/");
		Mockito.lenient().when(gpdApiLegacy.updatePositionWithHttpInfo(any(), any(), any(), any(), any()))
				.thenAnswer((Answer<ResponseEntity<PaymentPositionModel>>) PaymentPositionModelUtils::creaResponseUpdatePaymentPositionModelOk);

		JobExecution jobExecution = jobLauncherTestUtils.launchJob();
		assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

		verify(gpdApiService, never()).getGpdApiV3(any());
		verify(gpdApiLegacy).updatePositionWithHttpInfo(any(), any(), any(), any(), org.mockito.ArgumentMatchers.eq(false));
	}

	@Test
	@DisplayName("Documento con due opzioni, aca_legacy=true: entrambe restano nel legacy, anche l'opzione mai comunicata individualmente")
	void UC_6_11_DocumentoConOpzioneLegacyEOpzioneNativa_EntrambeNelLegacy() throws Exception {
		DocumentoFullEntity documento = PosizioneDebitoriaUtils.creaDocumento(documentoFullRepository, applicazioneRepository, dominioRepository, "DOC-011", null);
		// basta il flag sul documento: non serve piu' che ciascuna opzione porti il
		// proprio segnale di storia legacy, risolve esattamente il caso del documento
		// con due opzioni di cui una sola con una rata gia' comunicata a v1.
		documento.setAcaLegacy(true);
		documentoFullRepository.save(documento);
		PosizioneDebitoriaUtils.creaSoggettoDebitoreOrdineZero(soggettoDebitoreFullRepository, documento.getId());

		OffsetDateTime comunicataDieciGiorniFa = OffsetDateTime.now().minusDays(10);
		OpzionePagamentoFullEntity opzioneLegacy = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, documento.getId(),
				TipologiaOpzionePagamento.SOLUZIONE_UNICA_ENTRO, StatoOpzionePagamento.DISPONIBILE);
		VersamentoFullEntity rataLegacy = PosizioneDebitoriaUtils.creaRata(versamentoFullRepository, applicazioneRepository, dominioRepository,
				opzioneLegacy.getId(), "DOC-011-ENTRO", 1, 10.00, StatoVersamento.NON_ESEGUITO);
		rataLegacy.setDataUltimaComunicazioneAca(comunicataDieciGiorniFa);
		rataLegacy.setDataUltimaModificaAca(comunicataDieciGiorniFa);
		versamentoFullRepository.save(rataLegacy);

		OpzionePagamentoFullEntity opzioneSenzaSegnale = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, documento.getId(),
				TipologiaOpzionePagamento.SOLUZIONE_UNICA_OLTRE, StatoOpzionePagamento.DISPONIBILE);
		VersamentoFullEntity rataSenzaSegnale = PosizioneDebitoriaUtils.creaRata(versamentoFullRepository, applicazioneRepository, dominioRepository,
				opzioneSenzaSegnale.getId(), "DOC-011-OLTRE", 1, 10.00, StatoVersamento.NON_ESEGUITO);

		DebtPositionsApiApi gpdApiLegacy = Mockito.mock(DebtPositionsApiApi.class);
		Mockito.lenient().when(gpdApiService.getGpdApi(any())).thenReturn(gpdApiLegacy);
		Mockito.lenient().when(gpdApiService.getGpdActionsApi(any()))
				.thenReturn(Mockito.mock(it.govpay.gpd.client.api.DebtPositionActionsApiApi.class));
		Mockito.lenient().when(gpdApiService.getGpdBasePath(any())).thenReturn("http://fakehost:8080/");
		Mockito.lenient().when(gpdApiLegacy.createPositionWithHttpInfo(any(), any(), any(), any()))
				.thenAnswer((Answer<ResponseEntity<PaymentPositionModel>>) PaymentPositionModelUtils::creaResponseCreatePaymentPositionModelOk);
		Mockito.lenient().when(gpdApiLegacy.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()))
				.thenAnswer((Answer<ResponseEntity<PaymentPositionModelBaseResponse>>) invocation ->
						PaymentPositionModelUtils.creaResponseGetPositionOk(invocation, PaymentPositionModelBaseResponse.StatusEnum.VALID));

		JobExecution jobExecution = jobLauncherTestUtils.launchJob();
		assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

		// l'intero documento resta fuori da v3, comprese le opzioni senza segnale proprio
		verify(gpdApiService, never()).getGpdApiV3(any());
		verify(gpdApiService, never()).getGpdActionsApiV3(any());

		// la rata dell'opzione senza segnale proprio non resta orfana: la gestisce il legacy
		verify(gpdApiLegacy).createPositionWithHttpInfo(any(), any(), any(), any());
		VersamentoFullEntity rataSenzaSegnaleAggiornata = versamentoFullRepository.findById(rataSenzaSegnale.getId()).orElseThrow();
		assertNotNull(rataSenzaSegnaleAggiornata.getDataUltimaComunicazioneAca());
	}

	@Test
	@DisplayName("Due esecuzioni successive del batch su un documento aca_legacy, con un annullamento tra le due: il legacy gestisce anche il secondo invio, v3 resta escluso in entrambe")
	void UC_6_12_DueEsecuzioniSuccessive_RoutingInvariato() throws Exception {
		DocumentoFullEntity documento = PosizioneDebitoriaUtils.creaDocumento(documentoFullRepository, applicazioneRepository, dominioRepository, "DOC-012", null);
		documento.setAcaLegacy(true);
		documentoFullRepository.save(documento);
		PosizioneDebitoriaUtils.creaSoggettoDebitoreOrdineZero(soggettoDebitoreFullRepository, documento.getId());
		OpzionePagamentoFullEntity opzione = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, documento.getId(),
				TipologiaOpzionePagamento.SOLUZIONE_UNICA, StatoOpzionePagamento.DISPONIBILE);
		VersamentoFullEntity rata = PosizioneDebitoriaUtils.creaRata(versamentoFullRepository, applicazioneRepository, dominioRepository,
				opzione.getId(), "DOC-012-R1", 1, 10.00, StatoVersamento.NON_ESEGUITO);

		DebtPositionsApiApi gpdApiLegacy = Mockito.mock(DebtPositionsApiApi.class);
		Mockito.lenient().when(gpdApiService.getGpdApi(any())).thenReturn(gpdApiLegacy);
		Mockito.lenient().when(gpdApiService.getGpdActionsApi(any()))
				.thenReturn(Mockito.mock(it.govpay.gpd.client.api.DebtPositionActionsApiApi.class));
		Mockito.lenient().when(gpdApiService.getGpdBasePath(any())).thenReturn("http://fakehost:8080/");
		Mockito.lenient().when(gpdApiLegacy.createPositionWithHttpInfo(any(), any(), any(), any()))
				.thenAnswer((Answer<ResponseEntity<PaymentPositionModel>>) PaymentPositionModelUtils::creaResponseCreatePaymentPositionModelOk);
		Mockito.lenient().when(gpdApiLegacy.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()))
				.thenAnswer((Answer<ResponseEntity<PaymentPositionModelBaseResponse>>) invocation ->
						PaymentPositionModelUtils.creaResponseGetPositionOk(invocation, PaymentPositionModelBaseResponse.StatusEnum.VALID));

		JobExecution primaEsecuzione = jobLauncherTestUtils.launchJob();
		assertEquals("COMPLETED", primaEsecuzione.getExitStatus().getExitCode());
		verify(gpdApiService, never()).getGpdApiV3(any());
		verify(gpdApiLegacy).createPositionWithHttpInfo(any(), any(), any(), any());
		VersamentoFullEntity rataDopoPrimaEsecuzione = versamentoFullRepository.findById(rata.getId()).orElseThrow();
		assertNotNull(rataDopoPrimaEsecuzione.getDataUltimaComunicazioneAca());

		// modifica reale tra le due esecuzioni: la rata viene annullata dopo essere stata
		// comunicata dalla prima run - non solo una riesecuzione senza nulla da fare.
		rataDopoPrimaEsecuzione.setStatoVersamento(StatoVersamento.ANNULLATO);
		rataDopoPrimaEsecuzione.setDataUltimaModificaAca(OffsetDateTime.now().plusMinutes(1));
		versamentoFullRepository.save(rataDopoPrimaEsecuzione);
		Mockito.lenient().when(gpdApiLegacy.updatePositionWithHttpInfo(any(), any(), any(), any(), any()))
				.thenAnswer((Answer<ResponseEntity<PaymentPositionModel>>) PaymentPositionModelUtils::creaResponseUpdatePaymentPositionModelOk);

		JobExecution secondaEsecuzione = jobLauncherTestUtils.launchJob(
				new org.springframework.batch.core.job.parameters.JobParametersBuilder().addLong("run", 2L).toJobParameters());
		assertEquals("COMPLETED", secondaEsecuzione.getExitStatus().getExitCode());

		// il legacy gestisce davvero un secondo invio (l'annullamento), non solo una
		// riconferma senza effetti - e il documento resta comunque fuori da v3: aca_legacy
		// non viene mai ricalcolato dagli invii, dei due writer o nel tempo.
		verify(gpdApiLegacy).updatePositionWithHttpInfo(any(), any(), any(), any(), org.mockito.ArgumentMatchers.eq(false));
		verify(gpdApiService, never()).getGpdApiV3(any());
		DocumentoFullEntity documentoDopoSecondaEsecuzione = documentoFullRepository.findById(documento.getId()).orElseThrow();
		assertEquals(Boolean.TRUE, documentoDopoSecondaEsecuzione.getAcaLegacy());
	}
}
