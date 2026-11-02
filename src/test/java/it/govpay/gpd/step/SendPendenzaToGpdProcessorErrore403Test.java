package it.govpay.gpd.step;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;

import tools.jackson.databind.ObjectMapper;

import it.govpay.gpd.client.api.DebtPositionsApiApi;
import it.govpay.gpd.client.beans.PaymentPositionModel;
import it.govpay.gpd.entity.VersamentoGpdEntity;
import it.govpay.gpd.gde.service.GdeService;
import it.govpay.gpd.mapper.PaymentPositionModelRequestMapperImpl;
import it.govpay.gpd.repository.SingoloVersamentoGpdRepository;
import it.govpay.gpd.service.GpdApiService;

/**
 * Caricamento rifiutato dal GPD con 403: la pendenza viene ritentata o scartata secondo la policy.
 */
class SendPendenzaToGpdProcessorErrore403Test {

	private MockEnvironment environment;
	private SendPendenzaToGpdProcessor processor;
	private VersamentoGpdEntity item;

	@BeforeEach
	void setUp() throws Exception {
		PaymentPositionModelRequestMapperImpl mapper = mock(PaymentPositionModelRequestMapperImpl.class);
		when(mapper.versamentoGpdToPaymentPositionModel(any())).thenReturn(new PaymentPositionModel());

		DebtPositionsApiApi gpdApi = mock(DebtPositionsApiApi.class);
		when(gpdApi.createPositionWithHttpInfo(any(), any(), any(), anyBoolean()))
				.thenThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", null, null, null));

		GpdApiService gpdApiService = mock(GpdApiService.class);
		when(gpdApiService.getGpdApi(any())).thenReturn(gpdApi);

		this.environment = new MockEnvironment();
		this.processor = new SendPendenzaToGpdProcessor(mapper, gpdApiService, mock(GdeService.class),
				mock(ObjectMapper.class), mock(SingoloVersamentoGpdRepository.class), this.environment);

		this.item = new VersamentoGpdEntity();
		this.item.setIuvVersamento("01234567890123456");
		this.item.setDataScadenza(LocalDateTime.now().plusDays(30));
		this.item.setCodDominio("12345678901");
	}

	@Test
	void pendenzaRitentataConPolicyDisattiva() throws Exception {
		ReflectionTestUtils.setField(this.processor, "scartaErrore403", false);
		assertNull(this.processor.process(this.item));
	}

	@Test
	void pendenzaScartataConPolicyAttiva() throws Exception {
		ReflectionTestUtils.setField(this.processor, "scartaErrore403", true);
		assertSame(this.item, this.processor.process(this.item));
	}

	@Test
	void avvisoSoloConProprietaDeprecata() {
		assertDoesNotThrow(() -> this.processor.verificaProprietaDeprecate());
		this.environment.setProperty(SendPendenzaToGpdProcessor.PROP_REINVIO_403_DEPRECATA, "true");
		assertDoesNotThrow(() -> this.processor.verificaProprietaDeprecate());
	}
}
