package it.govpay.gpd.collaudo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public class Registrazione {

	private final ObjectMapper mapper;
	private final ObjectNode radice;
	private final ArrayNode passi;
	private final ObjectNode risorse;
	private final ObjectNode statoIniziale;
	private final ArrayNode note;
	private final String id;
	private String fase = "preparazione";

	public Registrazione(ObjectMapper mapper, Caso caso) {
		this.mapper = mapper;
		this.id = caso.id();
		this.radice = mapper.createObjectNode();
		this.radice.put("caso", caso.id());
		this.radice.put("versione", caso.versione());
		this.radice.put("gruppo", caso.gruppo());
		this.radice.put("descrizione", caso.descrizione());
		this.radice.put("avvio", OffsetDateTime.now().toString());
		this.risorse = this.radice.putObject("risorse");
		this.statoIniziale = this.radice.putObject("statoIniziale");
		this.passi = this.radice.putArray("passi");
		this.note = this.radice.putArray("note");
	}

	public void fase(String fase) {
		this.fase = fase;
	}

	/**
	 * {@code true} se almeno una chiamata che contribuisce alla conclusione (fasi di prova e di
	 * verifica) non ha ricevuto risposta (errore di rete). Preparazione e pulizia sono escluse:
	 * un loro errore non dice nulla sul comportamento osservato.
	 */
	public boolean provaSenzaRisposta() {
		for (var passo : this.passi) {
			String fasePasso = passo.path("fase").asString("");
			if (("prova".equals(fasePasso) || "verifica".equals(fasePasso)) && passo.has("erroreDiRete")) {
				return true;
			}
		}
		return false;
	}

	public ObjectNode nuovoPasso(String etichetta) {
		ObjectNode passo = this.passi.addObject();
		passo.put("passo", this.passi.size());
		passo.put("fase", this.fase);
		passo.put("etichetta", etichetta);
		passo.put("ora", OffsetDateTime.now().toString());
		return passo;
	}

	public void risorsa(Posizione posizione) {
		ObjectNode nodo = this.risorse.putObject(posizione.nome());
		nodo.put("iupd", posizione.iupd());
		if (posizione.nav() != null) {
			nodo.put("nav", posizione.nav());
		}
		if (posizione.iuv() != null) {
			nodo.put("iuv", posizione.iuv());
		}
	}

	public void statoIniziale(String nome, String stato) {
		this.statoIniziale.put(nome, stato);
	}

	public void nota(String nota) {
		this.note.add(nota);
	}

	public void esito(Esito esito, String motivo) {
		ObjectNode nodo = this.radice.putObject("esito");
		nodo.put("categoria", esito.name());
		if (motivo != null) {
			nodo.put("motivo", motivo);
		}
	}

	public ObjectNode risorse() {
		return this.risorse;
	}

	public void scrivi(Path cartella) throws IOException {
		Files.createDirectories(cartella);
		this.radice.put("fine", OffsetDateTime.now().toString());
		Files.writeString(cartella.resolve(this.id + ".json"),
				this.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this.radice));
	}
}
