package it.govpay.gpd.collaudo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class Identificativi {

	private static final DateTimeFormatter DATA_ORA = DateTimeFormatter.ofPattern("yyMMddHHmmss");

	private final String prefisso;
	private final String codiceSegregazione;
	private final AtomicLong ultimoProgressivo = new AtomicLong();

	public Identificativi(String prefisso, String codiceSegregazione) {
		this.prefisso = prefisso;
		this.codiceSegregazione = codiceSegregazione;
	}

	public String iupd() {
		byte[] casuale = new byte[3];
		ThreadLocalRandom.current().nextBytes(casuale);
		return prefisso + "-" + LocalDateTime.now().format(DATA_ORA) + "-" + HexFormat.of().formatHex(casuale);
	}

	/** IUV di 17 cifre: segregazione, progressivo di 13 cifre e resto della divisione per 93. */
	public String iuv() {
		long progressivo = ultimoProgressivo.updateAndGet(precedente -> Math.max(precedente + 1, System.currentTimeMillis()));
		String base = codiceSegregazione + String.format("%013d", progressivo);
		long controllo = Long.parseLong(base) % 93;
		return base + String.format("%02d", controllo);
	}

	public String nav(String iuv) {
		return "3" + iuv;
	}
}
