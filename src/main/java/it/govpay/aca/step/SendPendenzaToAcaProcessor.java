package it.govpay.aca.step;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import it.govpay.aca.client.api.AcaApi;
import it.govpay.aca.client.beans.NewDebtPositionRequest;
import it.govpay.aca.entity.VersamentoAcaEntity;
import it.govpay.aca.mapper.NewDebtPositionRequestMapperImpl;
import it.govpay.aca.service.GdeService;

import java.time.OffsetDateTime;

@Component
public class SendPendenzaToAcaProcessor implements ItemProcessor<VersamentoAcaEntity, VersamentoAcaEntity>{

	private Logger logger = LoggerFactory.getLogger(SendPendenzaToAcaProcessor.class);

	@Autowired
	NewDebtPositionRequestMapperImpl newDebtPositionRequestMapperImpl;

	@Autowired
	@Qualifier("acaApi")
	AcaApi acaApi;

	@Autowired
	GdeService gdeService;

	@Override
	public VersamentoAcaEntity process(VersamentoAcaEntity item) throws Exception {
		logger.info("Spedizione Pendenza [IdA2A:{}, ID:{}] all'ACA...", item.getCodApplicazione(), item.getCodVersamentoEnte());
		ResponseEntity<Void> responseEntity = null;
		RestClientException ex = null;
		OffsetDateTime dataStart = OffsetDateTime.now();

		// conversione dell'entity in un oggetto da spedire
		NewDebtPositionRequest newDebtPositionRequest = this.newDebtPositionRequestMapperImpl.versamentoAcaToNewDebtPositionRequest(item);
		
		String basePath = this.acaApi.getApiClient().getBasePath();
		try {
			responseEntity = this.acaApi.newDebtPositionWithHttpInfo(newDebtPositionRequest);

			logger.info("Spedizione Pendenza [IdA2A:{}, ID:{}] all'ACA completata con esito [{}].", item.getCodApplicazione(), item.getCodVersamentoEnte(), responseEntity.getStatusCode().value());

			if(responseEntity.getStatusCode().is2xxSuccessful()) {
				// salvataggio evento invio ok
				this.gdeService.salvaInvioOk(newDebtPositionRequest, basePath, dataStart, OffsetDateTime.now(), item, responseEntity);
				return item;
			}
		} catch (HttpClientErrorException | HttpServerErrorException e) {
			this.logErrorResponse(e);
			ex = e;
		} catch (ResourceAccessException e) {
			logger.error("ACA non raggiungibile: {}", e.getMessage());
			ex = e;
		}

		// non aggiorno gli elementi non inviati con successo.
		logger.info("Spedizione Pendenza [IdA2A:{}, ID:{}] all'ACA completata con errore.", item.getCodApplicazione(), item.getCodVersamentoEnte());
		this.gdeService.salvaInvioKo(newDebtPositionRequest, basePath, dataStart, OffsetDateTime.now(), item, responseEntity, ex);
		return null;
	}

	protected void logErrorResponse(HttpStatusCodeException e) {
		if(e instanceof HttpStatusCodeException) {
			logger.error("Ricevuto server error da ACA: {}", e.getMessage());
		}
		else {
			logger.error("Ricevuto client error da ACA: {}", e.getMessage());
		}
		logger.debug("HTTP Status Code: {}", e.getStatusCode().value());
		logger.debug("Status Text: {}", e.getStatusText());
		logger.debug("HTTP Headers: {}", e.getResponseHeaders());
		logger.debug("Response Body: {}", e.getResponseBodyAsString());	
	}
}
