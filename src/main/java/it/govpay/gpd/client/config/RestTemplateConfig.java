package it.govpay.gpd.client.config;

import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;

import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import it.govpay.gpd.costanti.Costanti;
import it.govpay.gpd.utils.OffsetDateTimeDeserializer;
import it.govpay.gpd.utils.OffsetDateTimeSerializer;

public class RestTemplateConfig {

	private RestTemplateConfig() {
	}

	public static JsonMapper createObjectMapper() {
		SimpleModule javaTimeModule = new SimpleModule();
		javaTimeModule.addSerializer(OffsetDateTime.class, new OffsetDateTimeSerializer(Costanti.PATTERN_DATA_JSON_YYYY_MM_DD_T_HH_MM_SS_SSS));
		javaTimeModule.addDeserializer(OffsetDateTime.class, new OffsetDateTimeDeserializer(Costanti.PATTERN_YYYY_MM_DD_T_HH_MM_SS_MILLIS_VARIABILI));

		return JsonMapper.builder()
				.defaultDateFormat(new SimpleDateFormat(Costanti.PATTERN_DATA_JSON_YYYY_MM_DD_T_HH_MM_SS_SSS))
				.addModule(javaTimeModule)
				.enable(EnumFeature.READ_ENUMS_USING_TO_STRING)
				.enable(EnumFeature.WRITE_ENUMS_USING_TO_STRING)
				.enable(DateTimeFeature.WRITE_DATES_WITH_ZONE_ID)
				.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
				.build();
	}
}
