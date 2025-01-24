package it.govpay.gpd.client;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public class SubscriptionKeyInterceptor implements ClientHttpRequestInterceptor {
	
    private String subscriptionKeyHeaderName;
    private String subscriptionKeyHeaderValue;
    
	public SubscriptionKeyInterceptor(String subscriptionKeyHeaderName, String subscriptionKeyHeaderValue) {
		this.subscriptionKeyHeaderName = subscriptionKeyHeaderName;
		this.subscriptionKeyHeaderValue = subscriptionKeyHeaderValue;
	}

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    	if(this.subscriptionKeyHeaderName != null && this.subscriptionKeyHeaderValue != null ) {
    		HttpHeaders headers = request.getHeaders();
    		headers.add(this.subscriptionKeyHeaderName, this.subscriptionKeyHeaderValue);
    	}

        // Continua con l'esecuzione della richiesta
        return execution.execute(request, body);
    }
}
