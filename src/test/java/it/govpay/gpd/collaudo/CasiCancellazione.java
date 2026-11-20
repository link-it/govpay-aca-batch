package it.govpay.gpd.collaudo;

import java.util.List;

import it.govpay.gpd.collaudo.ClientGpd.Risposta;

public final class CasiCancellazione {

	public static final String GRUPPO = "cancellazione";

	private CasiCancellazione() {
	}

	public static List<Caso> casi() {
		return List.of(
				cancellazione("CANC_01_DRAFT", "DRAFT"),
				cancellazione("CANC_02_PUBLISHED", "PUBLISHED"),
				cancellazione("CANC_03_VALID", "VALID"),
				Caso.di("RIUSO_01_STESSO_IUPD_STESSA_COPPIA", GRUPPO,
						"Dopo la cancellazione: stesso IUPD con gli stessi NAV e IUV", CasiCancellazione::stessoIupdStessaCoppia),
				Caso.di("RIUSO_02_STESSO_IUPD_COPPIA_NUOVA", GRUPPO,
						"Dopo la cancellazione: stesso IUPD con NAV e IUV nuovi", CasiCancellazione::stessoIupdCoppiaNuova),
				Caso.di("RIUSO_03_IUPD_NUOVO_STESSA_COPPIA", GRUPPO,
						"Dopo la cancellazione: IUPD nuovo con gli stessi NAV e IUV", CasiCancellazione::iupdNuovoStessaCoppia),
				Caso.di("RIUSO_04_IUPD_NUOVO_STESSO_IUV_NAV_OMESSO", GRUPPO,
						"Dopo la cancellazione: IUPD nuovo con lo stesso IUV e NAV omesso", CasiCancellazione::iupdNuovoStessoIuvNavOmesso),
				Caso.di("ELENCO_01_POSIZIONE_CANCELLATA", GRUPPO,
						"Presenza nell'elenco di una posizione cancellata", CasiCancellazione::elenco));
	}

	private static Caso cancellazione(String id, String stato) {
		return Caso.di(id, GRUPPO, "Cancellazione di una posizione effettivamente in stato " + stato, contesto -> {
			Posizione posizione = contesto.preparaInStato("posizione", stato);
			contesto.fase("prova");
			Risposta prima = contesto.client().cancella(contesto.registrazione(), "cancellazione", posizione.iupd());
			if (!prima.ok()) {
				contesto.nota("Cancellazione rifiutata: HTTP " + prima.stato());
				contesto.osservato();
				return;
			}
			contesto.fase("verifica");
			Osservatore.Lettura lettura = contesto.osservatore().leggiFinche(contesto.registrazione(),
					"lettura dopo la cancellazione", posizione.iupd(), r -> r.stato() == 404);
			contesto.nota(lettura.raggiunta()
					? "Dopo la cancellazione la lettura restituisce 404 alla lettura " + lettura.tentativi()
					: "Dopo " + lettura.secondi() + " s la lettura restituisce ancora HTTP " + lettura.ultima().stato()
							+ " con stato " + lettura.ultima().campo("status"));
			contesto.fase("prova");
			Risposta seconda = contesto.client().cancella(contesto.registrazione(), "cancellazione ripetuta", posizione.iupd());
			contesto.nota("Cancellazione ripetuta: HTTP " + seconda.stato());
			contesto.osservato();
		});
	}

	private static Posizione preparaCancellata(ContestoCaso contesto) {
		Posizione posizione = contesto.preparaInStato("originale", "VALID");
		contesto.cancellaComePreparazione(posizione);
		contesto.fase("prova");
		return posizione;
	}

	private static void ricrea(ContestoCaso contesto, Posizione nuova, String etichetta) {
		Risposta creazione = contesto.client().crea(contesto.registrazione(), etichetta, nuova.iupd(), nuova.corpo(), true);
		contesto.nota("Creazione: HTTP " + creazione.stato());
		contesto.fase("verifica");
		Osservatore.Lettura lettura = contesto.osservatore().leggiFinche(contesto.registrazione(),
				"lettura dopo la creazione", nuova.iupd(), r -> r.ok());
		contesto.nota(lettura.raggiunta() ? "La posizione e' leggibile con stato " + lettura.ultima().campo("status")
				: "La posizione non e' leggibile dopo " + lettura.secondi() + " s: HTTP " + lettura.ultima().stato());
		contesto.osservato();
	}

	private static void stessoIupdStessaCoppia(ContestoCaso contesto) {
		Posizione originale = preparaCancellata(contesto);
		Posizione nuova = contesto.posizione("ricreata", originale.iupd(), originale.nav(), originale.iuv(), null);
		ricrea(contesto, nuova, "creazione con stesso IUPD e stessi NAV e IUV");
	}

	private static void stessoIupdCoppiaNuova(ContestoCaso contesto) {
		Posizione originale = preparaCancellata(contesto);
		String iuv = contesto.identificativi().iuv();
		Posizione nuova = contesto.posizione("ricreata", originale.iupd(), contesto.identificativi().nav(iuv), iuv, null);
		ricrea(contesto, nuova, "creazione con stesso IUPD e NAV e IUV nuovi");
	}

	private static void iupdNuovoStessaCoppia(ContestoCaso contesto) {
		Posizione originale = preparaCancellata(contesto);
		Posizione nuova = contesto.posizione("nuova", contesto.identificativi().iupd(), originale.nav(), originale.iuv(), null);
		ricrea(contesto, nuova, "creazione con IUPD nuovo e stessi NAV e IUV");
	}

	private static void iupdNuovoStessoIuvNavOmesso(ContestoCaso contesto) {
		Posizione originale = preparaCancellata(contesto);
		Posizione nuova = contesto.posizione("nuova", contesto.identificativi().iupd(), null, originale.iuv(), null);
		ricrea(contesto, nuova, "creazione con IUPD nuovo, stesso IUV e NAV omesso");
	}

	private static void elenco(ContestoCaso contesto) {
		Posizione posizione = preparaCancellata(contesto);
		Posizione controllo = contesto.preparaInStato("controllo", "VALID");
		for (String stato : new String[] { null, "VALID" }) {
			String etichetta = stato == null ? "elenco senza filtro di stato" : "elenco con stato " + stato;
			Risposta elenco = contesto.client().elenco(contesto.registrazione(), etichetta, contesto.finestraElenco(stato));
			boolean presente = elenco.corpo() != null && elenco.corpo().contains(posizione.iupd());
			contesto.nota(etichetta + ": HTTP " + elenco.stato() + ", posizione cancellata "
					+ (presente ? "presente" : "assente") + " nella prima pagina di 50 elementi; posizione di controllo "
					+ (elenco.corpo() != null && elenco.corpo().contains(controllo.iupd()) ? "presente" : "assente"));
		}
		contesto.osservato();
	}
}
