package it.govpay.aca.step;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import it.govpay.aca.client.api.AcaApi;
import it.govpay.aca.client.api.impl.ApiClient;
import it.govpay.aca.client.beans.NewDebtPositionRequest;
import it.govpay.aca.entity.VersamentoAcaEntity;
import it.govpay.aca.mapper.NewDebtPositionRequestMapperImpl;

@Component
public class SendPendenzaToAcaProcessor implements ItemProcessor<VersamentoAcaEntity, VersamentoAcaEntity>{
	
	private Logger logger = LoggerFactory.getLogger(SendPendenzaToAcaProcessor.class);
	
	@Autowired
	NewDebtPositionRequestMapperImpl newDebtPositionRequestMapperImpl;

	@Autowired
	RestTemplate restTemplate;
	
	@Value("${it.govpay.aca.batch.client.debugging:false}")
	private boolean debugging;
	
    @Value("${it.govpay.aca.batch.client.baseUrl}")
    protected String baseUrl;
	
	private AcaApi acaApi;
	
	@PostConstruct
	private void prepareClient() {
		ApiClient apiClient= new ApiClient(this.restTemplate);
		apiClient.setBasePath(this.baseUrl);
		apiClient.setDebugging(this.debugging);
		this.acaApi = new AcaApi(apiClient);
		
	}
	
	@Override
	public VersamentoAcaEntity process(VersamentoAcaEntity item) throws Exception {
		logger.info("Spedizione Pendenza [IdA2A:{}, ID:{}] all'ACA...", item.getCodApplicazione(), item.getCodVersamentoEnte());
		try {
			// conversione dell'entity in un oggetto da spedire
			NewDebtPositionRequest newDebtPositionRequest = this.newDebtPositionRequestMapperImpl.versamentoAcaToNewDebtPositionRequest(item);
			
			this.acaApi.newDebtPosition(newDebtPositionRequest);
			
			logger.info("Spedizione Pendenza [IdA2A:{}, ID:{}] all'ACA completata con successo.", item.getCodApplicazione(), item.getCodVersamentoEnte());
			return item;
		} catch (HttpClientErrorException e) {
			this.logErrorResponse(e);
		} catch (ResourceAccessException e) {
			logger.error("ACA non raggiungibile: {}", e.getMessage());
		}

		// non aggiorno gli elementi non inviati con successo.
		logger.info("Spedizione Pendenza [IdA2A:{}, ID:{}] all'ACA completata con errore.", item.getCodApplicazione(), item.getCodVersamentoEnte());
		return null;
	}

	protected void logErrorResponse(HttpStatusCodeException e) {
		if(e instanceof HttpStatusCodeException) {
			logger.error("Ricevuto server error da ACA: {}", e.getMessage());
		}
		else {
			logger.error("Ricevuto client error da ACA: {}", e.getMessage());
		}
		logger.debug("HTTP Status Code: {}", e.getRawStatusCode());
		logger.debug("Status Text: {}", e.getStatusText());
		logger.debug("HTTP Headers: {}", e.getResponseHeaders());
		logger.debug("Response Body: {}", e.getResponseBodyAsString());	
	}
}
