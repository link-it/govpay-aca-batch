package it.govpay.gpd.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.govpay.gpd.entity.PosizioneDebitoriaGpdEntity;
import it.govpay.gpd.utils.Utils;
import it.govpay.pendenze.identificativo.IdentificatorePosizioneDebitoria;

@DisplayName("Test Utils - IUPD posizione debitoria")
class UtilsTest {

	@Test
	@DisplayName("generaIupdPosizione - stessa composizione di IdentificatorePosizioneDebitoria.comporre")
	void generaIupdPosizione_StessaComposizioneDellaLibreria() {
		PosizioneDebitoriaGpdEntity posizione = new PosizioneDebitoriaGpdEntity();
		posizione.setCodApplicazione("APP_01");
		posizione.setCodDominio("12345678901");
		posizione.setCodDocumento("DOC_01");

		String iupd = Utils.generaIupdPosizione(posizione);

		assertEquals(IdentificatorePosizioneDebitoria.comporre("APP_01", "12345678901", "DOC_01"), iupd);
		assertEquals("APP_0112345678901DOC_01", iupd);
	}
}
