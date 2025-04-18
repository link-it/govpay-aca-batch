package it.govpay.gpd.gde.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.gde.client.api.impl.ApiException;
import it.govpay.gde.client.beans.Header;
import it.govpay.gde.client.beans.NuovoEvento;
import it.govpay.gpd.client.api.DebtPositionActionsApiApi;
import it.govpay.gpd.client.api.DebtPositionsApiApi;
import it.govpay.gpd.client.beans.PaymentPositionModel;
import it.govpay.gpd.client.beans.PaymentPositionModelBaseResponse;
import it.govpay.gpd.costanti.Costanti;
import it.govpay.gpd.entity.VersamentoGpdEntity;
import it.govpay.gpd.gde.client.EventiApi;
import it.govpay.gpd.gde.mapper.EventoGdpMapperImpl;
import it.govpay.gpd.gde.utils.GdeUtils;
import it.govpay.gpd.utils.Utils;

@Service
public class GdeService {

	private Logger logger = LoggerFactory.getLogger(GdeService.class);

	private EventiApi gdeApi;
	
	private String gpdApiBasePath;

	private String gpdActionsApiBasePath;

	@Value("${it.govpay.gde.enabled:true}")
	private Boolean gdeEnabled;

	@Value("${it.govpay.gpd.batch.clusterId:GovPay-ACA-Batch}")
	private String clusterId;

	private ObjectMapper objectMapper;

	private EventoGdpMapperImpl eventoGdpMapper;

	public GdeService(ObjectMapper objectMapper,@Qualifier("gdeApi") EventiApi gdeApi, EventoGdpMapperImpl eventoGdpMapper, 
			@Qualifier("gpdApi") DebtPositionsApiApi gpdApi, 
			@Qualifier("gpdActionsApi") DebtPositionActionsApiApi gpdActionsApi) {
		this.objectMapper = objectMapper;
		this.gdeApi = gdeApi;
		this.eventoGdpMapper = eventoGdpMapper;
		this.gpdApiBasePath = (gpdApi != null && gpdApi.getApiClient() != null) ? gpdApi.getApiClient().getBasePath() : "";
		this.gpdActionsApiBasePath = (gpdActionsApi != null && gpdActionsApi.getApiClient() != null) ? gpdActionsApi.getApiClient().getBasePath() : "";
	}

	public void inviaEvento(NuovoEvento nuovoEvento) {
		if(this.gdeEnabled.booleanValue()) {
			logger.info("Spedizione evento per la pendenza [IdA2A:{}, ID:{}] al GDE...", nuovoEvento.getIdA2A(), nuovoEvento.getIdPendenza());
			try {
				this.gdeApi.addEventoWithHttpInfoAsync(nuovoEvento).thenApply(response -> {
					logger.info("Spedizione evento per la pendenza [IdA2A:{}, ID:{}] al GDE completata con esito [{}].", nuovoEvento.getIdA2A(), nuovoEvento.getIdPendenza(), response.statusCode());
					return null;
				}).exceptionally(ex -> {
					if (ex.getCause() instanceof HttpClientErrorException httpClientErrorException) {
						int statusCode = httpClientErrorException.getStatusCode().value();
						logger.error(String.format("Spedizione evento per la pendenza [IdA2A:%s, ID:%s] al GDE completata con errore [%s],[%s].", nuovoEvento.getIdA2A(), nuovoEvento.getIdPendenza(), statusCode, ex.getMessage() ), ex);
					} else if (ex.getCause() instanceof HttpServerErrorException httpServerErrorException) {
						int statusCode = httpServerErrorException.getStatusCode().value();
						logger.error(String.format("Spedizione evento per la pendenza [IdA2A:%s, ID:%s] al GDE completata con errore [%s],[%s].", nuovoEvento.getIdA2A(), nuovoEvento.getIdPendenza(), statusCode, ex.getMessage() ), ex);
					} else {
						logger.error(String.format("Spedizione evento per la pendenza [IdA2A:%s, ID:%s] al GDE completata con errore [%s].", nuovoEvento.getIdA2A(), nuovoEvento.getIdPendenza(), ex.getMessage()), ex);
					}
					return null;
				});
			} catch (HttpClientErrorException | HttpServerErrorException e) {
				int statusCode = e.getStatusCode().value();
				logger.error(String.format("Spedizione evento per la pendenza [IdA2A:%s, ID:%s] al GDE completata con errore: [%s],[%s].", nuovoEvento.getIdA2A(), nuovoEvento.getIdPendenza(), statusCode, e.getResponseBodyAsString()), e);
			} catch (ApiException e) {
				logger.error(String.format("GDE non raggiungibile: %s", e.getMessage()), e);
			}
		} else {
			logger.debug("Spedizione evento per la pendenza [IdA2A:{}, ID:{}] al GDE non abilitata.", nuovoEvento.getIdA2A(), nuovoEvento.getIdPendenza());
		}
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



	public void salvaCreatePositionOk(PaymentPositionModel request, String xRequestId, 
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModel> response) {
		String url = GdeUtils.creaUrlCreatePosition(versamentoGpdEntity.getCodDominio(), this.gpdApiBasePath, Costanti.ORGANIZATIONS_DEBT_POSITIONS, true);
		NuovoEvento nuovoEvento = this.creaEventoOK(versamentoGpdEntity, Costanti.CREATE_POSITION, xRequestId, dataStart, dataEnd);

		this.salvaInvio(nuovoEvento, dataEnd, url, xRequestId, request, response, null);
	}

	public void salvaCreatePositionKo(PaymentPositionModel request, String xRequestId, 
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModel> response, RestClientException e) {
		String url = GdeUtils.creaUrlCreatePosition(versamentoGpdEntity.getCodDominio(), this.gpdApiBasePath, Costanti.ORGANIZATIONS_DEBT_POSITIONS, true);
		NuovoEvento nuovoEvento = this.creaEventoKO(versamentoGpdEntity, Costanti.CREATE_POSITION, xRequestId, dataStart, dataEnd, response, e);

		this.salvaInvio(nuovoEvento, dataEnd, url, xRequestId, request, response, e);
	}

	public void salvaUpdatePositionOk(PaymentPositionModel request, String xRequestId, 
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModel> response) {
		String url = GdeUtils.creaUrlUpdatePosition(versamentoGpdEntity.getCodDominio(), Utils.generaIupd(versamentoGpdEntity), this.gpdApiBasePath, Costanti.ORGANIZATIONS_DEBT_POSITIONS_IUPD, true);
		NuovoEvento nuovoEvento = this.creaEventoOK(versamentoGpdEntity, Costanti.UPDATE_POSITION, xRequestId, dataStart, dataEnd);

		this.salvaInvio(nuovoEvento, dataEnd, url, xRequestId, request, response, null);
	}

	public void salvaUpdatePositionKo(PaymentPositionModel request, String xRequestId, 
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModel> response, RestClientException e) {
		String url = GdeUtils.creaUrlUpdatePosition(versamentoGpdEntity.getCodDominio(), Utils.generaIupd(versamentoGpdEntity), this.gpdApiBasePath, Costanti.ORGANIZATIONS_DEBT_POSITIONS_IUPD, true);
		NuovoEvento nuovoEvento = this.creaEventoKO(versamentoGpdEntity, Costanti.UPDATE_POSITION, xRequestId, dataStart, dataEnd, response, e);

		this.salvaInvio(nuovoEvento, dataEnd, url, xRequestId, request, response, e);
	}

	public void salvaPublishPositionOk(String xRequestId,  
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModel> response) {
		String url = GdeUtils.creaUrlUpdatePosition(versamentoGpdEntity.getCodDominio(), Utils.generaIupd(versamentoGpdEntity), this.gpdActionsApiBasePath, Costanti.ORGANIZATIONS_DEBT_POSITIONS_IUPD_PUBLISH, null);
		NuovoEvento nuovoEvento = this.creaEventoOK(versamentoGpdEntity, Costanti.PUBLISH_POSITION, xRequestId, dataStart, dataEnd);

		this.salvaInvio(nuovoEvento, dataEnd, url, xRequestId, null, response, null);
	}

	public void salvaPublishPositionKo(String xRequestId,  
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModel> response, RestClientException e) {
		String url = GdeUtils.creaUrlUpdatePosition(versamentoGpdEntity.getCodDominio(), Utils.generaIupd(versamentoGpdEntity), this.gpdActionsApiBasePath, Costanti.ORGANIZATIONS_DEBT_POSITIONS_IUPD_PUBLISH, null);
		NuovoEvento nuovoEvento = this.creaEventoKO(versamentoGpdEntity, Costanti.PUBLISH_POSITION, xRequestId, dataStart, dataEnd, response, e);

		this.salvaInvio(nuovoEvento, dataEnd, url, xRequestId, null, response, e);
	}

	public void salvaGetPositionOk(String xRequestId,  
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModelBaseResponse> response) {
		String url = GdeUtils.creaUrlUpdatePosition(versamentoGpdEntity.getCodDominio(), Utils.generaIupd(versamentoGpdEntity), this.gpdApiBasePath, Costanti.ORGANIZATIONS_DEBT_POSITIONS_IUPD, null);
		NuovoEvento nuovoEvento = this.creaEventoOK(versamentoGpdEntity, Costanti.GET_ORGANIZATION_DEBT_POSITION_BY_IUPD, xRequestId, dataStart, dataEnd);

		this.salvaInvio(nuovoEvento, dataEnd, url, xRequestId, null, response, null);
	}

	public void salvaGetPositionKo(String xRequestId,  
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModelBaseResponse> response, RestClientException e) {
		String url = GdeUtils.creaUrlUpdatePosition(versamentoGpdEntity.getCodDominio(), Utils.generaIupd(versamentoGpdEntity), this.gpdApiBasePath, Costanti.ORGANIZATIONS_DEBT_POSITIONS_IUPD, null);
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

		this.inviaEvento(nuovoEvento);
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