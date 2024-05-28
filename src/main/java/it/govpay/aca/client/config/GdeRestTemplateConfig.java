package it.govpay.aca.client.config;

import java.net.http.HttpClient;
import java.net.http.HttpClient.Builder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.aca.client.gde.EventiApi;
import it.govpay.gde.client.api.impl.ApiClient;

@Configuration
public class GdeRestTemplateConfig {

	@Autowired
	ObjectMapper objectMapper;

	@Value("${it.govpay.gde.client.baseUrl}")
	protected String baseUrl;

	@Bean("gdeApi")
	EventiApi gdeApi() {
		Builder builder = HttpClient.newBuilder();

		ApiClient apiClient= new ApiClient(builder, objectMapper, baseUrl);
		return new EventiApi(apiClient);
	}
}