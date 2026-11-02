package it.govpay.gpd.step;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Risoluzione della policy sulle pendenze rifiutate con 403: nome nuovo, nome deprecato e default.
 */
class SendPendenzaToGpdProcessorPolicy403Test {

	private static boolean risolvi(MockEnvironment env) {
		return Boolean.parseBoolean(env.resolveRequiredPlaceholders(SendPendenzaToGpdProcessor.PLACEHOLDER_SCARTA_403));
	}

	@Test
	void defaultFalse() {
		assertEquals(false, risolvi(new MockEnvironment()));
	}

	@Test
	void nuovaProprieta() {
		assertEquals(true, risolvi(new MockEnvironment().withProperty(SendPendenzaToGpdProcessor.PROP_SCARTA_403, "true")));
	}

	@Test
	void proprietaDeprecataAncoraLetta() {
		assertEquals(true, risolvi(new MockEnvironment().withProperty(SendPendenzaToGpdProcessor.PROP_REINVIO_403_DEPRECATA, "true")));
	}

	@Test
	void nuovaProprietaHaPrecedenza() {
		MockEnvironment env = new MockEnvironment()
				.withProperty(SendPendenzaToGpdProcessor.PROP_REINVIO_403_DEPRECATA, "true")
				.withProperty(SendPendenzaToGpdProcessor.PROP_SCARTA_403, "false");
		assertEquals(false, risolvi(env));
	}
}
