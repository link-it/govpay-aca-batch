package it.govpay.gpd.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.gpd.Application;
import it.govpay.gpd.test.entity.VersamentoFullEntity;
import it.govpay.gpd.utils.IuvUtils;

@SpringBootTest(classes = Application.class)
@EnableAutoConfiguration
@DisplayName("Test IUV Utils")
@ActiveProfiles("test")
class IuvTest extends UC_00_BaseTest {

	@Autowired
	ObjectMapper objectMapper;

	@Test
	void TC_01_IuvUtils_checkISO11640() {

		// Iuv numerico
		String iuvNumerico = "00340000002528883";
		assertFalse(IuvUtils.checkISO11640(iuvNumerico));

		// Iuv ISO11640
		String iuv = "RF24000000000000000000610";
		assertTrue(IuvUtils.checkISO11640(iuv));

	}

	@Test
	void TC_01_ObjectMapper() throws JsonProcessingException {
		try {

			assertNotNull(objectMapper);

			VersamentoFullEntity creaVersamentoNonEseguito = this.creaVersamentoNonEseguito();
			
			creaVersamentoNonEseguito.setDataCreazione(OffsetDateTime.now());

			assertNotNull(creaVersamentoNonEseguito);

			String writeValueAsString = this.objectMapper.writeValueAsString(creaVersamentoNonEseguito);

			assertNotNull(writeValueAsString);

			VersamentoFullEntity readValue = this.objectMapper.readValue(writeValueAsString, VersamentoFullEntity.class);

			assertNotNull(readValue);
		} finally {
			this.cleanDB();
		}
	}
}

