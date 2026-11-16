package it.govpay.gpd.step;

import java.io.IOException;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.ItemProcessor;
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

import tools.jackson.databind.ObjectMapper;

import it.govpay.gpd.client.v3.beans.PaymentPositionModelResponseV3;
import it.govpay.gpd.client.v3.beans.PaymentPositionModelResponseV3.StatusEnum;
import it.govpay.gpd.client.v3.beans.PaymentPositionModelV3;
import it.govpay.gpd.entity.OpzionePagamentoGpdEntity;
import it.govpay.gpd.entity.PendenzaGpdEntity;
import it.govpay.gpd.entity.PosizioneDebitoriaGpdEntity;
import it.govpay.gpd.gde.service.GdeService;
import it.govpay.gpd.mapper.PosizioneDebitoriaGpdMapperImpl;
import it.govpay.gpd.repository.OpzionePagamentoGpdRepository;
import it.govpay.gpd.repository.PendenzaGpdRepository;
import it.govpay.gpd.repository.SingoloVersamentoGpdRepository;
import it.govpay.gpd.service.GpdApiService;
import it.govpay.gpd.utils.Utils;
import it.govpay.pendenze.model.StatoOpzionePagamento;

/**
 * Spedizione ad ACA/GPD v3 delle posizioni debitorie del modello v3 di
 * govpay-common-pendenze - a grana di posizione (una {@code PaymentPositionModelV3} per
 * documento), non di singola pendenza come {@link SendPendenzaToGpdProcessor} (che resta
 * invariato per le pendenze legacy, popolazioni disgiunte per costruzione).
 * <p>Tabella di decisione: un'opzione ATTIVATA blocca qualunque invio (GPD la rende
 * immutabile tramite la ricevuta, qui si marca solo come comunicata); altrimenti,
 * se c'e' almeno un'opzione DISPONIBILE si carica o si aggiorna la posizione; se sono
 * tutte ANNULLATA si annulla (se gia' comunicata) o si marca comunicata senza inviare
 * (se non lo era mai stata).
 * <p>Ogni ramo di elaborazione si classifica in uno dei quattro {@link Esito}, che
 * determina se {@code data_ultima_comunicazione_aca} avanza o no: {@code
 * data_ultima_comunicazione_aca} registra quindi "ultima volta elaborata dal batch",
 * non "GPD conosce questa posizione" - un esito {@code NESSUN_INVIO_NECESSARIO} o
 * {@code SCARTATA_DEFINITIVAMENTE} avanza la marcatura senza che GPD abbia
 * necessariamente ricevuto nulla.
 */
@Component
public class SendPosizioneDebitoriaToGpdProcessor implements ItemProcessor<PosizioneDebitoriaGpdEntity, PosizioneDebitoriaGpdEntity> {

	/**
	 * Esito della elaborazione di una Posizione debitoria. {@code INVIATA}, {@code
	 * NESSUN_INVIO_NECESSARIO} e {@code SCARTATA_DEFINITIVAMENTE} avanzano {@code
	 * data_ultima_comunicazione_aca} (la posizione esce dal prossimo run); {@code
	 * RINVIATA} non la avanza, la posizione resta candidata al prossimo run.
	 */
	private enum Esito {
		/** Operazione remota conclusa con successo (create/update/cancel/publish). */
		INVIATA,
		/** Nessuna operazione remota necessaria per questo esito funzionale (es. opzione ATTIVATA). */
		NESSUN_INVIO_NECESSARIO,
		/** Errore risolvibile solo correggendo i dati della posizione; genera un evento GDE diagnostico. */
		SCARTATA_DEFINITIVAMENTE,
		/** Errore transitorio o risultato remoto incerto: si ritenta al prossimo run. */
		RINVIATA
	}

	private PosizioneDebitoriaGpdEntity esito(Esito esito, PosizioneDebitoriaGpdEntity item) {
		return esito == Esito.RINVIATA ? null : item;
	}

	public static final String AGGIORNAMENTO_POSIZIONE_SUL_GPD_COMPLETATO_CON_ESITO = "Aggiornamento Posizione debitoria [IdA2A:{}, CodDocumento:{}] sul GPD completato con esito [{}].";
	public static final String ANNULLAMENTO_POSIZIONE_SUL_GPD_COMPLETATO_CON_ESITO = "Annullamento Posizione debitoria [IdA2A:{}, CodDocumento:{}] sul GPD completato con esito [{}].";

	private static final String ERROR_MSG_GPD_NON_RAGGIUNGIBILE = "GPD non raggiungibile: {0}";

	private Logger logger = LoggerFactory.getLogger(SendPosizioneDebitoriaToGpdProcessor.class);

	private final PosizioneDebitoriaGpdMapperImpl posizioneDebitoriaGpdMapper;
	private final GpdApiService gpdApiService;
	private final GdeService gdeService;
	private final OpzionePagamentoGpdRepository opzionePagamentoGpdRepository;
	private final PendenzaGpdRepository pendenzaGpdRepository;

	// Stessa policy del 403 della spedizione legacy (SendPendenzaToGpdProcessor): un unico
	// interruttore operativo per "non ritentare mai un EC non abilitato", a prescindere
	// dalla grana dell'invio.
	@Value(SendPendenzaToGpdProcessor.PLACEHOLDER_SCARTA_403)
	private Boolean scartaErrore403;

	public SendPosizioneDebitoriaToGpdProcessor(PosizioneDebitoriaGpdMapperImpl posizioneDebitoriaGpdMapper,
			GpdApiService gpdApiService, GdeService gdeService,
			@Qualifier("gpdObjectMapper") ObjectMapper objectMapper,
			OpzionePagamentoGpdRepository opzionePagamentoGpdRepository,
			PendenzaGpdRepository pendenzaGpdRepository,
			SingoloVersamentoGpdRepository singoloVersamentoGpdRepository) {
		this.posizioneDebitoriaGpdMapper = posizioneDebitoriaGpdMapper;
		this.posizioneDebitoriaGpdMapper.setObjectMapper(objectMapper);
		this.posizioneDebitoriaGpdMapper.setOpzionePagamentoGpdRepository(opzionePagamentoGpdRepository);
		this.posizioneDebitoriaGpdMapper.setPendenzaGpdRepository(pendenzaGpdRepository);
		this.posizioneDebitoriaGpdMapper.setSingoloVersamentoGpdRepository(singoloVersamentoGpdRepository);
		this.gpdApiService = gpdApiService;
		this.gdeService = gdeService;
		this.opzionePagamentoGpdRepository = opzionePagamentoGpdRepository;
		this.pendenzaGpdRepository = pendenzaGpdRepository;
	}

	@Override
	public PosizioneDebitoriaGpdEntity process(PosizioneDebitoriaGpdEntity item) throws Exception {
		logger.info("Gestione della Posizione debitoria [IdA2A:{}, CodDocumento:{}] ...", item.getCodApplicazione(), item.getCodDocumento());

		if (item.getNumOpzioniAttivate() > 0) {
			logger.info("La Posizione debitoria [IdA2A:{}, CodDocumento:{}] ha almeno un'opzione ATTIVATA: nessun invio, solo marcatura come comunicata.",
					item.getCodApplicazione(), item.getCodDocumento());
			return esito(Esito.NESSUN_INVIO_NECESSARIO, item);
		}

		if (item.getNumOpzioniDisponibili() > 0) {
			if (item.getDataUltimaComunicazioneAca() == null) {
				return this.createPosition(item);
			}
			return this.updatePosition(item);
		}

		// tutte ANNULLATA
		if (item.getDataUltimaComunicazioneAca() == null) {
			logger.info("La Posizione debitoria [IdA2A:{}, CodDocumento:{}] ha tutte le opzioni ANNULLATA e non e' mai stata comunicata: nessun invio.",
					item.getCodApplicazione(), item.getCodDocumento());
			return esito(Esito.NESSUN_INVIO_NECESSARIO, item);
		}
		return this.cancelPosition(item);
	}

	/**
	 * Una rata senza IUV blocca l'INTERA posizione, a differenza delle rate scadute
	 * che escludono solo la loro opzione (vedi
	 * {@code PosizioneDebitoriaGpdMapper#mapPaymentOption}).
	 */
	private boolean hasRataSenzaIuv(PosizioneDebitoriaGpdEntity item) {
		List<OpzionePagamentoGpdEntity> opzioniDisponibili = this.opzionePagamentoGpdRepository
				.findByIdDocumentoAndStato(item.getId(), StatoOpzionePagamento.DISPONIBILE);
		for (OpzionePagamentoGpdEntity opzione : opzioniDisponibili) {
			for (PendenzaGpdEntity rata : this.pendenzaGpdRepository.findByIdOpzionePagamentoOrderByNumeroRataAsc(opzione.getId())) {
				if (!Utils.isValidIuv(rata)) {
					return true;
				}
			}
		}
		return false;
	}

	public PosizioneDebitoriaGpdEntity createPosition(PosizioneDebitoriaGpdEntity item) {
		logger.info("Caricamento Posizione debitoria [IdA2A:{}, CodDocumento:{}] sul GPD...", item.getCodApplicazione(), item.getCodDocumento());
		OffsetDateTime dataStart = OffsetDateTime.now();
		String xRequestId = Utils.creaXRequestId();

		if (hasRataSenzaIuv(item)) {
			logger.warn("IUV non presente su almeno una rata della Posizione debitoria [IdA2A:{}, CodDocumento:{}], caricamento non verra' effettuato.",
					item.getCodApplicazione(), item.getCodDocumento());
			HttpClientErrorException e = new HttpClientErrorException(HttpStatusCode.valueOf(400), "IUV non presente", new byte[0], Charset.defaultCharset());
			this.gdeService.salvaCreatePositionKo(null, xRequestId, dataStart, OffsetDateTime.now(), item, null, e);
			return esito(Esito.SCARTATA_DEFINITIVAMENTE, item);
		}

		PaymentPositionModelV3 paymentPositionModel;
		try {
			paymentPositionModel = this.posizioneDebitoriaGpdMapper.posizioneDebitoriaGpdToPaymentPositionModelV3(item);
		} catch (IOException e) {
			logger.warn("Errore nella deserializzazione dei metadata della Posizione debitoria [IdA2A:{}, CodDocumento:{}], caricamento non verra' effettuato.",
					item.getCodApplicazione(), item.getCodDocumento());
			HttpServerErrorException e1 = new HttpServerErrorException(HttpStatusCode.valueOf(502), "Errore nella deserializzazione dei metadata", new byte[0], Charset.defaultCharset());
			this.gdeService.salvaCreatePositionKo(null, xRequestId, dataStart, OffsetDateTime.now(), item, null, e1);
			return esito(Esito.SCARTATA_DEFINITIVAMENTE, item);
		}

		if (paymentPositionModel.getPaymentOption().isEmpty()) {
			logger.info("Nessuna opzione valida da spedire per la Posizione debitoria [IdA2A:{}, CodDocumento:{}] (rate tutte scadute o date incongruenti): nessun invio.",
					item.getCodApplicazione(), item.getCodDocumento());
			HttpClientErrorException e = new HttpClientErrorException(HttpStatusCode.valueOf(400), "Nessuna opzione valida da spedire", new byte[0], Charset.defaultCharset());
			this.gdeService.salvaCreatePositionKo(paymentPositionModel, xRequestId, dataStart, OffsetDateTime.now(), item, null, e);
			return esito(Esito.SCARTATA_DEFINITIVAMENTE, item);
		}

		try {
			ResponseEntity<PaymentPositionModelV3> responseEntity = this.gpdApiService.getGpdApiV3(item.getCodDominio()).createPositionWithHttpInfo(
					item.getCodDominio(), paymentPositionModel, true, xRequestId);
			HttpStatusCode statusCode = responseEntity.getStatusCode();

			logger.info("Caricamento Posizione debitoria [IdA2A:{}, CodDocumento:{}] sul GPD completato con esito [{}].",
					item.getCodApplicazione(), item.getCodDocumento(), statusCode.value());

			return handleCreatePositionResponse(item, paymentPositionModel, statusCode, xRequestId, dataStart, responseEntity);
		} catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
			return handleCreatePositionException(e, item, paymentPositionModel, xRequestId, dataStart);
		}
	}

	private PosizioneDebitoriaGpdEntity handleCreatePositionResponse(PosizioneDebitoriaGpdEntity item, PaymentPositionModelV3 paymentPositionModel,
			HttpStatusCode statusCode, String xRequestId, OffsetDateTime dataStart, ResponseEntity<PaymentPositionModelV3> responseEntity) {
		PaymentPositionModelResponseV3 positionModelResponse;
		if (statusCode.value() == 201) {
			this.gdeService.salvaCreatePositionOk(paymentPositionModel, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity);

			positionModelResponse = this.getPosition(item);

			if (positionModelResponse != null) {
				if (positionModelResponse.getStatus() == StatusEnum.DRAFT) {
					return this.publishPosition(item);
				}
				return esito(Esito.INVIATA, item);
			}

			// la creazione e' confermata (201), ma la verifica dello stato e' fallita: non
			// si puo' escludere che la posizione sia rimasta DRAFT e necessiti ancora di
			// una publish, si ritenta al prossimo run (che la trovera' in conflitto, 409,
			// e ripetera' la verifica).
			return esito(Esito.RINVIATA, item);
		}

		if (statusCode.value() == 403) {
			return gestisciErrore403(null, item, paymentPositionModel, xRequestId, dataStart, responseEntity);
		}

		if (statusCode.value() == 409) {
			logger.info("Caricamento Posizione debitoria [IdA2A:{}, CodDocumento:{}] sul GPD completato con errore 409 Conflict, la posizione e' gia' presente nel GPD, scarico dettaglio per verificare lo stato.",
					item.getCodApplicazione(), item.getCodDocumento());
			positionModelResponse = this.getPosition(item);

			if (positionModelResponse != null) {
				this.gdeService.salvaCreatePositionKo(paymentPositionModel, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, null);

				if (positionModelResponse.getStatus() == StatusEnum.DRAFT) {
					return this.publishPosition(item);
				}
				return esito(Esito.INVIATA, item);
			}
		}

		this.gdeService.salvaCreatePositionKo(paymentPositionModel, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, null);
		return esito(Esito.RINVIATA, item);
	}

	private PosizioneDebitoriaGpdEntity handleCreatePositionException(RestClientException e, PosizioneDebitoriaGpdEntity item, PaymentPositionModelV3 paymentPositionModel,
			String xRequestId, OffsetDateTime dataStart) {
		ResponseEntity<PaymentPositionModelV3> responseEntity = null;
		if (e instanceof HttpClientErrorException httpClientError) {
			this.logErrorResponse(httpClientError);

			if (httpClientError.getStatusCode().value() == 403) {
				return gestisciErrore403(e, item, paymentPositionModel, xRequestId, dataStart, responseEntity);
			}

			if (httpClientError.getStatusCode().value() == 409) {
				logger.info("Caricamento Posizione debitoria [IdA2A:{}, CodDocumento:{}] sul GPD completato con errore 409 Conflict, la posizione e' gia' presente nel GPD, scarico dettaglio per verificare lo stato.",
						item.getCodApplicazione(), item.getCodDocumento());
				PaymentPositionModelResponseV3 positionModelResponse = this.getPosition(item);

				if (positionModelResponse != null) {
					this.gdeService.salvaCreatePositionKo(paymentPositionModel, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, e);

					if (positionModelResponse.getStatus() == StatusEnum.DRAFT) {
						return this.publishPosition(item);
					}
					return esito(Esito.INVIATA, item);
				}
			}
		} else if (e instanceof HttpServerErrorException httpServerError) {
			this.logErrorResponse(httpServerError);
		} else if (e instanceof ResourceAccessException) {
			logger.error(MessageFormat.format(ERROR_MSG_GPD_NON_RAGGIUNGIBILE, e.getMessage()), e);
		}

		logger.info("Caricamento Posizione debitoria [IdA2A:{}, CodDocumento:{}] sul GPD completato con errore.", item.getCodApplicazione(), item.getCodDocumento());
		this.gdeService.salvaCreatePositionKo(paymentPositionModel, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, e);
		return esito(Esito.RINVIATA, item);
	}

	private PosizioneDebitoriaGpdEntity gestisciErrore403(RestClientException e, PosizioneDebitoriaGpdEntity item,
			PaymentPositionModelV3 paymentPositionModel, String xRequestId, OffsetDateTime dataStart,
			ResponseEntity<PaymentPositionModelV3> responseEntity) {
		logger.info("Caricamento Posizione debitoria [IdA2A:{}, CodDocumento:{}] sul GPD completato con errore 403 Forbidden, EC non abilitato all'invio sul GPD.",
				item.getCodApplicazione(), item.getCodDocumento());

		this.gdeService.salvaCreatePositionKo(paymentPositionModel, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, e);

		return Boolean.TRUE.equals(this.scartaErrore403) ? esito(Esito.SCARTATA_DEFINITIVAMENTE, item) : esito(Esito.RINVIATA, item);
	}

	public PosizioneDebitoriaGpdEntity updatePosition(PosizioneDebitoriaGpdEntity item) {
		logger.info("Aggiornamento Posizione debitoria [IdA2A:{}, CodDocumento:{}] sul GPD...", item.getCodApplicazione(), item.getCodDocumento());

		if (hasRataSenzaIuv(item)) {
			logger.warn("IUV non presente su almeno una rata della Posizione debitoria [IdA2A:{}, CodDocumento:{}], aggiornamento non verra' effettuato.",
					item.getCodApplicazione(), item.getCodDocumento());
			OffsetDateTime dataStart = OffsetDateTime.now();
			String xRequestId = Utils.creaXRequestId();
			HttpClientErrorException e = new HttpClientErrorException(HttpStatusCode.valueOf(400), "IUV non presente", new byte[0], Charset.defaultCharset());
			this.gdeService.salvaUpdatePositionKo(null, xRequestId, dataStart, OffsetDateTime.now(), item, null, e, true);
			return esito(Esito.SCARTATA_DEFINITIVAMENTE, item);
		}

		PaymentPositionModelV3 paymentPositionModel;
		OffsetDateTime dataStart = OffsetDateTime.now();
		String xRequestId = Utils.creaXRequestId();
		try {
			paymentPositionModel = this.posizioneDebitoriaGpdMapper.posizioneDebitoriaGpdToPaymentPositionModelV3(item);
		} catch (IOException e) {
			logger.warn("Errore nella deserializzazione dei metadata della Posizione debitoria [IdA2A:{}, CodDocumento:{}], aggiornamento non verra' effettuato.",
					item.getCodApplicazione(), item.getCodDocumento());
			HttpServerErrorException e1 = new HttpServerErrorException(HttpStatusCode.valueOf(502), "Errore nella deserializzazione dei metadata", new byte[0], Charset.defaultCharset());
			this.gdeService.salvaUpdatePositionKo(null, xRequestId, dataStart, OffsetDateTime.now(), item, null, e1, true);
			return esito(Esito.SCARTATA_DEFINITIVAMENTE, item);
		}

		if (paymentPositionModel.getPaymentOption().isEmpty()) {
			logger.info("Nessuna opzione valida da spedire per l'aggiornamento della Posizione debitoria [IdA2A:{}, CodDocumento:{}] (rate tutte scadute o date incongruenti): nessun invio.",
					item.getCodApplicazione(), item.getCodDocumento());
			HttpClientErrorException e = new HttpClientErrorException(HttpStatusCode.valueOf(400), "Nessuna opzione valida da spedire", new byte[0], Charset.defaultCharset());
			this.gdeService.salvaUpdatePositionKo(paymentPositionModel, xRequestId, dataStart, OffsetDateTime.now(), item, null, e, true);
			return esito(Esito.SCARTATA_DEFINITIVAMENTE, item);
		}

		return updatePositionEngine(item, paymentPositionModel, dataStart, xRequestId,
				AGGIORNAMENTO_POSIZIONE_SUL_GPD_COMPLETATO_CON_ESITO, true, true);
	}

	public PosizioneDebitoriaGpdEntity cancelPosition(PosizioneDebitoriaGpdEntity item) {
		logger.info("Annullamento Posizione debitoria [IdA2A:{}, CodDocumento:{}] attraverso modifica stato in DRAFT sul GPD...",
				item.getCodApplicazione(), item.getCodDocumento());

		OffsetDateTime dataStart = OffsetDateTime.now();
		String xRequestId = Utils.creaXRequestId();
		PaymentPositionModelV3 paymentPositionModel;
		try {
			paymentPositionModel = this.posizioneDebitoriaGpdMapper.posizioneDebitoriaGpdToPaymentPositionModelV3PerAnnullamento(item);
		} catch (IOException e) {
			logger.warn("Errore nella deserializzazione dei metadata della Posizione debitoria [IdA2A:{}, CodDocumento:{}], annullamento non verra' effettuato.",
					item.getCodApplicazione(), item.getCodDocumento());
			HttpServerErrorException e1 = new HttpServerErrorException(HttpStatusCode.valueOf(502), "Errore nella deserializzazione dei metadata", new byte[0], Charset.defaultCharset());
			this.gdeService.salvaUpdatePositionKo(null, xRequestId, dataStart, OffsetDateTime.now(), item, null, e1, false);
			return esito(Esito.SCARTATA_DEFINITIVAMENTE, item);
		}

		return updatePositionEngine(item, paymentPositionModel, dataStart, xRequestId,
				ANNULLAMENTO_POSIZIONE_SUL_GPD_COMPLETATO_CON_ESITO, false, false);
	}

	private PosizioneDebitoriaGpdEntity updatePositionEngine(PosizioneDebitoriaGpdEntity item, PaymentPositionModelV3 paymentPositionModel,
			OffsetDateTime dataStart, String xRequestId, String logMsgOk, boolean toPublish, boolean isAggiornamento) {
		try {
			ResponseEntity<PaymentPositionModelV3> responseEntity = this.gpdApiService.getGpdApiV3(item.getCodDominio()).updatePositionWithHttpInfo(
					item.getCodDominio(), Utils.generaIupdPosizione(item), paymentPositionModel, toPublish, xRequestId);

			logger.info(logMsgOk, item.getCodApplicazione(), item.getCodDocumento(), responseEntity.getStatusCode().value());

			return handleUpdatePositionResponse(item, paymentPositionModel, responseEntity.getStatusCode(), xRequestId, dataStart, responseEntity, isAggiornamento);
		} catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
			return handleUpdatePositionException(e, item, paymentPositionModel, xRequestId, dataStart, isAggiornamento);
		}
	}

	/**
	 * Un 404 ha significato diverso a seconda dell'operazione: durante un aggiornamento
	 * ({@code isAggiornamento=true}) indica che la posizione non e' mai stata creata sul
	 * GPD, e si ritenta con una creazione; durante un annullamento
	 * ({@code isAggiornamento=false}) indica che non c'e' nulla da ritirare, esito
	 * terminale.
	 */
	private PosizioneDebitoriaGpdEntity handleUpdatePositionResponse(PosizioneDebitoriaGpdEntity item, PaymentPositionModelV3 paymentPositionModel,
			HttpStatusCode statusCode, String xRequestId, OffsetDateTime dataStart, ResponseEntity<PaymentPositionModelV3> responseEntity, boolean isAggiornamento) {
		if (statusCode.value() == 200) {
			this.gdeService.salvaUpdatePositionOk(paymentPositionModel, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, isAggiornamento);
			return esito(Esito.INVIATA, item);
		}

		if (statusCode.value() == 404 && isAggiornamento) {
			logger.info("Aggiornamento Posizione debitoria [IdA2A:{}, CodDocumento:{}] sul GPD completato con errore 404, la posizione non risulta mai creata: tento il recupero con una creazione.",
					item.getCodApplicazione(), item.getCodDocumento());
			this.gdeService.salvaUpdatePositionKo(paymentPositionModel, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, null, isAggiornamento);
			return this.createPosition(item);
		}

		if (statusCode.value() == 404 || statusCode.value() == 409) {
			// annullamento di una posizione inesistente (nulla da ritirare), o
			// aggiornamento di una posizione gia' chiusa (pagata o meno): non si ritenta,
			// si marca come comunicata per escluderla dal prossimo run.
			logger.info("Aggiornamento Posizione debitoria [IdA2A:{}, CodDocumento:{}] sul GPD completato con errore {}.",
					item.getCodApplicazione(), item.getCodDocumento(), statusCode.value());
			this.gdeService.salvaUpdatePositionKo(paymentPositionModel, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, null, isAggiornamento);
			return esito(Esito.SCARTATA_DEFINITIVAMENTE, item);
		}

		this.gdeService.salvaUpdatePositionKo(paymentPositionModel, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, null, isAggiornamento);
		return esito(Esito.RINVIATA, item);
	}

	private PosizioneDebitoriaGpdEntity handleUpdatePositionException(RestClientException e, PosizioneDebitoriaGpdEntity item, PaymentPositionModelV3 paymentPositionModel,
			String xRequestId, OffsetDateTime dataStart, boolean isAggiornamento) {
		ResponseEntity<PaymentPositionModelV3> responseEntity = null;
		if (e instanceof HttpClientErrorException httpClientError) {
			this.logErrorResponse(httpClientError);

			if (httpClientError.getStatusCode().value() == 404 && isAggiornamento) {
				logger.info("Aggiornamento Posizione debitoria [IdA2A:{}, CodDocumento:{}] sul GPD completato con errore 404, la posizione non risulta mai creata: tento il recupero con una creazione.",
						item.getCodApplicazione(), item.getCodDocumento());
				this.gdeService.salvaUpdatePositionKo(paymentPositionModel, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, e, isAggiornamento);
				return this.createPosition(item);
			}

			if (httpClientError.getStatusCode().value() == 404 || httpClientError.getStatusCode().value() == 409) {
				logger.info("Aggiornamento Posizione debitoria [IdA2A:{}, CodDocumento:{}] sul GPD completato con errore {}.",
						item.getCodApplicazione(), item.getCodDocumento(), httpClientError.getStatusCode().value());
				this.gdeService.salvaUpdatePositionKo(paymentPositionModel, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, e, isAggiornamento);
				return esito(Esito.SCARTATA_DEFINITIVAMENTE, item);
			}
		} else if (e instanceof HttpServerErrorException httpServerError) {
			this.logErrorResponse(httpServerError);
		} else if (e instanceof ResourceAccessException) {
			logger.error(MessageFormat.format(ERROR_MSG_GPD_NON_RAGGIUNGIBILE, e.getMessage()), e);
		}

		logger.error("Aggiornamento Posizione debitoria [IdA2A:{}, CodDocumento:{}] sul GPD completato con errore.", item.getCodApplicazione(), item.getCodDocumento());
		this.gdeService.salvaUpdatePositionKo(paymentPositionModel, xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, e, isAggiornamento);
		return esito(Esito.RINVIATA, item);
	}

	public PaymentPositionModelResponseV3 getPosition(PosizioneDebitoriaGpdEntity item) {
		logger.info("Lettura Posizione debitoria [IdA2A:{}, CodDocumento:{}] dal GPD...", item.getCodApplicazione(), item.getCodDocumento());
		ResponseEntity<PaymentPositionModelResponseV3> responseEntity = null;
		RestClientException ex = null;
		OffsetDateTime dataStart = OffsetDateTime.now();
		String xRequestId = Utils.creaXRequestId();

		try {
			responseEntity = this.gpdApiService.getGpdApiV3(item.getCodDominio()).getOrganizationDebtPositionByIUPDWithHttpInfo(
					item.getCodDominio(), Utils.generaIupdPosizione(item), xRequestId);

			logger.info("Lettura Posizione debitoria [IdA2A:{}, CodDocumento:{}] dal GPD completato con esito [{}].",
					item.getCodApplicazione(), item.getCodDocumento(), responseEntity.getStatusCode().value());

			if (responseEntity.getStatusCode().is2xxSuccessful()) {
				PaymentPositionModelResponseV3 body = responseEntity.getBody();
				logger.info("Posizione debitoria [IdA2A:{}, CodDocumento:{}] stato [{}].",
						item.getCodApplicazione(), item.getCodDocumento(), body != null ? body.getStatus() : null);
				this.gdeService.salvaGetPositionOk(xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity);
				return body;
			}
		} catch (HttpClientErrorException | HttpServerErrorException e) {
			this.logErrorResponse(e);
			ex = e;
		} catch (ResourceAccessException e) {
			logger.error(MessageFormat.format(ERROR_MSG_GPD_NON_RAGGIUNGIBILE, e.getMessage()), e);
			ex = e;
		}

		logger.info("Lettura Posizione debitoria [IdA2A:{}, CodDocumento:{}] dal GPD completato con errore.", item.getCodApplicazione(), item.getCodDocumento());
		this.gdeService.salvaGetPositionKo(xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, ex);
		return null;
	}

	public PosizioneDebitoriaGpdEntity publishPosition(PosizioneDebitoriaGpdEntity item) {
		logger.info("Publish Posizione debitoria [IdA2A:{}, CodDocumento:{}] sul GPD...", item.getCodApplicazione(), item.getCodDocumento());
		ResponseEntity<PaymentPositionModelV3> responseEntity = null;
		RestClientException ex = null;
		OffsetDateTime dataStart = OffsetDateTime.now();
		String xRequestId = Utils.creaXRequestId();

		try {
			responseEntity = this.gpdApiService.getGpdActionsApiV3(item.getCodDominio()).publishPositionWithHttpInfo(
					item.getCodDominio(), Utils.generaIupdPosizione(item), xRequestId);

			logger.info("Publish Posizione debitoria [IdA2A:{}, CodDocumento:{}] sul GPD completato con esito [{}].",
					item.getCodApplicazione(), item.getCodDocumento(), responseEntity.getStatusCode().value());

			if (responseEntity.getStatusCode().is2xxSuccessful()) {
				this.gdeService.salvaPublishPositionOk(xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity);
				return esito(Esito.INVIATA, item);
			}
		} catch (HttpClientErrorException | HttpServerErrorException e) {
			this.logErrorResponse(e);
			ex = e;
		} catch (ResourceAccessException e) {
			logger.error(MessageFormat.format(ERROR_MSG_GPD_NON_RAGGIUNGIBILE, e.getMessage()), e);
			ex = e;
		}

		logger.info("Publish Posizione debitoria [IdA2A:{}, CodDocumento:{}] sul GPD completato con errore.", item.getCodApplicazione(), item.getCodDocumento());
		this.gdeService.salvaPublishPositionKo(xRequestId, dataStart, OffsetDateTime.now(), item, responseEntity, ex);
		return esito(Esito.RINVIATA, item);
	}

	private void logErrorResponse(HttpStatusCodeException e) {
		if (e instanceof HttpServerErrorException) {
			logger.error("Ricevuto server error da GPD: {}", e.getMessage());
		} else {
			logger.error("Ricevuto client error da GPD: {}", e.getMessage());
		}
		logger.debug("HTTP Status Code: {}", e.getStatusCode().value());
		logger.debug("Status Text: {}", e.getStatusText());
		logger.debug("HTTP Headers: {}", e.getResponseHeaders());
		logger.debug("Response Body: {}", e.getResponseBodyAsString());
	}
}
