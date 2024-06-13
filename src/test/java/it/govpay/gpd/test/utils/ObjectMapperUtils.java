package it.govpay.gpd.test.utils;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

import org.apache.commons.lang3.time.DateFormatUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class ObjectMapperUtils {

	public static ObjectMapper createObjectMapper() {
		SimpleDateFormat sdf = new SimpleDateFormat(DateFormatUtils.ISO_8601_EXTENDED_DATE_FORMAT.getPattern());
		sdf.setTimeZone(TimeZone.getTimeZone("Europe/Rome"));
		sdf.setLenient(false);
		
		SimpleModule module = new SimpleModule();
		
		ObjectMapper mapper = JsonMapper.builder().build();
		mapper.registerModule(module);
		mapper.registerModule(new JavaTimeModule());
		mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		mapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
		mapper.enable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID); 
		mapper.setDateFormat(sdf);
		
		return mapper;
	}
}
