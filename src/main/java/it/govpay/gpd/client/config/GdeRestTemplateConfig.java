package it.govpay.gpd.client.config;

import java.net.http.HttpClient;
import java.net.http.HttpClient.Builder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.gde.client.api.impl.ApiClient;
import it.govpay.gpd.gde.client.EventiApi;

@Configuration
public class GdeRestTemplateConfig {

	@Value("${it.govpay.gde.client.baseUrl}")
	protected String baseUrl;

	@Bean("gdeApi")
	EventiApi gdeApi(ObjectMapper objectMapper) {
		Builder builder = HttpClient.newBuilder();

		ApiClient apiClient= new ApiClient(builder, objectMapper, baseUrl);
		return new EventiApi(apiClient);
	}
}