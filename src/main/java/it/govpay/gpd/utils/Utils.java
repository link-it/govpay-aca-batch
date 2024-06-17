package it.govpay.gpd.utils;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;

import it.govpay.gpd.entity.VersamentoGpdEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Utils {

	private static final String PATTERN_DATA_JSON_YYYY_MM_DD_T_HH_MM_SS_SSS = "yyyy-MM-dd'T'HH:mm:ss.SSS";
	
	private static final DecimalFormat nFormatter = new DecimalFormat("0.00", new DecimalFormatSymbols(Locale.ENGLISH));
	
	public static boolean invocaPublish(OffsetDateTime validityDate, boolean toPublish) {
		return !(toPublish && validityDate == null);
	}
	
	public static String generaIupd(VersamentoGpdEntity versamentoGpdEntity) {
		return versamentoGpdEntity.getCodDominio() + versamentoGpdEntity.getCodApplicazione() + versamentoGpdEntity.getCodVersamentoEnte();
	}
	
	public static String creaXRequestId() {
		return UUID.randomUUID().toString();
	}
	
	public static SimpleDateFormat newSimpleDateFormat(String pattern) {
		SimpleDateFormat sdf = new SimpleDateFormat(pattern);
		sdf.setLenient(false);
		return sdf;
	}
	
	public static SimpleDateFormat newSimpleDateFormat() {
		return newSimpleDateFormat(Utils.PATTERN_DATA_JSON_YYYY_MM_DD_T_HH_MM_SS_SSS);
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
	
	public static LocalDateTime calcolaDueDate(VersamentoGpdEntity versamento) {
		if(versamento.getDataValidita() != null) {
			return versamento.getDataValidita(); // indicates the expiration payment date
		} else if(versamento.getDataScadenza() != null) {
			return versamento.getDataScadenza(); // indicates the expiration payment date
		} else {
			return null; //LocalDateTime.MAX; //31.12.2999
		}   
	}
	
//	public static OffsetDateTime toOffsetDateTime(Date date, String timeZone) {
//		if(date == null) return null;
//		ZoneId zoneId = ZoneId.of(timeZone);
//		return date.toInstant().atZone(zoneId).toOffsetDateTime();
//	}
	
	public static OffsetDateTime toOffsetDateTime(LocalDateTime date, String timeZone) {
		if(date == null) return null;
		ZoneId zoneId = ZoneId.of(timeZone);
		return date.atZone(zoneId).toOffsetDateTime();
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
