package it.govpay.gpd.collaudo;

import java.util.function.Predicate;

public class Osservatore {

	public record Lettura(ClientGpd.Risposta ultima, int tentativi, boolean raggiunta, long secondi) {
	}

	private final ConfigurazioneCollaudo configurazione;
	private final ClientGpd client;

	public Osservatore(ConfigurazioneCollaudo configurazione, ClientGpd client) {
		this.configurazione = configurazione;
		this.client = client;
	}

	/** Legge la posizione almeno una volta e poi a intervalli, fino alla condizione o alla durata massima. */
	public Lettura leggiFinche(Registrazione registrazione, String etichetta, String iupd, Predicate<ClientGpd.Risposta> condizione) {
		long inizio = System.nanoTime();
		long durataMassimaNanos = this.configurazione.osservazioneDurataSecondi() * 1_000_000_000L;
		int tentativi = 0;
		while (true) {
			tentativi++;
			ClientGpd.Risposta risposta = this.client.leggi(registrazione, etichetta + " (lettura " + tentativi + ")", iupd);
			long trascorsi = System.nanoTime() - inizio;
			boolean raggiunta = condizione.test(risposta);
			if (raggiunta || trascorsi >= durataMassimaNanos) {
				return new Lettura(risposta, tentativi, raggiunta, trascorsi / 1_000_000_000L);
			}
			attendi();
		}
	}

	private void attendi() {
		try {
			Thread.sleep(this.configurazione.osservazioneIntervalloSecondi() * 1000L);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Esecuzione interrotta", e);
		}
	}
}
