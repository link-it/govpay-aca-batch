package it.govpay.gpd.client.config;

import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.govpay.gpd.costanti.Costanti;
import it.govpay.gpd.utils.OffsetDateTimeDeserializer;
import it.govpay.gpd.utils.OffsetDateTimeSerializer;



@Configuration
public class WebConfig {

    @Bean
    public ObjectMapper objectMapper() {
        // Crea un ObjectMapper con configurazioni personalizzate
        ObjectMapper objectMapper = new ObjectMapper();
        
        // Imposta il formato delle date
        objectMapper.setDateFormat(new SimpleDateFormat(Costanti.PATTERN_TIMESTAMP_3_YYYY_MM_DD_T_HH_MM_SS_SSSXXX));
        
    	objectMapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
		objectMapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
		objectMapper.enable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID); 
		objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Aggiungi moduli personalizzati se necessario
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(OffsetDateTime.class, new OffsetDateTimeSerializer());
        javaTimeModule.addDeserializer(OffsetDateTime.class, new OffsetDateTimeDeserializer());
		objectMapper.registerModule(javaTimeModule);
        
        return objectMapper;
    }
}
