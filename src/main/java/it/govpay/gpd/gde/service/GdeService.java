package it.govpay.gpd.gde.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

	@Autowired
	@Qualifier("gdeApi")
	EventiApi gdeApi;

	@Value("${it.govpay.gde.enabled:true}")
	Boolean gdeEnabled;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	EventoGdpMapperImpl eventoGdpMapper;

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

	public void salvaCreatePositionOk(PaymentPositionModel request, String baseUrl, String xRequestId, Boolean toPublish, 
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModel> response) {
		String url = GdeUtils.creaUrlCreatePosition(versamentoGpdEntity.getCodDominio(), baseUrl, Costanti.ORGANIZATIONS_DEBT_POSITIONS, toPublish);
		
		this.salvaInvio(true, versamentoGpdEntity, Costanti.CREATE_POSITION, xRequestId, dataStart, dataEnd, url,
				HttpMethod.POST.name(), xRequestId, request, response, null);
	}

	public void salvaCreatePositionKo(PaymentPositionModel request, String baseUrl, String xRequestId, Boolean toPublish, 
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModel> response, RestClientException e) {
		String url = GdeUtils.creaUrlCreatePosition(versamentoGpdEntity.getCodDominio(), baseUrl, Costanti.ORGANIZATIONS_DEBT_POSITIONS, toPublish);
		
		this.salvaInvio(false, versamentoGpdEntity, Costanti.CREATE_POSITION, xRequestId, dataStart, dataEnd, url,
				HttpMethod.POST.name(), xRequestId, request, response, e);
	}
	
	public void salvaUpdatePositionOk(PaymentPositionModel request, String baseUrl, String xRequestId, Boolean toPublish, 
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModel> response) {
		String url = GdeUtils.creaUrlUpdatePosition(versamentoGpdEntity.getCodDominio(), Utils.generaIupd(versamentoGpdEntity), baseUrl, Costanti.ORGANIZATIONS_DEBT_POSITIONS_IUPD, toPublish);
		
		this.salvaInvio(true, versamentoGpdEntity, Costanti.UPDATE_POSITION, xRequestId, dataStart, dataEnd, url,
				HttpMethod.PUT.name(), xRequestId, request, response, null);
	}

	public void salvaUpdatePositionKo(PaymentPositionModel request, String baseUrl, String xRequestId, Boolean toPublish, 
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModel> response, RestClientException e) {
		String url = GdeUtils.creaUrlUpdatePosition(versamentoGpdEntity.getCodDominio(), Utils.generaIupd(versamentoGpdEntity), baseUrl, Costanti.ORGANIZATIONS_DEBT_POSITIONS_IUPD, toPublish);
		
		this.salvaInvio(false, versamentoGpdEntity, Costanti.UPDATE_POSITION, xRequestId, dataStart, dataEnd, url,
				HttpMethod.PUT.name(), xRequestId, request, response, e);
	}
	
	public void salvaInvalidatePositionOk(String baseUrl, String xRequestId,  
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModel> response) {
		String url = GdeUtils.creaUrlUpdatePosition(versamentoGpdEntity.getCodDominio(), Utils.generaIupd(versamentoGpdEntity), baseUrl, Costanti.ORGANIZATIONS_DEBT_POSITIONS_IUPD_INVALIDATE, null);
		
		this.salvaInvio(true, versamentoGpdEntity, Costanti.INVALIDATE_POSITION, xRequestId, dataStart, dataEnd, url,
				HttpMethod.POST.name(), xRequestId, null, response, null);
	}

	public void salvaInvalidatePositionKo(String baseUrl, String xRequestId,  
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModel> response, RestClientException e) {
		String url = GdeUtils.creaUrlUpdatePosition(versamentoGpdEntity.getCodDominio(), Utils.generaIupd(versamentoGpdEntity), baseUrl, Costanti.ORGANIZATIONS_DEBT_POSITIONS_IUPD_INVALIDATE, null);
		
		this.salvaInvio(false, versamentoGpdEntity, Costanti.INVALIDATE_POSITION, xRequestId, dataStart, dataEnd, url,
				HttpMethod.POST.name(), xRequestId, null, response, e);
	}
	
	public void salvaPublishPositionOk(String baseUrl, String xRequestId,  
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModel> response) {
		String url = GdeUtils.creaUrlUpdatePosition(versamentoGpdEntity.getCodDominio(), Utils.generaIupd(versamentoGpdEntity), baseUrl, Costanti.ORGANIZATIONS_DEBT_POSITIONS_IUPD_PUBLISH, null);
		
		this.salvaInvio(true, versamentoGpdEntity, Costanti.PUBLISH_POSITION, xRequestId, dataStart, dataEnd, url,
				HttpMethod.POST.name(), xRequestId, null, response, null);
	}

	public void salvaPublishPositionKo(String baseUrl, String xRequestId,  
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModel> response, RestClientException e) {
		String url = GdeUtils.creaUrlUpdatePosition(versamentoGpdEntity.getCodDominio(), Utils.generaIupd(versamentoGpdEntity), baseUrl, Costanti.ORGANIZATIONS_DEBT_POSITIONS_IUPD_PUBLISH, null);
		
		this.salvaInvio(false, versamentoGpdEntity, Costanti.PUBLISH_POSITION, xRequestId, dataStart, dataEnd, url,
				HttpMethod.POST.name(), xRequestId, null, response, e);
	}
	
	public void salvaGetPositionOk(String baseUrl, String xRequestId,  
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModelBaseResponse> response) {
		String url = GdeUtils.creaUrlUpdatePosition(versamentoGpdEntity.getCodDominio(), Utils.generaIupd(versamentoGpdEntity), baseUrl, Costanti.ORGANIZATIONS_DEBT_POSITIONS_IUPD, null);
		
		this.salvaInvio(true, versamentoGpdEntity, Costanti.GET_ORGANIZATION_DEBT_POSITION_BY_IUPD, xRequestId, dataStart, dataEnd, url,
				HttpMethod.GET.name(), xRequestId, null, response, null);
	}

	public void salvaGetPositionKo(String baseUrl, String xRequestId,  
			OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoGpdEntity versamentoGpdEntity, ResponseEntity<PaymentPositionModelBaseResponse> response, RestClientException e) {
		String url = GdeUtils.creaUrlUpdatePosition(versamentoGpdEntity.getCodDominio(), Utils.generaIupd(versamentoGpdEntity), baseUrl, Costanti.ORGANIZATIONS_DEBT_POSITIONS_IUPD, null);
		
		this.salvaInvio(false, versamentoGpdEntity, Costanti.GET_ORGANIZATION_DEBT_POSITION_BY_IUPD, xRequestId, dataStart, dataEnd, url,
				HttpMethod.GET.name(), xRequestId, null, response, e);
	}

	private void salvaInvio(boolean isOK, VersamentoGpdEntity versamentoGpdEntity, String tipoEvento, String transactionId, OffsetDateTime dataStart, OffsetDateTime dataEnd,
			String url, String httpMethod, String xRequestId, Object request, 
			ResponseEntity<?> responseEntity, RestClientException e) {
		List<Header> headerRichiesta = GdeUtils.creaHeaderRichiesta(false);
		GdeUtils.aggiungiHeaderXRequestId(headerRichiesta, xRequestId);

		NuovoEvento nuovoEvento = isOK ?				
				this.eventoGdpMapper.mapEventoOk(versamentoGpdEntity, tipoEvento, transactionId, dataStart, dataEnd) :
				this.eventoGdpMapper.mapEventoKo(versamentoGpdEntity, tipoEvento, transactionId, dataStart, dataEnd, responseEntity, e);

		this.eventoGdpMapper.creaParametriRichiestaERisposta(nuovoEvento, url, httpMethod, headerRichiesta, dataStart, dataEnd, responseEntity);

		GdeUtils.serializzaPayload(this.objectMapper, nuovoEvento, request, responseEntity, e);

		this.inviaEvento(nuovoEvento);
	}

}