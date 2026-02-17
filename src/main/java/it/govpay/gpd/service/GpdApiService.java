package it.govpay.gpd.service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import it.govpay.common.client.model.Connettore;
import it.govpay.common.client.service.ConnettoreService;
import it.govpay.common.entity.IntermediarioEntity;
import it.govpay.common.repository.IntermediarioRepository;
import it.govpay.gpd.client.api.DebtPositionActionsApiApi;
import it.govpay.gpd.client.api.DebtPositionsApiApi;
import it.govpay.gpd.client.config.RestTemplateConfig;

@Service
public class GpdApiService {

	private Logger logger = LoggerFactory.getLogger(GpdApiService.class);

	private final IntermediarioRepository intermediarioRepository;
	private final ConnettoreService connettoreService;

	private final ConcurrentHashMap<String, DebtPositionsApiApi> gpdApiCache = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, DebtPositionActionsApiApi> gpdActionsApiCache = new ConcurrentHashMap<>();

	public GpdApiService(IntermediarioRepository intermediarioRepository, ConnettoreService connettoreService) {
		this.intermediarioRepository = intermediarioRepository;
		this.connettoreService = connettoreService;
	}

	private String resolveConnectorCode(String codDominio) {
		Optional<IntermediarioEntity> intermediarioOpt = intermediarioRepository.findByCodDominio(codDominio);
		IntermediarioEntity intermediario = intermediarioOpt.orElseThrow(() ->
			new IllegalStateException("Nessun intermediario trovato per il dominio: " + codDominio));

		String codConnettore = intermediario.getCodConnettoreAca();
		if (codConnettore == null || codConnettore.isBlank()) {
			throw new IllegalStateException(
				"Connettore ACA non configurato per l'intermediario " + intermediario.getCodIntermediario()
				+ " (dominio: " + codDominio + ")");
		}

		logger.debug("Dominio {} -> Intermediario {} -> Connettore ACA: {}",
			codDominio, intermediario.getCodIntermediario(), codConnettore);
		return codConnettore;
	}

	public DebtPositionsApiApi getGpdApi(String codDominio) {
		String codConnettore = resolveConnectorCode(codDominio);
		return gpdApiCache.computeIfAbsent(codConnettore, code -> {
			RestTemplate restTemplate = connettoreService.getRestTemplate(code);

			MappingJackson2HttpMessageConverter converter =
				new MappingJackson2HttpMessageConverter(RestTemplateConfig.createObjectMapper());
			restTemplate.getMessageConverters().removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
			restTemplate.getMessageConverters().add(0, converter);

			Connettore connettore = connettoreService.getConnettore(code);
			it.govpay.gpd.client.api.impl.ApiClient apiClient = new it.govpay.gpd.client.api.impl.ApiClient(restTemplate);
			apiClient.setBasePath(connettore.getUrl());

			logger.info("Creata istanza DebtPositionsApiApi per connettore {} (URL: {})", code, connettore.getUrl());
			return new DebtPositionsApiApi(apiClient);
		});
	}

	public DebtPositionActionsApiApi getGpdActionsApi(String codDominio) {
		String codConnettore = resolveConnectorCode(codDominio);
		return gpdActionsApiCache.computeIfAbsent(codConnettore, code -> {
			RestTemplate restTemplate = connettoreService.getRestTemplate(code);

			MappingJackson2HttpMessageConverter converter =
				new MappingJackson2HttpMessageConverter(RestTemplateConfig.createObjectMapper());
			restTemplate.getMessageConverters().removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
			restTemplate.getMessageConverters().add(0, converter);

			Connettore connettore = connettoreService.getConnettore(code);
			it.govpay.gpd.client.api.impl.ApiClient apiClient = new it.govpay.gpd.client.api.impl.ApiClient(restTemplate);
			apiClient.setBasePath(connettore.getUrl());

			logger.info("Creata istanza DebtPositionActionsApiApi per connettore {} (URL: {})", code, connettore.getUrl());
			return new DebtPositionActionsApiApi(apiClient);
		});
	}

	public String getGpdBasePath(String codDominio) {
		String codConnettore = resolveConnectorCode(codDominio);
		return connettoreService.getConnettore(codConnettore).getUrl();
	}
}
