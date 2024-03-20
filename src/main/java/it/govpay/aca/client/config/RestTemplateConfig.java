package it.govpay.aca.client.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.govpay.aca.client.SubscriptionKeyInterceptor;
import it.govpay.aca.utils.Utils;

@Configuration
public class RestTemplateConfig {

	@Bean
	RestTemplate restTemplate() {
		RestTemplate restTemplate = new RestTemplate();
		// This allows us to read the response more than once - Necessary for debugging.
		restTemplate.setRequestFactory(new BufferingClientHttpRequestFactory(restTemplate.getRequestFactory()));

		// disable default URL encoding
		DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory();
		uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);
		restTemplate.setUriTemplateHandler(uriBuilderFactory);

		ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
				.dateFormat(Utils.newSimpleDateFormat())
				.build();
		objectMapper.registerModule(new JavaTimeModule());
		objectMapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
		objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		objectMapper.setDateFormat (Utils.newSimpleDateFormat());

		MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

		restTemplate.getMessageConverters().add(0,converter);
		
		// aggiungo l'interceptor per la gestione della subscription key
		List<ClientHttpRequestInterceptor> currentInterceptors = restTemplate.getInterceptors();
		ClientHttpRequestInterceptor interceptor = new SubscriptionKeyInterceptor();
		currentInterceptors.add(interceptor);
		restTemplate.setInterceptors(currentInterceptors);
		
		return restTemplate;
	}
}
