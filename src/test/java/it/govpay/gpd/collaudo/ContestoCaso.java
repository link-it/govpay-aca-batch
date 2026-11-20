package it.govpay.gpd.collaudo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import it.govpay.gpd.collaudo.ClientGpd.Risposta;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

public class ContestoCaso {

	private final ConfigurazioneCollaudo configurazione;
	private final ClientGpd client;
	private final Osservatore osservatore;
	private final Identificativi identificativi;
	private final Payloads payloads;
	private final Registrazione registrazione;
	private Esito esito;
	private String motivo;

	public ContestoCaso(ConfigurazioneCollaudo configurazione, ClientGpd client, Osservatore osservatore,
			Identificativi identificativi, Payloads payloads, Registrazione registrazione) {
		this.configurazione = configurazione;
		this.client = client;
		this.osservatore = osservatore;
		this.identificativi = identificativi;
		this.payloads = payloads;
		this.registrazione = registrazione;
	}

	public ClientGpd client() {
		return this.client;
	}

	public Registrazione registrazione() {
		return this.registrazione;
	}

	public Osservatore osservatore() {
		return this.osservatore;
	}

	public Identificativi identificativi() {
		return this.identificativi;
	}

	public void fase(String fase) {
		this.registrazione.fase(fase);
	}

	public void nota(String nota) {
		this.registrazione.nota(nota);
	}

	public void osservato() {
		if (this.registrazione.provaSenzaRisposta()) {
			impostaEsito(Esito.NON_DETERMINABILE, "almeno una chiamata delle fasi di prova o di verifica non ha ricevuto risposta");
			return;
		}
		impostaEsito(Esito.OSSERVATO, null);
	}

	public void nonDeterminabile(String motivo) {
		impostaEsito(Esito.NON_DETERMINABILE, motivo);
	}

	public void nonEseguibile(String motivo) {
		impostaEsito(Esito.NON_ESEGUIBILE, motivo);
	}

	public void erroreTecnico(String motivo) {
		impostaEsito(Esito.ERRORE_TECNICO, motivo);
	}

	public Esito esito() {
		return this.esito;
	}

	public String motivo() {
		return this.motivo;
	}

	private void impostaEsito(Esito esito, String motivo) {
		this.esito = esito;
		this.motivo = motivo;
	}

	public Posizione nuova(String nome, String validityDate) {
		String iupd = this.identificativi.iupd();
		String iuv = this.identificativi.iuv();
		return posizione(nome, iupd, this.identificativi.nav(iuv), iuv, validityDate);
	}

	public Posizione posizione(String nome, String iupd, String nav, String iuv, String validityDate) {
		ObjectNode corpo = this.payloads.posizione(iupd,
				this.payloads.opzione(validityDate, false, this.payloads.rata(nav, iuv, 100, Payloads.data(60))));
		Posizione posizione = new Posizione(nome, iupd, nav, iuv, corpo);
		this.registrazione.risorsa(posizione);
		return posizione;
	}

	/** Crea la posizione e verifica con GET che sia nello stato richiesto; altrimenti il caso non e' eseguibile. */
	public Posizione preparaInStato(String nome, String statoRichiesto) {
		String validityDate = "PUBLISHED".equals(statoRichiesto) ? Payloads.data(30) : null;
		return prepara(nuova(nome, validityDate), statoRichiesto);
	}

	/** Crea la posizione data e verifica con GET lo stato richiesto; altrimenti il caso non e' eseguibile. */
	public Posizione prepara(Posizione posizione, String statoRichiesto) {
		fase("preparazione");
		String nome = posizione.nome();
		boolean daPubblicare = !"DRAFT".equals(statoRichiesto);
		Risposta creazione = this.client.crea(this.registrazione, "creazione richiesta in stato " + statoRichiesto,
				posizione.iupd(), posizione.corpo(), daPubblicare);
		if (!creazione.ok()) {
			throw new CasoNonEseguibile("Creazione di " + nome + " non riuscita: HTTP " + creazione.stato());
		}
		Osservatore.Lettura lettura = this.osservatore.leggiFinche(this.registrazione, "verifica dello stato iniziale",
				posizione.iupd(), r -> r.ok() && statoRichiesto.equals(r.campo("status")));
		String osservato = lettura.ultima().ok() ? lettura.ultima().campo("status") : "HTTP " + lettura.ultima().stato();
		this.registrazione.statoIniziale(nome, osservato);
		if (!lettura.raggiunta()) {
			throw new CasoNonEseguibile("Stato richiesto " + statoRichiesto + " non raggiunto da " + nome + ": osservato " + osservato);
		}
		return posizione;
	}

	/** Cancella una posizione come passo di preparazione; se la cancellazione non riesce il caso non e' eseguibile. */
	public void cancellaComePreparazione(Posizione posizione) {
		fase("preparazione");
		Risposta cancellazione = this.client.cancella(this.registrazione, "cancellazione di " + posizione.nome(), posizione.iupd());
		if (!cancellazione.ok()) {
			throw new CasoNonEseguibile("Cancellazione di " + posizione.nome() + " non riuscita: HTTP " + cancellazione.stato());
		}
		Osservatore.Lettura lettura = this.osservatore.leggiFinche(this.registrazione, "verifica della cancellazione",
				posizione.iupd(), r -> r.stato() == 404);
		if (!lettura.raggiunta()) {
			throw new CasoNonEseguibile("Cancellazione di " + posizione.nome() + " non confermata: ancora HTTP "
					+ lettura.ultima().stato() + " dopo " + lettura.secondi() + " s");
		}
		this.registrazione.nota("Dopo la cancellazione di " + posizione.nome() + ": 404 alla lettura " + lettura.tentativi());
	}

	/** Opzione di prova con una sola rata, registrata con un nome logico. */
	public record OpzioneProva(String nome, String iuv, String nav, ObjectNode corpo) {
	}

	public OpzioneProva opzione(String nome, String iupd, String codiceFiscale, String nominativo, String validityDate) {
		return opzione(nome, iupd, codiceFiscale, nominativo, validityDate, this.identificativi.iuv(), 100);
	}

	public OpzioneProva opzione(String nome, String iupd, String codiceFiscale, String nominativo, String validityDate,
			String iuv, long importoCentesimi) {
		return opzione(nome, iupd, codiceFiscale, nominativo, validityDate, iuv, importoCentesimi, Payloads.data(60));
	}

	public OpzioneProva opzione(String nome, String iupd, String codiceFiscale, String nominativo, String validityDate,
			String iuv, long importoCentesimi, String dueDate) {
		String nav = this.identificativi.nav(iuv);
		ObjectNode corpo = this.payloads.opzione(validityDate, false, codiceFiscale, nominativo,
				this.payloads.rata(nav, iuv, importoCentesimi, dueDate));
		this.registrazione.risorsa(new Posizione(nome, iupd, nav, iuv, null));
		return new OpzioneProva(nome, iuv, nav, corpo);
	}

	/** Opzione con piu' rate, ognuna con il proprio IUV e scadenze a 30, 60, ... giorni. */
	public OpzioneProva opzioneRateale(String nome, String iupd, String codiceFiscale, String nominativo,
			String validityDate, int numeroRate) {
		ObjectNode[] rate = new ObjectNode[numeroRate];
		String primoIuv = null;
		String primoNav = null;
		for (int i = 0; i < numeroRate; i++) {
			String iuv = this.identificativi.iuv();
			String nav = this.identificativi.nav(iuv);
			rate[i] = this.payloads.rata(nav, iuv, 100, Payloads.data(30 * (i + 1)));
			this.registrazione.risorsa(new Posizione(nome + "_rata" + (i + 1), iupd, nav, iuv, null));
			if (i == 0) {
				primoIuv = iuv;
				primoNav = nav;
			}
		}
		return new OpzioneProva(nome, primoIuv, primoNav,
				this.payloads.opzione(validityDate, false, codiceFiscale, nominativo, rate));
	}

	/** Crea la posizione e ne registra lo stato iniziale osservato, senza richiederne uno in particolare. */
	public Posizione creaOsservando(Posizione posizione, boolean daPubblicare) {
		fase("preparazione");
		Risposta creazione = this.client.crea(this.registrazione, "creazione di " + posizione.nome(), posizione.iupd(),
				posizione.corpo(), daPubblicare);
		if (!creazione.ok()) {
			throw new CasoNonEseguibile("Creazione di " + posizione.nome() + " non riuscita: HTTP " + creazione.stato());
		}
		Risposta lettura = this.client.leggi(this.registrazione, "lettura dello stato iniziale di " + posizione.nome(), posizione.iupd());
		this.registrazione.statoIniziale(posizione.nome(), lettura.ok() ? lettura.campo("status") : "HTTP " + lettura.stato());
		return posizione;
	}

	public void attendiFino(Instant istante, String motivo) {
		long millis = istante.toEpochMilli() - System.currentTimeMillis();
		nota("Attesa di " + Math.max(0, millis / 1000) + " s: " + motivo);
		if (millis > 0) {
			try {
				Thread.sleep(millis);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Esecuzione interrotta", e);
			}
		}
	}

	public ObjectNode corpoPosizione(String iupd, OpzioneProva... opzioni) {
		ObjectNode[] corpi = new ObjectNode[opzioni.length];
		for (int i = 0; i < opzioni.length; i++) {
			corpi[i] = opzioni[i].corpo();
		}
		return this.payloads.posizione(iupd, corpi);
	}

	public Posizione posizioneConOpzioni(String nome, String iupd, OpzioneProva... opzioni) {
		Posizione posizione = new Posizione(nome, iupd, null, null, corpoPosizione(iupd, opzioni));
		this.registrazione.risorsa(posizione);
		return posizione;
	}

	/** Sintesi delle opzioni presenti in una lettura: debitore, IUV e importo di ogni rata. */
	public String descriviOpzioni(Risposta lettura) {
		if (!lettura.ok() || lettura.json() == null) {
			return "lettura non riuscita: HTTP " + lettura.stato();
		}
		JsonNode opzioni = lettura.json().path("paymentOption");
		if (!opzioni.isArray()) {
			return "elenco delle opzioni assente nella risposta";
		}
		List<String> parti = new ArrayList<>();
		for (JsonNode opzione : opzioni) {
			String debitore = testo(opzione.path("debtor").path("fiscalCode"));
			for (JsonNode rata : opzione.path("installments")) {
				parti.add("[debitore=" + debitore + " iuv=" + testo(rata.path("iuv")) + " nav=" + testo(rata.path("nav"))
						+ " stato=" + testo(rata.path("status")) + " importo=" + rata.path("amount").asLong(-1)
						+ " validityDate=" + testo(opzione.path("validityDate")) + " dueDate=" + testo(rata.path("dueDate"))
						+ " switchToExpired=" + opzione.path("switchToExpired").asBoolean(false) + "]");
			}
		}
		return opzioni.size() + " opzioni, " + parti.size() + " rate " + String.join(" ", parti);
	}

	private static String testo(JsonNode nodo) {
		return nodo.isString() ? nodo.asString() : null;
	}

	public Map<String, String> finestraElenco(String stato) {
		// GPD limita l'intervallo tra le due date a 30 giorni; le rate di prova scadono tra 60 giorni
		String da = Payloads.data(50).substring(0, 10);
		String a = Payloads.data(70).substring(0, 10);
		return stato == null
				? Map.of("orderby", "INSERTED_DATE", "ordering", "DESC", "limit", "50", "page", "0", "due_date_from", da, "due_date_to", a)
				: Map.of("status", stato, "orderby", "INSERTED_DATE", "ordering", "DESC", "limit", "50", "page", "0", "due_date_from", da, "due_date_to", a);
	}

	public ConfigurazioneCollaudo configurazione() {
		return this.configurazione;
	}
}
