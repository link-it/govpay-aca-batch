package it.govpay.gpd.collaudo;

import java.util.List;

import it.govpay.gpd.collaudo.ClientGpd.Risposta;

public final class CasiDuplicati {

	public static final String GRUPPO = "duplicati";

	private CasiDuplicati() {
	}

	public static List<Caso> casi() {
		return List.of(
				Caso.di("DUP_01_STESSO_IUPD_COPPIA_NUOVA", GRUPPO,
						"Posizione presente: stesso IUPD con NAV e IUV nuovi", CasiDuplicati::stessoIupdCoppiaNuova),
				Caso.di("DUP_02_IUPD_NUOVO_STESSA_COPPIA", GRUPPO,
						"Posizione presente: IUPD nuovo con gli stessi NAV e IUV", CasiDuplicati::iupdNuovoStessaCoppia),
				Caso.di("DUP_03_IUPD_NUOVO_STESSO_IUV_NAV_OMESSO", GRUPPO,
						"Posizione presente: IUPD nuovo con lo stesso IUV e NAV omesso", CasiDuplicati::iupdNuovoStessoIuvNavOmesso),
				Caso.di("DUP_04_STESSO_IUPD_STESSA_COPPIA", GRUPPO,
						"Posizione presente: stesso IUPD con gli stessi NAV e IUV", CasiDuplicati::stessoIupdStessaCoppia));
	}

	private static void provaDuplicato(ContestoCaso contesto, Posizione presente, Posizione duplicato, String etichetta) {
		contesto.fase("prova");
		Risposta risposta = contesto.client().crea(contesto.registrazione(), etichetta, duplicato.iupd(), duplicato.corpo(), true);
		contesto.nota("Creazione del duplicato: HTTP " + risposta.stato());
		contesto.fase("verifica");
		Risposta originale = contesto.client().leggi(contesto.registrazione(), "lettura della posizione originale", presente.iupd());
		contesto.nota("Posizione originale dopo il tentativo: HTTP " + originale.stato() + ", stato " + originale.campo("status"));
		contesto.osservato();
	}

	private static void stessoIupdCoppiaNuova(ContestoCaso contesto) {
		Posizione presente = contesto.preparaInStato("originale", "VALID");
		String iuv = contesto.identificativi().iuv();
		Posizione duplicato = contesto.posizione("duplicato", presente.iupd(), contesto.identificativi().nav(iuv), iuv, null);
		provaDuplicato(contesto, presente, duplicato, "creazione con stesso IUPD e NAV e IUV nuovi");
	}

	private static void iupdNuovoStessaCoppia(ContestoCaso contesto) {
		Posizione presente = contesto.preparaInStato("originale", "VALID");
		Posizione duplicato = contesto.posizione("duplicato", contesto.identificativi().iupd(), presente.nav(), presente.iuv(), null);
		provaDuplicato(contesto, presente, duplicato, "creazione con IUPD nuovo e stessi NAV e IUV");
	}

	private static void iupdNuovoStessoIuvNavOmesso(ContestoCaso contesto) {
		Posizione presente = contesto.preparaInStato("originale", "VALID");
		Posizione duplicato = contesto.posizione("duplicato", contesto.identificativi().iupd(), null, presente.iuv(), null);
		provaDuplicato(contesto, presente, duplicato, "creazione con IUPD nuovo, stesso IUV e NAV omesso");
	}

	private static void stessoIupdStessaCoppia(ContestoCaso contesto) {
		Posizione presente = contesto.preparaInStato("originale", "VALID");
		Posizione duplicato = contesto.posizione("duplicato", presente.iupd(), presente.nav(), presente.iuv(), null);
		provaDuplicato(contesto, presente, duplicato, "creazione con stesso IUPD e stessi NAV e IUV");
	}
}
