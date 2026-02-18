package it.govpay.gpd.gde.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.common.client.gde.HttpDataHolder;
import it.govpay.common.configurazione.service.ConfigurazioneService;
import it.govpay.common.gde.AbstractGdeService;
import it.govpay.common.gde.GdeEventInfo;
import it.govpay.gde.client.beans.Header;
import it.govpay.gde.client.beans.NuovoEvento;
import it.govpay.gpd.client.beans.PaymentPositionModel;
import it.govpay.gpd.client.beans.PaymentPositionModelBaseResponse;
import it.govpay.gpd.costanti.Costanti;
import it.govpay.gpd.entity.VersamentoGpdEntity;
import it.govpay.gpd.gde.mapper.EventoGdpMapperImpl;
import it.govpay.gpd.gde.utils.GdeUtils;
import it.govpay.gpd.service.GpdApiService;
import it.govpay.gpd.utils.Utils;

@Service
public class GdeService extends AbstractGdeService {

	private Logger logger = LoggerFactory.getLogger(GdeService.class);

	private final GpdApiService gpdApiService;

	@Value("${it.govpay.gpd.batch.clusterId:GovPay-ACA-Batch}")
	private String clusterId;

	private final EventoGdpMapperImpl eventoGdpMapper;

	private final ConfigurazioneService configurazioneService;

	public GdeService(ObjectMapper objectMapper,
			@Qualifier("asyncHttpExecutor") Executor asyncHttpExecutor,
			ConfigurazioneService configurazioneService,
			EventoGdpMapperImpl eventoGdpMapper,
			GpdApiService gpdApiService) {
		super(objectMapper, asyncHttpExecutor, configurazioneService);
		this.configurazioneService = configurazioneService;
		this.eventoGdpMapper = eventoGdpMapper;
		this.gpdApiService = gpdApiService;
	}

	@Override
	protected String getGdeEndpoint() {
		return configurazioneService.getServizioGDE().getUrl() + Costanti.EVENTI;
	}

	@Override
	protected NuovoEvento convertToGdeEvent(GdeEventInfo eventInfo) {
		throw new UnsupportedOperationException(
				"GdeService usa sendEventAsync(NuovoEvento) direttamente, non il pattern GdeEventInfo");
	}

	@Override
	public boolean isAbilitato() {
		try {
			return super.isAbilitato();
		} catch (Exception e) {
			logger.debug("Servizio GDE non configurato: {}", e.getMessage());
			return false;
		}
	}

	public void sendEventAsync(NuovoEvento nuovoEvento) {
		if (!isAbilitato()) {
			logger.debug("Spedizione evento per la pendenza [IdA2A:{}, ID:{}] al GDE non abilitata.", nuovoEvento.getIdA2A(), nuovoEvento.getIdPendenza());
			return;
		}
		logger.info("Spedizione evento per la pendenza [IdA2A:{}, ID:{}] al GDE...", nuovoEvento.getIdA2A(), nuovoEvento.getIdPendenza());
		CompletableFuture.runAsync(() -> {
			try {
				getGdeRestTemplate().postForEntity(getGdeEndpoint(), nuovoEvento, Void.class);
				logger.info("Spedizione evento per la pendenza [IdA2A:{}, ID:{}] al GDE completata con successo.", nuovoEvento.getIdA2A(), nuovoEvento.getIdPendenza());
			} catch (Exception ex) {
				logger.warn("Spedizione evento per la pendenza [IdA2A:{}, ID:{}] al GDE completata con errore: {}",
						nuovoEvento.getIdA2A(), nuovoEvento.getIdPendenza(), ex.getMessage());
				logger.debug("Dettaglio errore GDE:", ex);
			} finally {
				HttpDataHolder.clear();
			}
		}, this.asyncExecutor);
	}

	public static String getHttpMethod(String tipoEvento) {
		switch (tipoEvento) {
		case Costanti.CREATE_POSITION:
			return HttpMethod.POST.name();
		case Costanti.UPDATE_POSITION:
			return HttpMethod.PUT.name();
		case Costanti.PUBLISH_POSITION:
			return HttpMethod.POST.name();
		case Costanti.GET_ORGANIZATION_DEBT_POSITION_BY_IUPD:
			return HttpMethod.GET.name();
		default:
			return null;
		}
	}

	private String getGpdBasePath(VersamentoGpdEntity versamentoGpdEntity) {
		try {
			return this.gpdApiService.getGpdBasePath(versamentoGpdEntity.getCodDominio());
		} catch (Exception e) {
			logger.warn("Impossibile risolvere il base path GPD per il dominio {}: {}", versamentoGpdEntity.getCodDominio(), e.getMessage());
			return "";
		}
	}

	public void salvaCreatePositionOk(PaymentPositionModel request, String xRequestId,
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModel> response) {
		String basePath = getGpdBasePath(versamentoGpdEntity);
		String url = GdeUtils.creaUrlCreatePosition(versamentoGpdEntity.getCodDominio(), basePath, Costanti.ORGANIZATIONS_DEBT_POSITIONS, true);
		NuovoEvento nuovoEvento = this.creaEventoOK(versamentoGpdEntity, Costanti.CREATE_POSITION, xRequestId, dataStart, dataEnd);

		this.salvaInvio(nuovoEvento, dataEnd, url, xRequestId, request, response, null);
	}

	public void salvaCreatePositionKo(PaymentPositionModel request, String xRequestId,
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModel> response, RestClientException e) {
		String basePath = getGpdBasePath(versamentoGpdEntity);
		String url = GdeUtils.creaUrlCreatePosition(versamentoGpdEntity.getCodDominio(), basePath, Costanti.ORGANIZATIONS_DEBT_POSITIONS, true);
		NuovoEvento nuovoEvento = this.creaEventoKO(versamentoGpdEntity, Costanti.CREATE_POSITION, xRequestId, dataStart, dataEnd, response, e);

		this.salvaInvio(nuovoEvento, dataEnd, url, xRequestId, request, response, e);
	}

	public void salvaUpdatePositionOk(PaymentPositionModel request, String xRequestId,
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModel> response) {
		String basePath = getGpdBasePath(versamentoGpdEntity);
		String url = GdeUtils.creaUrlUpdatePosition(versamentoGpdEntity.getCodDominio(), Utils.generaIupd(versamentoGpdEntity), basePath, Costanti.ORGANIZATIONS_DEBT_POSITIONS_IUPD, true);
		NuovoEvento nuovoEvento = this.creaEventoOK(versamentoGpdEntity, Costanti.UPDATE_POSITION, xRequestId, dataStart, dataEnd);

		this.salvaInvio(nuovoEvento, dataEnd, url, xRequestId, request, response, null);
	}

	public void salvaUpdatePositionKo(PaymentPositionModel request, String xRequestId,
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModel> response, RestClientException e) {
		String basePath = getGpdBasePath(versamentoGpdEntity);
		String url = GdeUtils.creaUrlUpdatePosition(versamentoGpdEntity.getCodDominio(), Utils.generaIupd(versamentoGpdEntity), basePath, Costanti.ORGANIZATIONS_DEBT_POSITIONS_IUPD, true);
		NuovoEvento nuovoEvento = this.creaEventoKO(versamentoGpdEntity, Costanti.UPDATE_POSITION, xRequestId, dataStart, dataEnd, response, e);

		this.salvaInvio(nuovoEvento, dataEnd, url, xRequestId, request, response, e);
	}

	public void salvaPublishPositionOk(String xRequestId,
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModel> response) {
		String basePath = getGpdBasePath(versamentoGpdEntity);
		String url = GdeUtils.creaUrlUpdatePosition(versamentoGpdEntity.getCodDominio(), Utils.generaIupd(versamentoGpdEntity), basePath, Costanti.ORGANIZATIONS_DEBT_POSITIONS_IUPD_PUBLISH, null);
		NuovoEvento nuovoEvento = this.creaEventoOK(versamentoGpdEntity, Costanti.PUBLISH_POSITION, xRequestId, dataStart, dataEnd);

		this.salvaInvio(nuovoEvento, dataEnd, url, xRequestId, null, response, null);
	}

	public void salvaPublishPositionKo(String xRequestId,
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModel> response, RestClientException e) {
		String basePath = getGpdBasePath(versamentoGpdEntity);
		String url = GdeUtils.creaUrlUpdatePosition(versamentoGpdEntity.getCodDominio(), Utils.generaIupd(versamentoGpdEntity), basePath, Costanti.ORGANIZATIONS_DEBT_POSITIONS_IUPD_PUBLISH, null);
		NuovoEvento nuovoEvento = this.creaEventoKO(versamentoGpdEntity, Costanti.PUBLISH_POSITION, xRequestId, dataStart, dataEnd, response, e);

		this.salvaInvio(nuovoEvento, dataEnd, url, xRequestId, null, response, e);
	}

	public void salvaGetPositionOk(String xRequestId,
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModelBaseResponse> response) {
		String basePath = getGpdBasePath(versamentoGpdEntity);
		String url = GdeUtils.creaUrlUpdatePosition(versamentoGpdEntity.getCodDominio(), Utils.generaIupd(versamentoGpdEntity), basePath, Costanti.ORGANIZATIONS_DEBT_POSITIONS_IUPD, null);
		NuovoEvento nuovoEvento = this.creaEventoOK(versamentoGpdEntity, Costanti.GET_ORGANIZATION_DEBT_POSITION_BY_IUPD, xRequestId, dataStart, dataEnd);

		this.salvaInvio(nuovoEvento, dataEnd, url, xRequestId, null, response, null);
	}

	public void salvaGetPositionKo(String xRequestId,
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModelBaseResponse> response, RestClientException e) {
		String basePath = getGpdBasePath(versamentoGpdEntity);
		String url = GdeUtils.creaUrlUpdatePosition(versamentoGpdEntity.getCodDominio(), Utils.generaIupd(versamentoGpdEntity), basePath, Costanti.ORGANIZATIONS_DEBT_POSITIONS_IUPD, null);
		NuovoEvento nuovoEvento = this.creaEventoKO(versamentoGpdEntity, Costanti.GET_ORGANIZATION_DEBT_POSITION_BY_IUPD, xRequestId, dataStart, dataEnd, response, e);

		this.salvaInvio(nuovoEvento, dataEnd, url, xRequestId, null, response, e);
	}

	private void salvaInvio(NuovoEvento nuovoEvento, OffsetDateTime dataEnd, String url, String xRequestId, Object request,
			ResponseEntity<?> responseEntity, RestClientException e) {

		List<Header> headerRichiesta = GdeUtils.creaHeaderRichiesta(false);
		GdeUtils.aggiungiHeaderXRequestId(headerRichiesta, xRequestId);

		this.eventoGdpMapper.creaParametriRichiesta(nuovoEvento, url, getHttpMethod(nuovoEvento.getTipoEvento()), headerRichiesta);
		this.eventoGdpMapper.creaParametriRisposta(nuovoEvento, dataEnd, responseEntity, e);

		GdeUtils.serializzaPayload(this.objectMapper, nuovoEvento, request, responseEntity, e);

		this.sendEventAsync(nuovoEvento);
	}

	public NuovoEvento creaEventoOK(VersamentoGpdEntity versamentoGpdEntity, String tipoEvento, String transactionId, OffsetDateTime dataStart, OffsetDateTime dataEnd) {
		NuovoEvento nuovoEvento = this.eventoGdpMapper.mapEventoOk(versamentoGpdEntity, tipoEvento, transactionId, dataStart, dataEnd);
		nuovoEvento.setClusterId(this.clusterId);
		return nuovoEvento;
	}

	public NuovoEvento creaEventoKO(VersamentoGpdEntity versamentoGpdEntity, String tipoEvento, String transactionId,
			OffsetDateTime dataStart, OffsetDateTime dataEnd, ResponseEntity<?> responseEntity, RestClientException e) {
		NuovoEvento nuovoEvento = this.eventoGdpMapper.mapEventoKo(versamentoGpdEntity, tipoEvento, transactionId, dataStart, dataEnd,	responseEntity, e);
		nuovoEvento.setClusterId(this.clusterId);
		return nuovoEvento;
	}
}
