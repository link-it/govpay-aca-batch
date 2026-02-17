package it.govpay.gpd.client.config;

import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.govpay.gpd.costanti.Costanti;
import it.govpay.gpd.utils.OffsetDateTimeDeserializer;
import it.govpay.gpd.utils.OffsetDateTimeSerializer;

public class RestTemplateConfig {

	private RestTemplateConfig() {
	}

	public static ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        objectMapper.setDateFormat(new SimpleDateFormat(Costanti.PATTERN_DATA_JSON_YYYY_MM_DD_T_HH_MM_SS_SSS));

        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(OffsetDateTime.class, new OffsetDateTimeSerializer(Costanti.PATTERN_DATA_JSON_YYYY_MM_DD_T_HH_MM_SS_SSS));
        javaTimeModule.addDeserializer(OffsetDateTime.class, new OffsetDateTimeDeserializer(Costanti.PATTERN_YYYY_MM_DD_T_HH_MM_SS_MILLIS_VARIABILI));

		objectMapper.registerModule(javaTimeModule);

		objectMapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
		objectMapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
		objectMapper.enable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID);
		objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return objectMapper;
    }
}
