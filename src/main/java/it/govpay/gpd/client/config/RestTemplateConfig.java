package it.govpay.gpd.client.config;

import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.govpay.gpd.client.SubscriptionKeyInterceptor;
import it.govpay.gpd.client.api.DebtPositionActionsApiApi;
import it.govpay.gpd.client.api.DebtPositionsApiApi;
import it.govpay.gpd.costanti.Costanti;
import it.govpay.gpd.utils.OffsetDateTimeDeserializer;
import it.govpay.gpd.utils.OffsetDateTimeSerializer;
import it.govpay.gpd.utils.Utils;

@Configuration
public class RestTemplateConfig {
	
	@Value("${it.govpay.gpd.batch.client.debugging:false}")
	private boolean debugging;
	
    @Value("${it.govpay.gpd.batch.client.baseUrl}")
    protected String baseUrl;
    
    @Value("${it.govpay.gpd.batch.client.header.subscriptionKey.name}")
    private String subscriptionKeyHeaderName;
	
    @Value("${it.govpay.gpd.batch.client.header.subscriptionKey.value}")
    private String subscriptionKeyHeaderValue;
    
	@Bean
	RestTemplate restTemplate(ObjectMapper objectMapper) {
		RestTemplate restTemplate = new RestTemplate();
		// This allows us to read the response more than once - Necessary for debugging.
		restTemplate.setRequestFactory(new BufferingClientHttpRequestFactory(restTemplate.getRequestFactory()));

		// disable default URL encoding
		DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory();
		uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);
		restTemplate.setUriTemplateHandler(uriBuilderFactory);

//		ObjectMapper objectMapper = createObjectMapper();
		
		MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

		restTemplate.getMessageConverters().add(0,converter);
		
		// aggiungo l'interceptor per la gestione della subscription key
		List<ClientHttpRequestInterceptor> currentInterceptors = restTemplate.getInterceptors();
		ClientHttpRequestInterceptor interceptor = new SubscriptionKeyInterceptor(this.subscriptionKeyHeaderName, this.subscriptionKeyHeaderValue);
		currentInterceptors.add(interceptor);
		restTemplate.setInterceptors(currentInterceptors);
		
		return restTemplate;
	}
	
//	public static ObjectMapper createObjectMapper() {
//        // Crea un'istanza di ObjectMapper
//        ObjectMapper objectMapper = new ObjectMapper();
//        
//        objectMapper.setDateFormat(new SimpleDateFormat(Costanti.PATTERN_TIMESTAMP_3_YYYY_MM_DD_T_HH_MM_SS_SSSXXX));
//
//        // Registra il modulo per la gestione delle date Java 8 (LocalDateTime, OffsetDateTime, etc.)
//        JavaTimeModule javaTimeModule = new JavaTimeModule();
//        javaTimeModule.addSerializer(OffsetDateTime.class, new OffsetDateTimeSerializer());
//        javaTimeModule.addDeserializer(OffsetDateTime.class, new OffsetDateTimeDeserializer());
//        
//		objectMapper.registerModule(javaTimeModule);
//
//		objectMapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
//		objectMapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
//		objectMapper.enable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID); 
//		objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
//
//        return objectMapper;
//    }
	
	@Bean("gpdApi")
	DebtPositionsApiApi gpdApi(RestTemplate restTemplate) {
		it.govpay.gpd.client.api.impl.ApiClient apiClient= new it.govpay.gpd.client.api.impl.ApiClient(restTemplate);
		apiClient.setBasePath(this.baseUrl);
		apiClient.setDebugging(this.debugging);
		return new DebtPositionsApiApi(apiClient);
	}
	
	@Bean("gpdActionsApi")
	DebtPositionActionsApiApi gpdActionsApi(RestTemplate restTemplate) {
		it.govpay.gpd.client.api.impl.ApiClient apiClient= new it.govpay.gpd.client.api.impl.ApiClient(restTemplate);
		apiClient.setBasePath(this.baseUrl);
		apiClient.setDebugging(this.debugging);
		return new DebtPositionActionsApiApi(apiClient);
	}
}
