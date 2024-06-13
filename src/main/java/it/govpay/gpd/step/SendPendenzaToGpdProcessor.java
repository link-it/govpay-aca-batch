package it.govpay.gpd.step;

import java.time.OffsetDateTime;

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

import it.govpay.gpd.client.api.DebtPositionActionsApiApi;
import it.govpay.gpd.client.api.DebtPositionsApiApi;
import it.govpay.gpd.client.beans.PaymentPositionModel;
import it.govpay.gpd.entity.VersamentoGpdEntity;
import it.govpay.gpd.entity.VersamentoGpdEntity.StatoVersamento;
import it.govpay.gpd.gde.service.GdeService;
import it.govpay.gpd.mapper.PaymentPositionModelRequestMapperImpl;
import it.govpay.gpd.utils.Utils;

@Component
public class SendPendenzaToGpdProcessor implements ItemProcessor<VersamentoGpdEntity, VersamentoGpdEntity>{

	private Logger logger = LoggerFactory.getLogger(SendPendenzaToGpdProcessor.class);

	@Autowired
	PaymentPositionModelRequestMapperImpl paymentPositionModelRequestMapperImpl;

	@Autowired
	@Qualifier("gpdApi")
	DebtPositionsApiApi gpdApi;
	
	@Autowired
	@Qualifier("gpdActionsApi")
	DebtPositionActionsApiApi gpdActionsApi;

	@Autowired
	GdeService gdeService;
	
	private boolean aca;
	private boolean standIn;
	private boolean switchToExpired;

	@Override
	public VersamentoGpdEntity process(VersamentoGpdEntity item) throws Exception {
		logger.info("Gestione della Pendenza [IdA2A:{}, ID:{}] ...", item.getCodApplicazione(), item.getCodVersamentoEnte());
		
		// se la pendenza non ha una data di ultima comunicazione vuol dire che e' stata creata e non ancora inviata
		if(item.getDataUltimaComunicazioneAca() == null) {
			return this.createPosition(item);
		} else {
			StatoVersamento statoVersamento = item.getStatoVersamento();
			
			switch (statoVersamento) {
			case NON_ESEGUITO:
				return this.updatePosition(item);
			case ANNULLATO:
				return this.invalidatePosition(item);
			default:
				logger.info("La Pendenza [IdA2A:{}, ID:{}] e' in uno stato [{}] che non deve essere gestito con un invio al GDP.", item.getCodApplicazione(), item.getCodVersamentoEnte(), statoVersamento);
				return null;
			}
		}
	}
	
	public VersamentoGpdEntity createPosition(VersamentoGpdEntity item) throws Exception {
		logger.info("Caricamento Pendenza [IdA2A:{}, ID:{}] sul GPD...", item.getCodApplicazione(), item.getCodVersamentoEnte());
		ResponseEntity<PaymentPositionModel> responseEntity = null;
		RestClientException ex = null;
		OffsetDateTime dataStart = OffsetDateTime.now();

		// conversione dell'entity in un oggetto da spedire
		PaymentPositionModel paymentPositionModel = this.paymentPositionModelRequestMapperImpl.versamentoGpdToPaymentPositionModel(item, aca, standIn, switchToExpired);
		
		boolean toPublish = true;
		String xRequestId = Utils.creaXRequestId();
		
		String basePath = this.gpdApi.getApiClient().getBasePath();
		try {
			responseEntity = this.gpdApi.createPositionWithHttpInfo(item.getCodDominio(), paymentPositionModel, xRequestId, toPublish);

			logger.info("Caricamento Pendenza [IdA2A:{}, ID:{}] sul GPD completato con esito [{}].", item.getCodApplicazione(), item.getCodVersamentoEnte(), responseEntity.getStatusCode().value());

			if(responseEntity.getStatusCode().is2xxSuccessful()) {
				// salvataggio evento invio ok
				this.gdeService.salvaCreatePositionOk(paymentPositionModel, basePath, xRequestId, toPublish, dataStart, OffsetDateTime.now(), item, responseEntity);
				return item;
			}
			
			// TODO gestione 409
			
		} catch (HttpClientErrorException | HttpServerErrorException e) {
			this.logErrorResponse(e);
			ex = e;
		} catch (ResourceAccessException e) {
			logger.error("GPD non raggiungibile: "+ e.getMessage(), e);
			ex = e;
		}

		// non aggiorno gli elementi non inviati con successo.
		logger.info("Caricamento Pendenza [IdA2A:{}, ID:{}] sul GPD completato con errore.", item.getCodApplicazione(), item.getCodVersamentoEnte());
		this.gdeService.salvaCreatePositionKo(paymentPositionModel, basePath,  xRequestId, toPublish, dataStart, OffsetDateTime.now(), item, responseEntity, ex);
		return null;
	}
	
	public VersamentoGpdEntity updatePosition(VersamentoGpdEntity item) throws Exception {
		logger.info("Aggiornamento Pendenza [IdA2A:{}, ID:{}] sul GPD...", item.getCodApplicazione(), item.getCodVersamentoEnte());
		ResponseEntity<PaymentPositionModel> responseEntity = null;
		RestClientException ex = null;
		OffsetDateTime dataStart = OffsetDateTime.now();

		// conversione dell'entity in un oggetto da spedire
		PaymentPositionModel paymentPositionModel = this.paymentPositionModelRequestMapperImpl.versamentoGpdToPaymentPositionModel(item, aca, standIn, switchToExpired);
		
		boolean toPublish = true;
		String xRequestId = Utils.creaXRequestId();
		
		String basePath = this.gpdApi.getApiClient().getBasePath();
		try {
			responseEntity = this.gpdApi.updatePositionWithHttpInfo(item.getCodDominio(), Utils.generaIupd(item), paymentPositionModel, xRequestId, toPublish);

			logger.info("Aggiornamento Pendenza [IdA2A:{}, ID:{}] sul GPD completato con esito [{}].", item.getCodApplicazione(), item.getCodVersamentoEnte(), responseEntity.getStatusCode().value());

			if(responseEntity.getStatusCode().is2xxSuccessful()) {
				// salvataggio evento invio ok
				this.gdeService.salvaUpdatePositionOk(paymentPositionModel, basePath, xRequestId, toPublish, dataStart, dataStart, item, responseEntity);
				return item;
			}
		} catch (HttpClientErrorException | HttpServerErrorException e) {
			this.logErrorResponse(e);
			ex = e;
		} catch (ResourceAccessException e) {
			logger.error("GPD non raggiungibile: "+ e.getMessage(), e);
			ex = e;
		}

		// non aggiorno gli elementi non inviati con successo.
		logger.info("Aggiornamento Pendenza [IdA2A:{}, ID:{}] sul GPD completato con errore.", item.getCodApplicazione(), item.getCodVersamentoEnte());
		this.gdeService.salvaUpdatePositionKo(paymentPositionModel, basePath,  xRequestId, toPublish, dataStart, OffsetDateTime.now(), item, responseEntity, ex);
		return null;
	}
	
	public VersamentoGpdEntity invalidatePosition(VersamentoGpdEntity item) throws Exception {
		logger.info("Annullamento Pendenza [IdA2A:{}, ID:{}] sul GPD...", item.getCodApplicazione(), item.getCodVersamentoEnte());
		ResponseEntity<PaymentPositionModel> responseEntity = null;
		RestClientException ex = null;
		OffsetDateTime dataStart = OffsetDateTime.now();

		// operazione con POST senza body
		String xRequestId = Utils.creaXRequestId();
		
		String basePath = this.gpdActionsApi.getApiClient().getBasePath();
		try {
			responseEntity = this.gpdActionsApi.invalidatePositionWithHttpInfo(item.getCodDominio(), Utils.generaIupd(item), xRequestId);

			logger.info("Annullamento Pendenza [IdA2A:{}, ID:{}] sul GPD completato con esito [{}].", item.getCodApplicazione(), item.getCodVersamentoEnte(), responseEntity.getStatusCode().value());

			if(responseEntity.getStatusCode().is2xxSuccessful()) {
				// salvataggio evento invio ok
				this.gdeService.salvaInvalidatePositionOk(basePath, xRequestId, dataStart, dataStart, item, responseEntity);
				return item;
			}
		} catch (HttpClientErrorException | HttpServerErrorException e) {
			this.logErrorResponse(e);
			ex = e;
		} catch (ResourceAccessException e) {
			logger.error("GPD non raggiungibile: "+ e.getMessage(), e);
			ex = e;
		}

		// non aggiorno gli elementi non inviati con successo.
		logger.info("Annullamento Pendenza [IdA2A:{}, ID:{}] sul GPD completato con errore.", item.getCodApplicazione(), item.getCodVersamentoEnte());
		this.gdeService.salvaInvalidatePositionKo(basePath, xRequestId, dataStart, dataStart, item, responseEntity, ex);
		return null;
	}
	
	
	

	protected void logErrorResponse(HttpStatusCodeException e) {
		if(e instanceof HttpStatusCodeException) {
			logger.error("Ricevuto server error da GPD: {}", e.getMessage());
		}
		else {
			logger.error("Ricevuto client error da GPD: {}", e.getMessage());
		}
		logger.debug("HTTP Status Code: {}", e.getStatusCode().value());
		logger.debug("Status Text: {}", e.getStatusText());
		logger.debug("HTTP Headers: {}", e.getResponseHeaders());
		logger.debug("Response Body: {}", e.getResponseBodyAsString());	
	}
}
