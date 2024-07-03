package it.govpay.gpd.gde.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.gde.client.beans.DettaglioRichiesta;
import it.govpay.gde.client.beans.DettaglioRisposta;
import it.govpay.gde.client.beans.Header;
import it.govpay.gde.client.beans.NuovoEvento;
import it.govpay.gpd.costanti.Costanti;

public class GdeUtils {

	private GdeUtils () {}

	public static void serializzaPayload(ObjectMapper objectMapper, NuovoEvento nuovoEvento, Object request, ResponseEntity<?> response) {
		serializzaPayload(objectMapper, nuovoEvento, request, response, null);
	}

	public static void serializzaPayload(ObjectMapper objectMapper, NuovoEvento nuovoEvento, Object request, ResponseEntity<?> response, RestClientException e) {
		DettaglioRichiesta parametriRichiesta = nuovoEvento.getParametriRichiesta();
		DettaglioRisposta parametriRisposta = nuovoEvento.getParametriRisposta();

		if(request != null && parametriRichiesta != null) {
			parametriRichiesta.setPayload(writeValueAsString(objectMapper, request));
		}

		if(parametriRisposta != null) {
			if(e != null) {
				parametriRisposta.setPayload(writeValueAsString(objectMapper, e.getMessage()));
			} else if(response != null) {
				parametriRisposta.setPayload(writeValueAsString(objectMapper, response.getBody()));
			} 
		}
	}

	public static String creaUrlCreatePosition(String codDominio, String baseUrl, String operationPath, Boolean toPublish) {
		Map<String, String> queryParams = new HashMap<>();
		if(toPublish != null) {
			queryParams.put(Costanti.TO_PUBLISH, toPublish.toString());
		}

		Map<String, String> urlParams = new HashMap<>();
		queryParams.put(("{" + Costanti.ORGANIZATION_FISCAL_CODE + "}"), codDominio);

		return GdeUtils.valorizzaUrl(baseUrl, operationPath, urlParams, queryParams);
	}
	
	public static String creaUrlUpdatePosition(String codDominio, String iupd, String baseUrl, String operationPath, Boolean toPublish) {
		Map<String, String> queryParams = new HashMap<>();
		if(toPublish != null) {
			queryParams.put(Costanti.TO_PUBLISH, toPublish.toString());
		}

		Map<String, String> urlParams = new HashMap<>();
		queryParams.put(("{" + Costanti.ORGANIZATION_FISCAL_CODE + "}"), codDominio);
		queryParams.put(("{" + Costanti.IUPD + "}"), iupd);

		return GdeUtils.valorizzaUrl(baseUrl, operationPath, urlParams, queryParams);
	}

	public static String valorizzaUrl(String url, String operationPath, Map<String, String> urlParams, Map<String, String> queryParams) {

		if(operationPath != null) {
			if(url.endsWith("/")) {
				url = url + operationPath.substring(1);
			} else {
				url = url + operationPath;
			}
		}

		if(urlParams != null) {
			for (Entry<String, String> urlParam : urlParams.entrySet()) {
				url = url.replace(urlParam.getKey(), urlParam.getValue());
			}
		}

		url = appendQueryString(url, queryParams);

		return url;
	}

	private static String appendQueryString(String url, Map<String, String> queryParams) {
		if(queryParams != null && queryParams.size() > 0) {
			StringBuilder sb = new StringBuilder();
			for (Entry<String, String> queryParam : queryParams.entrySet()) {
				if(sb.length() > 0) {
					sb.append("&");
				}

				sb.append(queryParam.getKey()).append("=").append(queryParam.getValue());
			}

			if(url.contains("?")) {
				url = url + "&" + sb.toString(); 
			} else {
				url = url + "?" + sb.toString();
			}
		}
		return url;
	}

	public static String writeValueAsString(ObjectMapper objectMapper, Object obj) {
		try {
			return objectMapper.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			return Costanti.MSG_PAYLOAD_NON_SERIALIZZABILE;
		}
	}

	public static void aggiungiHeaderXRequestId(List<Header> headers, String xRequestId) {
		Header headerAccept = new Header();
		headerAccept.setNome(Costanti.HEADER_X_REQUEST_ID);
		headerAccept.setValore(xRequestId);
		headers.add(headerAccept);
	}

	public static List<Header> creaHeaderRichiesta(boolean isGet) {
		List<Header> headers = new ArrayList<>();
		Header headerAccept = new Header();
		headerAccept.setNome(HttpHeaders.ACCEPT);
		headerAccept.setValore(Costanti.GOVPAY_GDE_HEADER_ACCEPT);
		headers.add(headerAccept);
		if(!isGet) {
			Header headerContentType = new Header();
			headerContentType.setNome(HttpHeaders.CONTENT_TYPE);
			headerContentType.setValore(Costanti.GOVPAY_GDE_HEADER_CONTENT_TYPE);
			headers.add(headerContentType);
		}
		return headers;
	}
}
