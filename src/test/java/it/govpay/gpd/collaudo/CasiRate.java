package it.govpay.gpd.collaudo;

import java.util.List;

import it.govpay.gpd.collaudo.ClientGpd.Risposta;
import it.govpay.gpd.collaudo.ContestoCaso.OpzioneProva;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public final class CasiRate {

	public static final String GRUPPO = "rate";

	private static final String DEBITORE = "RSSMRA80A01H501U";
	private static final String NOME = "Mario Rossi";

	private CasiRate() {
	}

	public static List<Caso> casi() {
		return List.of(
				Caso.di("RATE_01_DUE_RATE", GRUPPO, "Creazione pubblicata con un'opzione di due rate", CasiRate::dueRate),
				Caso.di("RATE_02_RATA_OMESSA_CON_PUT", GRUPPO,
						"Opzione VALID di due rate: PUT con la sola prima rata, poi riuso dell'IUV della seconda", CasiRate::rataOmessa),
				Caso.di("RATE_03_STESSO_IUV_TRA_RATE", GRUPPO, "Creazione con due rate dello stesso IUV", CasiRate::stessoIuvTraRate),
				Caso.di("RATE_04_DUEDATE_NON_CRESCENTI", GRUPPO,
						"Creazione con la seconda rata che scade prima della prima", CasiRate::dueDateNonCrescenti));
	}

	private static Posizione conDueRate(ContestoCaso contesto) {
		String iupd = contesto.identificativi().iupd();
		OpzioneProva opzione = contesto.opzioneRateale("opzione", iupd, DEBITORE, NOME, null, 2);
		return contesto.posizioneConOpzioni("posizione", iupd, opzione);
	}

	private static void dueRate(ContestoCaso contesto) {
		CasiDate.provaCreazione(contesto, "creazione con un'opzione di due rate", conDueRate(contesto));
	}

	private static void rataOmessa(ContestoCaso contesto) {
		Posizione posizione = contesto.prepara(conDueRate(contesto), "VALID");
		ObjectNode seconda = (ObjectNode) CorpiProva.opzione(posizione.corpo()).path("installments").get(1);
		String iuvSeconda = seconda.path("iuv").asString();
		String navSeconda = seconda.path("nav").asString();
		contesto.fase("verifica");
		CasiPut.leggiEDescrivi(contesto, "lettura prima del PUT", posizione);
		ObjectNode corpo = posizione.corpo().deepCopy();
		((ArrayNode) CorpiProva.opzione(corpo).path("installments")).remove(1);
		contesto.fase("prova");
		Risposta aggiornamento = CasiPut.put(contesto, "PUT con la sola prima rata", posizione, corpo, true);
		contesto.fase("verifica");
		CasiPut.leggiEDescrivi(contesto, "lettura dopo il PUT", posizione);
		if (!aggiornamento.ok()) {
			contesto.nonEseguibile("PUT con rata omessa rifiutato: HTTP " + aggiornamento.stato());
			return;
		}
		contesto.fase("prova");
		Posizione nuova = contesto.posizione("nuova", contesto.identificativi().iupd(), navSeconda, iuvSeconda, null);
		Risposta creazione = contesto.client().crea(contesto.registrazione(), "creazione con IUV e NAV della rata omessa",
				nuova.iupd(), nuova.corpo(), true);
		contesto.nota("Creazione con IUV e NAV della rata omessa: HTTP " + creazione.stato());
		contesto.osservato();
	}

	private static void stessoIuvTraRate(ContestoCaso contesto) {
		Posizione posizione = conDueRate(contesto);
		ArrayNode rate = (ArrayNode) CorpiProva.opzione(posizione.corpo()).path("installments");
		((ObjectNode) rate.get(1)).put("iuv", rate.get(0).path("iuv").asString());
		((ObjectNode) rate.get(1)).put("nav", rate.get(0).path("nav").asString());
		CasiDate.provaCreazione(contesto, "creazione con due rate dello stesso IUV", posizione);
	}

	private static void dueDateNonCrescenti(ContestoCaso contesto) {
		Posizione posizione = conDueRate(contesto);
		ArrayNode rate = (ArrayNode) CorpiProva.opzione(posizione.corpo()).path("installments");
		((ObjectNode) rate.get(1)).put("dueDate", Payloads.data(10));
		CasiDate.provaCreazione(contesto, "creazione con la seconda rata che scade prima della prima", posizione);
	}
}
