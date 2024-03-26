package it.govpay.aca.test.utils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Random;

import it.govpay.aca.entity.VersamentoAcaEntity.StatoVersamento;
import it.govpay.aca.entity.VersamentoAcaEntity.TIPO;
import it.govpay.aca.test.costanti.Costanti;
import it.govpay.aca.test.entity.ApplicazioneEntity;
import it.govpay.aca.test.entity.DominioEntity;
import it.govpay.aca.test.entity.VersamentoFullEntity;
import it.govpay.aca.test.repository.ApplicazioneRepository;
import it.govpay.aca.test.repository.DominioRepository;
import it.govpay.aca.test.repository.VersamentoFullRepository;

public class VersamentoUtils {
	
	public static Long getNextVersamentoId(VersamentoFullRepository versamentoFullRepository) {
		Long maxId = versamentoFullRepository.findTopByOrderByIdDesc();
		if(maxId == null) maxId = 1L;
		
		return maxId + 1;
	}

	public static VersamentoFullEntity creaVersamentoNonEseguito(VersamentoFullRepository versamentoFullRepository, ApplicazioneRepository applicazioneRepository,
			DominioRepository dominioRepository) throws UnsupportedEncodingException {
		String idPendenza = VersamentoUtils.generaIdPendenza();
		
		return creaVersamento(idPendenza, StatoVersamento.NON_ESEGUITO, versamentoFullRepository, applicazioneRepository, dominioRepository);
	}
	
	public static VersamentoFullEntity creaVersamentoEseguito(VersamentoFullRepository versamentoFullRepository, ApplicazioneRepository applicazioneRepository,
			DominioRepository dominioRepository) throws UnsupportedEncodingException {
		String idPendenza = VersamentoUtils.generaIdPendenza();
		
		return creaVersamento(idPendenza, StatoVersamento.ESEGUITO, versamentoFullRepository, applicazioneRepository, dominioRepository);
	}

	public static VersamentoFullEntity creaVersamento(String idPendenza, StatoVersamento statoVersamento, VersamentoFullRepository versamentoFullRepository, ApplicazioneRepository applicazioneRepository,
			DominioRepository dominioRepository) throws UnsupportedEncodingException {
		String causale = ("Pagamento n" +idPendenza);
		return creaVersamento(idPendenza, statoVersamento, causale, versamentoFullRepository, applicazioneRepository, dominioRepository);
	}
	
	public static VersamentoFullEntity creaVersamento(String idPendenza,
			StatoVersamento statoVersamento, String causaleVersamento, 
			VersamentoFullRepository versamentoFullRepository, ApplicazioneRepository applicazioneRepository,
			DominioRepository dominioRepository) throws UnsupportedEncodingException {
		
		ApplicazioneEntity applicazione = applicazioneRepository.findByCodApplicazione(Costanti.CODAPPLICAZIONE);
		DominioEntity dominio = dominioRepository.findByCodDominio(Costanti.CODDOMINIO);
		
		
		
		
		OffsetDateTime dataCreazione = OffsetDateTime.now();
		OffsetDateTime dataScadenza = null;
		OffsetDateTime dataUltimaComunicazioneAca = OffsetDateTime.now().minusMinutes(1);
		OffsetDateTime dataUltimaModificaAca = OffsetDateTime.now();
		OffsetDateTime dataValidita = null;
		
		String debitoreAnagrafica = Costanti.DEBITORE_ANAGRAFICA;
		String debitoreIdentificativo = Costanti.DEBITORE_CF;
		TIPO debitoreTipo = TIPO.F;
		Double importoTotale = Double.valueOf("10.00");
		String numeroAvviso = VersamentoUtils.generaNumeroAvviso();
		String iuvVersamento = VersamentoUtils.getIuvFromNumeroAvviso(numeroAvviso);
		
		Long id = VersamentoUtils.getNextVersamentoId(versamentoFullRepository);

		return creaVersamento(id, statoVersamento, causaleVersamento, applicazione.getId(), dominio.getId(), 
				idPendenza, dataCreazione, dataScadenza, dataUltimaComunicazioneAca, dataUltimaModificaAca, 
				dataValidita, debitoreAnagrafica, debitoreIdentificativo, debitoreTipo, importoTotale, numeroAvviso, iuvVersamento);
	}
	
	
	public static VersamentoFullEntity creaVersamento(Long id, StatoVersamento statoVersamento, String causaleVersamento, Long idApplicazione, Long idDominio,
            String codVersamentoEnte, OffsetDateTime dataCreazione, OffsetDateTime dataScadenza, OffsetDateTime dataUltimaComunicazioneAca, OffsetDateTime dataUltimaModificaAca,
            OffsetDateTime dataValidita, String debitoreAnagrafica, String debitoreIdentificativo, TIPO debitoreTipo, Double importoTotale, String numeroAvviso, String iuvVersamento) throws UnsupportedEncodingException {
        
        VersamentoFullEntity versamentoAcaEntity = new VersamentoFullEntity();
        
        versamentoAcaEntity.setCausaleVersamento(VersamentoUtils.encode(causaleVersamento));
        versamentoAcaEntity.setIdApplicazione(idApplicazione);
        versamentoAcaEntity.setIdDominio(idDominio);
        versamentoAcaEntity.setCodVersamentoEnte(codVersamentoEnte);
        versamentoAcaEntity.setDataCreazione(dataCreazione);
        versamentoAcaEntity.setDataScadenza(dataScadenza);
        versamentoAcaEntity.setDataUltimaComunicazioneAca(dataUltimaComunicazioneAca);
        versamentoAcaEntity.setDataUltimaModificaAca(dataUltimaModificaAca);
        versamentoAcaEntity.setDataValidita(dataValidita);
        versamentoAcaEntity.setDebitoreAnagrafica(debitoreAnagrafica);
        versamentoAcaEntity.setDebitoreIdentificativo(debitoreIdentificativo);
        versamentoAcaEntity.setDebitoreTipo(debitoreTipo);
        versamentoAcaEntity.setImportoTotale(importoTotale);
        versamentoAcaEntity.setNumeroAvviso(numeroAvviso);
        versamentoAcaEntity.setIuvVersamento(iuvVersamento);
        versamentoAcaEntity.setStatoVersamento(statoVersamento);
        
        versamentoAcaEntity.setId(id);

        return versamentoAcaEntity;
    }
	

	public static String generaIdPendenza() {
		return ""+System.currentTimeMillis();
	}

	public static String getIuvFromNumeroAvviso(String numeroAvviso) {
		if(numeroAvviso.startsWith("3"))
			return numeroAvviso.substring(1);
		else
			return numeroAvviso.substring(3);
	}

	public static String generaNumeroAvviso() {
        Random random = new Random();
        StringBuilder stringBuilder = new StringBuilder();

        // Iniziamo con un numero casuale tra 0 e 3
        int firstDigit = random.nextInt(4);
        stringBuilder.append(firstDigit);

        // Aggiungiamo 17 cifre casuali
        for (int i = 0; i < 17; i++) {
            int digit = random.nextInt(10); // numeri casuali da 0 a 9
            stringBuilder.append(digit);
        }

        return stringBuilder.toString();
    }
	
	public static String encode(String causale){
		if(causale == null) return null;
		return "01 " + Base64.getEncoder().encodeToString(causale.getBytes(StandardCharsets.UTF_8));
	}
}
