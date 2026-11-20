package it.govpay.gpd.collaudo;

import java.util.List;

import it.govpay.gpd.collaudo.ClientGpd.Risposta;
import tools.jackson.databind.node.ObjectNode;

public final class CasiDate {

	public static final String GRUPPO = "date";

	private CasiDate() {
	}

	public static List<Caso> casi() {
		return List.of(
				Caso.di("DATE_01_VALIDITY_FUTURA", GRUPPO, "Creazione pubblicata con validityDate tra 30 giorni", CasiDate::validityFutura),
				Caso.di("DATE_02_VALIDITY_PASSATA", GRUPPO, "Creazione pubblicata con validityDate di ieri", CasiDate::validityPassata),
				Caso.di("DATE_03_DUEDATE_PASSATA_CREAZIONE", GRUPPO, "Creazione pubblicata con dueDate di cinque giorni fa", CasiDate::dueDatePassataCreazione),
				Caso.di("DATE_04_DUEDATE_PASSATA_AGGIORNAMENTO", GRUPPO, "Posizione VALID: PUT con dueDate di cinque giorni fa", CasiDate::dueDatePassataAggiornamento),
				Caso.di("DATE_05_SWITCH_TO_EXPIRED", GRUPPO, "Creazione pubblicata con switchToExpired=true e dueDate futura", CasiDate::switchToExpired),
				Caso.di("DATE_06_SWITCH_TO_EXPIRED_DUEDATE_PASSATA", GRUPPO, "Creazione pubblicata con switchToExpired=true e dueDate passata", CasiDate::switchToExpiredDueDatePassata),
				Caso.di("DATE_07_VALIDITY_SPOSTATA_AVANTI", GRUPPO, "Posizione VALID: PUT con validityDate tra 30 giorni", CasiDate::validitySpostataAvanti),
				Caso.di("DATE_08_VALIDITY_SPOSTATA_INDIETRO", GRUPPO, "Posizione PUBLISHED: PUT senza validityDate", CasiDate::validitySpostataIndietro),
				Caso.di("DATE_09_OPZIONI_VALIDITY_MISTE", GRUPPO,
						"Creazione pubblicata con due opzioni: una subito valida e una con validityDate tra 30 giorni", CasiDate::opzioniValidityMiste),
				Caso.di("DATE_10_DRAFT_DUEDATE_PASSATA", GRUPPO, "Creazione in DRAFT con dueDate passata", CasiDate::draftDueDatePassata),
				Caso.di("DATE_11_DRAFT_VALIDITY_PASSATA", GRUPPO, "Creazione in DRAFT con validityDate passata", CasiDate::draftValidityPassata),
				Caso.di("DATE_12_PUT_DRAFT_DUEDATE_PASSATA", GRUPPO,
						"Posizione DRAFT: PUT con toPublish=false e dueDate passata", CasiDate::putDraftDueDatePassata));
	}

	static void provaCreazione(ContestoCaso contesto, String etichetta, Posizione posizione) {
		provaCreazione(contesto, etichetta, posizione, true);
	}

	static void provaCreazione(ContestoCaso contesto, String etichetta, Posizione posizione, boolean daPubblicare) {
		contesto.fase("prova");
		Risposta creazione = contesto.client().crea(contesto.registrazione(), etichetta, posizione.iupd(), posizione.corpo(), daPubblicare);
		contesto.nota(etichetta + ": HTTP " + creazione.stato());
		contesto.fase("verifica");
		Risposta lettura = contesto.client().leggi(contesto.registrazione(), "lettura dopo la creazione", posizione.iupd());
		contesto.nota("lettura dopo la creazione: HTTP " + lettura.stato() + ", stato " + lettura.campo("status") + ", "
				+ contesto.descriviOpzioni(lettura));
		contesto.osservato();
	}

	private static void provaAggiornamento(ContestoCaso contesto, String etichetta, Posizione posizione, ObjectNode corpo) {
		provaAggiornamento(contesto, etichetta, posizione, corpo, true);
	}

	private static void provaAggiornamento(ContestoCaso contesto, String etichetta, Posizione posizione, ObjectNode corpo,
			boolean daPubblicare) {
		contesto.fase("prova");
		Risposta aggiornamento = contesto.client().aggiorna(contesto.registrazione(), etichetta, posizione.iupd(), corpo, daPubblicare);
		contesto.nota(etichetta + ": HTTP " + aggiornamento.stato());
		contesto.fase("verifica");
		Risposta lettura = contesto.client().leggi(contesto.registrazione(), "lettura dopo il PUT", posizione.iupd());
		contesto.nota("lettura dopo il PUT: HTTP " + lettura.stato() + ", stato " + lettura.campo("status") + ", "
				+ contesto.descriviOpzioni(lettura));
		contesto.osservato();
	}

	private static void validityFutura(ContestoCaso contesto) {
		provaCreazione(contesto, "creazione con validityDate futura", contesto.nuova("posizione", Payloads.data(30)));
	}

	private static void validityPassata(ContestoCaso contesto) {
		provaCreazione(contesto, "creazione con validityDate passata", contesto.nuova("posizione", Payloads.data(-1)));
	}

	private static void dueDatePassataCreazione(ContestoCaso contesto) {
		Posizione posizione = contesto.nuova("posizione", null);
		CorpiProva.rata(posizione.corpo()).put("dueDate", Payloads.data(-5));
		provaCreazione(contesto, "creazione con dueDate passata", posizione);
	}

	private static void dueDatePassataAggiornamento(ContestoCaso contesto) {
		Posizione posizione = contesto.preparaInStato("posizione", "VALID");
		ObjectNode corpo = posizione.corpo().deepCopy();
		CorpiProva.rata(corpo).put("dueDate", Payloads.data(-5));
		provaAggiornamento(contesto, "PUT con dueDate passata", posizione, corpo);
	}

	private static void switchToExpired(ContestoCaso contesto) {
		Posizione posizione = contesto.nuova("posizione", null);
		CorpiProva.opzione(posizione.corpo()).put("switchToExpired", true);
		provaCreazione(contesto, "creazione con switchToExpired=true", posizione);
	}

	private static void switchToExpiredDueDatePassata(ContestoCaso contesto) {
		Posizione posizione = contesto.nuova("posizione", null);
		CorpiProva.opzione(posizione.corpo()).put("switchToExpired", true);
		CorpiProva.rata(posizione.corpo()).put("dueDate", Payloads.data(-5));
		provaCreazione(contesto, "creazione con switchToExpired=true e dueDate passata", posizione);
	}

	private static void validitySpostataAvanti(ContestoCaso contesto) {
		Posizione posizione = contesto.preparaInStato("posizione", "VALID");
		ObjectNode corpo = posizione.corpo().deepCopy();
		CorpiProva.opzione(corpo).put("validityDate", Payloads.data(30));
		provaAggiornamento(contesto, "PUT con validityDate futura", posizione, corpo);
	}

	private static void validitySpostataIndietro(ContestoCaso contesto) {
		Posizione posizione = contesto.preparaInStato("posizione", "PUBLISHED");
		ObjectNode corpo = posizione.corpo().deepCopy();
		CorpiProva.opzione(corpo).remove("validityDate");
		provaAggiornamento(contesto, "PUT senza validityDate", posizione, corpo);
	}

	private static void opzioniValidityMiste(ContestoCaso contesto) {
		String iupd = contesto.identificativi().iupd();
		ContestoCaso.OpzioneProva subito = contesto.opzione("opzioneSubito", iupd, "RSSMRA80A01H501U", "Mario Rossi", null);
		ContestoCaso.OpzioneProva differita = contesto.opzione("opzioneDifferita", iupd, "VRDLGU85M01H501Q", "Luigi Verdi", Payloads.data(30));
		provaCreazione(contesto, "creazione con opzioni a validita' miste", contesto.posizioneConOpzioni("posizione", iupd, subito, differita));
	}

	private static void draftDueDatePassata(ContestoCaso contesto) {
		Posizione posizione = contesto.nuova("posizione", null);
		CorpiProva.rata(posizione.corpo()).put("dueDate", Payloads.data(-5));
		provaCreazione(contesto, "creazione in DRAFT con dueDate passata", posizione, false);
	}

	private static void draftValidityPassata(ContestoCaso contesto) {
		provaCreazione(contesto, "creazione in DRAFT con validityDate passata", contesto.nuova("posizione", Payloads.data(-1)), false);
	}

	private static void putDraftDueDatePassata(ContestoCaso contesto) {
		Posizione posizione = contesto.preparaInStato("posizione", "DRAFT");
		ObjectNode corpo = posizione.corpo().deepCopy();
		CorpiProva.rata(corpo).put("dueDate", Payloads.data(-5));
		provaAggiornamento(contesto, "PUT in DRAFT con dueDate passata", posizione, corpo, false);
	}
}
