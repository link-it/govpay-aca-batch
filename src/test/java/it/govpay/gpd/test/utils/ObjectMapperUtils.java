package it.govpay.gpd.test.utils;

import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.util.TimeZone;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.govpay.gpd.costanti.Costanti;
import it.govpay.gpd.utils.OffsetDateTimeDeserializer;
import it.govpay.gpd.utils.OffsetDateTimeSerializer;


public class ObjectMapperUtils {

	public static ObjectMapper createObjectMapper() {
		SimpleDateFormat sdf = new SimpleDateFormat(Costanti.PATTERN_TIMESTAMP_3_YYYY_MM_DD_T_HH_MM_SS_SSSXXX);
		sdf.setTimeZone(TimeZone.getTimeZone("Europe/Rome"));
		sdf.setLenient(false);
		
		ObjectMapper mapper = JsonMapper.builder().build();
		JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(OffsetDateTime.class, new OffsetDateTimeSerializer());
        javaTimeModule.addDeserializer(OffsetDateTime.class, new OffsetDateTimeDeserializer());
        mapper.registerModule(javaTimeModule);
		mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		mapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
		mapper.enable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID); 
		mapper.setDateFormat(sdf);
		
		return mapper;
	}
}
