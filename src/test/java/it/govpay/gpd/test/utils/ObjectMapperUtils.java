package it.govpay.gpd.test.utils;

import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.util.TimeZone;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import it.govpay.gpd.costanti.Costanti;
import it.govpay.gpd.utils.OffsetDateTimeDeserializer;
import it.govpay.gpd.utils.OffsetDateTimeSerializer;


public class ObjectMapperUtils {

	public static ObjectMapper createObjectMapper() {
		SimpleDateFormat sdf = new SimpleDateFormat(Costanti.PATTERN_TIMESTAMP_3_YYYY_MM_DD_T_HH_MM_SS_SSSXXX);
		sdf.setTimeZone(TimeZone.getTimeZone("Europe/Rome"));
		sdf.setLenient(false);

		SimpleModule javaTimeModule = new SimpleModule();
		javaTimeModule.addSerializer(OffsetDateTime.class, new OffsetDateTimeSerializer());
		javaTimeModule.addDeserializer(OffsetDateTime.class, new OffsetDateTimeDeserializer());

		return JsonMapper.builder()
				.addModule(javaTimeModule)
				.enable(EnumFeature.READ_ENUMS_USING_TO_STRING)
				.enable(EnumFeature.WRITE_ENUMS_USING_TO_STRING)
				.enable(DateTimeFeature.WRITE_DATES_WITH_ZONE_ID)
				.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
				.defaultDateFormat(sdf)
				.build();
	}
}
