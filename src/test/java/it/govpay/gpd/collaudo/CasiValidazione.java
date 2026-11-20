package it.govpay.gpd.collaudo;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import it.govpay.gpd.collaudo.ClientGpd.Risposta;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public final class CasiValidazione {

	public static final String GRUPPO = "validazione";
	/** Casi che lasciano sul collaudo posizioni non eliminabili: solo su richiesta esplicita. */
	public static final String GRUPPO_ORFANI = "orfani";

	private CasiValidazione() {
	}

	public static List<Caso> casi() {
		return List.of(
				caso("VAL_01_RATA_SENZA_IUV", "Rata senza iuv", c -> CorpiProva.rata(c).remove("iuv")),
				caso("VAL_02_NAV_NON_COERENTE_CON_IUV", "NAV diverso da 3 seguito dall'IUV",
						c -> CorpiProva.rata(c).put("nav", "3" + "99999999999999999")),
				caso("VAL_03_DUEDATE_PRIMA_DI_VALIDITY", "dueDate precedente alla validityDate", c -> {
					CorpiProva.opzione(c).put("validityDate", Payloads.data(30));
					CorpiProva.rata(c).put("dueDate", Payloads.data(10));
				}),
				caso("VAL_04_CINQUE_TRASFERIMENTI", "Cinque trasferimenti (limite del contratto)", c -> trasferimenti(c, 5)),
				caso("VAL_05_SEI_TRASFERIMENTI", "Sei trasferimenti (oltre il limite del contratto)", c -> trasferimenti(c, 6)),
				caso("VAL_06_DESCRIZIONE_LUNGA", "Descrizione della rata di 600 caratteri",
						c -> CorpiProva.rata(c).put("description", "d".repeat(600))),
				caso("VAL_07_DEBITORE_MANCANTE", "Opzione senza debitore", c -> CorpiProva.opzione(c).remove("debtor")),
				caso("VAL_08_OPZIONE_SENZA_RATE", "Opzione con elenco rate vuoto",
						c -> CorpiProva.opzione(c).putArray("installments")),
				caso("VAL_09_IMPORTO_TRASFERIMENTI_DIVERSO", "Somma dei trasferimenti diversa dall'importo della rata",
						c -> ((ObjectNode) CorpiProva.rata(c).path("transfer").get(0)).put("amount", 40)),
				caso("VAL_10_IMPORTO_ZERO", "Rata con importo 0", c -> {
					CorpiProva.rata(c).put("amount", 0);
					((ObjectNode) CorpiProva.rata(c).path("transfer").get(0)).put("amount", 0);
				}),
				caso("VAL_11_DUE_OPZIONI_STESSO_IUV", "Due opzioni della stessa posizione con lo stesso IUV",
						c -> ((ArrayNode) c.path("paymentOption")).add(CorpiProva.opzione(c).deepCopy())),
				caso("VAL_12_NAV_OMESSO", "NAV omesso (si osserva se GPD lo assegna)", c -> CorpiProva.rata(c).remove("nav")),
				casoConPosizione(GRUPPO, "VAL_13_IUPD_TROPPO_LUNGO", "IUPD di circa 100 caratteri",
						ctx -> posizioneConIupd(ctx, ctx.identificativi().iupd() + "-" + "a".repeat(80))),
				casoConPosizione(GRUPPO_ORFANI, "ORFANI_01_IUPD_CARATTERI_SPECIALI", "IUPD con caratteri speciali ($): la posizione resta sul collaudo e non e' piu' leggibile ne' eliminabile",
						ctx -> posizioneConIupd(ctx, ctx.identificativi().iupd() + "$$")),
				caso("VAL_15_IUV_NON_NUMERICO", "IUV non numerico", c -> {
					CorpiProva.rata(c).put("iuv", "ABCDEFGHIJKLMNOPQ");
					CorpiProva.rata(c).put("nav", "3ABCDEFGHIJKLMNOPQ");
				}),
				caso("VAL_16_IUV_CONTROLLO_ERRATO", "IUV con le due cifre di controllo sbagliate", c -> {
					String iuv = CorpiProva.rata(c).path("iuv").asString();
					int controllo = Integer.parseInt(iuv.substring(15));
					String errato = iuv.substring(0, 15) + String.format("%02d", (controllo + 1) % 93);
					CorpiProva.rata(c).put("iuv", errato);
					CorpiProva.rata(c).put("nav", "3" + errato);
				}),
				caso("VAL_17_IUV_DICIOTTO_CIFRE", "IUV di 18 cifre", c -> {
					String iuv = CorpiProva.rata(c).path("iuv").asString() + "7";
					CorpiProva.rata(c).put("iuv", iuv);
					CorpiProva.rata(c).put("nav", "3" + iuv);
				}),
				caso("VAL_18_IBAN_NON_VALIDO", "IBAN non valido",
						c -> ((ObjectNode) CorpiProva.rata(c).path("transfer").get(0)).put("iban", "IT00INVALID")),
				caso("VAL_19_CODICE_FISCALE_DEBITORE_NON_VALIDO", "Codice fiscale del debitore non valido",
						c -> ((ObjectNode) CorpiProva.opzione(c).path("debtor")).put("fiscalCode", "XXX")),
				caso("VAL_20_COMPANY_NAME_MANCANTE", "companyName assente", c -> c.remove("companyName")),
				caso("VAL_21_CATEGORY_NON_VALIDA", "Category del trasferimento non valida",
						c -> ((ObjectNode) CorpiProva.rata(c).path("transfer").get(0)).put("category", "XYZ")),
				caso("VAL_22_IDTRANSFER_DUPLICATO", "Due trasferimenti con lo stesso idTransfer", c -> {
					trasferimenti(c, 2);
					((ObjectNode) CorpiProva.rata(c).path("transfer").get(1)).put("idTransfer", "1");
				}),
				caso("VAL_23_TRASFERIMENTO_ALTRO_ENTE", "Secondo trasferimento a un altro ente beneficiario", c -> {
					trasferimenti(c, 2);
					((ObjectNode) CorpiProva.rata(c).path("transfer").get(1)).put("organizationFiscalCode", "99999999999");
				}),
				caso("VAL_24_TIPO_DEBITORE_MANCANTE", "Debitore senza type",
						c -> ((ObjectNode) CorpiProva.opzione(c).path("debtor")).remove("type")),
				caso("VAL_25_EMAIL_DEBITORE_NON_VALIDA", "Email del debitore non valida",
						c -> ((ObjectNode) CorpiProva.opzione(c).path("debtor")).put("email", "non-una-email")),
				caso("VAL_26_CAUSALE_TRASFERIMENTO_MANCANTE", "remittanceInformation assente",
						c -> ((ObjectNode) CorpiProva.rata(c).path("transfer").get(0)).remove("remittanceInformation")));
	}

	private static Caso caso(String id, String descrizione, Consumer<ObjectNode> modifica) {
		return casoConPosizione(GRUPPO, id, descrizione, contesto -> {
			Posizione posizione = contesto.nuova("posizione", null);
			modifica.accept(posizione.corpo());
			return posizione;
		});
	}

	/** Posizione di prova con IUPD scelto: la pulizia deve conoscere l'IUPD realmente inviato. */
	private static Posizione posizioneConIupd(ContestoCaso contesto, String iupd) {
		String iuv = contesto.identificativi().iuv();
		return contesto.posizione("posizione", iupd, contesto.identificativi().nav(iuv), iuv, null);
	}

	private static Caso casoConPosizione(String gruppo, String id, String descrizione, Function<ContestoCaso, Posizione> costruisci) {
		return Caso.di(id, gruppo, "Creazione pubblicata: " + descrizione, contesto -> {
			Posizione posizione = costruisci.apply(contesto);
			contesto.fase("prova");
			Risposta creazione = contesto.client().crea(contesto.registrazione(), "creazione: " + descrizione,
					posizione.iupd(), posizione.corpo(), true);
			contesto.nota("Creazione: HTTP " + creazione.stato());
			if (creazione.ok()) {
				contesto.fase("verifica");
				Risposta lettura = contesto.client().leggi(contesto.registrazione(), "lettura dopo la creazione", posizione.iupd());
				contesto.nota("Lettura dopo la creazione: HTTP " + lettura.stato() + ", stato " + lettura.campo("status") + ", "
						+ contesto.descriviOpzioni(lettura));
			}
			contesto.osservato();
		});
	}

	/** Divide l'importo della rata (100) in n trasferimenti con idTransfer da 1 a n. */
	private static void trasferimenti(ObjectNode corpo, int n) {
		ObjectNode rata = CorpiProva.rata(corpo);
		ObjectNode modello = ((ObjectNode) rata.path("transfer").get(0)).deepCopy();
		rata.put("amount", n * 100L);
		ArrayNode elenco = rata.putArray("transfer");
		for (int i = 1; i <= n; i++) {
			ObjectNode trasferimento = modello.deepCopy();
			trasferimento.put("idTransfer", String.valueOf(i));
			trasferimento.put("amount", 100);
			elenco.add(trasferimento);
		}
	}
}
