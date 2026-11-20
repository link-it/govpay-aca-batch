package it.govpay.gpd.collaudo;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public class Payloads {

	private static final ZoneId FUSO = ZoneId.of("Europe/Rome");

	private final ObjectMapper mapper;
	private final ConfigurazioneCollaudo configurazione;

	public Payloads(ObjectMapper mapper, ConfigurazioneCollaudo configurazione) {
		this.mapper = mapper;
		this.configurazione = configurazione;
	}

	public static String data(int giorniDaOggi) {
		return LocalDateTime.now(FUSO).plusDays(giorniDaOggi).truncatedTo(ChronoUnit.MILLIS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
	}

	/** Data nel fuso UTC, con cui GPD sembra confrontare le date senza fuso con l'istante corrente. */
	public static String dataUtcTra(long secondi) {
		return LocalDateTime.now(ZoneOffset.UTC).plusSeconds(secondi).truncatedTo(ChronoUnit.MILLIS)
				.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
	}

	public ObjectNode posizione(String iupd, ObjectNode... opzioni) {
		ObjectNode posizione = this.mapper.createObjectNode();
		posizione.put("iupd", iupd);
		posizione.put("payStandIn", false);
		posizione.put("companyName", "Ente di prova");
		posizione.put("officeName", "Ufficio di prova");
		ArrayNode elenco = posizione.putArray("paymentOption");
		for (ObjectNode opzione : opzioni) {
			elenco.add(opzione);
		}
		return posizione;
	}

	/** Opzione con debitore fisso di prova; {@code validityDate} nulla significa pubblicazione immediata. */
	public ObjectNode opzione(String validityDate, boolean switchToExpired, ObjectNode... rate) {
		return opzione(validityDate, switchToExpired, "RSSMRA80A01H501U", "Mario Rossi", rate);
	}

	public ObjectNode opzione(String validityDate, boolean switchToExpired, String codiceFiscaleDebitore,
			String nominativoDebitore, ObjectNode... rate) {
		ObjectNode opzione = this.mapper.createObjectNode();
		opzione.put("description", "Opzione di prova");
		if (validityDate != null) {
			opzione.put("validityDate", validityDate);
		}
		opzione.put("switchToExpired", switchToExpired);
		ObjectNode debitore = opzione.putObject("debtor");
		debitore.put("type", "F");
		debitore.put("fiscalCode", codiceFiscaleDebitore);
		debitore.put("fullName", nominativoDebitore);
		ArrayNode elenco = opzione.putArray("installments");
		for (ObjectNode rata : rate) {
			elenco.add(rata);
		}
		return opzione;
	}

	public ObjectNode rata(String nav, String iuv, long importoCentesimi, String dueDate) {
		ObjectNode rata = this.mapper.createObjectNode();
		if (nav != null) {
			rata.put("nav", nav);
		}
		rata.put("iuv", iuv);
		rata.put("amount", importoCentesimi);
		rata.put("description", "Rata di prova");
		rata.put("dueDate", dueDate);
		ArrayNode trasferimenti = rata.putArray("transfer");
		ObjectNode trasferimento = trasferimenti.addObject();
		trasferimento.put("idTransfer", "1");
		trasferimento.put("amount", importoCentesimi);
		trasferimento.put("organizationFiscalCode", this.configurazione.codiceFiscaleEnte());
		trasferimento.put("remittanceInformation", "Pagamento di prova");
		trasferimento.put("category", "9/0301100TS/");
		trasferimento.put("iban", this.configurazione.iban());
		return rata;
	}
}
