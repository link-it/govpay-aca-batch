package it.govpay.gpd.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import it.govpay.gpd.Application;
import it.govpay.gpd.client.v3.api.DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi;
import it.govpay.gpd.client.v3.beans.InstallmentModel;
import it.govpay.gpd.client.v3.beans.PaymentOptionModelV3;
import it.govpay.gpd.client.v3.beans.PaymentPositionModelResponseV3;
import it.govpay.gpd.client.v3.beans.PaymentPositionModelV3;
import it.govpay.gpd.service.GpdApiService;
import it.govpay.gpd.test.config.LibreriaPendenzeTestConfig;
import it.govpay.gpd.test.utils.PaymentPositionModelV3Utils;
import it.govpay.pendenze.comando.AggiornamentoPendenza;
import it.govpay.pendenze.comando.DatiDebitore;
import it.govpay.pendenze.comando.DatiOpzionePagamento;
import it.govpay.pendenze.comando.DatiPdDebitore;
import it.govpay.pendenze.comando.DatiPendenza;
import it.govpay.pendenze.comando.DatiPosizioneDebitoria;
import it.govpay.pendenze.comando.DatiVocePendenza;
import it.govpay.pendenze.entity.OpzionePagamento;
import it.govpay.pendenze.entity.PosizioneDebitoria;
import it.govpay.pendenze.model.StatoPendenza;
import it.govpay.pendenze.model.TipoSoggetto;
import it.govpay.pendenze.model.TipologiaOpzionePagamento;
import it.govpay.pendenze.service.PosizioneDebitoriaService;

/**
 * Le posizioni debitorie create e modificate con il servizio di govpay-common-pendenze
 * sono selezionate dal batch e inviate a GPD, sullo schema usato dalla libreria.
 */
@SpringBootTest(classes = Application.class)
@EnableAutoConfiguration
@Import(LibreriaPendenzeTestConfig.class)
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:h2:mem:govpaylibreria;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false;MODE=PostgreSQL",
		"spring.sql.init.schema-locations=classpath:schema-libreria.sql,classpath:schema-libreria-batch.sql,classpath:create-views.sql",
		"spring.sql.init.data-locations=classpath:data-libreria.sql" })
@DisplayName("Test Invio GPD - Dalla libreria al batch")
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
@SpringBatchTest
class UC_8_DaLibreriaTest {

	private static final String ID_A2A = "IDA2A01";
	private static final long ID_APPLICAZIONE = 1L;
	private static final long ID_DOMINIO = 1L;
	private static final long ID_UO_UFFICIO = 100L;
	private static final long ID_IBAN = 1L;

	private static final String CF_ROSSI = "RSSMRA80A01H501U";
	private static final String CF_VERDI = "VRDLGI85B41F205Z";

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
	private PosizioneDebitoriaService posizioneDebitoriaService;

	@Autowired
	private LibreriaPendenzeTestConfig.OrologioDiProva orologio;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi gpdApiV3;

	@BeforeEach
	void setUp() {
		gpdApiV3 = Mockito.mock(DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi.class);
		Mockito.lenient().when(gpdApiService.getGpdApiV3(any())).thenReturn(gpdApiV3);
		Mockito.lenient().when(gpdApiService.getGpdActionsApiV3(any()))
				.thenReturn(Mockito.mock(it.govpay.gpd.client.v3.api.DebtPositionActionsApiApi.class));
		Mockito.lenient().when(gpdApiService.getGpdBasePathV3(any())).thenReturn("http://fakehost:8080/");
		Mockito.lenient().when(gpdApiV3.createPositionWithHttpInfo(any(), any(), any(), any()))
				.thenAnswer(PaymentPositionModelV3Utils::creaResponseCreateOk);
		Mockito.lenient().when(gpdApiV3.updatePositionWithHttpInfo(any(), any(), any(), any(), any()))
				.thenAnswer(PaymentPositionModelV3Utils::creaResponseUpdateOk);
		Mockito.lenient().when(gpdApiV3.getOrganizationDebtPositionByIUPDWithHttpInfo(any(), any(), any()))
				.thenAnswer(invocation -> PaymentPositionModelV3Utils.creaResponseGetPositionOk(invocation,
						PaymentPositionModelResponseV3.StatusEnum.VALID));
		jobLauncherTestUtils.setJob(job);
	}

	@AfterEach
	void tearDown() {
		jobRepositoryTestUtils.removeJobExecutions();
	}

	@Test
	@DisplayName("Posizione appena creata: selezionata e inviata con i dati scritti dalla libreria")
	void UC_8_01_Creazione() throws Exception {
		crea(null, debitore(CF_ROSSI, soluzioneUnica(pendenza(1, "Causale di prova"))));

		eseguiJob();

		PaymentPositionModelV3 payload = payloadCreazione();
		assertEquals("Ufficio Tributi", payload.getOfficeName());
		PaymentOptionModelV3 opzione = payload.getPaymentOption().get(0);
		assertEquals(CF_ROSSI, opzione.getDebtor().getFiscalCode());
		InstallmentModel rata = opzione.getInstallments().get(0);
		assertEquals(nav(1), rata.getNav());
		assertEquals("Causale di prova", rata.getDescription());
		assertEquals("9/DOVUTO", rata.getTransfer().get(0).getCategory());
		assertComunicata();
		assertNessunNuovoInvio();
	}

	@Test
	@DisplayName("Due debitori: una opzione per debitore, ciascuna con il proprio debitore")
	void UC_8_02_DueDebitori() throws Exception {
		crea(null, debitore(CF_ROSSI, soluzioneUnica(pendenza(1, "Quota Rossi"))),
				debitore(CF_VERDI, soluzioneUnica(pendenza(2, "Quota Verdi"))));

		eseguiJob();

		List<PaymentOptionModelV3> opzioni = payloadCreazione().getPaymentOption();
		assertEquals(List.of(CF_ROSSI, CF_VERDI),
				opzioni.stream().map(o -> o.getDebtor().getFiscalCode()).toList());
		assertEquals(List.of(nav(1), nav(2)),
				opzioni.stream().map(o -> o.getInstallments().get(0).getNav()).toList());
		assertComunicata();
		assertNessunNuovoInvio();
	}

	@Test
	@DisplayName("Piano rateale: una sola opzione con le rate in ordine")
	void UC_8_03_PianoRateale() throws Exception {
		crea(null, debitore(CF_ROSSI, opzione(TipologiaOpzionePagamento.PIANO_RATEALE, null,
				pendenza(1, "Prima rata"), pendenza(2, "Seconda rata"))));

		eseguiJob();

		List<PaymentOptionModelV3> opzioni = payloadCreazione().getPaymentOption();
		assertEquals(1, opzioni.size());
		assertEquals(List.of(nav(1), nav(2)),
				opzioni.get(0).getInstallments().stream().map(InstallmentModel::getNav).toList());
		assertComunicata();
	}

	@Test
	@DisplayName("Opzioni ENTRO e OLTRE con scadenza propria: una opzione per soglia, con la sua scadenza e switchToExpired")
	void UC_8_04_EntroOltre() throws Exception {
		LocalDate scadenzaEntro = LocalDate.now(orologio).plusDays(10);
		LocalDate scadenzaOltre = LocalDate.now(orologio).plusDays(40);
		crea(null, debitore(CF_ROSSI,
				opzioneConSoglia(TipologiaOpzionePagamento.SOLUZIONE_UNICA_ENTRO, scadenzaEntro,
						pendenza(1, "Entro cinque giorni")),
				opzioneConSoglia(TipologiaOpzionePagamento.SOLUZIONE_UNICA_OLTRE, scadenzaOltre,
						pendenza(2, "Oltre cinque giorni"))));

		eseguiJob();

		List<PaymentOptionModelV3> opzioni = payloadCreazione().getPaymentOption();
		assertEquals(List.of(nav(1), nav(2)),
				opzioni.stream().map(o -> o.getInstallments().get(0).getNav()).toList());
		assertEquals(List.of(scadenzaEntro, scadenzaOltre),
				opzioni.stream().map(o -> o.getInstallments().get(0).getDueDate().toLocalDate()).toList());
		assertTrue(opzioni.stream().allMatch(o -> Boolean.TRUE.equals(o.isSwitchToExpired())));
		assertComunicata();
		assertNessunNuovoInvio();
	}

	@Test
	@DisplayName("Modifica di una pendenza: la posizione e' reinviata in aggiornamento con i nuovi dati")
	void UC_8_05_ModificaPendenza() throws Exception {
		crea(null, debitore(CF_ROSSI, soluzioneUnica(pendenza(1, "Causale iniziale"))));
		eseguiJobEAzzeraInvocazioni();

		DatiPendenza modificata = pendenza(1, "Causale modificata");
		posizioneDebitoriaService.aggiornaPendenza(ID_A2A, modificata.idPendenza(),
				new AggiornamentoPendenza(modificata.idPendenza(), modificata.idTipoPendenza(),
						modificata.idTipoVersamento(), null, modificata.importo(), modificata.numeroAvviso(), null,
						null, modificata.voci(), modificata.causale()));
		eseguiJob();

		PaymentPositionModelV3 payload = payloadAggiornamento(true);
		assertEquals("Causale modificata",
				payload.getPaymentOption().get(0).getInstallments().get(0).getDescription());
		assertComunicata();
		assertNessunNuovoInvio();
	}

	@Test
	@DisplayName("Attivazione di un'opzione: la posizione e' marcata come comunicata senza invii")
	void UC_8_06_Attivazione() throws Exception {
		crea(null, debitore(CF_ROSSI, soluzioneUnica(pendenza(1, "Prima alternativa")),
				soluzioneUnica(pendenza(2, "Seconda alternativa"))));
		eseguiJobEAzzeraInvocazioni();

		posizioneDebitoriaService.registraEsitoPendenza(ID_A2A, "PEND-1", StatoPendenza.ESEGUITO);
		assertDaComunicare();
		eseguiJob();

		verifyNoInteractions(gpdApiV3);
		assertComunicata();
	}

	@Test
	@DisplayName("Annullamento e ripristino: opzioni disponibili pubblicate, ritiro quando non ne restano")
	void UC_8_07_AnnullamentoERipristino() throws Exception {
		PosizioneDebitoria posizione = crea(null, debitore(CF_ROSSI,
				soluzioneUnica(pendenza(1, "Prima alternativa")),
				soluzioneUnica(pendenza(2, "Seconda alternativa"))));
		String idPosizione = posizioneDebitoriaService.idPosizioneDebitoriaPubblica(posizione);
		OpzionePagamento prima = posizione.getOpzioniPagamento().get(0);
		OpzionePagamento seconda = posizione.getOpzioniPagamento().get(1);
		eseguiJobEAzzeraInvocazioni();

		posizioneDebitoriaService.annulla(ID_A2A, idPosizione, prima.getIdOpzionePagamento());
		eseguiJob();
		assertEquals(List.of(nav(2)), nav(payloadAggiornamento(true)));
		assertComunicata();
		clearInvocations(gpdApiV3);

		posizioneDebitoriaService.annulla(ID_A2A, idPosizione, seconda.getIdOpzionePagamento());
		eseguiJob();
		assertEquals(List.of(nav(1), nav(2)), nav(payloadAggiornamento(false)));
		assertComunicata();
		clearInvocations(gpdApiV3);

		posizioneDebitoriaService.ripristina(ID_A2A, idPosizione, seconda.getIdOpzionePagamento());
		eseguiJob();
		assertEquals(List.of(nav(2)), nav(payloadAggiornamento(true)));
		assertComunicata();
		assertNessunNuovoInvio();
	}

	@Test
	@DisplayName("Rimozione di un debitore: ritiro con tutte le opzioni e ripubblicazione senza quelle rimosse")
	void UC_8_08_RimozioneDebitore() throws Exception {
		PosizioneDebitoria posizione = crea(null,
				debitore(CF_ROSSI, soluzioneUnica(pendenza(1, "Quota Rossi"))),
				debitore(CF_VERDI, soluzioneUnica(pendenza(2, "Quota Verdi"))));
		eseguiJobEAzzeraInvocazioni();

		posizioneDebitoriaService.rimuoviDebitore(ID_A2A,
				posizioneDebitoriaService.idPosizioneDebitoriaPubblica(posizione), CF_VERDI);
		eseguiJob();

		ArgumentCaptor<PaymentPositionModelV3> payload = ArgumentCaptor.forClass(PaymentPositionModelV3.class);
		ArgumentCaptor<Boolean> toPublish = ArgumentCaptor.forClass(Boolean.class);
		verify(gpdApiV3, times(2)).updatePositionWithHttpInfo(any(), any(), payload.capture(), toPublish.capture(),
				any());
		assertEquals(List.of(false, true), toPublish.getAllValues());
		assertEquals(List.of(nav(1), nav(2)), nav(payload.getAllValues().get(0)));
		assertEquals(List.of(nav(1)), nav(payload.getAllValues().get(1)));
		assertComunicata();
		assertNessunNuovoInvio();
	}

	@Test
	@DisplayName("Data di pubblicazione futura: nessun invio e posizione ancora da comunicare")
	void UC_8_09_PubblicazioneFutura() throws Exception {
		crea(LocalDate.now(orologio).plusDays(2), debitore(CF_ROSSI, soluzioneUnica(pendenza(1, "Futura"))));

		eseguiJob();

		verifyNoInteractions(gpdApiV3);
		assertNull(valore("SELECT data_ultima_comunicazione_aca FROM documenti"));
	}

	@Test
	@DisplayName("Data di pubblicazione raggiunta: la posizione creata in anticipo viene inviata")
	void UC_8_10_PubblicazioneRaggiunta() throws Exception {
		LocalDate pubblicazione = LocalDate.now(ZoneId.of("Europe/Rome")).minusDays(1);
		orologio.sposta(Duration.ofDays(-3));
		crea(pubblicazione, debitore(CF_ROSSI, soluzioneUnica(pendenza(1, "Pubblicata"))));
		orologio.sposta(Duration.ZERO);
		assertEquals(Timestamp.valueOf(pubblicazione.atStartOfDay()),
				valore("SELECT data_ultima_modifica_aca FROM documenti"));

		eseguiJob();

		assertEquals(List.of(nav(1)), nav(payloadCreazione()));
		assertComunicata();
		assertNessunNuovoInvio();
	}

	// ── Dati di ingresso del servizio ───────────────────────────────────────

	private static String nav(int numero) {
		return String.format("3%017d", numero);
	}

	private static DatiPendenza pendenza(int numero, String causale) {
		DatiVocePendenza voce = new DatiVocePendenza("VOCE-" + numero, 10.00, "Voce di prova", null, null, ID_IBAN,
				null, "9/DOVUTO", null, null, null, List.of());
		return new DatiPendenza("PEND-" + numero, 1L, 1L, null, 10.00, nav(numero), String.format("%017d", numero),
				null, null, List.of(voce), causale);
	}

	private static DatiOpzionePagamento opzione(TipologiaOpzionePagamento tipologia, Integer giorni,
			DatiPendenza... pendenze) {
		return new DatiOpzionePagamento(tipologia, giorni, null, null, false, null, List.of(pendenze));
	}

	private static DatiOpzionePagamento opzioneConSoglia(TipologiaOpzionePagamento tipologia, LocalDate dataScadenza,
			DatiPendenza pendenza) {
		return new DatiOpzionePagamento(tipologia, 5, null, dataScadenza, false, null, List.of(pendenza));
	}

	private static DatiOpzionePagamento soluzioneUnica(DatiPendenza pendenza) {
		return opzione(TipologiaOpzionePagamento.SOLUZIONE_UNICA, null, pendenza);
	}

	private static DatiPdDebitore debitore(String codiceFiscale, DatiOpzionePagamento... opzioni) {
		DatiDebitore debitore = new DatiDebitore(TipoSoggetto.F, codiceFiscale, "Debitore " + codiceFiscale, null,
				null, null, null, null, null, null, null);
		return new DatiPdDebitore(debitore, List.of(opzioni));
	}

	private PosizioneDebitoria crea(LocalDate dataPubblicazione, DatiPdDebitore... debitori) {
		return posizioneDebitoriaService.crea(new DatiPosizioneDebitoria("DOC-1", ID_APPLICAZIONE, ID_DOMINIO,
				ID_UO_UFFICIO, "Pagamento di prova", dataPubblicazione, List.of(debitori)));
	}

	// ── Esecuzione e controlli ──────────────────────────────────────────────

	private void eseguiJob() throws Exception {
		JobExecution jobExecution = jobLauncherTestUtils.launchJob();
		assertEquals("COMPLETED", jobExecution.getExitStatus().getExitCode());
	}

	private void eseguiJobEAzzeraInvocazioni() throws Exception {
		eseguiJob();
		assertComunicata();
		clearInvocations(gpdApiV3);
	}

	private void assertNessunNuovoInvio() throws Exception {
		clearInvocations(gpdApiV3);
		eseguiJob();
		verifyNoInteractions(gpdApiV3);
	}

	private PaymentPositionModelV3 payloadCreazione() {
		ArgumentCaptor<PaymentPositionModelV3> payload = ArgumentCaptor.forClass(PaymentPositionModelV3.class);
		verify(gpdApiV3).createPositionWithHttpInfo(any(), payload.capture(), any(), any());
		verify(gpdApiV3, Mockito.never()).updatePositionWithHttpInfo(any(), any(), any(), anyBoolean(), any());
		return payload.getValue();
	}

	private PaymentPositionModelV3 payloadAggiornamento(boolean toPublish) {
		ArgumentCaptor<PaymentPositionModelV3> payload = ArgumentCaptor.forClass(PaymentPositionModelV3.class);
		verify(gpdApiV3).updatePositionWithHttpInfo(any(), any(), payload.capture(), eq(toPublish), any());
		verify(gpdApiV3, Mockito.never()).createPositionWithHttpInfo(any(), any(), any(), any());
		return payload.getValue();
	}

	private static List<String> nav(PaymentPositionModelV3 payload) {
		return payload.getPaymentOption().stream()
				.flatMap(o -> o.getInstallments().stream())
				.map(InstallmentModel::getNav)
				.toList();
	}

	private Object valore(String query) {
		return jdbcTemplate.queryForObject(query, (rs, riga) -> rs.getTimestamp(1));
	}

	private void assertDaComunicare() {
		assertEquals(1, jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM documenti WHERE data_ultima_comunicazione_aca IS NULL"
						+ " OR data_ultima_modifica_aca > data_ultima_comunicazione_aca",
				Integer.class));
	}

	/** La posizione e tutte le sue rate portano come data di comunicazione l'ultima modifica della posizione. */
	private void assertComunicata() {
		assertEquals(1, jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM documenti WHERE data_ultima_comunicazione_aca = data_ultima_modifica_aca",
				Integer.class));
		assertEquals(0, jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM versamenti v, documenti d WHERE v.data_ultima_comunicazione_aca IS NULL"
						+ " OR v.data_ultima_comunicazione_aca <> d.data_ultima_modifica_aca",
				Integer.class));
	}
}
