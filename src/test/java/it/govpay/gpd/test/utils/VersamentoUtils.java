package it.govpay.gpd.test.utils;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.gpd.entity.VersamentoGpdEntity;
import it.govpay.gpd.entity.VersamentoGpdEntity.StatoVersamento;
import it.govpay.gpd.entity.VersamentoGpdEntity.TIPO;
import it.govpay.gpd.repository.VersamentoGpdRepository;
import it.govpay.gpd.repository.VersamentoFilters;
import it.govpay.gpd.test.costanti.Costanti;
import it.govpay.gpd.test.entity.ApplicazioneEntity;
import it.govpay.gpd.test.entity.DominioEntity;
import it.govpay.gpd.test.entity.SingoloVersamentoFullEntity;
import it.govpay.gpd.test.entity.VersamentoFullEntity;
import it.govpay.gpd.test.entity.SingoloVersamentoFullEntity.StatoSingoloVersamento;
import it.govpay.gpd.test.repository.ApplicazioneRepository;
import it.govpay.gpd.test.repository.DominioRepository;
import it.govpay.gpd.test.repository.IbanAccreditoRepository;
import it.govpay.gpd.test.repository.TributoRepository;
import it.govpay.gpd.test.repository.VersamentoFullRepository;

public class VersamentoUtils {
	
	public static Long getNextVersamentoId(VersamentoFullRepository versamentoFullRepository) {
		VersamentoFullEntity versamentoFullEntity = versamentoFullRepository.findFirstByOrderByIdDesc();
		Long maxId = 0L;
		if(versamentoFullEntity != null) maxId = versamentoFullEntity.getId();
		
		return maxId + 1;
	}

	public static VersamentoFullEntity creaVersamentoNonEseguitoMonovoceDefinito(VersamentoFullRepository versamentoFullRepository, ApplicazioneRepository applicazioneRepository,
			DominioRepository dominioRepository, IbanAccreditoRepository ibanAccreditoRepository) {
		String idPendenza = VersamentoUtils.generaIdPendenza();
		
		OffsetDateTime dataUltimaComunicazioneAca = null; 
		VersamentoFullEntity versamentoFullEntity = creaVersamento(idPendenza, StatoVersamento.NON_ESEGUITO,  dataUltimaComunicazioneAca, versamentoFullRepository, applicazioneRepository, dominioRepository);
		
		SingoloVersamentoFullEntity singoloVersamentoFullEntity = creaSingoloVersamentoDefinito(Costanti.CODDOMINIO, 1, versamentoFullEntity.getImportoTotale(), StatoSingoloVersamento.NON_ESEGUITO, null, dominioRepository,
				Costanti.TIPO_CONTABILITA_ALTRO, Costanti.COD_CONTABILITA_GEN, Costanti.CODIBAN_ACCREDITO_DOM_1, null, ibanAccreditoRepository);
		
		Set<SingoloVersamentoFullEntity> singoliVersamenti = new HashSet<>();
		singoliVersamenti.add(singoloVersamentoFullEntity);
		versamentoFullEntity.setSingoliVersamenti(singoliVersamenti);
		
		return versamentoFullEntity;
	}
	
	public static VersamentoFullEntity creaVersamentoNonEseguitoMonovoceRiferimento(VersamentoFullRepository versamentoFullRepository, ApplicazioneRepository applicazioneRepository,
			DominioRepository dominioRepository, TributoRepository tributoRepository) {
		String idPendenza = VersamentoUtils.generaIdPendenza();
		
		OffsetDateTime dataUltimaComunicazioneAca = null; 
		VersamentoFullEntity versamentoFullEntity = creaVersamento(idPendenza, StatoVersamento.NON_ESEGUITO,  dataUltimaComunicazioneAca, versamentoFullRepository, applicazioneRepository, dominioRepository);
		
		SingoloVersamentoFullEntity singoloVersamentoFullEntity = creaSingoloVersamentoRiferimento(Costanti.CODDOMINIO, 1, versamentoFullEntity.getImportoTotale(), StatoSingoloVersamento.NON_ESEGUITO, null, dominioRepository, Costanti.CODTRIBUTO_SEGRETERIA, tributoRepository);
		
		Set<SingoloVersamentoFullEntity> singoliVersamenti = new HashSet<>();
		singoliVersamenti.add(singoloVersamentoFullEntity);
		versamentoFullEntity.setSingoliVersamenti(singoliVersamenti);
		
		return versamentoFullEntity;
	}
	
	public static VersamentoFullEntity creaVersamentoNonEseguitoMonovoceMBT(VersamentoFullRepository versamentoFullRepository, ApplicazioneRepository applicazioneRepository,
			DominioRepository dominioRepository) {
		String idPendenza = VersamentoUtils.generaIdPendenza();
		
		OffsetDateTime dataUltimaComunicazioneAca = null; 
		VersamentoFullEntity versamentoFullEntity = creaVersamento(idPendenza, StatoVersamento.NON_ESEGUITO,  dataUltimaComunicazioneAca, versamentoFullRepository, applicazioneRepository, dominioRepository);
		
		SingoloVersamentoFullEntity singoloVersamentoFullEntity = creaSingoloVersamentoMBT(Costanti.CODDOMINIO, 1, versamentoFullEntity.getImportoTotale(), StatoSingoloVersamento.NON_ESEGUITO, null, dominioRepository,
				Costanti.MBT_HASH_DOCUMENTO, Costanti.MBT_PROVINCIA_RO, Costanti.MBT_TIPO_BOLLO_01);
		
		Set<SingoloVersamentoFullEntity> singoliVersamenti = new HashSet<>();
		singoliVersamenti.add(singoloVersamentoFullEntity);
		versamentoFullEntity.setSingoliVersamenti(singoliVersamenti);
		
		return versamentoFullEntity;
	}
	
	public static VersamentoFullEntity creaVersamentoEseguito(VersamentoFullRepository versamentoFullRepository, ApplicazioneRepository applicazioneRepository,
			DominioRepository dominioRepository) {
		String idPendenza = VersamentoUtils.generaIdPendenza();
		
		OffsetDateTime dataUltimaComunicazioneAca = OffsetDateTime.now().minusMinutes(1);
		return creaVersamento(idPendenza, StatoVersamento.ESEGUITO,  dataUltimaComunicazioneAca, versamentoFullRepository, applicazioneRepository, dominioRepository);
	}
	
	public static VersamentoFullEntity creaVersamento(String idPendenza, StatoVersamento statoVersamento,
			OffsetDateTime dataUltimaComunicazioneAca,
			VersamentoFullRepository versamentoFullRepository, ApplicazioneRepository applicazioneRepository,
			DominioRepository dominioRepository) {
		String causale = ("Pagamento n" +idPendenza);
		return creaVersamento(idPendenza, statoVersamento, causale,  dataUltimaComunicazioneAca, versamentoFullRepository, applicazioneRepository, dominioRepository);
	}
	
	public static VersamentoFullEntity creaVersamento(String idPendenza,
			StatoVersamento statoVersamento, String causaleVersamento, 
			OffsetDateTime dataUltimaComunicazioneAca, 
			VersamentoFullRepository versamentoFullRepository, ApplicazioneRepository applicazioneRepository,
			DominioRepository dominioRepository) {
		
		ApplicazioneEntity applicazione = applicazioneRepository.findByCodApplicazione(Costanti.CODAPPLICAZIONE);
		DominioEntity dominio = dominioRepository.findByCodDominio(Costanti.CODDOMINIO);
		
		OffsetDateTime dataCreazione = OffsetDateTime.now();
		OffsetDateTime dataScadenza = null;
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
            OffsetDateTime dataValidita, String debitoreAnagrafica, String debitoreIdentificativo, TIPO debitoreTipo, Double importoTotale, String numeroAvviso, String iuvVersamento) {
        
        VersamentoFullEntity versamentoFullEntity = new VersamentoFullEntity();
        
        versamentoFullEntity.setCausaleVersamento(VersamentoUtils.encode(causaleVersamento));
        versamentoFullEntity.setIdApplicazione(idApplicazione);
        versamentoFullEntity.setIdDominio(idDominio);
        versamentoFullEntity.setCodVersamentoEnte(codVersamentoEnte);
        versamentoFullEntity.setDataCreazione(dataCreazione);
        versamentoFullEntity.setDataScadenza(dataScadenza);
        versamentoFullEntity.setDataUltimaComunicazioneAca(dataUltimaComunicazioneAca);
        versamentoFullEntity.setDataUltimaModificaAca(dataUltimaModificaAca);
        versamentoFullEntity.setDataValidita(dataValidita);
        versamentoFullEntity.setDebitoreAnagrafica(debitoreAnagrafica);
        versamentoFullEntity.setDebitoreIdentificativo(debitoreIdentificativo);
        versamentoFullEntity.setDebitoreTipo(debitoreTipo);
        versamentoFullEntity.setImportoTotale(importoTotale);
        versamentoFullEntity.setNumeroAvviso(numeroAvviso);
        versamentoFullEntity.setIuvVersamento(iuvVersamento);
        versamentoFullEntity.setStatoVersamento(statoVersamento);
        
        versamentoFullEntity.setId(id);

        return versamentoFullEntity;
    }
	
	public static SingoloVersamentoFullEntity creaSingoloVersamento(String codDominio, int indiceDati, Double importoTotale, StatoSingoloVersamento statoSingoloVersamento, String metadata, DominioRepository dominioRepository ) {
		SingoloVersamentoFullEntity singoloVersamentoFullEntity = new SingoloVersamentoFullEntity();
		if(codDominio != null) {
			singoloVersamentoFullEntity.setIdDominio(dominioRepository.findByCodDominio(codDominio).getId());
		}
		singoloVersamentoFullEntity.setIndiceDati(indiceDati);
		singoloVersamentoFullEntity.setImportoSingoloVersamento(importoTotale);
		singoloVersamentoFullEntity.setCodSingoloVersamentoEnte("" + indiceDati);
		singoloVersamentoFullEntity.setStatoSingoloVersamento(statoSingoloVersamento);
		singoloVersamentoFullEntity.setMetadata(metadata);
		return singoloVersamentoFullEntity;
	}
	
	public static SingoloVersamentoFullEntity creaSingoloVersamentoMBT(String codDominio, int indiceDati, Double importoTotale, StatoSingoloVersamento statoSingoloVersamento, String metadata, DominioRepository dominioRepository,
			String hashDocumento, String provinciaResidenza, String tipoBollo) {
		SingoloVersamentoFullEntity singoloVersamentoFullEntity = creaSingoloVersamento(codDominio, indiceDati, importoTotale, statoSingoloVersamento, metadata, dominioRepository);

		singoloVersamentoFullEntity.setHashDocumento(hashDocumento);
		singoloVersamentoFullEntity.setProvinciaResidenza(provinciaResidenza);
		singoloVersamentoFullEntity.setTipoBollo(tipoBollo);
		
		return singoloVersamentoFullEntity;
	}
	
	public static SingoloVersamentoFullEntity creaSingoloVersamentoRiferimento(String codDominio, int indiceDati, Double importoTotale, StatoSingoloVersamento statoSingoloVersamento, 
			String metadata, DominioRepository dominioRepository, String codTributo, TributoRepository tributoRepository) {
		SingoloVersamentoFullEntity singoloVersamentoFullEntity = creaSingoloVersamento(codDominio, indiceDati, importoTotale, statoSingoloVersamento, metadata, dominioRepository);
		singoloVersamentoFullEntity.setIdTributo(tributoRepository.findByCodTributoAndCodDominio(codTributo, codDominio).getId());
		return singoloVersamentoFullEntity;
	}
	
	public static SingoloVersamentoFullEntity creaSingoloVersamentoDefinito(String codDominio, int indiceDati, Double importoTotale, StatoSingoloVersamento statoSingoloVersamento, 
			String metadata, DominioRepository dominioRepository, String tipoContabilita, String codiceContabilita, String ibanAccredito, String ibanAppoggio, IbanAccreditoRepository ibanAccreditoRepository) {
		SingoloVersamentoFullEntity singoloVersamentoFullEntity = creaSingoloVersamento(codDominio, indiceDati, importoTotale, statoSingoloVersamento, metadata, dominioRepository);
		singoloVersamentoFullEntity.setTipoContabilita(tipoContabilita);
		singoloVersamentoFullEntity.setCodContabilita(codiceContabilita);
		singoloVersamentoFullEntity.setIdIbanAccredito(getIdIbanAccredito(codDominio, ibanAccredito, ibanAccreditoRepository));
		singoloVersamentoFullEntity.setIdIbanAppoggio(getIdIbanAccredito(codDominio, ibanAppoggio, ibanAccreditoRepository));
		return singoloVersamentoFullEntity;
	}

	private static Long getIdIbanAccredito(String codDominio, String ibanAccredito, IbanAccreditoRepository ibanAccreditoRepository) {
		if(ibanAccredito != null) {
			if(codDominio == null) {
				return ibanAccreditoRepository.findByCodIban(ibanAccredito).getId();
			} else {
				return ibanAccreditoRepository.findByCodIbanAndCodDominio(ibanAccredito, codDominio).getId();
			}
		}
		return null;
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
	
	public static long countVersamentiDaSpedire(VersamentoGpdRepository versamentoGpdRepository, Integer numeroGiorni) {
		Specification<VersamentoGpdEntity> spec = VersamentoFilters.creaFiltriRicercaVersamentiDaSpedire(numeroGiorni);
		return versamentoGpdRepository.count(spec); 
	}
}
