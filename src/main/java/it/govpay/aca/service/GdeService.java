package it.govpay.aca.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.aca.client.beans.NewDebtPositionRequest;
import it.govpay.aca.client.gde.EventiApi;
import it.govpay.aca.entity.VersamentoAcaEntity;
import it.govpay.aca.mapper.NuovoEventoMapperImpl;
import it.govpay.gde.client.api.impl.ApiException;
import it.govpay.gde.client.beans.NuovoEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.OffsetDateTime;

@Service
public class GdeService {

    private Logger logger = LoggerFactory.getLogger(GdeService.class);

    @Autowired
    @Qualifier("gdeApi")
    EventiApi gdeApi;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NuovoEventoMapperImpl nuovoEventoMapper;

    public void inviaEvento(NuovoEvento nuovoEvento) {
        try {
            this.gdeApi.addEventoWithHttpInfoAsync(nuovoEvento).thenApply(response -> {
                logger.info("Spedizione NuovoEvento al GDE completata con esito [{}].",  response.statusCode());
                return null;
            }).exceptionally(ex -> {
                logger.error(String.format("Spedizione NuovoEvento al GDE completata con errore [%s].",  ex.getMessage()), ex);
                return null;
            });
        } catch (ApiException e) {
            logger.error(String.format("GDE non raggiungibile: %s", e.getMessage()), e);
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