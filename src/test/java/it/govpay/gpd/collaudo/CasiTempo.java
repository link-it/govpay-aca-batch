package it.govpay.gpd.collaudo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import it.govpay.gpd.collaudo.ClientGpd.Risposta;
import it.govpay.gpd.collaudo.ContestoCaso.OpzioneProva;
import tools.jackson.databind.node.ObjectNode;

/**
 * Casi che dipendono dal passare del tempo: creano posizioni con date a pochi secondi di distanza (in UTC, il
 * fuso con cui GPD sembra confrontare le date), attendono e osservano cosa cambia. Non fanno parte dell'esecuzione
 * predefinita.
 */
public final class CasiTempo {

	public static final String GRUPPO = "tempo";

	private static final String DEBITORE_1 = "RSSMRA80A01H501U";
	private static final String NOME_1 = "Mario Rossi";
	private static final String DEBITORE_2 = "VRDLGU85M01H501Q";
	private static final String NOME_2 = "Luigi Verdi";
	private static final int INTERVALLO_SORVEGLIANZA_SECONDI = 15;

	private CasiTempo() {
	}

	public static List<Caso> casi() {
		return List.of(
				Caso.di("TEMPO_01_VALIDITY_A_DATA", GRUPPO,
						"Opzioni con validityDate tra pochi secondi: quando e come cambia lo stato", CasiTempo::validitaADataPrevista),
				Caso.di("TEMPO_03_RITIRO_DOPO_SCADENZA", GRUPPO,
						"Posizioni VALID con opzioni scadute: PUT toPublish=false (ritiro) con e senza le opzioni scadute, poi ripubblicazione",
						CasiTempo::ritiroDopoScadenza),
				Caso.di("TEMPO_04_RATA_SCADUTA_IN_PIANO", GRUPPO,
						"Posizione VALID con un piano di due rate di cui la prima scade: PUT con tutte le rate e con la sola rata futura",
						CasiTempo::rataScadutaInPiano),
				Caso.di("TEMPO_02_SCADENZA_A_DATA", GRUPPO,
						"Rate con dueDate tra pochi secondi: stato alla scadenza e PUT dopo la scadenza", CasiTempo::scadenzaADataPrevista));
	}

	private record Sorvegliata(String nome, Posizione posizione, Predicate<Risposta> finale, String descrizioneFinale) {
	}

	/**
	 * Legge a intervalli le posizioni indicate, da poco prima dell'evento fino alla durata di osservazione lunga
	 * dopo l'evento, e registra per ciascuna se e quando la condizione finale si e' verificata.
	 */
	private static void sorveglia(ContestoCaso contesto, Instant evento, List<Sorvegliata> voci) {
		contesto.fase("verifica");
		contesto.attendiFino(evento.minusSeconds(10), "inizio della sorveglianza, evento previsto alle " + evento);
		Instant limite = evento.plusSeconds(contesto.configurazione().osservazioneLungaSecondi());
		Map<String, Long> raggiunte = new LinkedHashMap<>();
		Map<String, Risposta> ultime = new LinkedHashMap<>();
		List<Sorvegliata> inAttesa = new ArrayList<>(voci);
		while (!inAttesa.isEmpty() && Instant.now().isBefore(limite)) {
			for (Sorvegliata voce : new ArrayList<>(inAttesa)) {
				Risposta lettura = contesto.client().leggi(contesto.registrazione(), "sorveglianza di " + voce.nome(), voce.posizione().iupd());
				ultime.put(voce.nome(), lettura);
				if (voce.finale().test(lettura)) {
					raggiunte.put(voce.nome(), Instant.now().getEpochSecond() - evento.getEpochSecond());
					inAttesa.remove(voce);
				}
			}
			if (!inAttesa.isEmpty()) {
				contesto.attendiFino(Instant.now().plusSeconds(INTERVALLO_SORVEGLIANZA_SECONDI), "prossima lettura");
			}
		}
		for (Sorvegliata voce : voci) {
			Risposta ultima = ultime.get(voce.nome());
			String stato = ultima == null ? "mai letta" : "HTTP " + ultima.stato() + ", stato " + ultima.campo("status");
			if (raggiunte.containsKey(voce.nome())) {
				contesto.nota(voce.nome() + ": " + voce.descrizioneFinale() + " " + raggiunte.get(voce.nome())
						+ " s dopo l'evento previsto (negativo: prima); " + stato);
			} else {
				contesto.nota(voce.nome() + ": " + voce.descrizioneFinale() + " NON verificato entro "
						+ contesto.configurazione().osservazioneLungaSecondi() + " s dall'evento; ultimo: " + stato + ", "
						+ contesto.descriviOpzioni(ultima));
			}
		}
	}

	private static void validitaADataPrevista(ContestoCaso contesto) {
		int attesa = contesto.configurazione().scadenzaSecondi();
		Instant evento = Instant.now().plusSeconds(attesa);
		String validity = Payloads.dataUtcTra(attesa);

		String iupd1 = contesto.identificativi().iupd();
		OpzioneProva unica = contesto.opzione("opzione", iupd1, DEBITORE_1, NOME_1, validity);
		Posizione singola = contesto.prepara(contesto.posizioneConOpzioni("singola", iupd1, unica), "PUBLISHED");

		String iupd2 = contesto.identificativi().iupd();
		OpzioneProva subito = contesto.opzione("opzioneSubito", iupd2, DEBITORE_1, NOME_1, null);
		OpzioneProva differita = contesto.opzione("opzioneDifferita", iupd2, DEBITORE_2, NOME_2, validity);
		Posizione mista = contesto.creaOsservando(contesto.posizioneConOpzioni("mista", iupd2, subito, differita), true);

		sorveglia(contesto, evento, List.of(
				new Sorvegliata("singola", singola, r -> r.ok() && "VALID".equals(r.campo("status")), "passata a VALID"),
				new Sorvegliata("mista", mista, r -> r.ok() && "VALID".equals(r.campo("status"))
						&& Instant.now().isAfter(evento.plusSeconds(5)), "VALID dopo la validityDate della seconda opzione")));
		contesto.fase("verifica");
		CasiPut.leggiEDescrivi(contesto, "lettura finale della posizione singola", singola);
		CasiPut.leggiEDescrivi(contesto, "lettura finale della posizione mista", mista);
		contesto.osservato();
	}

	private static void scadenzaADataPrevista(ContestoCaso contesto) {
		int attesa = contesto.configurazione().scadenzaSecondi();
		Instant evento = Instant.now().plusSeconds(attesa);
		String scadenza = Payloads.dataUtcTra(attesa);

		String iupd1 = contesto.identificativi().iupd();
		OpzioneProva senzaSwitch = contesto.opzione("opzione", iupd1, DEBITORE_1, NOME_1, null, contesto.identificativi().iuv(), 100, scadenza);
		Posizione prima = contesto.prepara(contesto.posizioneConOpzioni("senzaSwitch", iupd1, senzaSwitch), "VALID");

		String iupd2 = contesto.identificativi().iupd();
		OpzioneProva conSwitch = contesto.opzione("opzione", iupd2, DEBITORE_1, NOME_1, null, contesto.identificativi().iuv(), 100, scadenza);
		conSwitch.corpo().put("switchToExpired", true);
		Posizione seconda = contesto.prepara(contesto.posizioneConOpzioni("conSwitch", iupd2, conSwitch), "VALID");

		String iupd3 = contesto.identificativi().iupd();
		OpzioneProva scade = contesto.opzione("opzioneCheScade", iupd3, DEBITORE_1, NOME_1, null, contesto.identificativi().iuv(), 100, scadenza);
		OpzioneProva resta = contesto.opzione("opzioneCheResta", iupd3, DEBITORE_2, NOME_2, null);
		Posizione terza = contesto.prepara(contesto.posizioneConOpzioni("unaScade", iupd3, scade, resta), "VALID");

		Predicate<Risposta> statoCambiato = r -> r.ok() && r.campo("status") != null && !"VALID".equals(r.campo("status"));
		sorveglia(contesto, evento, List.of(
				new Sorvegliata("senzaSwitch", prima, statoCambiato, "stato diverso da VALID"),
				new Sorvegliata("conSwitch", seconda, statoCambiato, "stato diverso da VALID"),
				new Sorvegliata("unaScade", terza, statoCambiato, "stato diverso da VALID")));

		contesto.fase("prova");
		for (Posizione posizione : List.of(prima, seconda)) {
			CasiPut.put(contesto, "PUT identico dopo la scadenza (" + posizione.nome() + ")", posizione, posizione.corpo(), true);
			CasiPut.leggiEDescrivi(contesto, "lettura dopo il PUT identico (" + posizione.nome() + ")", posizione);
			ObjectNode rinnovata = posizione.corpo().deepCopy();
			CorpiProva.rata(rinnovata).put("dueDate", Payloads.data(60));
			CasiPut.put(contesto, "PUT con nuova dueDate (" + posizione.nome() + ")", posizione, rinnovata, true);
			CasiPut.leggiEDescrivi(contesto, "lettura dopo il PUT con nuova dueDate (" + posizione.nome() + ")", posizione);
		}
		CasiPut.put(contesto, "PUT identico con un'opzione scaduta (unaScade)", terza, terza.corpo(), true);
		CasiPut.leggiEDescrivi(contesto, "lettura dopo il PUT identico (unaScade)", terza);
		CasiPut.put(contesto, "PUT che omette l'opzione scaduta (unaScade)", terza, contesto.corpoPosizione(iupd3, resta), true);
		CasiPut.leggiEDescrivi(contesto, "lettura dopo il PUT senza l'opzione scaduta (unaScade)", terza);
		contesto.osservato();
	}

	private static void ritiroDopoScadenza(ContestoCaso contesto) {
		int attesa = contesto.configurazione().scadenzaSecondi();
		Instant evento = Instant.now().plusSeconds(attesa);
		String scadenza = Payloads.dataUtcTra(attesa);

		String iupd1 = contesto.identificativi().iupd();
		OpzioneProva conSwitch = contesto.opzione("opzione", iupd1, DEBITORE_1, NOME_1, null, contesto.identificativi().iuv(), 100, scadenza);
		conSwitch.corpo().put("switchToExpired", true);
		Posizione singolaConSwitch = contesto.prepara(contesto.posizioneConOpzioni("singolaConSwitch", iupd1, conSwitch), "VALID");

		String iupd2 = contesto.identificativi().iupd();
		OpzioneProva senzaSwitch = contesto.opzione("opzione", iupd2, DEBITORE_1, NOME_1, null, contesto.identificativi().iuv(), 100, scadenza);
		Posizione singolaSenzaSwitch = contesto.prepara(contesto.posizioneConOpzioni("singolaSenzaSwitch", iupd2, senzaSwitch), "VALID");

		String iupd3 = contesto.identificativi().iupd();
		OpzioneProva scade = contesto.opzione("opzioneCheScade", iupd3, DEBITORE_1, NOME_1, null, contesto.identificativi().iuv(), 100, scadenza);
		scade.corpo().put("switchToExpired", true);
		OpzioneProva resta = contesto.opzione("opzioneCheResta", iupd3, DEBITORE_2, NOME_2, null);
		resta.corpo().put("switchToExpired", true);
		Posizione mista = contesto.prepara(contesto.posizioneConOpzioni("mista", iupd3, scade, resta), "VALID");

		contesto.attendiFino(evento.plusSeconds(10), "scadenza delle opzioni di prova");

		contesto.fase("prova");
		CasiPut.put(contesto, "ritiro con l'opzione scaduta, switchToExpired=true (singolaConSwitch)", singolaConSwitch, singolaConSwitch.corpo(), false);
		CasiPut.leggiEDescrivi(contesto, "lettura dopo il ritiro (singolaConSwitch)", singolaConSwitch);
		CasiPut.put(contesto, "ritiro con l'opzione scaduta, switchToExpired=false (singolaSenzaSwitch)", singolaSenzaSwitch, singolaSenzaSwitch.corpo(), false);
		CasiPut.leggiEDescrivi(contesto, "lettura dopo il ritiro (singolaSenzaSwitch)", singolaSenzaSwitch);
		CasiPut.put(contesto, "ritiro con tutte le opzioni, una scaduta (mista)", mista, mista.corpo(), false);
		CasiPut.leggiEDescrivi(contesto, "lettura dopo il ritiro con tutte le opzioni (mista)", mista);
		CasiPut.put(contesto, "ritiro con la sola opzione non scaduta (mista)", mista, contesto.corpoPosizione(iupd3, resta), false);
		CasiPut.leggiEDescrivi(contesto, "lettura dopo il ritiro con la sola opzione non scaduta (mista)", mista);
		CasiPut.put(contesto, "ripubblicazione con la sola opzione non scaduta (mista)", mista, contesto.corpoPosizione(iupd3, resta), true);
		CasiPut.leggiEDescrivi(contesto, "lettura dopo la ripubblicazione (mista)", mista);
		contesto.osservato();
	}

	private static void rataScadutaInPiano(ContestoCaso contesto) {
		int attesa = contesto.configurazione().scadenzaSecondi();
		Instant evento = Instant.now().plusSeconds(attesa);

		String iupd = contesto.identificativi().iupd();
		OpzioneProva piano = contesto.opzioneRateale("piano", iupd, DEBITORE_1, NOME_1, null, 2);
		piano.corpo().put("switchToExpired", true);
		((ObjectNode) piano.corpo().path("installments").get(0)).put("dueDate", Payloads.dataUtcTra(attesa));
		Posizione posizione = contesto.prepara(contesto.posizioneConOpzioni("posizione", iupd, piano), "VALID");

		ObjectNode soloFutura = piano.corpo().deepCopy();
		((tools.jackson.databind.node.ArrayNode) soloFutura.path("installments")).remove(0);
		OpzioneProva opzioneSoloFutura = new OpzioneProva("pianoSoloRataFutura", piano.iuv(), piano.nav(), soloFutura);

		contesto.attendiFino(evento.plusSeconds(10), "scadenza della prima rata");

		contesto.fase("prova");
		CasiPut.put(contesto, "PUT con tutte le rate, toPublish=true", posizione, posizione.corpo(), true);
		CasiPut.leggiEDescrivi(contesto, "lettura dopo il PUT con tutte le rate, toPublish=true", posizione);
		CasiPut.put(contesto, "PUT con tutte le rate, toPublish=false", posizione, posizione.corpo(), false);
		CasiPut.leggiEDescrivi(contesto, "lettura dopo il PUT con tutte le rate, toPublish=false", posizione);
		CasiPut.put(contesto, "PUT con la sola rata futura, toPublish=false", posizione, contesto.corpoPosizione(iupd, opzioneSoloFutura), false);
		CasiPut.leggiEDescrivi(contesto, "lettura dopo il PUT con la sola rata futura, toPublish=false", posizione);
		CasiPut.put(contesto, "PUT con la sola rata futura, toPublish=true", posizione, contesto.corpoPosizione(iupd, opzioneSoloFutura), true);
		CasiPut.leggiEDescrivi(contesto, "lettura dopo il PUT con la sola rata futura, toPublish=true", posizione);
		contesto.osservato();
	}
}
