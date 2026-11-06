package it.govpay.gpd.utils;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;

import it.govpay.gpd.entity.OpzionePagamentoGpdEntity;
import it.govpay.gpd.entity.PendenzaGpdEntity;
import it.govpay.gpd.entity.PosizioneDebitoriaGpdEntity;
import it.govpay.gpd.entity.SingoloVersamentoGpdEntity;
import it.govpay.gpd.entity.VersamentoGpdEntity;
import it.govpay.pendenze.identificativo.IdentificatorePosizioneDebitoria;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Utils {

	private static final DecimalFormat nFormatter = new DecimalFormat("0.00", new DecimalFormatSymbols(Locale.ENGLISH));
	
	public static boolean invocaPublish(OffsetDateTime validityDate, boolean toPublish) {
		return !(toPublish && validityDate == null);
	}
	
	public static String generaIupd(VersamentoGpdEntity versamentoGpdEntity) {
		return versamentoGpdEntity.getCodDominio() + versamentoGpdEntity.getCodApplicazione() + versamentoGpdEntity.getCodVersamentoEnte();
	}

	// IUPD a grana di posizione debitoria (modello v3): stessa composizione usata da
	// govpay-common-pendenze (IdentificatorePosizioneDebitoria), non la convenzione di
	// generaIupd (a grana di pendenza legacy, ordine diverso).
	public static String generaIupdPosizione(PosizioneDebitoriaGpdEntity posizioneDebitoriaGpdEntity) {
		return IdentificatorePosizioneDebitoria.comporre(posizioneDebitoriaGpdEntity.getCodApplicazione(),
				posizioneDebitoriaGpdEntity.getCodDominio(), posizioneDebitoriaGpdEntity.getCodDocumento());
	}

	public static String creaXRequestId() {
		return UUID.randomUUID().toString();
	}
	
	public static SimpleDateFormat newSimpleDateFormat(String pattern) {
		SimpleDateFormat sdf = new SimpleDateFormat(pattern);
		sdf.setLenient(false);
		return sdf;
	}
	
	public static String printImporto(BigDecimal value, boolean removeDecimalSeparator) {
		DecimalFormatSymbols custom=new DecimalFormatSymbols();
		custom.setDecimalSeparator('.');
		
		DecimalFormat format = new DecimalFormat();
		format.setDecimalFormatSymbols(custom);
		format.setGroupingUsed(false);
		format.setMaximumFractionDigits(2);
		format.setMinimumFractionDigits(2);
		
		String formatValue = format.format(value);
		
		if(removeDecimalSeparator) {
			formatValue = formatValue.replace(".", "");
		}
		
		return formatValue;
	}
	
	public static boolean isValidIuv(VersamentoGpdEntity versamento) {
		return versamento.getIuvVersamento() != null;
	}
	
	public static boolean isValidDueDate(VersamentoGpdEntity versamento) {
		LocalDateTime validityDate = calcolaDueDate(versamento, false, false);
		
		return !validityDate.isBefore(LocalDateTime.now());
	}
	
	public static LocalDateTime calcolaDueDate(VersamentoGpdEntity versamento, boolean allowNull) {
		return calcolaDueDate(versamento, allowNull, true);
	}
	
	public static LocalDateTime calcolaDueDate(VersamentoGpdEntity versamento, boolean allowNull, boolean extendDate) {
		LocalDateTime toReturn = null;
		
		if(versamento.getDataScadenza() != null) {
			toReturn = versamento.getDataScadenza();
		} else if(versamento.getDataValidita() != null) {
			toReturn = versamento.getDataValidita();
		} else {
			if (allowNull) {
				return estendiDueDate(null);
			}
			return LocalDateTime.parse("2999-12-31T23:59:59.999"); //31.12.2999
		}   
		
		if (extendDate) {
			toReturn = estendiDueDate(toReturn);
		}
		
		return toReturn;
	}	
	
	/**
	 * Estende la data di scadenza a fine giornata perche' pagoPA accetta un date time mentre su GovPay e' un date 
	 * 
	 * @param localDateTime
	 * @return
	 */
	public static LocalDateTime estendiDueDate(LocalDateTime localDateTime) {
		if(localDateTime == null) {
            return null;
        }
		
		return localDateTime.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
	}

	public static OffsetDateTime toOffsetDateTime(LocalDateTime date, String timeZone) {
		if(date == null) return null;
		ZoneId zoneId = ZoneId.of(timeZone);
		return date.atZone(zoneId).toOffsetDateTime();
	}

	// Stesso offset usato da PaymentPositionModelRequestMapper#leggiDueDate (CET),
	// qui esposto come utility riusabile anche dal mapper v3.
	public static OffsetDateTime toOffsetDateTimeCet(LocalDateTime localDateTime) {
		if (localDateTime == null) {
			return null;
		}
		ZoneOffset offset = ZoneOffset.ofHoursMinutes(1, 0);
		return OffsetDateTime.of(localDateTime, offset);
	}

	// ==================== Modello v3 (posizione/opzione/rata) ====================

	public static boolean isValidIuv(PendenzaGpdEntity rata) {
		return rata.getIuvVersamento() != null;
	}

	public static boolean isValidDueDate(PendenzaGpdEntity rata, OpzionePagamentoGpdEntity opzione) {
		return !calcolaDueDateRata(rata, opzione).isBefore(LocalDateTime.now());
	}

	/**
	 * Due date di una rata (modello v3): {@code versamenti.data_scadenza}, altrimenti
	 * {@code opzioni_pagamento.data_scadenza}, altrimenti il sentinella 31/12/2999 —
	 * stesso fallback a 3 livelli di {@link #calcolaDueDate(VersamentoGpdEntity, boolean)}
	 * con un livello aggiuntivo (la scadenza dell'opzione), inesistente a grana di
	 * pendenza legacy.
	 */
	public static LocalDateTime calcolaDueDateRata(PendenzaGpdEntity rata, OpzionePagamentoGpdEntity opzione) {
		LocalDateTime toReturn;
		if (rata.getDataScadenza() != null) {
			toReturn = rata.getDataScadenza();
		} else if (opzione.getDataScadenza() != null) {
			toReturn = opzione.getDataScadenza();
		} else {
			return LocalDateTime.parse("2999-12-31T23:59:59.999"); //31.12.2999
		}
		return estendiDueDate(toReturn);
	}

	/**
	 * {@code validityDate} di un'opzione (modello v3): la data piu' recente tra
	 * {@code documenti.data_pubblicazione} e {@code opzioni_pagamento.data_inizio_validita};
	 * {@code null} se entrambe sono nulle (pubblicazione immediata).
	 */
	public static LocalDateTime calcolaValidityDatePosizione(PosizioneDebitoriaGpdEntity posizione, OpzionePagamentoGpdEntity opzione) {
		LocalDateTime pubblicazione = posizione.getDataPubblicazione();
		LocalDateTime inizioValidita = opzione.getDataInizioValidita();
		if (pubblicazione == null) {
			return inizioValidita;
		}
		if (inizioValidita == null) {
			return pubblicazione;
		}
		return pubblicazione.isAfter(inizioValidita) ? pubblicazione : inizioValidita;
	}

	/**
	 * Categoria di un transfer (es. {@code "9/0101108TS/"}): stessa logica di
	 * {@code PaymentPositionModelRequestMapper#getCategory}, estratta qui per essere
	 * riusata anche dal mapper v3 — {@code v_sng_versamenti_gpd} e' invariata tra i due
	 * modelli.
	 */
	public static String getCategory(SingoloVersamentoGpdEntity singoloVersamentoGdpEntity) {
		String tipoContabilita = singoloVersamentoGdpEntity.getTipoContabilita();

		if (tipoContabilita == null) {
			if (singoloVersamentoGdpEntity.getTributoTipoContabilita() != null) {
				tipoContabilita = singoloVersamentoGdpEntity.getTributoTipoContabilita();
			} else {
				tipoContabilita = singoloVersamentoGdpEntity.getTipoTributoTipoContabilita();
			}
		}

		String codContabilita = singoloVersamentoGdpEntity.getCodContabilita();

		if (codContabilita == null) {
			if (singoloVersamentoGdpEntity.getTributoCodContabilita() != null) {
				codContabilita = singoloVersamentoGdpEntity.getTributoCodContabilita();
			} else {
				codContabilita = singoloVersamentoGdpEntity.getTipoTributoCodContabilita();
			}
		}

		return tipoContabilita + "/" + codContabilita;
	}

	/**
	 * Dominio fiscale del transfer, con fallback su quello della posizione/pendenza
	 * contenitrice: stessa logica di
	 * {@code PaymentPositionModelRequestMapper#getCodDominioSingoloVersamento}, estratta
	 * qui con il fallback passato come stringa invece che come l'intera entity
	 * contenitrice, cosi' funziona sia per {@code VersamentoGpdEntity} (legacy)
	 * sia per {@code PosizioneDebitoriaGpdEntity} (v3).
	 */
	public static String getCodDominioSingoloVersamento(SingoloVersamentoGpdEntity singoloVersamentoGdpEntity, String codDominioFallback) {
		if (singoloVersamentoGdpEntity.getCodDominio() != null) {
			return singoloVersamentoGdpEntity.getCodDominio();
		}

		return codDominioFallback;
	}

	public static String formattaCausaleRFS(String iuv) {
		return iuv.replaceAll("(.{4})", "$1 ");
	}
	
	public static String buildCausaleSingoloVersamento(String iuv, Double importoTotale, String descrizione, String descrizioneCausaleRPT) {
		StringBuilder sb = new StringBuilder();
		//Controllo se lo IUV che mi e' stato passato e' ISO11640:2011
		if(IuvUtils.checkISO11640(iuv)) {
			sb.append("/RFS/");
			// Issue #366. Formato causale RFS prevede uno spazio ogni 4 cifre dello IUV
			sb.append(formattaCausaleRFS(iuv).trim());
		}else { 
			sb.append("/RFB/");
			sb.append(iuv);
		}
		
		sb.append("/");
		sb.append(nFormatter.format(BigDecimal.valueOf(importoTotale)));
		if(StringUtils.isNotEmpty(descrizioneCausaleRPT)) {
			sb.append("/TXT/").append(descrizioneCausaleRPT);
		} else {
			if(StringUtils.isNotEmpty(descrizione)) {
				sb.append("/TXT/").append(descrizione);
			}
		}
		
		if(sb.toString().length() > 140)
			return sb.toString().substring(0, 140);
		
		return sb.toString();
	}
}
