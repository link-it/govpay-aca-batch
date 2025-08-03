package it.govpay.gpd.step;

import java.io.IOException;
import java.nio.charset.Charset;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.gpd.client.api.DebtPositionActionsApiApi;
import it.govpay.gpd.client.api.DebtPositionsApiApi;
import it.govpay.gpd.client.beans.PaymentPositionModel;
import it.govpay.gpd.client.beans.PaymentPositionModelBaseResponse;
import it.govpay.gpd.client.beans.PaymentPositionModelBaseResponse.StatusEnum;
import it.govpay.gpd.entity.VersamentoGpdEntity;
import it.govpay.gpd.entity.VersamentoGpdEntity.StatoVersamento;
import it.govpay.gpd.gde.service.GdeService;
import it.govpay.gpd.mapper.PaymentPositionModelRequestMapperImpl;
import it.govpay.gpd.repository.SingoloVersamentoGpdRepository;
import it.govpay.gpd.utils.Utils;

@Component
public class SendPendenzaToGpdProcessor implements ItemProcessor<VersamentoGpdEntity, VersamentoGpdEntity>{

	
	public static final String AGGIORNAMENTO_PENDENZA_ID_A2A_ID_SUL_GPD_COMPLETATO_CON_ESITO = "Aggiornamento Pendenza [IdA2A:{}, ID:{}] sul GPD completato con esito [{}].";
	public static final String AGGIORNAMENTO_PENDENZA_ID_A2A_ID_SUL_GPD_COMPLETATO_CON_ERRORE = "Aggiornamento Pendenza [IdA2A:{}, ID:{}] sul GPD completato con errore {}.";
	public static final String ANNULLAMENTO_PENDENZA_ID_A2A_ID_SUL_GPD_COMPLETATO_CON_ESITO = "Annullamento Pendenza [IdA2A:{}, ID:{}] sul GPD completato con esito [{}].";
	public static final String ANNULLAMENTO_PENDENZA_ID_A2A_ID_SUL_GPD_COMPLETATO_CON_ERRORE = "Annullamento Pendenza [IdA2A:{}, ID:{}] sul GPD completato con errore {}.";

	private static final String ERROR_MSG_GPD_NON_RAGGIUNGIBILE = "GPD non raggiungibile: {0}";

	private Logger logger = LoggerFactory.getLogger(SendPendenzaToGpdProcessor.class);

	PaymentPositionModelRequestMapperImpl paymentPositionModelRequestMapperImpl;

	DebtPositionsApiApi gpdApi;

	DebtPositionActionsApiApi gpdActionsApi;

	GdeService gdeService;
	
	@Value("${it.govpay.gpd.batch.policy.reinvio.403.enabled:false}")
	private Boolean reinvioErrore403;

	public SendPendenzaToGpdProcessor(PaymentPositionModelRequestMapperImpl paymentPositionModelRequestMapperImpl,
			@Qualifier("gpdApi") DebtPositionsApiApi gpdApi, 
			@Qualifier("gpdActionsApi") DebtPositionActionsApiApi gpdActionsApi, GdeService gdeService, 
			ObjectMapper objectMapper, SingoloVersamentoGpdRepository singoloVersamentoGpdRepository) {
		this.paymentPositionModelRequestMapperImpl = paymentPositionModelRequestMapperImpl;
		this.paymentPositionModelRequestMapperImpl.setObjectMapper(objectMapper);
		this.paymentPositionModelRequestMapperImpl.setSingoloVersamentoGpdRepository(singoloVersamentoGpdRepository);
		this.gpdApi = gpdApi;
		this.gpdActionsApi = gpdActionsApi;
		this.gdeService = gdeService;
	}

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
				return this.cancelPosition(item);
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

		if(!Utils.isValidIuv(item)) {
			logger.warn("IUV non presente per la Pendenza [IdA2A:{}, ID:{}], caricamento non verra' effettuato.", item.getCodApplicazione(), item.getCodVersamentoEnte());
			HttpClientErrorException e = new HttpClientErrorException(HttpStatusCode.valueOf(400), "IUV non presente", new byte[0], Charset.defaultCharset());
			
			this.gdeService.salvaCreatePositionKo(null, xRequestId, dataStart, OffsetDateTime.now(), item, null, e);
			return item;
		}
		
		// pendenza con data scadenza precedente ad ora non vengono spedite
		if(!Utils.isValidDueDate(item)) {
			logger.warn("Due date decorsa per la Pendenza [IdA2A:{}, ID:{}], caricamento non verra' effettuato.", item.getCodApplicazione(), item.getCodVersamentoEnte());
			HttpClientErrorException e = new HttpClientErrorException(HttpStatusCode.valueOf(400), "Due date decorsa", new byte[0], Charset.defaultCharset());
			
			this.gdeService.salvaCreatePositionKo(null, xRequestId, dataStart, OffsetDateTime.now(), item, null, e);
			return item;
		}
		
		PaymentPositionModel paymentPositionModel = null;
		try {
			paymentPositionModel = this.paymentPositionModelRequestMapperImpl.versamentoGpdToPaymentPositionModel(item);
		} catch (IOException e) {
			logger.warn("Errore nella deserializzazione dei metadata della Pendenza [IdA2A:{}, ID:{}], caricamento non verra' effettuato.", item.getCodApplicazione(), item.getCodVersamentoEnte());
			HttpServerErrorException e1 = new HttpServerErrorException(HttpStatusCode.valueOf(502), "Errore nella deserializzazione dei metadata", new byte[0], Charset.defaultCharset());
			this.gdeService.salvaCreatePositionKo(null, xRequestId, dataStart, OffsetDateTime.now(), item, null, e1);
			return item;
		}

		try {
			ResponseEntity<PaymentPositionModel> responseEntity = this.gpdApi.createPositionWithHttpInfo(
					item.getCodDominio(), paymentPositionModel, xRequestId, true);
			HttpStatusCode statusCode = responseEntity.getStatusCode();

			logger.info("Caricamento Pendenza [IdA2A:{}, ID:{}] sul GPD completato con esito [{}].",
					item.getCodApplicazione(), item.getCodVersamentoEnte(), statusCode.value());

			return handleCreatePositionResponse(item, paymentPositionModel, statusCode, xRequestId, dataStart, responseEntity);
		} catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
			return handleCreatePositionException(e, item, paymentPositionModel, xRequestId, dataStart);
		}
	}

	private VersamentoGpdEntity handleCreatePositionResponse(VersamentoGpdEntity item, PaymentPositionModel paymentPositionModel,
			HttpStatusCode statusCode, String xRequestId, OffsetDateTime dataStart, ResponseEntity<PaymentPositionModel> responseEntity) {
		PaymentPositionModelBaseResponse positionModelBaseResponse = null;
		if (statusCode.value() == 201) {
			this.gdeService.salvaCreatePositionOk(paymentPositionModel, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity);
			
			// controllo che sia stata pubblicata
			positionModelBaseResponse = this.getPosition(item);
			
			if (positionModelBaseResponse != null) {
				// pubblica la pendenza se e' in stato DRAFT
				if (positionModelBaseResponse.getStatus() == StatusEnum.DRAFT) {
					return this.publishPosition(item);
				}
				
				// in tutti gli altri casi non si puo' piu' intervenire con la create
				return item;
			}
			
			return item;
		}

		// EC non autorizzato
		if (statusCode.value() == 403) {
			return gestisciErrore403(null, item, paymentPositionModel, xRequestId, dataStart, responseEntity);
		}

		if (statusCode.value() == 409) {
			logger.info("Caricamento Pendenza [IdA2A:{}, ID:{}] sul GPD completato con errore 409 Conflict, la pendenza e' gia' presente nel GPD, scarico dettaglio per verificare lo stato.", item.getCodApplicazione(), item.getCodVersamentoEnte());
			positionModelBaseResponse = this.getPosition(item);

			if (positionModelBaseResponse != null) {
				// salva evento di creazione KO 
				this.gdeService.salvaCreatePositionKo(paymentPositionModel, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, null);

				// pubblica la pendenza se e' in stato DRAFT
				if (positionModelBaseResponse.getStatus() == StatusEnum.DRAFT) {
					return this.publishPosition(item);
				}

				// in tutti gli altri casi non si puo' piu' intervenire con la create
				return item;
			}
		}

		this.gdeService.salvaCreatePositionKo(paymentPositionModel, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, null);
		return null;
	}

	private VersamentoGpdEntity handleCreatePositionException(RestClientException e, VersamentoGpdEntity item, PaymentPositionModel paymentPositionModel, 
			String xRequestId, OffsetDateTime dataStart) {
		ResponseEntity<PaymentPositionModel> responseEntity = null;
		if (e instanceof HttpClientErrorException httpClientError) {
			this.logErrorResponse(httpClientError);

			// EC non autorizzato
			if (httpClientError.getStatusCode().value() == 403) {
				return gestisciErrore403(e, item, paymentPositionModel, xRequestId, dataStart, responseEntity);
			}

			if (httpClientError.getStatusCode().value() == 409) {
				logger.info("Caricamento Pendenza [IdA2A:{}, ID:{}] sul GPD completato con errore 409 Conflict, la pendenza e' gia' presente nel GPD, scarico dettaglio per verificare lo stato.", item.getCodApplicazione(), item.getCodVersamentoEnte());
				PaymentPositionModelBaseResponse positionModelBaseResponse = this.getPosition(item);

				if (positionModelBaseResponse != null) {
					// salva evento di creazione KO 
					this.gdeService.salvaCreatePositionKo(paymentPositionModel, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, e);

					// pubblica la pendenza se e' in stato DRAFT
					if (positionModelBaseResponse.getStatus() == StatusEnum.DRAFT) {
						return this.publishPosition(item);
					}

					// in tutti gli altri casi non si puo' piu' intervenire con la create
					return item;
				}
			}
		} else if (e instanceof HttpServerErrorException httpServerError) {
			this.logErrorResponse(httpServerError);
		} else if (e instanceof ResourceAccessException) {
			String errMessage = MessageFormat.format(ERROR_MSG_GPD_NON_RAGGIUNGIBILE, e.getMessage());
			logger.error(errMessage, e);
		}

		logger.info("Caricamento Pendenza [IdA2A:{}, ID:{}] sul GPD completato con errore.", item.getCodApplicazione(), item.getCodVersamentoEnte());
		this.gdeService.salvaCreatePositionKo(paymentPositionModel, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, e);
		return null;
	}

	private VersamentoGpdEntity gestisciErrore403(RestClientException e, VersamentoGpdEntity item,
			PaymentPositionModel paymentPositionModel, String xRequestId, OffsetDateTime dataStart,
			ResponseEntity<PaymentPositionModel> responseEntity) {
		logger.info("Caricamento Pendenza [IdA2A:{}, ID:{}] sul GPD completato con errore 403 Forbidden, EC non abilitato all'invio delle pendenze sul GPD.", item.getCodApplicazione(), item.getCodVersamentoEnte());

		// salva evento di creazione KO 
		this.gdeService.salvaCreatePositionKo(paymentPositionModel, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, e);

		// ripeto se previsto dalla configurazione
		return Boolean.TRUE.equals(this.reinvioErrore403) ? item : null;
	}

	public VersamentoGpdEntity updatePosition(VersamentoGpdEntity item) {
		logger.info("Aggiornamento Pendenza [IdA2A:{}, ID:{}] sul GPD...", item.getCodApplicazione(), item.getCodVersamentoEnte());
		return updatePositionEngine(item, AGGIORNAMENTO_PENDENZA_ID_A2A_ID_SUL_GPD_COMPLETATO_CON_ESITO, AGGIORNAMENTO_PENDENZA_ID_A2A_ID_SUL_GPD_COMPLETATO_CON_ERRORE, true);
	}
	
	public VersamentoGpdEntity cancelPosition(VersamentoGpdEntity item) {
		logger.info("Annullamento Pendenza [IdA2A:{}, ID:{}] attraverso modifica stato in DRAFT sul GPD...", item.getCodApplicazione(), item.getCodVersamentoEnte());
		return updatePositionEngine(item, ANNULLAMENTO_PENDENZA_ID_A2A_ID_SUL_GPD_COMPLETATO_CON_ESITO, ANNULLAMENTO_PENDENZA_ID_A2A_ID_SUL_GPD_COMPLETATO_CON_ERRORE, false);
	}
	
	public VersamentoGpdEntity updatePositionEngine(VersamentoGpdEntity item, String logMsgOk, String logMsgKo, boolean toPublish) {
		OffsetDateTime dataStart = OffsetDateTime.now();
		String xRequestId = Utils.creaXRequestId();

		if(!Utils.isValidIuv(item)) {
			logger.warn("IUV non presente per la Pendenza [IdA2A:{}, ID:{}], aggiornamento non verra' effettuato.", item.getCodApplicazione(), item.getCodVersamentoEnte());
			HttpClientErrorException e = new HttpClientErrorException(HttpStatusCode.valueOf(400), "IUV non presente", new byte[0], Charset.defaultCharset());
			
			this.gdeService.salvaUpdatePositionKo(null, xRequestId, dataStart, OffsetDateTime.now(), item, null, e);
			return item;
		}
		
		// pendenza con data scadenza precedente ad ora non vengono spedite
		if(!Utils.isValidDueDate(item)) {
			logger.warn("Due date decorsa per la Pendenza [IdA2A:{}, ID:{}], aggiornamento non verra' effettuato.", item.getCodApplicazione(), item.getCodVersamentoEnte());
			HttpClientErrorException e = new HttpClientErrorException(HttpStatusCode.valueOf(400), "Due date decorsa", new byte[0], Charset.defaultCharset());
			
			this.gdeService.salvaUpdatePositionKo(null, xRequestId, dataStart, OffsetDateTime.now(), item, null, e);
			return item;
		}
		
		PaymentPositionModel paymentPositionModel = null;
		try {
			paymentPositionModel = this.paymentPositionModelRequestMapperImpl.versamentoGpdToPaymentPositionModel(item);
		} catch (IOException e) {
			logger.warn("Errore nella deserializzazione dei metadata della Pendenza [IdA2A:{}, ID:{}], aggiornamento non verra' effettuato.", item.getCodApplicazione(), item.getCodVersamentoEnte());
			HttpServerErrorException e1 = new HttpServerErrorException(HttpStatusCode.valueOf(502), "Errore nella deserializzazione dei metadata", new byte[0], Charset.defaultCharset());
			this.gdeService.salvaUpdatePositionKo(null, xRequestId, dataStart, OffsetDateTime.now(), item, null, e1);
			return item;
		}

		try {
			ResponseEntity<PaymentPositionModel> responseEntity = this.gpdApi.updatePositionWithHttpInfo(
					item.getCodDominio(), Utils.generaIupd(item), paymentPositionModel, xRequestId, toPublish);

			logger.info(logMsgOk, item.getCodApplicazione(), item.getCodVersamentoEnte(), responseEntity.getStatusCode().value());

			return handleUpdatePositionResponse(logMsgKo, item, paymentPositionModel, responseEntity.getStatusCode(), xRequestId, dataStart, responseEntity);
		} catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
			return handleUpdatePositionException(logMsgKo, e, item, paymentPositionModel, xRequestId, dataStart);
		}
	}

	private VersamentoGpdEntity handleUpdatePositionResponse(String logMsg, VersamentoGpdEntity item, PaymentPositionModel paymentPositionModel, 
			HttpStatusCode statusCode, String xRequestId,
			OffsetDateTime dataStart, ResponseEntity<PaymentPositionModel> responseEntity) {
		if (statusCode.value() == 200) {
			this.gdeService.salvaUpdatePositionOk(paymentPositionModel, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity);

			logger.info(logMsg, item.getCodApplicazione(), item.getCodVersamentoEnte(), responseEntity.getStatusCode().value());
			return item;
		}

		if (statusCode.value() == 404) {
			logger.info(logMsg, item.getCodApplicazione(), item.getCodVersamentoEnte(), statusCode.value());
			// in questo caso sto provando ad aggiornare una posizione che non esiste
			// non devo piu' spedire il messaggio faccio in modo che venga escluso dal prossimo run del batch
			return item;
		}

		if (statusCode.value() == 409) {
			logger.info(logMsg, item.getCodApplicazione(), item.getCodVersamentoEnte(), statusCode.value());
			// in questo caso sto provando ad aggiornare una posizione gia' chiusa (pagata o meno)
			// non devo piu' spedire il messaggio faccio in modo che venga escluso dal prossimo run del batch
			return item;
		}

		this.gdeService.salvaUpdatePositionKo(paymentPositionModel, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, null);
		return null;
	}
	
	private VersamentoGpdEntity handleUpdatePositionException(String logMsg, RestClientException e, VersamentoGpdEntity item, PaymentPositionModel paymentPositionModel, 
			String xRequestId, OffsetDateTime dataStart) {
		ResponseEntity<PaymentPositionModel> responseEntity = null;
		if (e instanceof HttpClientErrorException httpClientError) {
			this.logErrorResponse(httpClientError);

			if (httpClientError.getStatusCode().value() == 409) {
				logger.info(logMsg, item.getCodApplicazione(), item.getCodVersamentoEnte(), httpClientError.getStatusCode().value());
				// in questo caso sto provando ad aggiornare una posizione gia' chiusa (pagata o meno)
				// non devo piu' spedire il messaggio faccio in modo che venga escluso dal prossimo run del batch
				return item;
			}

			if (httpClientError.getStatusCode().value() == 404) {
				logger.info(logMsg, item.getCodApplicazione(), item.getCodVersamentoEnte(), httpClientError.getStatusCode().value());
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

		logger.error(logMsg, item.getCodApplicazione(), item.getCodVersamentoEnte());
		this.gdeService.salvaUpdatePositionKo(paymentPositionModel, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, e);
		return null;
	}

	public PaymentPositionModelBaseResponse getPosition(VersamentoGpdEntity item) {
		logger.info("Lettura Pendenza [IdA2A:{}, ID:{}] dal GPD...", item.getCodApplicazione(), item.getCodVersamentoEnte());
		ResponseEntity<PaymentPositionModelBaseResponse> responseEntity = null;
		RestClientException ex = null;
		OffsetDateTime dataStart = OffsetDateTime.now();

		String xRequestId = Utils.creaXRequestId();

		try {
			responseEntity = this.gpdApi.getOrganizationDebtPositionByIUPDWithHttpInfo(item.getCodDominio(), Utils.generaIupd(item), xRequestId);

			logger.info("Lettura Pendenza [IdA2A:{}, ID:{}] dal GPD completato con esito [{}].", item.getCodApplicazione(), item.getCodVersamentoEnte(), responseEntity.getStatusCode().value());

			if(responseEntity.getStatusCode().is2xxSuccessful()) {
				PaymentPositionModelBaseResponse paymentPositionModelBaseResponse = responseEntity.getBody();
				StatusEnum status = paymentPositionModelBaseResponse != null ? paymentPositionModelBaseResponse.getStatus() : null;
				logger.info("Pendenza [IdA2A:{}, ID:{}] stato [{}].", item.getCodApplicazione(), item.getCodVersamentoEnte(), status);
				// salvataggio evento invio ok
				this.gdeService.salvaGetPositionOk(xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity);
				return paymentPositionModelBaseResponse;
			}
		} catch (HttpClientErrorException | HttpServerErrorException e) {
			this.logErrorResponse(e);
			ex = e;
		} catch (ResourceAccessException e) {
			logger.error(MessageFormat.format(ERROR_MSG_GPD_NON_RAGGIUNGIBILE, e.getMessage()), e);
			ex = e;
		}

		logger.info("Lettura Pendenza [IdA2A:{}, ID:{}] dal GPD completato con errore.", item.getCodApplicazione(), item.getCodVersamentoEnte());
		this.gdeService.salvaGetPositionKo(xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, ex);
		return null;
	}

	public VersamentoGpdEntity publishPosition(VersamentoGpdEntity item) {
		logger.info("Publish Pendenza [IdA2A:{}, ID:{}] sul GPD...", item.getCodApplicazione(), item.getCodVersamentoEnte());
		ResponseEntity<PaymentPositionModel> responseEntity = null;
		RestClientException ex = null;
		OffsetDateTime dataStart = OffsetDateTime.now();

		// operazione con POST senza body
		String xRequestId = Utils.creaXRequestId();

		try {
			responseEntity = this.gpdActionsApi.publishPositionWithHttpInfo(item.getCodDominio(), Utils.generaIupd(item), xRequestId);

			logger.info("Publish Pendenza [IdA2A:{}, ID:{}] sul GPD completato con esito [{}].", item.getCodApplicazione(), item.getCodVersamentoEnte(), responseEntity.getStatusCode().value());

			if(responseEntity.getStatusCode().is2xxSuccessful()) {
				// salvataggio evento invio ok
				this.gdeService.salvaPublishPositionOk(xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity);
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
		this.gdeService.salvaPublishPositionKo(xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, ex);
		return null;
	}

	protected void logErrorResponse(HttpStatusCodeException e) {
		if(e instanceof HttpServerErrorException) {
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
