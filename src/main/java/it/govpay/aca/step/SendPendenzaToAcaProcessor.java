package it.govpay.aca.step;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import it.govpay.aca.client.api.AcaApi;
import it.govpay.aca.client.beans.NewDebtPositionRequest;
import it.govpay.aca.entity.VersamentoAcaEntity;
import it.govpay.aca.mapper.NewDebtPositionRequestMapperImpl;

@Component
public class SendPendenzaToAcaProcessor implements ItemProcessor<VersamentoAcaEntity, VersamentoAcaEntity>{
	
	private Logger logger = LoggerFactory.getLogger(SendPendenzaToAcaProcessor.class);
	
	@Autowired
	NewDebtPositionRequestMapperImpl newDebtPositionRequestMapperImpl;

    @Autowired
    @Qualifier("acaApi")
	AcaApi acaApi;
	
	@Override
	public VersamentoAcaEntity process(VersamentoAcaEntity item) throws Exception {
		logger.info("Spedizione Pendenza [IdA2A:{}, ID:{}] all'ACA...", item.getCodApplicazione(), item.getCodVersamentoEnte());
		try {
			// conversione dell'entity in un oggetto da spedire
			NewDebtPositionRequest newDebtPositionRequest = this.newDebtPositionRequestMapperImpl.versamentoAcaToNewDebtPositionRequest(item);
			
			ResponseEntity<Void> responseEntity = this.acaApi.newDebtPositionWithHttpInfo(newDebtPositionRequest);
			
			logger.info("Spedizione Pendenza [IdA2A:{}, ID:{}] all'ACA completata con esito [{}].", item.getCodApplicazione(), item.getCodVersamentoEnte(), responseEntity.getStatusCodeValue());
			
			if(responseEntity.getStatusCode().is2xxSuccessful()) {
				return item;
			}
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
