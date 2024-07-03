package it.govpay.gpd.test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import it.govpay.gpd.Application;
import it.govpay.gpd.test.utils.VersamentoUtils;
import it.govpay.gpd.utils.IuvUtils;

@SpringBootTest(classes = Application.class)
@EnableAutoConfiguration
@DisplayName("Test IUV Utils")
@ActiveProfiles("test")
class IuvTest extends UC_00_BaseTest {

	@Test
	void TC_01_IuvUtils_checkISO11640() throws Exception {

		// Iuv numerico
		String iuvNumerico = VersamentoUtils.generaIUVNumerico(1, "");
		assertFalse(IuvUtils.checkISO11640(iuvNumerico));
		
		// Iuv ISO11640
		String iuv = VersamentoUtils.generaIUVISO11694(1, "");
		assertTrue(IuvUtils.checkISO11640(iuv));
		
		iuv = VersamentoUtils.generaIUVISO11694(1, "abcde");
		assertTrue(IuvUtils.checkISO11640(iuv));
		
		iuv = VersamentoUtils.generaIUVISO11694(1, "fghil");
		assertTrue(IuvUtils.checkISO11640(iuv));
		
		iuv = VersamentoUtils.generaIUVISO11694(1, "mnopq");
		assertTrue(IuvUtils.checkISO11640(iuv));
		
		iuv = VersamentoUtils.generaIUVISO11694(1, "rstuvz");
		assertTrue(IuvUtils.checkISO11640(iuv));
		
		iuv = VersamentoUtils.generaIUVISO11694(1, "jkwxy");
		assertTrue(IuvUtils.checkISO11640(iuv));
		
		// Iuv non valido
		String iuvNonValido1 = "123";
		assertFalse(IuvUtils.checkISO11640(iuvNonValido1));
		
		// Iuv con caratteri non validi
		String iuvNonValido2 = VersamentoUtils.generaIUVISO11694(1, "1111").replace("1111", "11-1");
		assertFalse(IuvUtils.checkISO11640(iuvNonValido2));
	}
}
