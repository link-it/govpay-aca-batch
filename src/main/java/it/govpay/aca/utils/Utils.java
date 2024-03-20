package it.govpay.aca.utils;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import it.govpay.aca.entity.VersamentoAcaEntity;

public class Utils {

	private static final String PATTERN_DATA_JSON_YYYY_MM_DD_T_HH_MM_SS_SSS = "yyyy-MM-dd'T'HH:mm:ss.SSS";
	private static final String PATTERN_DATA_JSON_YYYY_MM_DD_T_HH_MM_SS = "yyyy-MM-dd'T'HH:mm:ss";
	private static final String PATTERN_DATA_JSON_YYYY_MM_DD = "yyyy-MM-dd";
	
	public static SimpleDateFormat newSimpleDateFormatNoMillis() {
		return newSimpleDateFormat(Utils.PATTERN_DATA_JSON_YYYY_MM_DD_T_HH_MM_SS);
	}

	public static SimpleDateFormat newSimpleDateFormatSoloData() {
		return newSimpleDateFormat(Utils.PATTERN_DATA_JSON_YYYY_MM_DD);
	}

	public static SimpleDateFormat newSimpleDateFormat(String pattern) {
		SimpleDateFormat sdf = new SimpleDateFormat(pattern);
		sdf.setLenient(false);
		return sdf;
	}
	
	public static SimpleDateFormat newSimpleDateFormat() {
		return newSimpleDateFormat(Utils.PATTERN_DATA_JSON_YYYY_MM_DD_T_HH_MM_SS_SSS);
	}
	
	private static ObjectMapper mapper;
	static {
		mapper = new ObjectMapper();
		mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		mapper.setDateFormat (Utils.newSimpleDateFormat());
	}
	
	public static String toJSON(Object obj) throws JsonProcessingException {
		return mapper.writeValueAsString(obj);
	}
	
	public static byte[] toJSONBytes(Object obj) throws JsonProcessingException {
		return mapper.writeValueAsBytes(obj);
	}
	
	public static <T> T fromJSONBytes(byte [] msg, Class<T> clazz) throws IOException {
		return mapper.readValue(msg,clazz);
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
	
	public static LocalDateTime toLocalDateTime(Date date) {
		ZoneOffset offset = ZoneOffset.ofHoursMinutes(1, 0); // CET (Central European Time)
		return date.toInstant().atOffset(offset).toLocalDateTime();
	}
}
