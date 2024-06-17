package it.govpay.gpd.step;

import java.text.MessageFormat;
import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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

	private static final String ERROR_MSG_GPD_NON_RAGGIUNGIBILE = "GPD non raggiungibile: {0}";

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
		ResponseEntity<PaymentPositionModel> responseEntity = null;
		RestClientException ex = null;
		OffsetDateTime dataStart = OffsetDateTime.now();

		// conversione dell'entity in un oggetto da spedire
		PaymentPositionModel paymentPositionModel = this.paymentPositionModelRequestMapperImpl.versamentoGpdToPaymentPositionModel(item);

		String xRequestId = Utils.creaXRequestId();

		String basePath = this.gpdApi.getApiClient().getBasePath();
		try {
			responseEntity = this.gpdApi.createPositionWithHttpInfo(item.getCodDominio(), paymentPositionModel, xRequestId, toPublish);

			logger.info("Caricamento Pendenza [IdA2A:{}, ID:{}] sul GPD completato con esito [{}].", item.getCodApplicazione(), item.getCodVersamentoEnte(), responseEntity.getStatusCode().value());

			if(responseEntity.getStatusCode().is2xxSuccessful()) {
				// salvataggio evento invio ok
				this.gdeService.salvaCreatePositionOk(paymentPositionModel, basePath, xRequestId, toPublish, dataStart, OffsetDateTime.now(), item, responseEntity);

				// la posizione deve essere pubblicata. Viene pubblicata automaticamente se toPublish = true e validityDate == null
				if(Utils.invocaPublish(paymentPositionModel.getValidityDate(), this.toPublish)) {
					// se la publish non va a buon fine lascio il versamento come da caricare, nei tentativi successivi otterro' un 409 e la gestione passa al ramo sotto
					return this.publishPosition(item);
				}

				return item;
			}

			// se in fase di creazione viene rilevato un conflitto perche' il versamento e' gia' stato caricato ( dataComunicazioneAca e' null perche' e' fallita la publish)
			// verifico lo stato della posizione sul gpd, e procedo alla pubblicazione se previsto
			if(responseEntity.getStatusCode().equals(HttpStatus.CONFLICT)) {
				// leggo lo stato della pendenza sul GPD
				PaymentPositionModelBaseResponse positionModelBaseResponse = this.getPosition(item);

				if(positionModelBaseResponse != null) {
					StatusEnum status = positionModelBaseResponse.getStatus();

					// la posizione deve essere pubblicata. Viene pubblicata automaticamente se toPublish = true e validityDate == null
					// arrivo qui solo se ho caricato una pendenza in draft ed e' fallita la publish
					if(status != null && status.equals(StatusEnum.DRAFT)) {
						// se la publish non va a buon fine lascio il versamento come da caricare, nei tentativi successivi otterro' un 409 e la gestione passa al ramo sotto
						return this.publishPosition(item);
					}
				}
			}
		} catch (HttpClientErrorException | HttpServerErrorException e) {
			this.logErrorResponse(e);
			ex = e;
		} catch (ResourceAccessException e) {
			logger.error(MessageFormat.format(ERROR_MSG_GPD_NON_RAGGIUNGIBILE, e.getMessage()), e);
			ex = e;
		}

		// non aggiorno gli elementi non inviati con successo.
		logger.info("Caricamento Pendenza [IdA2A:{}, ID:{}] sul GPD completato con errore.", item.getCodApplicazione(), item.getCodVersamentoEnte());
		this.gdeService.salvaCreatePositionKo(paymentPositionModel, basePath,  xRequestId, toPublish, dataStart, OffsetDateTime.now(), item, responseEntity, ex);
		return null;
	}

	public VersamentoGpdEntity updatePosition(VersamentoGpdEntity item) {
		logger.info("Aggiornamento Pendenza [IdA2A:{}, ID:{}] sul GPD...", item.getCodApplicazione(), item.getCodVersamentoEnte());
		ResponseEntity<PaymentPositionModel> responseEntity = null;
		RestClientException ex = null;
		OffsetDateTime dataStart = OffsetDateTime.now();

		// conversione dell'entity in un oggetto da spedire
		PaymentPositionModel paymentPositionModel = this.paymentPositionModelRequestMapperImpl.versamentoGpdToPaymentPositionModel(item);

		String xRequestId = Utils.creaXRequestId();

		String basePath = this.gpdApi.getApiClient().getBasePath();
		try {
			responseEntity = this.gpdApi.updatePositionWithHttpInfo(item.getCodDominio(), Utils.generaIupd(item), paymentPositionModel, xRequestId, toPublish);

			logger.info("Aggiornamento Pendenza [IdA2A:{}, ID:{}] sul GPD completato con esito [{}].", item.getCodApplicazione(), item.getCodVersamentoEnte(), responseEntity.getStatusCode().value());

			if(responseEntity.getStatusCode().is2xxSuccessful()) {
				// salvataggio evento invio ok
				this.gdeService.salvaUpdatePositionOk(paymentPositionModel, basePath, xRequestId, toPublish, dataStart, OffsetDateTime.now(), item, responseEntity);
				
				// la posizione deve essere pubblicata. Viene pubblicata automaticamente se toPublish = true e validityDate == null
				if(Utils.invocaPublish(dataStart, this.toPublish)) {
					// se la publish non va a buon fine lascio il versamento come da aggiornare, nei tentativi successivi verra' riprovato l'update
					return this.publishPosition(item);
				}
				
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
		logger.info("Aggiornamento Pendenza [IdA2A:{}, ID:{}] sul GPD completato con errore.", item.getCodApplicazione(), item.getCodVersamentoEnte());
		this.gdeService.salvaUpdatePositionKo(paymentPositionModel, basePath,  xRequestId, toPublish, dataStart, OffsetDateTime.now(), item, responseEntity, ex);
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
