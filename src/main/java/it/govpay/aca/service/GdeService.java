package it.govpay.aca.service;

import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.aca.client.beans.NewDebtPositionRequest;
import it.govpay.aca.client.gde.EventiApi;
import it.govpay.aca.entity.VersamentoAcaEntity;
import it.govpay.aca.mapper.NuovoEventoMapperImpl;
import it.govpay.gde.client.api.impl.ApiException;
import it.govpay.gde.client.beans.NuovoEvento;

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
    NuovoEventoMapperImpl nuovoEventoMapper;

    public void inviaEvento(NuovoEvento nuovoEvento) {
    	if(this.gdeEnabled.booleanValue()) {
	    	logger.info("Spedizione evento per la pendenza [IdA2A:{}, ID:{}] al GDE...", nuovoEvento.getIdA2A(), nuovoEvento.getIdPendenza());
	        try {
	            this.gdeApi.addEventoWithHttpInfoAsync(nuovoEvento).thenApply(response -> {
	                logger.info("Spedizione evento per la pendenza [IdA2A:{}, ID:{}] al GDE completata con esito [{}].", nuovoEvento.getIdA2A(), nuovoEvento.getIdPendenza(), response.statusCode());
	                return null;
	            }).exceptionally(ex -> {
	            	if (ex.getCause() instanceof HttpClientErrorException) {
	                    HttpClientErrorException httpClientErrorException = (HttpClientErrorException) ex.getCause();
	                    int statusCode = httpClientErrorException.getRawStatusCode();
	                    logger.error(String.format("Spedizione evento per la pendenza [IdA2A:%s, ID:%s] al GDE completata con errore [%s],[%s].", nuovoEvento.getIdA2A(), nuovoEvento.getIdPendenza(), statusCode, ex.getMessage() ), ex);
	                } else if (ex.getCause() instanceof HttpServerErrorException) {
	                	HttpServerErrorException httpServerErrorException = (HttpServerErrorException) ex.getCause();
	                    int statusCode = httpServerErrorException.getRawStatusCode();
	                    logger.error(String.format("Spedizione evento per la pendenza [IdA2A:%s, ID:%s] al GDE completata con errore [%s],[%s].", nuovoEvento.getIdA2A(), nuovoEvento.getIdPendenza(), statusCode, ex.getMessage() ), ex);
	                } else {
	                    logger.error(String.format("Spedizione evento per la pendenza [IdA2A:%s, ID:%s] al GDE completata con errore [%s].", nuovoEvento.getIdA2A(), nuovoEvento.getIdPendenza(), ex.getMessage()), ex);
	                }
	                return null;
	            });
	        } catch (HttpClientErrorException | HttpServerErrorException e) {
	        	 int statusCode = e.getRawStatusCode();
	            logger.error(String.format("Spedizione evento per la pendenza [IdA2A:%s, ID:%s] al GDE completata con errore: [%s],[%s].", nuovoEvento.getIdA2A(), nuovoEvento.getIdPendenza(), statusCode, e.getResponseBodyAsString()), e);
	        } catch (ApiException e) {
	            logger.error(String.format("GDE non raggiungibile: %s", e.getMessage()), e);
	        }
    	} else {
    		logger.debug("Spedizione evento per la pendenza [IdA2A:{}, ID:{}] al GDE non abilitata.", nuovoEvento.getIdA2A(), nuovoEvento.getIdPendenza());
    	}
    }

    public void salvaInvioOk(NewDebtPositionRequest newDebtPositionRequest, String baseUrl, OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoAcaEntity versamentoAcaEntity, ResponseEntity<Void> responseEntity) {
        NuovoEvento nuovoEvento = this.nuovoEventoMapper.mapEventoOk(objectMapper, newDebtPositionRequest, baseUrl, dataStart, dataEnd, versamentoAcaEntity, responseEntity);

        this.inviaEvento(nuovoEvento);
    }

    public void salvaInvioKo(NewDebtPositionRequest newDebtPositionRequest, String baseUrl, OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoAcaEntity versamentoAcaEntity, ResponseEntity<Void> responseEntity, RestClientException e) {
        NuovoEvento nuovoEvento = this.nuovoEventoMapper.mapEventoKo(objectMapper, newDebtPositionRequest, baseUrl, dataStart, dataEnd, versamentoAcaEntity, responseEntity, e);

        this.inviaEvento(nuovoEvento);
    }
}