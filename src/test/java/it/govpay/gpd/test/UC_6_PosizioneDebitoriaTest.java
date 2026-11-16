package it.govpay.gpd.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
import it.govpay.gpd.client.v3.api.DebtPositionActionsApiApi;
import it.govpay.gpd.client.v3.api.DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi;
import it.govpay.gpd.client.v3.beans.PaymentOptionModelV3;
import it.govpay.gpd.client.v3.beans.PaymentPositionModelResponseV3;
import it.govpay.gpd.client.v3.beans.PaymentPositionModelResponseV3.StatusEnum;
import it.govpay.gpd.client.v3.beans.PaymentPositionModelV3;
import it.govpay.gpd.entity.VersamentoGpdEntity.StatoVersamento;
import it.govpay.gpd.repository.DocumentoRepository;
import it.govpay.gpd.repository.VersamentoRepository;
import it.govpay.gpd.service.GpdApiService;
import it.govpay.gpd.test.entity.DocumentoFullEntity;
import it.govpay.gpd.test.entity.OpzionePagamentoFullEntity;
import it.govpay.gpd.test.entity.PdDebitoreFullEntity;
import it.govpay.gpd.test.entity.VersamentoFullEntity;
import it.govpay.gpd.test.repository.DocumentoFullRepository;
import it.govpay.gpd.test.repository.OpzionePagamentoFullRepository;
import it.govpay.gpd.test.repository.DebitoreFullRepository;
import it.govpay.gpd.test.repository.PdDebitoreFullRepository;
import it.govpay.gpd.test.repository.VersamentoFullRepository;
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
	private DebitoreFullRepository debitoreFullRepository;

	@Autowired
	private PdDebitoreFullRepository pdDebitoreFullRepository;

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
		this.pdDebitoreFullRepository.deleteAll();
		this.debitoreFullRepository.deleteAll();
		this.documentoFullRepository.deleteAll();
	}

	@Test
	@DisplayName("Soluzione unica mai comunicata: createPosition, getPosition (VALID), nessuna publish")
	void UC_6_01_SoluzioneUnicaMaiComunicata_Create() throws Exception {
		DocumentoFullEntity documento = PosizioneDebitoriaUtils.creaDocumento(documentoFullRepository, applicazioneRepository, dominioRepository, "DOC-001", null);
		PdDebitoreFullEntity debitore = PosizioneDebitoriaUtils.creaDebitore(debitoreFullRepository, pdDebitoreFullRepository, documento.getId());
		OpzionePagamentoFullEntity opzione = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, debitore.getId(),
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
		PdDebitoreFullEntity debitore = PosizioneDebitoriaUtils.creaDebitore(debitoreFullRepository, pdDebitoreFullRepository, documento.getId());
		OpzionePagamentoFullEntity opzione = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, debitore.getId(),
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
		PdDebitoreFullEntity debitore = PosizioneDebitoriaUtils.creaDebitore(debitoreFullRepository, pdDebitoreFullRepository, documento.getId());
		OpzionePagamentoFullEntity opzione = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, debitore.getId(),
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
		PdDebitoreFullEntity debitore = PosizioneDebitoriaUtils.creaDebitore(debitoreFullRepository, pdDebitoreFullRepository, documento.getId());
		OpzionePagamentoFullEntity opzione = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, debitore.getId(),
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
		PdDebitoreFullEntity debitore = PosizioneDebitoriaUtils.creaDebitore(debitoreFullRepository, pdDebitoreFullRepository, documento.getId());
		OpzionePagamentoFullEntity opzione = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, debitore.getId(),
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
		PdDebitoreFullEntity debitore = PosizioneDebitoriaUtils.creaDebitore(debitoreFullRepository, pdDebitoreFullRepository, documento.getId());
		OpzionePagamentoFullEntity opzione = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, debitore.getId(),
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
		PdDebitoreFullEntity debitore = PosizioneDebitoriaUtils.creaDebitore(debitoreFullRepository, pdDebitoreFullRepository, documento.getId());
		OpzionePagamentoFullEntity opzione = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, debitore.getId(),
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
		PdDebitoreFullEntity debitore = PosizioneDebitoriaUtils.creaDebitore(debitoreFullRepository, pdDebitoreFullRepository, documento.getId());
		OpzionePagamentoFullEntity opzione = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, debitore.getId(),
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
	@DisplayName("Posizione con due debitori: una opzione per debitore, ciascuna con il proprio debitore nel payload")
	void UC_6_09_DueDebitori_OgniOpzioneHaIlProprioDebitore() throws Exception {
		DocumentoFullEntity documento = PosizioneDebitoriaUtils.creaDocumento(documentoFullRepository, applicazioneRepository, dominioRepository, "DOC-009", null);
		PdDebitoreFullEntity debitoreA = PosizioneDebitoriaUtils.creaDebitore(debitoreFullRepository, pdDebitoreFullRepository, documento.getId(), 0, "RSSMRA80A01H501U", "Mario Rossi");
		PdDebitoreFullEntity debitoreB = PosizioneDebitoriaUtils.creaDebitore(debitoreFullRepository, pdDebitoreFullRepository, documento.getId(), 1, "VRDLGI85B41F205Z", "Luigia Verdi");
		OpzionePagamentoFullEntity opzioneA = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, debitoreA.getId(),
				TipologiaOpzionePagamento.SOLUZIONE_UNICA, StatoOpzionePagamento.DISPONIBILE);
		OpzionePagamentoFullEntity opzioneB = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, debitoreB.getId(),
				TipologiaOpzionePagamento.SOLUZIONE_UNICA, StatoOpzionePagamento.DISPONIBILE);
		PosizioneDebitoriaUtils.creaRata(versamentoFullRepository, applicazioneRepository, dominioRepository,
				opzioneA.getId(), "DOC-009-A", 1, 10.00, StatoVersamento.NON_ESEGUITO);
		PosizioneDebitoriaUtils.creaRata(versamentoFullRepository, applicazioneRepository, dominioRepository,
				opzioneB.getId(), "DOC-009-B", 1, 20.00, StatoVersamento.NON_ESEGUITO);

		Mockito.lenient().when(gpdApiV3.createPositionWithHttpInfo(any(), any(), any(), any()))
				.thenAnswer((Answer<ResponseEntity<PaymentPositionModelV3>>) PaymentPositionModelV3Utils::creaResponseCreateOk);
		Mockito.lenient().when(gpdApiV3.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()))
				.thenAnswer((Answer<ResponseEntity<PaymentPositionModelResponseV3>>) invocation -> PaymentPositionModelV3Utils.creaResponseGetPositionOk(invocation, StatusEnum.VALID));

		JobExecution jobExecution = jobLauncherTestUtils.launchJob();
		assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

		ArgumentCaptor<PaymentPositionModelV3> modelCaptor = ArgumentCaptor.forClass(PaymentPositionModelV3.class);
		verify(gpdApiV3).createPositionWithHttpInfo(any(), modelCaptor.capture(), any(), any());

		List<PaymentOptionModelV3> opzioni = modelCaptor.getValue().getPaymentOption();
		assertEquals(2, opzioni.size());
		assertEquals(Set.of("RSSMRA80A01H501U", "VRDLGI85B41F205Z"),
				opzioni.stream().map(o -> o.getDebtor().getFiscalCode()).collect(Collectors.toSet()));
		assertEquals(Set.of("Mario Rossi", "Luigia Verdi"),
				opzioni.stream().map(o -> o.getDebtor().getFullName()).collect(Collectors.toSet()));
	}

	@Test
	@DisplayName("Debitore rimosso dopo l'ultima comunicazione: la posizione va in DRAFT con tutte le opzioni e viene ripubblicata con le sole opzioni del debitore rimasto")
	void UC_6_10_DebitoreRimosso_RitiroERipubblicazione() throws Exception {
		OffsetDateTime comunicataIeri = OffsetDateTime.now().minusDays(1);
		DocumentoFullEntity documento = PosizioneDebitoriaUtils.creaDocumento(documentoFullRepository, applicazioneRepository, dominioRepository, "DOC-010", comunicataIeri);
		PdDebitoreFullEntity debitoreA = PosizioneDebitoriaUtils.creaDebitore(debitoreFullRepository, pdDebitoreFullRepository, documento.getId(), 0, "RSSMRA80A01H501U", "Mario Rossi");
		PdDebitoreFullEntity debitoreB = PosizioneDebitoriaUtils.creaDebitore(debitoreFullRepository, pdDebitoreFullRepository, documento.getId(), 1, "VRDLGI85B41F205Z", "Luigia Verdi");
		debitoreB.setDataRimozione(OffsetDateTime.now());
		pdDebitoreFullRepository.save(debitoreB);

		OpzionePagamentoFullEntity opzioneA = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, debitoreA.getId(),
				TipologiaOpzionePagamento.SOLUZIONE_UNICA, StatoOpzionePagamento.DISPONIBILE);
		OpzionePagamentoFullEntity opzioneB = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, debitoreB.getId(),
				TipologiaOpzionePagamento.SOLUZIONE_UNICA, StatoOpzionePagamento.ANNULLATA);
		PosizioneDebitoriaUtils.creaRata(versamentoFullRepository, applicazioneRepository, dominioRepository,
				opzioneA.getId(), "DOC-010-A", 1, 10.00, StatoVersamento.NON_ESEGUITO);
		PosizioneDebitoriaUtils.creaRata(versamentoFullRepository, applicazioneRepository, dominioRepository,
				opzioneB.getId(), "DOC-010-B", 1, 20.00, StatoVersamento.ANNULLATO);

		Mockito.lenient().when(gpdApiV3.updatePositionWithHttpInfo(any(), any(), any(), any(), any()))
				.thenAnswer((Answer<ResponseEntity<PaymentPositionModelV3>>) PaymentPositionModelV3Utils::creaResponseUpdateOk);

		JobExecution jobExecution = jobLauncherTestUtils.launchJob();
		assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

		ArgumentCaptor<PaymentPositionModelV3> modelCaptor = ArgumentCaptor.forClass(PaymentPositionModelV3.class);
		ArgumentCaptor<Boolean> toPublishCaptor = ArgumentCaptor.forClass(Boolean.class);
		verify(gpdApiV3, times(2)).updatePositionWithHttpInfo(any(), any(), modelCaptor.capture(), toPublishCaptor.capture(), any());

		// prima il ritiro (DRAFT) con il payload completo, poi la ripubblicazione
		assertEquals(List.of(false, true), toPublishCaptor.getAllValues());
		assertEquals(2, modelCaptor.getAllValues().get(0).getPaymentOption().size());
		List<PaymentOptionModelV3> opzioniRipubblicate = modelCaptor.getAllValues().get(1).getPaymentOption();
		assertEquals(1, opzioniRipubblicate.size());
		assertEquals("RSSMRA80A01H501U", opzioniRipubblicate.get(0).getDebtor().getFiscalCode());

		DocumentoFullEntity documentoAggiornato = documentoFullRepository.findById(documento.getId()).orElseThrow();
		assertTrue(documentoAggiornato.getDataUltimaComunicazioneAca().isAfter(comunicataIeri));
	}

	@Test
	@DisplayName("Debitore rimosso gia' comunicato a GPD: nessun ritiro, un normale aggiornamento")
	void UC_6_11_DebitoreRimossoGiaComunicato_SoloAggiornamento() throws Exception {
		OffsetDateTime comunicataIeri = OffsetDateTime.now().minusDays(1);
		DocumentoFullEntity documento = PosizioneDebitoriaUtils.creaDocumento(documentoFullRepository, applicazioneRepository, dominioRepository, "DOC-011", comunicataIeri);
		PdDebitoreFullEntity debitoreA = PosizioneDebitoriaUtils.creaDebitore(debitoreFullRepository, pdDebitoreFullRepository, documento.getId(), 0, "RSSMRA80A01H501U", "Mario Rossi");
		PdDebitoreFullEntity debitoreB = PosizioneDebitoriaUtils.creaDebitore(debitoreFullRepository, pdDebitoreFullRepository, documento.getId(), 1, "VRDLGI85B41F205Z", "Luigia Verdi");
		// rimosso e comunicato in un giro precedente: la rimozione non e' piu' da comunicare
		debitoreB.setDataRimozione(OffsetDateTime.now().minusDays(2));
		pdDebitoreFullRepository.save(debitoreB);

		OpzionePagamentoFullEntity opzioneA = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, debitoreA.getId(),
				TipologiaOpzionePagamento.SOLUZIONE_UNICA, StatoOpzionePagamento.DISPONIBILE);
		OpzionePagamentoFullEntity opzioneB = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, debitoreB.getId(),
				TipologiaOpzionePagamento.SOLUZIONE_UNICA, StatoOpzionePagamento.ANNULLATA);
		PosizioneDebitoriaUtils.creaRata(versamentoFullRepository, applicazioneRepository, dominioRepository,
				opzioneA.getId(), "DOC-011-A", 1, 10.00, StatoVersamento.NON_ESEGUITO);
		PosizioneDebitoriaUtils.creaRata(versamentoFullRepository, applicazioneRepository, dominioRepository,
				opzioneB.getId(), "DOC-011-B", 1, 20.00, StatoVersamento.ANNULLATO);

		Mockito.lenient().when(gpdApiV3.updatePositionWithHttpInfo(any(), any(), any(), any(), any()))
				.thenAnswer((Answer<ResponseEntity<PaymentPositionModelV3>>) PaymentPositionModelV3Utils::creaResponseUpdateOk);

		JobExecution jobExecution = jobLauncherTestUtils.launchJob();
		assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

		verify(gpdApiV3, times(1)).updatePositionWithHttpInfo(any(), any(), any(), org.mockito.ArgumentMatchers.eq(true), any());
		verify(gpdApiV3, never()).updatePositionWithHttpInfo(any(), any(), any(), org.mockito.ArgumentMatchers.eq(false), any());
	}

	@Test
	@DisplayName("Data di pubblicazione futura: la posizione non viene inviata finche' la data di ultima modifica non e' raggiunta")
	void UC_6_12_DataPubblicazioneFutura_NonInviata() throws Exception {
		DocumentoFullEntity documento = PosizioneDebitoriaUtils.creaDocumento(documentoFullRepository, applicazioneRepository, dominioRepository, "DOC-012", null);
		// la libreria posticipa la data di ultima modifica a quella di pubblicazione
		documento.setDataUltimaModificaAca(OffsetDateTime.now().plusDays(2));
		documentoFullRepository.save(documento);
		PdDebitoreFullEntity debitore = PosizioneDebitoriaUtils.creaDebitore(debitoreFullRepository, pdDebitoreFullRepository, documento.getId());
		OpzionePagamentoFullEntity opzione = PosizioneDebitoriaUtils.creaOpzione(opzionePagamentoFullRepository, debitore.getId(),
				TipologiaOpzionePagamento.SOLUZIONE_UNICA, StatoOpzionePagamento.DISPONIBILE);
		PosizioneDebitoriaUtils.creaRata(versamentoFullRepository, applicazioneRepository, dominioRepository,
				opzione.getId(), "DOC-012", 1, 10.00, StatoVersamento.NON_ESEGUITO);

		JobExecution jobExecution = jobLauncherTestUtils.launchJob();
		assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());

		verify(gpdApiService, never()).getGpdApiV3(any());
		DocumentoFullEntity documentoAggiornato = documentoFullRepository.findById(documento.getId()).orElseThrow();
		assertEquals(null, documentoAggiornato.getDataUltimaComunicazioneAca());
	}
}
