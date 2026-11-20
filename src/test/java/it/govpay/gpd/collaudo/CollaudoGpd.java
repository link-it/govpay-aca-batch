package it.govpay.gpd.collaudo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public class CollaudoGpd {

	public static final String VERSIONE_CATALOGO = "6";

	private final ConfigurazioneCollaudo configurazione;
	private final ObjectMapper mapper = JsonMapper.builder().build();
	private final ClientGpd client;
	private final Osservatore osservatore;
	private final Identificativi identificativi;
	private final Payloads payloads;
	private final Path cartella;
	private final String idEsecuzione;

	CollaudoGpd(ConfigurazioneCollaudo configurazione) {
		this.configurazione = configurazione;
		this.client = new ClientGpd(configurazione, this.mapper);
		this.osservatore = new Osservatore(configurazione, this.client);
		this.identificativi = new Identificativi(configurazione.prefisso(), configurazione.codiceSegregazione());
		this.payloads = new Payloads(this.mapper, configurazione);
		this.idEsecuzione = OffsetDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		this.cartella = configurazione.cartellaUscita().resolve(this.idEsecuzione);
	}

	public static List<Caso> catalogo() {
		List<Caso> casi = new ArrayList<>();
		casi.addAll(CasiCancellazione.casi());
		casi.addAll(CasiDuplicati.casi());
		casi.addAll(CasiPut.casi());
		casi.addAll(CasiDate.casi());
		casi.addAll(CasiValidazione.casi());
		casi.addAll(CasiRate.casi());
		casi.addAll(CasiTempo.casi());
		return casi;
	}

	public static void main(String[] args) throws IOException {
		List<String> casiRichiesti = new ArrayList<>();
		List<String> gruppiRichiesti = new ArrayList<>();
		boolean soloElenco = false;
		for (String argomento : args) {
			if ("--elenca".equals(argomento)) {
				soloElenco = true;
			} else if (argomento.startsWith("--caso=")) {
				casiRichiesti.addAll(Arrays.asList(argomento.substring("--caso=".length()).split(",")));
			} else if (argomento.startsWith("--gruppo=")) {
				gruppiRichiesti.addAll(Arrays.asList(argomento.substring("--gruppo=".length()).split(",")));
			} else {
				throw new IllegalArgumentException("Argomento sconosciuto: " + argomento
						+ " (ammessi: --elenca, --caso=ID[,ID], --gruppo=NOME[,NOME])");
			}
		}

		List<Caso> catalogo = catalogo();
		if (soloElenco) {
			catalogo.forEach(c -> System.out.println(c.gruppo() + "\t" + c.id() + "\t" + c.descrizione()));
			return;
		}

		List<Caso> selezionati = catalogo.stream()
				.filter(c -> (casiRichiesti.isEmpty() && gruppiRichiesti.isEmpty() && !CasiTempo.GRUPPO.equals(c.gruppo()) && !CasiValidazione.GRUPPO_ORFANI.equals(c.gruppo()))
						|| casiRichiesti.contains(c.id()) || gruppiRichiesti.contains(c.gruppo()))
				.collect(Collectors.toList());
		Set<String> noti = catalogo.stream().flatMap(c -> java.util.stream.Stream.of(c.id(), c.gruppo())).collect(Collectors.toSet());
		for (String richiesto : concat(casiRichiesti, gruppiRichiesti)) {
			if (!noti.contains(richiesto)) {
				throw new IllegalArgumentException("Caso o gruppo sconosciuto: " + richiesto + " (usare --elenca)");
			}
		}

		new CollaudoGpd(ConfigurazioneCollaudo.daAmbiente()).esegui(selezionati);
	}

	private static List<String> concat(List<String> a, List<String> b) {
		List<String> tutti = new ArrayList<>(a);
		tutti.addAll(b);
		return tutti;
	}

	void esegui(List<Caso> casi) throws IOException {
		System.out.println("Registrazioni in " + this.cartella);
		OffsetDateTime avvio = OffsetDateTime.now();
		ArrayNode esitiCasi = this.mapper.createArrayNode();
		Map<Esito, Integer> conteggi = new EnumMap<>(Esito.class);
		ObjectNode risorseDelleEsecuzione = this.mapper.createObjectNode();

		for (Caso caso : casi) {
			Registrazione registrazione = new Registrazione(this.mapper, caso);
			ContestoCaso contesto = new ContestoCaso(this.configurazione, this.client, this.osservatore,
					this.identificativi, this.payloads, registrazione);
			this.client.iniziaCaso();
			try {
				caso.esegui(contesto);
				if (contesto.esito() == null) {
					contesto.osservato();
				}
			} catch (CasoNonEseguibile e) {
				contesto.nonEseguibile(e.getMessage());
			} catch (RuntimeException e) {
				contesto.erroreTecnico(e.toString());
			}
			pulisci(contesto);

			registrazione.esito(contesto.esito(), contesto.motivo());
			registrazione.scrivi(this.cartella);
			conteggi.merge(contesto.esito(), 1, Integer::sum);
			ObjectNode sintesi = esitiCasi.addObject();
			sintesi.put("caso", caso.id());
			sintesi.put("versione", caso.versione());
			sintesi.put("gruppo", caso.gruppo());
			sintesi.put("esito", contesto.esito().name());
			if (contesto.motivo() != null) {
				sintesi.put("motivo", contesto.motivo());
			}
			risorseDelleEsecuzione.set(caso.id(), registrazione.risorse());
			System.out.println(caso.id() + ": " + contesto.esito() + (contesto.motivo() == null ? "" : " - " + contesto.motivo()));
		}

		scriviRiepilogo(avvio, esitiCasi, conteggi, risorseDelleEsecuzione);
	}

	private void pulisci(ContestoCaso contesto) {
		Set<String> daPulire = this.client.attiviDelCaso();
		if (daPulire.isEmpty()) {
			return;
		}
		contesto.fase("pulizia");
		for (String iupd : daPulire) {
			try {
				this.client.cancella(contesto.registrazione(), "cancellazione di pulizia di " + iupd, iupd);
			} catch (RuntimeException e) {
				contesto.nota("Pulizia di " + iupd + " non riuscita: " + e);
			}
		}
	}

	private void scriviRiepilogo(OffsetDateTime avvio, ArrayNode esitiCasi, Map<Esito, Integer> conteggi,
			ObjectNode risorse) throws IOException {
		ObjectNode riepilogo = this.mapper.createObjectNode();
		riepilogo.put("esecuzione", this.idEsecuzione);
		riepilogo.put("versioneCatalogo", VERSIONE_CATALOGO);
		riepilogo.put("revisioneCodice", revisioneCodice());
		riepilogo.put("avvio", avvio.toString());
		riepilogo.put("fine", OffsetDateTime.now().toString());

		ObjectNode ambiente = riepilogo.putObject("ambiente");
		ambiente.put("baseUrl", this.configurazione.baseUrl());
		ambiente.put("ente", this.configurazione.codiceFiscaleEnte());
		ambiente.put("prefisso", this.configurazione.prefisso());
		ambiente.put("codiceSegregazione", this.configurazione.codiceSegregazione());
		ambiente.put("osservazioneIntervalloSecondi", this.configurazione.osservazioneIntervalloSecondi());
		ambiente.put("osservazioneDurataSecondi", this.configurazione.osservazioneDurataSecondi());
		ambiente.put("pausaMillisecondi", this.configurazione.pausaMillis());
		ambiente.put("scadenzaSecondi", this.configurazione.scadenzaSecondi());
		ambiente.put("osservazioneLungaSecondi", this.configurazione.osservazioneLungaSecondi());

		ObjectNode esiti = riepilogo.putObject("conteggiEsiti");
		for (Esito esito : Esito.values()) {
			esiti.put(esito.name(), conteggi.getOrDefault(esito, 0));
		}
		riepilogo.set("casi", esitiCasi);
		riepilogo.set("risorseLogiche", risorse);

		ObjectNode pulizia = riepilogo.putObject("pulizia");
		elenco(pulizia.putArray("posizioniEliminate"), new LinkedHashSet<>(this.client.eliminati()));
		elenco(pulizia.putArray("posizioniResidue"), this.client.attivi());
		elenco(pulizia.putArray("posizioniConEsitoIncerto"), this.client.incerti());

		Files.createDirectories(this.cartella);
		Files.writeString(this.cartella.resolve("riepilogo.json"),
				this.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(riepilogo), StandardCharsets.UTF_8);
	}

	private static void elenco(ArrayNode nodo, Set<String> valori) {
		valori.forEach(nodo::add);
	}

	private static String revisioneCodice() {
		try {
			String revisione = esegui("git", "rev-parse", "--short", "HEAD");
			String modifiche = esegui("git", "status", "--porcelain", "--", "src/test/java/it/govpay/gpd/collaudo");
			return modifiche.isBlank() ? revisione : revisione + "+modifiche-locali";
		} catch (IOException | RuntimeException e) {
			return "n/d";
		}
	}

	private static String esegui(String... comando) throws IOException {
		Process processo = new ProcessBuilder(comando).redirectErrorStream(true).start();
		String uscita = new String(processo.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
		try {
			if (processo.waitFor() != 0) {
				throw new IOException("Comando fallito: " + String.join(" ", comando));
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException(e);
		}
		return uscita;
	}
}
