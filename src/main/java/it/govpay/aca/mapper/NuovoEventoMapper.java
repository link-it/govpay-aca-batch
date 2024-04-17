package it.govpay.aca.mapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import it.govpay.gde.client.beans.ComponenteEvento;
import it.govpay.gde.client.beans.DettaglioRichiesta;
import it.govpay.gde.client.beans.DettaglioRisposta;
import it.govpay.gde.client.beans.EsitoEvento;
import it.govpay.gde.client.beans.Header;
import it.govpay.gde.client.beans.RuoloEvento;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.aca.client.beans.NewDebtPositionRequest;
import it.govpay.aca.costanti.Costanti;
import it.govpay.aca.entity.VersamentoAcaEntity;
import it.govpay.gde.client.beans.CategoriaEvento;
import it.govpay.gde.client.beans.NuovoEvento;

@Mapper(componentModel = "spring")
public interface NuovoEventoMapper {

	@Mapping(target = "idDominio", source = "versamentoAcaEntity.codDominio")
	@Mapping(target = "iuv", source = "versamentoAcaEntity.iuvVersamento")
	@Mapping(target = "idA2A", source = "versamentoAcaEntity.codApplicazione")
	@Mapping(target = "idPendenza", source = "versamentoAcaEntity.codVersamentoEnte")
	NuovoEvento mapEventoBase(VersamentoAcaEntity versamentoAcaEntity);

	default NuovoEvento mapEvento(ObjectMapper objectMapper, NewDebtPositionRequest newDebtPositionRequest, String baseUrl, 
			OffsetDateTime dataStart, OffsetDateTime dataEnd, 
			VersamentoAcaEntity versamentoAcaEntity, ResponseEntity<Void> responseEntity, RestClientException restClientException){
		NuovoEvento nuovoEvento = mapEventoBase(versamentoAcaEntity);

		nuovoEvento.setCategoriaEvento(CategoriaEvento.INTERFACCIA);
		nuovoEvento.setClusterId(Costanti.GOVPAY_GDE_CLUSTER_ID);
		nuovoEvento.setDataEvento(dataStart);
		nuovoEvento.setDurataEvento(dataEnd.toEpochSecond() - dataStart.toEpochSecond());
		nuovoEvento.setRuolo(RuoloEvento.CLIENT);
		nuovoEvento.setComponente(ComponenteEvento.API_PAGOPA);
		nuovoEvento.setParametriRichiesta(mapDettagliRichiesta(objectMapper, newDebtPositionRequest, dataStart, baseUrl));
		nuovoEvento.setParametriRisposta(mapDettagliRisposta(objectMapper, responseEntity, dataEnd, restClientException));
		nuovoEvento.setTipoEvento(Costanti.GOVPAY_GDE_NOME_EVENTO_PA_CREATE_POSITION);
		nuovoEvento.setTransactionId(null);
		nuovoEvento.setDatiPagoPA(null);

		return nuovoEvento;
	}

	default NuovoEvento mapEventoOk(ObjectMapper objectMapper, NewDebtPositionRequest newDebtPositionRequest, String baseUrl, OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoAcaEntity versamentoAcaEntity, ResponseEntity<Void> responseEntity){
		NuovoEvento nuovoEvento = mapEvento(objectMapper, newDebtPositionRequest, baseUrl, dataStart, dataEnd, versamentoAcaEntity, responseEntity, null);

		nuovoEvento.setEsito(EsitoEvento.OK);

		return nuovoEvento;
	}

	default NuovoEvento mapEventoKo(ObjectMapper objectMapper, NewDebtPositionRequest newDebtPositionRequest, String baseUrl, OffsetDateTime dataStart, OffsetDateTime dataEnd, VersamentoAcaEntity versamentoAcaEntity, ResponseEntity<Void> responseEntity, RestClientException restClientException){
		NuovoEvento nuovoEvento = mapEvento(objectMapper, newDebtPositionRequest, baseUrl, dataStart, dataEnd, versamentoAcaEntity, responseEntity, restClientException);

		if(restClientException != null) {
			if (restClientException instanceof HttpStatusCodeException) {
				HttpStatusCodeException httpStatusCodeException = (HttpStatusCodeException) restClientException;
				nuovoEvento.setDettaglioEsito(httpStatusCodeException.getResponseBodyAsString());
				nuovoEvento.setSottotipoEsito(httpStatusCodeException.getRawStatusCode() + "");
				
				if (httpStatusCodeException.getStatusCode().is5xxServerError()) {
					nuovoEvento.setEsito(EsitoEvento.FAIL);
				} else {
					nuovoEvento.setEsito(EsitoEvento.KO);
				}
			} else {
				nuovoEvento.setDettaglioEsito(restClientException.getMessage());
				nuovoEvento.setSottotipoEsito("500");
			}
		} else {
			nuovoEvento.setDettaglioEsito(responseEntity.getStatusCode().getReasonPhrase());
			nuovoEvento.setSottotipoEsito("" + responseEntity.getStatusCodeValue());
			if (responseEntity.getStatusCode().is5xxServerError()) {
				nuovoEvento.setEsito(EsitoEvento.FAIL);
			} else {
				nuovoEvento.setEsito(EsitoEvento.KO);
			}
		}

		return nuovoEvento;
	}


	default DettaglioRichiesta mapDettagliRichiesta(ObjectMapper objectMapper, NewDebtPositionRequest newDebtPositionRequest, OffsetDateTime dataStart, String baseUrl ) {
		DettaglioRichiesta dettaglioRichiesta = new DettaglioRichiesta();

		dettaglioRichiesta.setDataOraRichiesta(dataStart);
		dettaglioRichiesta.setMethod(Costanti.GOVPAY_GDE_METHOD_PA_CREATE_POSITION);
		try {
			dettaglioRichiesta.setPayload(objectMapper.writeValueAsString(newDebtPositionRequest));
		} catch (JsonProcessingException e) {
			dettaglioRichiesta.setPayload(Costanti.MSG_PAYLOAD_NON_SERIALIZZABILE);
		}

		dettaglioRichiesta.setUrl(baseUrl + Costanti.GOVPAY_GDE_PATH_SERVIZIO_PA_CREATE_POSITION);
		List<Header> headers = new ArrayList<>();
		Header headerAccept = new Header();
		headerAccept.setNome(HttpHeaders.ACCEPT);
		headerAccept.setValore(Costanti.GOVPAY_GDE_HEADER_ACCEPT_PA_CREATE_POSITION);
		headers.add(headerAccept);
		Header headerContentType = new Header();
		headerContentType.setNome(HttpHeaders.CONTENT_TYPE);
		headerContentType.setValore(Costanti.GOVPAY_GDE_HEADER_CONTENT_TYPE_PA_CREATE_POSITION);
		headers.add(headerContentType);
		dettaglioRichiesta.setHeaders(headers);

		return dettaglioRichiesta;    			
	}


	default DettaglioRisposta mapDettagliRisposta(ObjectMapper objectMapper, ResponseEntity<Void> responseEntity, OffsetDateTime dataEnd, RestClientException restClientException) {
		DettaglioRisposta dettaglioRisposta = new DettaglioRisposta();

		dettaglioRisposta.setDataOraRisposta(dataEnd);
		dettaglioRisposta.setStatus(BigDecimal.valueOf(responseEntity.getStatusCodeValue()));
		// mappa gli headers
		List<Header> headers = new ArrayList<>();
		responseEntity.getHeaders().forEach((key, value) -> {
			Header header = new Header();
			header.setNome(key);
			header.setValore(value.get(0));
			headers.add(header);
		});

		dettaglioRisposta.setHeaders(headers);
		if(restClientException != null) {
			dettaglioRisposta.setPayload(restClientException.getMessage());
		}

		return dettaglioRisposta;
	}
}
