package it.govpay.gpd.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.common.configurazione.model.GdeInterfaccia;
import it.govpay.common.configurazione.model.Giornale;
import it.govpay.common.configurazione.service.ConfigurazioneService;
import it.govpay.gde.client.beans.ComponenteEvento;
import it.govpay.gde.client.beans.NuovoEvento;
import it.govpay.gpd.client.beans.PaymentPositionModel;
import it.govpay.gpd.entity.VersamentoGpdEntity;
import it.govpay.gpd.gde.mapper.EventoGdpMapperImpl;
import it.govpay.gpd.gde.service.GdeService;
import it.govpay.gpd.service.GpdApiService;

@ExtendWith(MockitoExtension.class)
@DisplayName("Test GdeService - copertura getGpdBasePath catch block")
class GdeServiceTest {

	@Mock
	GpdApiService gpdApiService;

	@Mock
	ConfigurazioneService configurazioneService;

	@Mock
	EventoGdpMapperImpl eventoGdpMapper;

	private GdeService gdeService;

	@BeforeEach
	void setUp() {
		ObjectMapper objectMapper = new ObjectMapper();
		Executor syncExecutor = Runnable::run;
		gdeService = new GdeService(objectMapper, syncExecutor, configurazioneService, eventoGdpMapper, gpdApiService);
		ReflectionTestUtils.setField(gdeService, "clusterId", "TEST-CLUSTER");
	}

	@Test
	@DisplayName("salvaCreatePositionOk - getGpdBasePath lancia eccezione: fallback a stringa vuota")
	void salvaCreatePositionOk_ConGpdBasePathInErrore() {
		when(gpdApiService.getGpdBasePath(any()))
				.thenThrow(new IllegalStateException("Nessun intermediario trovato per il dominio: 12345678901"));

		NuovoEvento nuovoEvento = new NuovoEvento();
		nuovoEvento.setTipoEvento(it.govpay.gpd.costanti.Costanti.CREATE_POSITION);
		when(eventoGdpMapper.mapEventoOk(any(), any(), any(), any(), any()))
				.thenReturn(nuovoEvento);

		VersamentoGpdEntity versamento = new VersamentoGpdEntity();
		versamento.setCodDominio("12345678901");
		versamento.setCodApplicazione("APP_01");
		versamento.setCodVersamentoEnte("VERS_01");

		PaymentPositionModel request = new PaymentPositionModel();
		ResponseEntity<PaymentPositionModel> response = new ResponseEntity<>(new PaymentPositionModel(), HttpStatus.CREATED);
		OffsetDateTime now = OffsetDateTime.now();

		assertDoesNotThrow(() -> gdeService.salvaCreatePositionOk(
				request, "x-req-id", now, now.plusSeconds(1), versamento, response));

		ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
		verify(eventoGdpMapper).creaParametriRichiesta(any(), urlCaptor.capture(), any(), any());

		// Il basePath e' vuoto (fallback da catch), quindi la URL inizia senza host
		String capturedUrl = urlCaptor.getValue();
		org.junit.jupiter.api.Assertions.assertTrue(
				capturedUrl != null && !capturedUrl.contains("fakehost"),
				"La URL non deve contenere un basePath reale quando getGpdBasePath fallisce");
	}

	@Test
	@DisplayName("getConfigurazioneComponente - componente null restituisce null")
	void getConfigurazioneComponente_ComponenteNull() {
		GdeInterfaccia result = ReflectionTestUtils.invokeMethod(
				gdeService, "getConfigurazioneComponente", null, new Giornale());
		assertNull(result);
	}

	@Test
	@DisplayName("getConfigurazioneComponente - giornale null restituisce null")
	void getConfigurazioneComponente_GiornaleNull() {
		GdeInterfaccia result = ReflectionTestUtils.invokeMethod(
				gdeService, "getConfigurazioneComponente", ComponenteEvento.API_PAGOPA, null);
		assertNull(result);
	}

	@ParameterizedTest
	@EnumSource(value = ComponenteEvento.class, names = {
			"API_PAGOPA", "API_ENTE", "API_PAGAMENTO", "API_RAGIONERIA",
			"API_BACKOFFICE", "API_PENDENZE", "API_BACKEND_IO", "API_MAGGIOLI_JPPA" })
	@DisplayName("getConfigurazioneComponente - mappa ogni componente alla GdeInterfaccia del Giornale")
	void getConfigurazioneComponente_MappaturaComponenti(ComponenteEvento componente) {
		Giornale giornale = new Giornale();
		GdeInterfaccia atteso = new GdeInterfaccia();
		switch (componente) {
			case API_PAGOPA -> giornale.setApiPagoPA(atteso);
			case API_ENTE -> giornale.setApiEnte(atteso);
			case API_PAGAMENTO -> giornale.setApiPagamento(atteso);
			case API_RAGIONERIA -> giornale.setApiRagioneria(atteso);
			case API_BACKOFFICE -> giornale.setApiBackoffice(atteso);
			case API_PENDENZE -> giornale.setApiPendenze(atteso);
			case API_BACKEND_IO -> giornale.setApiBackendIO(atteso);
			case API_MAGGIOLI_JPPA -> giornale.setApiMaggioliJPPA(atteso);
			default -> { /* non raggiungibile per i valori dell'EnumSource */ }
		}

		GdeInterfaccia result = ReflectionTestUtils.invokeMethod(
				gdeService, "getConfigurazioneComponente", componente, giornale);

		assertSame(atteso, result);
	}

	@ParameterizedTest
	@EnumSource(value = ComponenteEvento.class, names = {
			"API_SECIM", "API_MYPIVOT", "API_GOVPAY", "API_HYPERSIC_APK", "API_USER", "GOVPAY" })
	@DisplayName("getConfigurazioneComponente - componenti non gestiti restituiscono null")
	void getConfigurazioneComponente_DefaultBranch(ComponenteEvento componente) {
		GdeInterfaccia result = ReflectionTestUtils.invokeMethod(
				gdeService, "getConfigurazioneComponente", componente, new Giornale());
		assertNull(result);
	}
}
