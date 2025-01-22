package it.govpay.gpd.step;

import java.text.MessageFormat;
import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
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
import it.govpay.gpd.client.beans.PaymentPositionModelBaseResponse;
import it.govpay.gpd.client.beans.PaymentPositionModelBaseResponse.StatusEnum;
import it.govpay.gpd.entity.VersamentoGpdEntity;
import it.govpay.gpd.entity.VersamentoGpdEntity.StatoVersamento;
import it.govpay.gpd.gde.service.GdeService;
import it.govpay.gpd.mapper.PaymentPositionModelRequestMapperImpl;
import it.govpay.gpd.utils.Utils;

@Component
public class SendPendenzaToGpdProcessor implements ItemProcessor<VersamentoGpdEntity, VersamentoGpdEntity>{

	public static final String AGGIORNAMENTO_PENDENZA_ID_A2A_ID_SUL_GPD_COMPLETATO_CON_ERRORE = "Aggiornamento Pendenza [IdA2A:{}, ID:{}] sul GPD completato con errore.";

	private static final String ERROR_MSG_GPD_NON_RAGGIUNGIBILE = "GPD non raggiungibile: {0}";

	private Logger logger = LoggerFactory.getLogger(SendPendenzaToGpdProcessor.class);

	PaymentPositionModelRequestMapperImpl paymentPositionModelRequestMapperImpl;

	@Qualifier("gpdApi")
	DebtPositionsApiApi gpdApi;

	@Qualifier("gpdActionsApi")
	DebtPositionActionsApiApi gpdActionsApi;

	GdeService gdeService;
	
	public SendPendenzaToGpdProcessor(PaymentPositionModelRequestMapperImpl paymentPositionModelRequestMapperImpl,
			@Qualifier("gpdApi") DebtPositionsApiApi gpdApi, 
			@Qualifier("gpdActionsApi") DebtPositionActionsApiApi gpdActionsApi, GdeService gdeService) {
        this.paymentPositionModelRequestMapperImpl = paymentPositionModelRequestMapperImpl;
        this.gpdApi = gpdApi;
        this.gpdActionsApi = gpdActionsApi;
        this.gdeService = gdeService;
	}

	@Value("${it.govpay.gpd.toPublish.enabled:true}")
	Boolean toPublish;

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

	public VersamentoGpdEntity createPosition(VersamentoGpdEntity item) {
	    logger.info("Caricamento Pendenza [IdA2A:{}, ID:{}] sul GPD...", item.getCodApplicazione(), item.getCodVersamentoEnte());
	    OffsetDateTime dataStart = OffsetDateTime.now();
	    String xRequestId = Utils.creaXRequestId();
	    String basePath = this.gpdApi.getApiClient().getBasePath();

	    PaymentPositionModel paymentPositionModel = this.paymentPositionModelRequestMapperImpl.versamentoGpdToPaymentPositionModel(item);

	    try {
	        ResponseEntity<PaymentPositionModel> responseEntity = this.gpdApi.createPositionWithHttpInfo(
	                item.getCodDominio(), paymentPositionModel, xRequestId, toPublish);
	        HttpStatusCode statusCode = responseEntity.getStatusCode();

	        logger.info("Caricamento Pendenza [IdA2A:{}, ID:{}] sul GPD completato con esito [{}].",
	                item.getCodApplicazione(), item.getCodVersamentoEnte(), statusCode.value());

	        return handleCreatePositionResponse(item, paymentPositionModel, statusCode, xRequestId, basePath, dataStart, responseEntity);
	    } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
	    	return handleCreatePositionException(e, item, paymentPositionModel, xRequestId, basePath, dataStart);
	    }
	}

	private VersamentoGpdEntity handleCreatePositionResponse(VersamentoGpdEntity item, PaymentPositionModel paymentPositionModel,
               HttpStatusCode statusCode, String xRequestId, String basePath, OffsetDateTime dataStart, ResponseEntity<PaymentPositionModel> responseEntity) {
	    if (statusCode.value() == 201) {
	        this.gdeService.salvaCreatePositionOk(paymentPositionModel, basePath, xRequestId, toPublish, dataStart, OffsetDateTime.now(), item, responseEntity);

	        if (Utils.invocaPublish(paymentPositionModel.getValidityDate(), this.toPublish)) {
	            return this.publishPosition(item);
	        }
	        return item;
	    }

	    if (statusCode.value() == 409) {
	        PaymentPositionModelBaseResponse positionModelBaseResponse = this.getPosition(item);
	        if (positionModelBaseResponse != null && positionModelBaseResponse.getStatus() == StatusEnum.DRAFT) {
	            this.gdeService.salvaCreatePositionKo(paymentPositionModel, basePath, xRequestId, toPublish, dataStart, OffsetDateTime.now(), item, responseEntity, null);
	            return this.publishPosition(item);
	        }
	    }

	    this.gdeService.salvaCreatePositionKo(paymentPositionModel, basePath, xRequestId, toPublish, dataStart, OffsetDateTime.now(), item, responseEntity, null);
	    return null;
	}

	private VersamentoGpdEntity handleCreatePositionException(RestClientException e, VersamentoGpdEntity item, PaymentPositionModel paymentPositionModel, 
			String xRequestId, String basePath, OffsetDateTime dataStart) {
	    ResponseEntity<PaymentPositionModel> responseEntity = null;
	    if (e instanceof HttpClientErrorException httpClientError) {
	    	this.logErrorResponse(httpClientError);
	        if (httpClientError.getStatusCode().value() == 409) {
	            PaymentPositionModelBaseResponse positionModelBaseResponse = this.getPosition(item);
	            if (positionModelBaseResponse != null && positionModelBaseResponse.getStatus() == StatusEnum.DRAFT) {
	                this.gdeService.salvaCreatePositionKo(paymentPositionModel, basePath, xRequestId, toPublish, dataStart, OffsetDateTime.now(), item, responseEntity, e);
	                return this.publishPosition(item);
	            }
	        }
	    } else if (e instanceof HttpServerErrorException httpServerError) {
	        this.logErrorResponse(httpServerError);
	    } else if (e instanceof ResourceAccessException) {
	    	String errMessage = MessageFormat.format(ERROR_MSG_GPD_NON_RAGGIUNGIBILE, e.getMessage());
			logger.error(errMessage, e);
	    }

	    logger.info("Caricamento Pendenza [IdA2A:{}, ID:{}] sul GPD completato con errore.", item.getCodApplicazione(), item.getCodVersamentoEnte());
	    this.gdeService.salvaCreatePositionKo(paymentPositionModel, basePath, xRequestId, toPublish, dataStart, OffsetDateTime.now(), item, responseEntity, e);
	    return null;
	}


	public VersamentoGpdEntity updatePosition(VersamentoGpdEntity item) {
	    logger.info("Aggiornamento Pendenza [IdA2A:{}, ID:{}] sul GPD...", item.getCodApplicazione(), item.getCodVersamentoEnte());
	    OffsetDateTime dataStart = OffsetDateTime.now();
	    String xRequestId = Utils.creaXRequestId();
	    String basePath = this.gpdApi.getApiClient().getBasePath();

	    PaymentPositionModel paymentPositionModel = this.paymentPositionModelRequestMapperImpl.versamentoGpdToPaymentPositionModel(item);

	    try {
	        ResponseEntity<PaymentPositionModel> responseEntity = this.gpdApi.updatePositionWithHttpInfo(
	                item.getCodDominio(), Utils.generaIupd(item), paymentPositionModel, xRequestId, toPublish);
	        
	        logger.info("Aggiornamento Pendenza [IdA2A:{}, ID:{}] sul GPD completato con esito [{}].",
	                item.getCodApplicazione(), item.getCodVersamentoEnte(), responseEntity.getStatusCode().value());

	        return handleUpdatePositionResponse(item, paymentPositionModel, responseEntity.getStatusCode(), xRequestId, basePath, dataStart, responseEntity);
	    } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
	        return handleUpdatePositionException(e, item, paymentPositionModel, xRequestId, basePath, dataStart);
	    }
	}

	private VersamentoGpdEntity handleUpdatePositionResponse(VersamentoGpdEntity item, PaymentPositionModel paymentPositionModel,
	                                                 HttpStatusCode statusCode, String xRequestId, String basePath,
	                                                 OffsetDateTime dataStart, ResponseEntity<PaymentPositionModel> responseEntity) {
	    if (statusCode.value() == 200) {
	        this.gdeService.salvaUpdatePositionOk(paymentPositionModel, basePath, xRequestId, toPublish, dataStart, OffsetDateTime.now(), item, responseEntity);

	        if (Utils.invocaPublish(paymentPositionModel.getValidityDate(), this.toPublish)) {
	        	logger.info("Aggiornamento Pendenza [IdA2A:{}, ID:{}] sul GPD completato con esito [{}].", item.getCodApplicazione(), item.getCodVersamentoEnte(), responseEntity.getStatusCode().value());
	            this.gdeService.salvaUpdatePositionKo(paymentPositionModel, basePath, xRequestId, toPublish, dataStart, OffsetDateTime.now(), item, responseEntity, null);
	            return this.publishPosition(item);
	        }
	        return item;
	    }
	    
	    if (statusCode.value() == 404) {
	    	logger.info(AGGIORNAMENTO_PENDENZA_ID_A2A_ID_SUL_GPD_COMPLETATO_CON_ERRORE, item.getCodApplicazione(), item.getCodVersamentoEnte());
	    	// in questo caso sto provando ad aggiornare una posizione che non esiste
	    	// non devo piu' spedire il messaggio faccio in modo che venga escluso dal prossimo run del batch
	    	return item;
	    }
	    
	    if (statusCode.value() == 409) {
	    	logger.info(AGGIORNAMENTO_PENDENZA_ID_A2A_ID_SUL_GPD_COMPLETATO_CON_ERRORE, item.getCodApplicazione(), item.getCodVersamentoEnte());
	    	// in questo caso sto provando ad aggiornare una posizione gia' chiusa (pagata o meno)
	    	// non devo piu' spedire il messaggio faccio in modo che venga escluso dal prossimo run del batch
	    	return item;
	    }

	    this.gdeService.salvaUpdatePositionKo(paymentPositionModel, basePath, xRequestId, toPublish, dataStart, OffsetDateTime.now(), item, responseEntity, null);
	    return null;
	}

	private VersamentoGpdEntity handleUpdatePositionException(RestClientException e, VersamentoGpdEntity item, PaymentPositionModel paymentPositionModel,
	                             String xRequestId, String basePath, OffsetDateTime dataStart) {
	    ResponseEntity<PaymentPositionModel> responseEntity = null;
	    if (e instanceof HttpClientErrorException httpClientError) {
	    	this.logErrorResponse(httpClientError);
	    	
	        if (httpClientError.getStatusCode().value() == 409) {
	        	logger.info(AGGIORNAMENTO_PENDENZA_ID_A2A_ID_SUL_GPD_COMPLETATO_CON_ERRORE, item.getCodApplicazione(), item.getCodVersamentoEnte());
		    	// in questo caso sto provando ad aggiornare una posizione gia' chiusa (pagata o meno)
		    	// non devo piu' spedire il messaggio faccio in modo che venga escluso dal prossimo run del batch
		    	return item;
	        }
	        
	        if (httpClientError.getStatusCode().value() == 404) {
	        	logger.info(AGGIORNAMENTO_PENDENZA_ID_A2A_ID_SUL_GPD_COMPLETATO_CON_ERRORE, item.getCodApplicazione(), item.getCodVersamentoEnte());
		    	// in questo caso sto provando ad aggiornare una posizione che non esiste
		    	// non devo piu' spedire il messaggio faccio in modo che venga escluso dal prossimo run del batch
		    	return item;
	        }
	    } else if (e instanceof HttpServerErrorException httpServerError) {
	        this.logErrorResponse(httpServerError);
	    } else if (e instanceof ResourceAccessException) {
	    	String errMessage = MessageFormat.format(ERROR_MSG_GPD_NON_RAGGIUNGIBILE, e.getMessage());
			logger.error(errMessage, e);
	    }

	    logger.error(AGGIORNAMENTO_PENDENZA_ID_A2A_ID_SUL_GPD_COMPLETATO_CON_ERRORE, item.getCodApplicazione(), item.getCodVersamentoEnte());
	    this.gdeService.salvaUpdatePositionKo(paymentPositionModel, basePath, xRequestId, toPublish, dataStart, OffsetDateTime.now(), item, responseEntity, e);
	    return null;
	}

	public PaymentPositionModelBaseResponse getPosition(VersamentoGpdEntity item) {
		logger.info("Lettura Pendenza [IdA2A:{}, ID:{}] dal GPD...", item.getCodApplicazione(), item.getCodVersamentoEnte());
		ResponseEntity<PaymentPositionModelBaseResponse> responseEntity = null;
		RestClientException ex = null;
		OffsetDateTime dataStart = OffsetDateTime.now();

		String xRequestId = Utils.creaXRequestId();

		String basePath = this.gpdApi.getApiClient().getBasePath();
		try {
			responseEntity = this.gpdApi.getOrganizationDebtPositionByIUPDWithHttpInfo(item.getCodDominio(), Utils.generaIupd(item), xRequestId);

			logger.info("Lettura Pendenza [IdA2A:{}, ID:{}] dal GPD completato con esito [{}].", item.getCodApplicazione(), item.getCodVersamentoEnte(), responseEntity.getStatusCode().value());

			if(responseEntity.getStatusCode().is2xxSuccessful()) {
				// salvataggio evento invio ok
				this.gdeService.salvaGetPositionOk(basePath, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity);
				return responseEntity.getBody();
			}
		} catch (HttpClientErrorException | HttpServerErrorException e) {
			this.logErrorResponse(e);
			ex = e;
		} catch (ResourceAccessException e) {
			logger.error(MessageFormat.format(ERROR_MSG_GPD_NON_RAGGIUNGIBILE, e.getMessage()), e);
			ex = e;
		}

		logger.info("Lettura Pendenza [IdA2A:{}, ID:{}] dal GPD completato con errore.", item.getCodApplicazione(), item.getCodVersamentoEnte());
		this.gdeService.salvaGetPositionKo(basePath, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, ex);
		return null;
	}

	public VersamentoGpdEntity invalidatePosition(VersamentoGpdEntity item) {
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
				this.gdeService.salvaInvalidatePositionOk(basePath, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity);
				return item;
			}
		} catch (HttpClientErrorException | HttpServerErrorException e) {
			this.logErrorResponse(e);
			ex = e;
		} catch (ResourceAccessException e) {
			logger.error(MessageFormat.format(ERROR_MSG_GPD_NON_RAGGIUNGIBILE, e.getMessage()), e);
			ex = e;
		}

		// non aggiorno gli elementi non inviati con successo.
		logger.info("Annullamento Pendenza [IdA2A:{}, ID:{}] sul GPD completato con errore.", item.getCodApplicazione(), item.getCodVersamentoEnte());
		this.gdeService.salvaInvalidatePositionKo(basePath, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, ex);
		return null;
	}

	public VersamentoGpdEntity publishPosition(VersamentoGpdEntity item) {
		logger.info("Publish Pendenza [IdA2A:{}, ID:{}] sul GPD...", item.getCodApplicazione(), item.getCodVersamentoEnte());
		ResponseEntity<PaymentPositionModel> responseEntity = null;
		RestClientException ex = null;
		OffsetDateTime dataStart = OffsetDateTime.now();

		// operazione con POST senza body
		String xRequestId = Utils.creaXRequestId();

		String basePath = this.gpdActionsApi.getApiClient().getBasePath();
		try {
			responseEntity = this.gpdActionsApi.publishPositionWithHttpInfo(item.getCodDominio(), Utils.generaIupd(item), xRequestId);

			logger.info("Publish Pendenza [IdA2A:{}, ID:{}] sul GPD completato con esito [{}].", item.getCodApplicazione(), item.getCodVersamentoEnte(), responseEntity.getStatusCode().value());

			if(responseEntity.getStatusCode().is2xxSuccessful()) {
				// salvataggio evento invio ok
				this.gdeService.salvaPublishPositionOk(basePath, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity);
				return item;
			}
		} catch (HttpClientErrorException | HttpServerErrorException e) {
			this.logErrorResponse(e);
			ex = e;
		} catch (ResourceAccessException e) {
			logger.error(MessageFormat.format(ERROR_MSG_GPD_NON_RAGGIUNGIBILE, e.getMessage()), e);
			ex = e;
		}

		// non aggiorno gli elementi non inviati con successo.
		logger.info("Publish Pendenza [IdA2A:{}, ID:{}] sul GPD completato con errore.", item.getCodApplicazione(), item.getCodVersamentoEnte());
		this.gdeService.salvaPublishPositionKo(basePath, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, ex);
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
