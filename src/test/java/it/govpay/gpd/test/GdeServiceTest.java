package it.govpay.gpd.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.gde.client.beans.NuovoEvento;
import it.govpay.gpd.client.beans.PaymentPositionModel;
import it.govpay.gpd.entity.VersamentoGpdEntity;
import it.govpay.gpd.gde.client.EventiApi;
import it.govpay.gpd.gde.mapper.EventoGdpMapperImpl;
import it.govpay.gpd.gde.service.GdeService;
import it.govpay.gpd.service.GpdApiService;

@ExtendWith(MockitoExtension.class)
@DisplayName("Test GdeService - copertura getGpdBasePath catch block")
class GdeServiceTest {

	@Mock
	GpdApiService gpdApiService;

	@Mock
	EventiApi gdeApi;

	@Mock
	EventoGdpMapperImpl eventoGdpMapper;

	private GdeService gdeService;

	@BeforeEach
	void setUp() {
		ObjectMapper objectMapper = new ObjectMapper();
		gdeService = new GdeService(objectMapper, gdeApi, eventoGdpMapper, gpdApiService);
		ReflectionTestUtils.setField(gdeService, "gdeEnabled", Boolean.TRUE);
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
}
