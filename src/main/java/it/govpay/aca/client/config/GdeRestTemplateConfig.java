package it.govpay.aca.client.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import it.govpay.aca.client.gde.EventiApi;
import it.govpay.gde.client.api.impl.ApiClient;

@Configuration
public class GdeRestTemplateConfig {

    @Value("${it.govpay.gde.client.baseUrl}")
    protected String baseUrl;

    @Bean("gdeApi")
    EventiApi gdeApi() {
        ApiClient apiClient= new ApiClient();
        apiClient.setBasePath(this.baseUrl);
        return new EventiApi(apiClient);
    }
}