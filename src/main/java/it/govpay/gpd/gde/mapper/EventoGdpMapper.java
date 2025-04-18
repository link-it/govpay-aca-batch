package it.govpay.gpd.gde.mapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import it.govpay.gde.client.beans.CategoriaEvento;
import it.govpay.gde.client.beans.ComponenteEvento;
import it.govpay.gde.client.beans.DettaglioRichiesta;
import it.govpay.gde.client.beans.DettaglioRisposta;
import it.govpay.gde.client.beans.EsitoEvento;
import it.govpay.gde.client.beans.Header;
import it.govpay.gde.client.beans.NuovoEvento;
import it.govpay.gde.client.beans.RuoloEvento;
import it.govpay.gpd.costanti.Costanti;
import it.govpay.gpd.entity.VersamentoGpdEntity;

@Mapper(componentModel = "spring")
public interface EventoGdpMapper {

	@Mapping(target = "idDominio", source = "versamentoGpdEntity.codDominio")
	@Mapping(target = "iuv", source = "versamentoGpdEntity.iuvVersamento")
	@Mapping(target = "idA2A", source = "versamentoGpdEntity.codApplicazione")
	@Mapping(target = "idPendenza", source = "versamentoGpdEntity.codVersamentoEnte")
	NuovoEvento mapEventoBase(VersamentoGpdEntity versamentoGpdEntity);

	default NuovoEvento createEvento(VersamentoGpdEntity versamentoGpdEntity, String tipoEvento, String transactionId, OffsetDateTime dataStart, OffsetDateTime dataEnd){
		NuovoEvento nuovoEvento = mapEventoBase(versamentoGpdEntity);

		nuovoEvento.setCategoriaEvento(CategoriaEvento.INTERFACCIA);
		nuovoEvento.setClusterId(Costanti.GOVPAY_GDE_CLUSTER_ID);
		nuovoEvento.setDataEvento(dataStart);
		nuovoEvento.setDurataEvento(dataEnd.toEpochSecond() - dataStart.toEpochSecond());
		nuovoEvento.setRuolo(RuoloEvento.CLIENT);
		nuovoEvento.setComponente(ComponenteEvento.API_PAGOPA);
		nuovoEvento.setTipoEvento(tipoEvento);
		nuovoEvento.setTransactionId(transactionId);
		nuovoEvento.setDatiPagoPA(null);

		return nuovoEvento;
	}
	
	default void creaParametriRichiesta(NuovoEvento nuovoEvento, String urlOperazione, String httpMethod, List<Header> headers){

		nuovoEvento.setParametriRichiesta(mapDettagliRichiesta(nuovoEvento.getDataEvento(), urlOperazione, httpMethod, headers));
	}
	
	default void creaParametriRisposta(NuovoEvento nuovoEvento, OffsetDateTime dataEnd, ResponseEntity<?> responseEntity, RestClientException restClientException){

		nuovoEvento.setParametriRisposta(mapDettagliRisposta(responseEntity, dataEnd, restClientException));
	}
	
	default NuovoEvento mapEventoOk(VersamentoGpdEntity versamentoGpdEntity, String tipoEvento, String transactionId, OffsetDateTime dataStart, OffsetDateTime dataEnd){
		NuovoEvento nuovoEvento = createEvento(versamentoGpdEntity, tipoEvento, transactionId, dataStart, dataEnd);
		nuovoEvento.setEsito(EsitoEvento.OK);

		return nuovoEvento;
	}
	
	default NuovoEvento mapEventoKo(VersamentoGpdEntity versamentoGpdEntity, String tipoEvento, String transactionId, OffsetDateTime dataStart, OffsetDateTime dataEnd,
			ResponseEntity<?> responseEntity, RestClientException restClientException){
		NuovoEvento nuovoEvento = createEvento(versamentoGpdEntity, tipoEvento, transactionId, dataStart, dataEnd);
		estraiInformazioniDaException(responseEntity, restClientException, nuovoEvento);

		return nuovoEvento;
	}

	default void estraiInformazioniDaException(ResponseEntity<?> responseEntity, RestClientException restClientException,
			NuovoEvento nuovoEvento) {
		if(restClientException != null) {
			if (restClientException instanceof HttpStatusCodeException httpStatusCodeException) {
				nuovoEvento.setDettaglioEsito(httpStatusCodeException.getResponseBodyAsString());
				nuovoEvento.setSottotipoEsito(httpStatusCodeException.getStatusCode() + "");

				if (httpStatusCodeException.getStatusCode().is5xxServerError()) {
					nuovoEvento.setEsito(EsitoEvento.FAIL);
				} else {
					nuovoEvento.setEsito(EsitoEvento.KO);
				}
			} else {
				nuovoEvento.setDettaglioEsito(restClientException.getMessage());
				nuovoEvento.setSottotipoEsito("500");
			}
		} else if(responseEntity != null){
			nuovoEvento.setDettaglioEsito(HttpStatus.valueOf(responseEntity.getStatusCode().value()).getReasonPhrase());
			nuovoEvento.setSottotipoEsito("" + responseEntity.getStatusCode().value());
			if (responseEntity.getStatusCode().is5xxServerError()) {
				nuovoEvento.setEsito(EsitoEvento.FAIL);
			} else {
				nuovoEvento.setEsito(EsitoEvento.KO);
			}
		}
	}

	default DettaglioRichiesta mapDettagliRichiesta(OffsetDateTime dataStart, String urlOperazione, String httpMethod, List<Header> headers) {
		DettaglioRichiesta dettaglioRichiesta = new DettaglioRichiesta();

		dettaglioRichiesta.setDataOraRichiesta(dataStart);
		dettaglioRichiesta.setMethod(httpMethod);
		dettaglioRichiesta.setUrl(urlOperazione);
		dettaglioRichiesta.setHeaders(headers);

		return dettaglioRichiesta;
	}

	default DettaglioRisposta mapDettagliRisposta(ResponseEntity<?> responseEntity, OffsetDateTime dataEnd, RestClientException restClientException) {
		DettaglioRisposta dettaglioRisposta = new DettaglioRisposta();

		dettaglioRisposta.setDataOraRisposta(dataEnd);
		
		// mappa gli headers
		List<Header> headers = new ArrayList<>();
		
		if(responseEntity != null) {
			dettaglioRisposta.setStatus(BigDecimal.valueOf(responseEntity.getStatusCode().value()));
			
			HttpHeaders httpHeaders = responseEntity.getHeaders();
			httpHeaders.forEach((key, value) -> {
				Header header = new Header();
				header.setNome(key);
				header.setValore(value.get(0));
				headers.add(header);
			});
		} else if(restClientException != null) {
			if (restClientException instanceof HttpStatusCodeException httpStatusCodeException) {
				dettaglioRisposta.setStatus(BigDecimal.valueOf(httpStatusCodeException.getStatusCode().value()));
				
				HttpHeaders httpHeaders = httpStatusCodeException.getResponseHeaders();
				if(httpHeaders != null) {
					httpHeaders.forEach((key, value) -> {
						Header header = new Header();
						header.setNome(key);
						header.setValore(value.get(0));
						headers.add(header);
					});
				}
			} else {
				dettaglioRisposta.setStatus(BigDecimal.valueOf(500));
			}
		} else {
			dettaglioRisposta.setStatus(BigDecimal.valueOf(500));
		}

		dettaglioRisposta.setHeaders(headers);

		return dettaglioRisposta;
	}
}