package it.govpay.gpd.service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
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

	// Override dell'URL del servizio v3: stesso connettore/credenziali del v1 per
	// default, solo il base path cambia. Se il prodotto APIM v3 richiede credenziali
	// diverse, si usa anche l'override del connettore sotto (codConnettoreV3).
	@Value("${it.govpay.gpd.batch.client.v3.baseUrl:}")
	private String baseUrlV3;

	// Override globale del connettore ACA per le chiamate v3: quando impostato, tutte
	// le risoluzioni v3 (CRUD, recupero, pubblicazione) usano questo connettore invece
	// di quello dell'intermediario, a prescindere dal dominio. Vuoto per default: stesso
	// connettore del v1, nessuna configurazione aggiuntiva richiesta.
	@Value("${it.govpay.gpd.batch.client.v3.codConnettore:}")
	private String codConnettoreV3;

	private final ConcurrentHashMap<String, DebtPositionsApiApi> gpdApiCache = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, DebtPositionActionsApiApi> gpdActionsApiCache = new ConcurrentHashMap<>();
	// Cache separate per il client GPD v3 - stesso connettore ACA dell'intermediario,
	// stessa risoluzione, solo un ApiClient diverso (contratto v3).
	private final ConcurrentHashMap<String, it.govpay.gpd.client.v3.api.DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi> gpdApiV3Cache = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, it.govpay.gpd.client.v3.api.DebtPositionActionsApiApi> gpdActionsApiV3Cache = new ConcurrentHashMap<>();

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

	// Se e' impostato un override globale, tutti i domini usano quel connettore per le
	// chiamate v3; altrimenti si risolve come per il v1 (stesso connettore
	// dell'intermediario).
	private String resolveConnectorCodeV3(String codDominio) {
		if (this.codConnettoreV3 != null && !this.codConnettoreV3.isBlank()) {
			return this.codConnettoreV3;
		}
		return resolveConnectorCode(codDominio);
	}

	public DebtPositionsApiApi getGpdApi(String codDominio) {
		String codConnettore = resolveConnectorCode(codDominio);
		return gpdApiCache.computeIfAbsent(codConnettore, code -> {
			RestTemplate restTemplate = connettoreService.getRestTemplate(code);

			JacksonJsonHttpMessageConverter converter =
				new JacksonJsonHttpMessageConverter(RestTemplateConfig.createObjectMapper());
			restTemplate.getMessageConverters().removeIf(JacksonJsonHttpMessageConverter.class::isInstance);
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

			JacksonJsonHttpMessageConverter converter =
				new JacksonJsonHttpMessageConverter(RestTemplateConfig.createObjectMapper());
			restTemplate.getMessageConverters().removeIf(JacksonJsonHttpMessageConverter.class::isInstance);
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

	public String getGpdBasePathV3(String codDominio) {
		String codConnettore = resolveConnectorCodeV3(codDominio);
		return resolveBasePathV3(codConnettore);
	}

	public it.govpay.gpd.client.v3.api.DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi getGpdApiV3(String codDominio) {
		String codConnettore = resolveConnectorCodeV3(codDominio);
		return gpdApiV3Cache.computeIfAbsent(codConnettore, code -> {
			RestTemplate restTemplate = connettoreService.getRestTemplate(code);

			JacksonJsonHttpMessageConverter converter =
				new JacksonJsonHttpMessageConverter(RestTemplateConfig.createObjectMapper());
			restTemplate.getMessageConverters().removeIf(JacksonJsonHttpMessageConverter.class::isInstance);
			restTemplate.getMessageConverters().add(0, converter);

			it.govpay.gpd.client.v3.api.impl.ApiClient apiClient = new it.govpay.gpd.client.v3.api.impl.ApiClient(restTemplate);
			apiClient.setBasePath(resolveBasePathV3(code));

			logger.info("Creata istanza DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi per connettore {} (URL: {})", code, apiClient.getBasePath());
			return new it.govpay.gpd.client.v3.api.DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi(apiClient);
		});
	}

	public it.govpay.gpd.client.v3.api.DebtPositionActionsApiApi getGpdActionsApiV3(String codDominio) {
		String codConnettore = resolveConnectorCodeV3(codDominio);
		return gpdActionsApiV3Cache.computeIfAbsent(codConnettore, code -> {
			RestTemplate restTemplate = connettoreService.getRestTemplate(code);

			JacksonJsonHttpMessageConverter converter =
				new JacksonJsonHttpMessageConverter(RestTemplateConfig.createObjectMapper());
			restTemplate.getMessageConverters().removeIf(JacksonJsonHttpMessageConverter.class::isInstance);
			restTemplate.getMessageConverters().add(0, converter);

			it.govpay.gpd.client.v3.api.impl.ApiClient apiClient = new it.govpay.gpd.client.v3.api.impl.ApiClient(restTemplate);
			apiClient.setBasePath(resolveBasePathV3(code));

			logger.info("Creata istanza DebtPositionActionsApiApi (v3) per connettore {} (URL: {})", code, apiClient.getBasePath());
			return new it.govpay.gpd.client.v3.api.DebtPositionActionsApiApi(apiClient);
		});
	}

	private String resolveBasePathV3(String codConnettore) {
		if (this.baseUrlV3 != null && !this.baseUrlV3.isBlank()) {
			return this.baseUrlV3;
		}
		return connettoreService.getConnettore(codConnettore).getUrl();
	}
}
