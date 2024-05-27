package it.govpay.aca.utils;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;

import it.govpay.aca.entity.VersamentoAcaEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Utils {

	private static final String PATTERN_DATA_JSON_YYYY_MM_DD_T_HH_MM_SS_SSS = "yyyy-MM-dd'T'HH:mm:ss.SSS";
	
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
	
	public static LocalDateTime calcolaDueDate(VersamentoAcaEntity versamento) {
		if(versamento.getDataValidita() != null) {
			return versamento.getDataValidita(); // indicates the expiration payment date
		} else if(versamento.getDataScadenza() != null) {
			return versamento.getDataScadenza(); // indicates the expiration payment date
		} else {
			return LocalDateTime.MAX; //31.12.2999
		}   
	}
	
	public static OffsetDateTime toOffsetDateTime(Date date, String timeZone) {
		if(date == null) return null;
		ZoneId zoneId = ZoneId.of(timeZone);
		return date.toInstant().atZone(zoneId).toOffsetDateTime();
	}
	
	public static OffsetDateTime toOffsetDateTime(LocalDateTime date, String timeZone) {
		if(date == null) return null;
		ZoneId zoneId = ZoneId.of(timeZone);
		return date.atZone(zoneId).toOffsetDateTime();
	}
}
