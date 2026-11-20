package it.govpay.gpd.collaudo;

import java.nio.file.Path;
import java.util.function.Function;

public record ConfigurazioneCollaudo(String baseUrl, String codiceFiscaleEnte, String apiKeyHeader, String apiKey,
		String iban, String codiceSegregazione, String prefisso, int osservazioneIntervalloSecondi,
		int osservazioneDurataSecondi, long pausaMillis, Path cartellaUscita, int scadenzaSecondi,
			int osservazioneLungaSecondi) {

	public static ConfigurazioneCollaudo daAmbiente() {
		return da(System::getenv);
	}

	static ConfigurazioneCollaudo da(Function<String, String> ambiente) {
		String home = System.getProperty("user.home");
		return new ConfigurazioneCollaudo(
				senzaSlashFinale(obbligatoria(ambiente, "GPD_COLLAUDO_BASE_URL")),
				obbligatoria(ambiente, "GPD_COLLAUDO_ENTE"),
				facoltativa(ambiente, "GPD_COLLAUDO_API_KEY_HEADER", "Ocp-Apim-Subscription-Key"),
				obbligatoria(ambiente, "GPD_COLLAUDO_API_KEY"),
				obbligatoria(ambiente, "GPD_COLLAUDO_IBAN"),
				facoltativa(ambiente, "GPD_COLLAUDO_SEGREGAZIONE", "01"),
				facoltativa(ambiente, "GPD_COLLAUDO_PREFISSO", "GVPTEST"),
				Integer.parseInt(facoltativa(ambiente, "GPD_COLLAUDO_OSSERVAZIONE_INTERVALLO_SECONDI", "2")),
				Integer.parseInt(facoltativa(ambiente, "GPD_COLLAUDO_OSSERVAZIONE_DURATA_SECONDI", "30")),
				Long.parseLong(facoltativa(ambiente, "GPD_COLLAUDO_PAUSA_MILLISECONDI", "0")),
				Path.of(facoltativa(ambiente, "GPD_COLLAUDO_USCITA", home + "/gpd-collaudo")),
					Integer.parseInt(facoltativa(ambiente, "GPD_COLLAUDO_SCADENZA_SECONDI", "90")),
					Integer.parseInt(facoltativa(ambiente, "GPD_COLLAUDO_OSSERVAZIONE_LUNGA_SECONDI", "300")));
	}

	private static String obbligatoria(Function<String, String> ambiente, String nome) {
		String valore = ambiente.apply(nome);
		if (valore == null || valore.isBlank()) {
			throw new IllegalStateException("Variabile d'ambiente mancante: " + nome);
		}
		return valore.trim();
	}

	private static String facoltativa(Function<String, String> ambiente, String nome, String predefinito) {
		String valore = ambiente.apply(nome);
		return valore == null || valore.isBlank() ? predefinito : valore.trim();
	}

	private static String senzaSlashFinale(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}
}
