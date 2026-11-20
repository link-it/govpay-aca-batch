package it.govpay.gpd.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.govpay.gpd.client.config.RestTemplateConfig;
import it.govpay.gpd.client.v3.beans.PaymentOptionModelV3;
import it.govpay.gpd.entity.OpzionePagamentoGpdEntity;
import it.govpay.gpd.entity.PendenzaGpdEntity;
import it.govpay.gpd.entity.PosizioneDebitoriaGpdEntity;
import it.govpay.gpd.repository.SingoloVersamentoGpdRepository;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Date e descrizioni di opzione nel payload GPD v3")
class PosizioneDebitoriaGpdMapperDateTest {

	private PosizioneDebitoriaGpdMapperImpl mapper;

	@BeforeEach
	void setUp() {
		this.mapper = new PosizioneDebitoriaGpdMapperImpl();
		SingoloVersamentoGpdRepository singoli = mock(SingoloVersamentoGpdRepository.class);
		when(singoli.findAll(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(List.of());
		this.mapper.setSingoloVersamentoGpdRepository(singoli);
	}

	private static PosizioneDebitoriaGpdEntity posizione(LocalDateTime dataPubblicazione) {
		PosizioneDebitoriaGpdEntity posizione = new PosizioneDebitoriaGpdEntity();
		posizione.setId(1L);
		posizione.setDescrizione("Posizione di prova");
		posizione.setDataPubblicazione(dataPubblicazione);
		return posizione;
	}

	private static OpzionePagamentoGpdEntity opzione(LocalDateTime inizioValidita, LocalDateTime scadenza) {
		OpzionePagamentoGpdEntity opzione = new OpzionePagamentoGpdEntity();
		opzione.setId(1L);
		opzione.setDataInizioValidita(inizioValidita);
		opzione.setDataScadenza(scadenza);
		return opzione;
	}

	private static PendenzaGpdEntity rata(LocalDateTime scadenza) {
		PendenzaGpdEntity rata = new PendenzaGpdEntity();
		rata.setId(1L);
		rata.setImportoTotale(1.0);
		rata.setDataScadenza(scadenza);
		return rata;
	}

	private PaymentOptionModelV3 mappa(PosizioneDebitoriaGpdEntity posizione, OpzionePagamentoGpdEntity opzione, PendenzaGpdEntity... rate) throws Exception {
		return this.mapper.opzionePagamentoGpdToPaymentOptionModelV3(opzione, posizione, List.of(rate));
	}

	@Test
	@DisplayName("validityDate passata: non viene inviata")
	void validityDatePassataOmessa() throws Exception {
		PaymentOptionModelV3 opzione = mappa(posizione(LocalDateTime.now().minusDays(1)), opzione(null, null),
				rata(LocalDateTime.now().plusDays(30)));
		assertNull(opzione.getValidityDate());
	}

	@Test
	@DisplayName("validityDate futura: viene inviata")
	void validityDateFuturaInviata() throws Exception {
		LocalDateTime futura = LocalDateTime.now().plusDays(10).withNano(0);
		PaymentOptionModelV3 opzione = mappa(posizione(LocalDateTime.now().minusDays(1)), opzione(futura, null),
				rata(LocalDateTime.now().plusDays(30)));
		assertNotNull(opzione.getValidityDate());
		assertEquals(futura, opzione.getValidityDate().toLocalDateTime());
	}

	@Test
	@DisplayName("retentionDate: la piu' tarda scadenza esplicita delle rate")
	void retentionDatePiuTardaScadenza() throws Exception {
		LocalDateTime prima = LocalDateTime.now().plusDays(30).withNano(0);
		LocalDateTime seconda = LocalDateTime.now().plusDays(60).withNano(0);
		PaymentOptionModelV3 opzione = mappa(posizione(null), opzione(null, null), rata(prima), rata(seconda));
		assertEquals(seconda.toLocalDate(), opzione.getRetentionDate().toLocalDate());
		assertTrue(!opzione.getRetentionDate().toLocalDateTime().isBefore(opzione.getInstallments().get(1).getDueDate().toLocalDateTime()));
	}

	@Test
	@DisplayName("retentionDate: non inviata se nessuna rata ha una scadenza esplicita")
	void retentionDateOmessaSenzaScadenza() throws Exception {
		PaymentOptionModelV3 opzione = mappa(posizione(null), opzione(null, null), rata(null));
		assertNull(opzione.getRetentionDate());
	}

	@Test
	@DisplayName("le date escono senza offset nel JSON inviato")
	void dateSenzaOffset() throws Exception {
		PaymentOptionModelV3 opzione = mappa(posizione(null), opzione(LocalDateTime.now().plusDays(10), LocalDateTime.now().plusDays(30)),
				rata(null));
		JsonMapper jsonMapper = RestTemplateConfig.createObjectMapper();
		String json = jsonMapper.writeValueAsString(opzione);
		assertFalse(json.matches("(?s).*\\d{2}:\\d{2}:\\d{2}\\.\\d{3}(\\+|-|Z).*"), json);
		assertTrue(json.matches("(?s).*\"validityDate\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\".*"), json);
	}

	@Test
	@DisplayName("descrizioni oltre 140 caratteri: troncate, il suffisso della rata e' mantenuto")
	void descrizioniTroncate() throws Exception {
		PosizioneDebitoriaGpdEntity posizione = posizione(null);
		posizione.setDescrizione("x".repeat(200));
		OpzionePagamentoGpdEntity opzione = opzione(null, null);
		opzione.setTipologia(it.govpay.pendenze.model.TipologiaOpzionePagamento.PIANO_RATEALE);
		PendenzaGpdEntity rata = rata(null);
		rata.setNumeroRata(12);
		PaymentOptionModelV3 modello = mappa(posizione, opzione, rata);
		assertEquals(140, modello.getDescription().length());
		assertEquals(140, modello.getInstallments().get(0).getDescription().length());
		assertTrue(modello.getInstallments().get(0).getDescription().endsWith(" - rata 12"));
	}

	private List<PaymentOptionModelV3> mappaPosizione(boolean soloDisponibiliEValide, List<PendenzaGpdEntity> rate) throws Exception {
		it.govpay.gpd.repository.OpzionePagamentoGpdRepository opzioni = mock(it.govpay.gpd.repository.OpzionePagamentoGpdRepository.class);
		it.govpay.gpd.repository.PendenzaGpdRepository pendenze = mock(it.govpay.gpd.repository.PendenzaGpdRepository.class);
		OpzionePagamentoGpdEntity opzione = opzione(null, null);
		when(opzioni.findByIdDocumentoAndStato(any(), any())).thenReturn(List.of(opzione));
		when(opzioni.findByIdDocumento(any())).thenReturn(List.of(opzione));
		when(pendenze.findByIdOpzionePagamentoOrderByNumeroRataAsc(any())).thenReturn(rate);
		this.mapper.setOpzionePagamentoGpdRepository(opzioni);
		this.mapper.setPendenzaGpdRepository(pendenze);
		return this.mapper.mapPaymentOption(posizione(null), soloDisponibiliEValide);
	}

	@Test
	@DisplayName("piano con una rata scaduta e una futura: la rata scaduta non viene inviata, in aggiornamento e in ritiro")
	void rataScadutaNonInviata() throws Exception {
		PendenzaGpdEntity scaduta = rata(LocalDateTime.now().minusDays(2));
		PendenzaGpdEntity futura = rata(LocalDateTime.now().plusDays(30));
		for (boolean aggiornamento : new boolean[] { true, false }) {
			List<PaymentOptionModelV3> opzioni = mappaPosizione(aggiornamento, List.of(scaduta, futura));
			assertEquals(1, opzioni.size());
			assertEquals(1, opzioni.get(0).getInstallments().size());
			assertTrue(opzioni.get(0).getInstallments().get(0).getDueDate().toLocalDateTime().isAfter(LocalDateTime.now()));
		}
	}

	@Test
	@DisplayName("opzione con tutte le rate scadute: non viene inviata, in aggiornamento e in ritiro")
	void opzioneScadutaNonInviata() throws Exception {
		for (boolean aggiornamento : new boolean[] { true, false }) {
			assertTrue(mappaPosizione(aggiornamento, List.of(rata(LocalDateTime.now().minusDays(2)))).isEmpty());
		}
	}
}
