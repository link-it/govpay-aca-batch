package it.govpay.gpd.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import it.govpay.common.client.model.Connettore;
import it.govpay.common.client.service.ConnettoreService;
import it.govpay.common.entity.IntermediarioEntity;
import it.govpay.common.repository.IntermediarioRepository;
import it.govpay.gpd.client.api.DebtPositionActionsApiApi;
import it.govpay.gpd.client.api.DebtPositionsApiApi;
import it.govpay.gpd.service.GpdApiService;

@ExtendWith(MockitoExtension.class)
@DisplayName("Test GpdApiService")
class GpdApiServiceTest {

	private static final String COD_DOMINIO = "12345678901";
	private static final String COD_DOMINIO_2 = "98765432109";
	private static final String COD_INTERMEDIARIO = "11111111111";
	private static final String COD_CONNETTORE_ACA = "PAGOPA_ACA";
	private static final String BASE_URL = "http://fakehost:8080/";

	@Mock
	IntermediarioRepository intermediarioRepository;

	@Mock
	ConnettoreService connettoreService;

	@InjectMocks
	GpdApiService gpdApiService;

	private void setupHappyPath(String codDominio) {
		IntermediarioEntity intermediario = IntermediarioEntity.builder()
				.codIntermediario(COD_INTERMEDIARIO)
				.codConnettoreAca(COD_CONNETTORE_ACA)
				.build();

		when(intermediarioRepository.findByCodDominio(codDominio))
				.thenReturn(Optional.of(intermediario));

		when(connettoreService.getRestTemplate(COD_CONNETTORE_ACA))
				.thenReturn(new RestTemplate());

		when(connettoreService.getConnettore(COD_CONNETTORE_ACA))
				.thenReturn(Connettore.builder().url(BASE_URL).build());
	}

	@Test
	@DisplayName("getGpdApi - happy path")
	void getGpdApi_Ok() {
		setupHappyPath(COD_DOMINIO);

		DebtPositionsApiApi result = gpdApiService.getGpdApi(COD_DOMINIO);

		assertNotNull(result);
	}

	@Test
	@DisplayName("getGpdApi - caching: stessa istanza per stesso dominio")
	void getGpdApi_Caching() {
		setupHappyPath(COD_DOMINIO);

		DebtPositionsApiApi first = gpdApiService.getGpdApi(COD_DOMINIO);
		DebtPositionsApiApi second = gpdApiService.getGpdApi(COD_DOMINIO);

		assertSame(first, second);
		verify(connettoreService, times(1)).getRestTemplate(COD_CONNETTORE_ACA);
	}

	@Test
	@DisplayName("getGpdApi - domini diversi, stesso connettore: cache condivisa")
	void getGpdApi_DominiDiversi_StessoConnettore() {
		IntermediarioEntity intermediario = IntermediarioEntity.builder()
				.codIntermediario(COD_INTERMEDIARIO)
				.codConnettoreAca(COD_CONNETTORE_ACA)
				.build();

		when(intermediarioRepository.findByCodDominio(COD_DOMINIO))
				.thenReturn(Optional.of(intermediario));
		when(intermediarioRepository.findByCodDominio(COD_DOMINIO_2))
				.thenReturn(Optional.of(intermediario));
		when(connettoreService.getRestTemplate(COD_CONNETTORE_ACA))
				.thenReturn(new RestTemplate());
		when(connettoreService.getConnettore(COD_CONNETTORE_ACA))
				.thenReturn(Connettore.builder().url(BASE_URL).build());

		DebtPositionsApiApi first = gpdApiService.getGpdApi(COD_DOMINIO);
		DebtPositionsApiApi second = gpdApiService.getGpdApi(COD_DOMINIO_2);

		assertSame(first, second);
		verify(connettoreService, times(1)).getRestTemplate(COD_CONNETTORE_ACA);
	}

	@Test
	@DisplayName("getGpdActionsApi - happy path")
	void getGpdActionsApi_Ok() {
		setupHappyPath(COD_DOMINIO);

		DebtPositionActionsApiApi result = gpdApiService.getGpdActionsApi(COD_DOMINIO);

		assertNotNull(result);
	}

	@Test
	@DisplayName("getGpdActionsApi - caching: stessa istanza per stesso dominio")
	void getGpdActionsApi_Caching() {
		setupHappyPath(COD_DOMINIO);

		DebtPositionActionsApiApi first = gpdApiService.getGpdActionsApi(COD_DOMINIO);
		DebtPositionActionsApiApi second = gpdApiService.getGpdActionsApi(COD_DOMINIO);

		assertSame(first, second);
		verify(connettoreService, times(1)).getRestTemplate(COD_CONNETTORE_ACA);
	}

	@Test
	@DisplayName("getGpdBasePath - happy path")
	void getGpdBasePath_Ok() {
		IntermediarioEntity intermediario = IntermediarioEntity.builder()
				.codIntermediario(COD_INTERMEDIARIO)
				.codConnettoreAca(COD_CONNETTORE_ACA)
				.build();

		when(intermediarioRepository.findByCodDominio(COD_DOMINIO))
				.thenReturn(Optional.of(intermediario));
		when(connettoreService.getConnettore(COD_CONNETTORE_ACA))
				.thenReturn(Connettore.builder().url(BASE_URL).build());

		String result = gpdApiService.getGpdBasePath(COD_DOMINIO);

		assertEquals(BASE_URL, result);
	}

	@Test
	@DisplayName("resolveConnectorCode - intermediario non trovato")
	void resolveConnectorCode_IntermediarioNonTrovato() {
		when(intermediarioRepository.findByCodDominio(COD_DOMINIO))
				.thenReturn(Optional.empty());

		IllegalStateException ex = assertThrows(IllegalStateException.class,
				() -> gpdApiService.getGpdApi(COD_DOMINIO));

		assertEquals("Nessun intermediario trovato per il dominio: " + COD_DOMINIO, ex.getMessage());
	}

	@Test
	@DisplayName("resolveConnectorCode - connettore ACA null")
	void resolveConnectorCode_ConnettoreAcaNull() {
		IntermediarioEntity intermediario = IntermediarioEntity.builder()
				.codIntermediario(COD_INTERMEDIARIO)
				.codConnettoreAca(null)
				.build();

		when(intermediarioRepository.findByCodDominio(COD_DOMINIO))
				.thenReturn(Optional.of(intermediario));

		IllegalStateException ex = assertThrows(IllegalStateException.class,
				() -> gpdApiService.getGpdApi(COD_DOMINIO));

		assertEquals("Connettore ACA non configurato per l'intermediario " + COD_INTERMEDIARIO
				+ " (dominio: " + COD_DOMINIO + ")", ex.getMessage());
	}

	@Test
	@DisplayName("resolveConnectorCode - connettore ACA blank")
	void resolveConnectorCode_ConnettoreAcaBlank() {
		IntermediarioEntity intermediario = IntermediarioEntity.builder()
				.codIntermediario(COD_INTERMEDIARIO)
				.codConnettoreAca("   ")
				.build();

		when(intermediarioRepository.findByCodDominio(COD_DOMINIO))
				.thenReturn(Optional.of(intermediario));

		IllegalStateException ex = assertThrows(IllegalStateException.class,
				() -> gpdApiService.getGpdActionsApi(COD_DOMINIO));

		assertEquals("Connettore ACA non configurato per l'intermediario " + COD_INTERMEDIARIO
				+ " (dominio: " + COD_DOMINIO + ")", ex.getMessage());
	}
}
