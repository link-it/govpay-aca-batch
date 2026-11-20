package it.govpay.gpd.collaudo;

import java.util.List;

import it.govpay.gpd.collaudo.ClientGpd.Risposta;
import it.govpay.gpd.collaudo.ContestoCaso.OpzioneProva;
import tools.jackson.databind.node.ObjectNode;

public final class CasiPut {

	public static final String GRUPPO = "put";

	private static final String DEBITORE_1 = "RSSMRA80A01H501U";
	private static final String NOME_1 = "Mario Rossi";
	private static final String DEBITORE_2 = "VRDLGU85M01H501Q";
	private static final String NOME_2 = "Luigi Verdi";

	private CasiPut() {
	}

	public static List<Caso> casi() {
		return List.of(
				Caso.di("PUT_01_OPZIONE_AGGIUNTA", GRUPPO,
						"Posizione VALID con un'opzione: PUT con la stessa opzione piu' una nuova", CasiPut::opzioneAggiunta),
				Caso.di("PUT_02_OPZIONE_OMESSA", GRUPPO,
						"Posizione VALID con due opzioni dello stesso debitore: PUT con una sola", CasiPut::opzioneOmessa),
				Caso.di("PUT_03_OPZIONE_OMESSA_RIUSO_IUV", GRUPPO,
						"Dopo l'omissione di un'opzione: nuova posizione con il suo IUV e NAV", CasiPut::opzioneOmessaRiusoIuv),
				Caso.di("PUT_04_DUE_DEBITORI_OMISSIONE_UNO", GRUPPO,
						"Posizione VALID con un'opzione per ciascuno di due debitori: PUT con una sola", CasiPut::dueDebitoriOmissioneUno),
				Caso.di("PUT_05_DRAFT_A_PUBBLICATA", GRUPPO,
						"Posizione DRAFT: PUT con toPublish=true", CasiPut::draftAPubblicata),
				Caso.di("PUT_06_PUBLISHED_A_DRAFT", GRUPPO,
						"Posizione PUBLISHED: PUT con toPublish=false", CasiPut::publishedADraft),
				Caso.di("PUT_07_VALID_A_DRAFT", GRUPPO,
						"Posizione VALID: PUT con toPublish=false", CasiPut::validADraft),
				Caso.di("PUT_08_VALID_IMPORTO_MODIFICATO", GRUPPO,
						"Posizione VALID: PUT con importo diverso", CasiPut::validImportoModificato),
				Caso.di("PUT_09_VALID_IUV_MODIFICATO", GRUPPO,
						"Posizione VALID: PUT con IUV e NAV diversi, poi nuova posizione con quelli vecchi", CasiPut::validIuvModificato),
				Caso.di("PUT_10_RIPETIZIONE_IDENTICA", GRUPPO,
						"Posizione VALID: stesso PUT ripetuto due volte", CasiPut::ripetizioneIdentica),
				Caso.di("PUT_11_IUPD_INESISTENTE", GRUPPO,
						"PUT su un IUPD mai creato", CasiPut::iupdInesistente));
	}

	static Risposta put(ContestoCaso contesto, String etichetta, Posizione posizione, ObjectNode corpo, boolean daPubblicare) {
		Risposta risposta = contesto.client().aggiorna(contesto.registrazione(), etichetta, posizione.iupd(), corpo, daPubblicare);
		contesto.nota(etichetta + ": HTTP " + risposta.stato());
		return risposta;
	}

	static void leggiEDescrivi(ContestoCaso contesto, String etichetta, Posizione posizione) {
		Risposta lettura = contesto.client().leggi(contesto.registrazione(), etichetta, posizione.iupd());
		contesto.nota(etichetta + ": HTTP " + lettura.stato() + ", stato " + lettura.campo("status") + ", "
				+ contesto.descriviOpzioni(lettura));
	}

	private static void opzioneAggiunta(ContestoCaso contesto) {
		String iupd = contesto.identificativi().iupd();
		OpzioneProva a = contesto.opzione("opzioneA", iupd, DEBITORE_1, NOME_1, null);
		OpzioneProva b = contesto.opzione("opzioneB", iupd, DEBITORE_1, NOME_1, null);
		Posizione posizione = contesto.prepara(contesto.posizioneConOpzioni("posizione", iupd, a), "VALID");
		contesto.fase("prova");
		put(contesto, "PUT con opzioni A e B", posizione, contesto.corpoPosizione(iupd, a, b), true);
		contesto.fase("verifica");
		leggiEDescrivi(contesto, "lettura dopo il PUT", posizione);
		contesto.osservato();
	}

	private static void opzioneOmessa(ContestoCaso contesto) {
		String iupd = contesto.identificativi().iupd();
		OpzioneProva a = contesto.opzione("opzioneA", iupd, DEBITORE_1, NOME_1, null);
		OpzioneProva b = contesto.opzione("opzioneB", iupd, DEBITORE_1, NOME_1, null);
		Posizione posizione = contesto.prepara(contesto.posizioneConOpzioni("posizione", iupd, a, b), "VALID");
		contesto.fase("verifica");
		leggiEDescrivi(contesto, "lettura prima del PUT", posizione);
		contesto.fase("prova");
		put(contesto, "PUT con la sola opzione A", posizione, contesto.corpoPosizione(iupd, a), true);
		contesto.fase("verifica");
		leggiEDescrivi(contesto, "lettura dopo il PUT", posizione);
		contesto.osservato();
	}

	private static void opzioneOmessaRiusoIuv(ContestoCaso contesto) {
		String iupd = contesto.identificativi().iupd();
		OpzioneProva a = contesto.opzione("opzioneA", iupd, DEBITORE_1, NOME_1, null);
		OpzioneProva b = contesto.opzione("opzioneB", iupd, DEBITORE_1, NOME_1, null);
		Posizione posizione = contesto.prepara(contesto.posizioneConOpzioni("posizione", iupd, a, b), "VALID");
		contesto.fase("prova");
		Risposta aggiornamento = put(contesto, "PUT con la sola opzione A", posizione, contesto.corpoPosizione(iupd, a), true);
		contesto.fase("verifica");
		leggiEDescrivi(contesto, "lettura dopo il PUT", posizione);
		if (!aggiornamento.ok()) {
			contesto.nonEseguibile("PUT con opzione omessa rifiutato: HTTP " + aggiornamento.stato());
			return;
		}
		contesto.fase("prova");
		Posizione nuova = contesto.posizione("nuova", contesto.identificativi().iupd(), b.nav(), b.iuv(), null);
		Risposta creazione = contesto.client().crea(contesto.registrazione(), "creazione con IUV e NAV dell'opzione omessa",
				nuova.iupd(), nuova.corpo(), true);
		contesto.nota("Creazione con IUV e NAV dell'opzione omessa: HTTP " + creazione.stato());
		contesto.osservato();
	}

	private static void dueDebitoriOmissioneUno(ContestoCaso contesto) {
		String iupd = contesto.identificativi().iupd();
		OpzioneProva primo = contesto.opzione("opzioneDebitore1", iupd, DEBITORE_1, NOME_1, null);
		OpzioneProva secondo = contesto.opzione("opzioneDebitore2", iupd, DEBITORE_2, NOME_2, null);
		Posizione posizione = contesto.prepara(contesto.posizioneConOpzioni("posizione", iupd, primo, secondo), "VALID");
		contesto.fase("verifica");
		leggiEDescrivi(contesto, "lettura prima del PUT", posizione);
		contesto.fase("prova");
		put(contesto, "PUT con la sola opzione del secondo debitore", posizione, contesto.corpoPosizione(iupd, secondo), true);
		contesto.fase("verifica");
		leggiEDescrivi(contesto, "lettura dopo il PUT", posizione);
		contesto.osservato();
	}

	private static void draftAPubblicata(ContestoCaso contesto) {
		Posizione posizione = contesto.preparaInStato("posizione", "DRAFT");
		contesto.fase("prova");
		put(contesto, "PUT con toPublish=true", posizione, posizione.corpo(), true);
		contesto.fase("verifica");
		leggiEDescrivi(contesto, "lettura dopo il PUT", posizione);
		contesto.osservato();
	}

	private static void publishedADraft(ContestoCaso contesto) {
		Posizione posizione = contesto.preparaInStato("posizione", "PUBLISHED");
		contesto.fase("prova");
		put(contesto, "PUT con toPublish=false", posizione, posizione.corpo(), false);
		contesto.fase("verifica");
		leggiEDescrivi(contesto, "lettura dopo il PUT", posizione);
		contesto.osservato();
	}

	private static void validADraft(ContestoCaso contesto) {
		Posizione posizione = contesto.preparaInStato("posizione", "VALID");
		contesto.fase("prova");
		put(contesto, "PUT con toPublish=false", posizione, posizione.corpo(), false);
		contesto.fase("verifica");
		leggiEDescrivi(contesto, "lettura dopo il PUT", posizione);
		contesto.osservato();
	}

	private static void validImportoModificato(ContestoCaso contesto) {
		String iupd = contesto.identificativi().iupd();
		OpzioneProva originale = contesto.opzione("opzione", iupd, DEBITORE_1, NOME_1, null);
		Posizione posizione = contesto.prepara(contesto.posizioneConOpzioni("posizione", iupd, originale), "VALID");
		OpzioneProva modificata = contesto.opzione("opzioneModificata", iupd, DEBITORE_1, NOME_1, null, originale.iuv(), 250);
		contesto.fase("prova");
		put(contesto, "PUT con importo 250 invece di 100", posizione, contesto.corpoPosizione(iupd, modificata), true);
		contesto.fase("verifica");
		leggiEDescrivi(contesto, "lettura dopo il PUT", posizione);
		contesto.osservato();
	}

	private static void validIuvModificato(ContestoCaso contesto) {
		String iupd = contesto.identificativi().iupd();
		OpzioneProva originale = contesto.opzione("opzione", iupd, DEBITORE_1, NOME_1, null);
		Posizione posizione = contesto.prepara(contesto.posizioneConOpzioni("posizione", iupd, originale), "VALID");
		OpzioneProva modificata = contesto.opzione("opzioneConIuvNuovo", iupd, DEBITORE_1, NOME_1, null);
		contesto.fase("prova");
		Risposta aggiornamento = put(contesto, "PUT con IUV e NAV diversi", posizione, contesto.corpoPosizione(iupd, modificata), true);
		contesto.fase("verifica");
		leggiEDescrivi(contesto, "lettura dopo il PUT", posizione);
		if (!aggiornamento.ok()) {
			contesto.nonEseguibile("PUT con IUV diverso rifiutato: HTTP " + aggiornamento.stato());
			return;
		}
		contesto.fase("prova");
		Posizione nuova = contesto.posizione("nuova", contesto.identificativi().iupd(), originale.nav(), originale.iuv(), null);
		Risposta creazione = contesto.client().crea(contesto.registrazione(), "creazione con IUV e NAV sostituiti dal PUT",
				nuova.iupd(), nuova.corpo(), true);
		contesto.nota("Creazione con IUV e NAV sostituiti dal PUT: HTTP " + creazione.stato());
		contesto.osservato();
	}

	private static void ripetizioneIdentica(ContestoCaso contesto) {
		Posizione posizione = contesto.preparaInStato("posizione", "VALID");
		contesto.fase("prova");
		put(contesto, "primo PUT identico", posizione, posizione.corpo(), true);
		put(contesto, "secondo PUT identico", posizione, posizione.corpo(), true);
		contesto.fase("verifica");
		leggiEDescrivi(contesto, "lettura dopo i due PUT", posizione);
		contesto.osservato();
	}

	private static void iupdInesistente(ContestoCaso contesto) {
		contesto.fase("prova");
		Posizione posizione = contesto.nuova("posizione", null);
		put(contesto, "PUT su IUPD mai creato", posizione, posizione.corpo(), true);
		contesto.fase("verifica");
		leggiEDescrivi(contesto, "lettura dopo il PUT", posizione);
		contesto.osservato();
	}
}
