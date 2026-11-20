package it.govpay.gpd.collaudo;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

public class ClientGpd {

	public record Risposta(int stato, String corpo, JsonNode json) {

		public String campo(String nome) {
			if (this.json == null || !this.json.path(nome).isString()) {
				return null;
			}
			return this.json.path(nome).asString();
		}

		public boolean ok() {
			return this.stato >= 200 && this.stato < 300;
		}
	}

	private static final Set<String> INTESTAZIONI_ESCLUSE = Set.of("set-cookie", "authorization", "proxy-authenticate",
			"www-authenticate", "ocp-apim-subscription-key", "x-api-key", "cookie");

	private final ConfigurazioneCollaudo configurazione;
	private final ObjectMapper mapper;
	private final HttpClient httpClient;
	private final Set<String> creati = new HashSet<>();
	private final Set<String> attivi = new LinkedHashSet<>();
	private final Set<String> incerti = new LinkedHashSet<>();
	private final List<String> eliminati = new ArrayList<>();
	private Set<String> attiviAllInizioCaso = Set.of();

	public ClientGpd(ConfigurazioneCollaudo configurazione, ObjectMapper mapper) {
		this.configurazione = configurazione;
		this.mapper = mapper;
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
	}

	public void iniziaCaso() {
		this.attiviAllInizioCaso = new HashSet<>(this.attivi);
	}

	public Set<String> attiviDelCaso() {
		Set<String> attiviDelCaso = new LinkedHashSet<>(this.attivi);
		attiviDelCaso.removeAll(this.attiviAllInizioCaso);
		return attiviDelCaso;
	}

	public Set<String> attivi() {
		return new LinkedHashSet<>(this.attivi);
	}

	public Set<String> incerti() {
		return new LinkedHashSet<>(this.incerti);
	}

	public List<String> eliminati() {
		return new ArrayList<>(this.eliminati);
	}

	public Risposta crea(Registrazione registrazione, String etichetta, String iupd, ObjectNode corpo, boolean daPubblicare) {
		this.creati.add(iupd);
		Risposta risposta = invia(registrazione, etichetta, "POST",
				this.configurazione.baseUrl() + percorsoBase() + "?toPublish=" + daPubblicare, corpo);
		if (risposta.stato() < 400 || risposta.stato() >= 500) {
			this.attivi.add(iupd);
		}
		if (risposta.stato() == -1) {
			verificaDopoEsitoIncerto(registrazione, iupd, true);
		}
		return risposta;
	}

	public Risposta aggiorna(Registrazione registrazione, String etichetta, String iupd, ObjectNode corpo, boolean daPubblicare) {
		Risposta risposta = invia(registrazione, etichetta, "PUT",
				this.configurazione.baseUrl() + percorsoPosizione(iupd) + "?toPublish=" + daPubblicare, corpo);
		if (risposta.ok()) {
			// un PUT su un IUPD sconosciuto potrebbe creare la posizione: va tracciata per la pulizia
			this.creati.add(iupd);
			this.attivi.add(iupd);
		}
		if (risposta.stato() == -1) {
			this.incerti.add(iupd);
			leggi(registrazione, "verifica dopo esito incerto", iupd);
		}
		return risposta;
	}

	public Risposta leggi(Registrazione registrazione, String etichetta, String iupd) {
		return invia(registrazione, etichetta, "GET", this.configurazione.baseUrl() + percorsoPosizione(iupd), null);
	}

	public Risposta cancella(Registrazione registrazione, String etichetta, String iupd) {
		if (!this.creati.contains(iupd)) {
			throw new IllegalStateException("Cancellazione rifiutata, posizione non creata in questa esecuzione: " + iupd);
		}
		Risposta risposta = invia(registrazione, etichetta, "DELETE", this.configurazione.baseUrl() + percorsoPosizione(iupd), null);
		if (risposta.ok()) {
			this.attivi.remove(iupd);
			this.eliminati.add(iupd);
		} else if (risposta.stato() == -1) {
			verificaDopoEsitoIncerto(registrazione, iupd, false);
		}
		return risposta;
	}

	public Risposta elenco(Registrazione registrazione, String etichetta, Map<String, String> parametri) {
		String query = parametri.entrySet().stream()
				.map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
				.collect(Collectors.joining("&"));
		return invia(registrazione, etichetta, "GET", this.configurazione.baseUrl() + percorsoBase() + (query.isEmpty() ? "" : "?" + query), null);
	}

	private void verificaDopoEsitoIncerto(Registrazione registrazione, String iupd, boolean dopoCreazione) {
		this.incerti.add(iupd);
		Risposta verifica = leggi(registrazione, "verifica dopo esito incerto", iupd);
		if (verifica.stato() == 404) {
			this.attivi.remove(iupd);
			if (!dopoCreazione) {
				this.eliminati.add(iupd);
			}
		}
	}

	private String percorsoBase() {
		return "/organizations/" + this.configurazione.codiceFiscaleEnte() + "/debtpositions";
	}

	private String percorsoPosizione(String iupd) {
		return percorsoBase() + "/" + iupd;
	}

	private Risposta invia(Registrazione registrazione, String etichetta, String metodo, String url, ObjectNode corpo) {
		pausa();
		ObjectNode passo = registrazione.nuovoPasso(etichetta);
		passo.put("metodo", metodo);
		passo.put("url", url);
		String corpoRichiesta = corpo == null ? null : corpo.toString();
		if (corpo != null) {
			passo.set("richiesta", corpo);
		}

		HttpRequest.Builder richiesta = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(60))
				.header(this.configurazione.apiKeyHeader(), this.configurazione.apiKey())
				.header("Accept", "application/json");
		if (corpoRichiesta == null) {
			richiesta.method(metodo, HttpRequest.BodyPublishers.noBody());
		} else {
			richiesta.header("Content-Type", "application/json")
					.method(metodo, HttpRequest.BodyPublishers.ofString(corpoRichiesta, StandardCharsets.UTF_8));
		}

		long inizio = System.nanoTime();
		try {
			HttpResponse<String> risposta = this.httpClient.send(richiesta.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			passo.put("durataMs", (System.nanoTime() - inizio) / 1_000_000);
			passo.put("stato", risposta.statusCode());
			ObjectNode intestazioni = passo.putObject("intestazioni");
			new TreeMap<>(risposta.headers().map()).forEach((nome, valori) -> {
				if (!INTESTAZIONI_ESCLUSE.contains(nome.toLowerCase(Locale.ROOT))) {
					intestazioni.put(nome, String.join(", ", valori));
				}
			});

			String corpoRisposta = risposta.body();
			JsonNode json = interpreta(corpoRisposta);
			if (json != null) {
				passo.set("risposta", json);
			} else if (corpoRisposta != null && !corpoRisposta.isEmpty()) {
				passo.put("rispostaTesto", corpoRisposta);
			}
			return new Risposta(risposta.statusCode(), corpoRisposta, json);
		} catch (IOException e) {
			passo.put("durataMs", (System.nanoTime() - inizio) / 1_000_000);
			passo.put("erroreDiRete", e.toString());
			return new Risposta(-1, null, null);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Esecuzione interrotta", e);
		}
	}

	private void pausa() {
		if (this.configurazione.pausaMillis() > 0) {
			try {
				Thread.sleep(this.configurazione.pausaMillis());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Esecuzione interrotta", e);
			}
		}
	}

	private JsonNode interpreta(String corpo) {
		if (corpo == null || corpo.isBlank()) {
			return null;
		}
		try {
			return this.mapper.readTree(corpo);
		} catch (RuntimeException e) {
			return null;
		}
	}
}
