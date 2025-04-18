package it.govpay.gpd.test.utils;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;

import org.springframework.data.jpa.domain.Specification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.gpd.entity.MapEntry;
import it.govpay.gpd.entity.Metadata;
import it.govpay.gpd.entity.VersamentoGpdEntity;
import it.govpay.gpd.entity.VersamentoGpdEntity.StatoVersamento;
import it.govpay.gpd.entity.VersamentoGpdEntity.TIPO;
import it.govpay.gpd.repository.VersamentoFilters;
import it.govpay.gpd.repository.VersamentoGpdRepository;
import it.govpay.gpd.test.costanti.Costanti;
import it.govpay.gpd.test.entity.ApplicazioneEntity;
import it.govpay.gpd.test.entity.DominioEntity;
import it.govpay.gpd.test.entity.SingoloVersamentoFullEntity;
import it.govpay.gpd.test.entity.SingoloVersamentoFullEntity.StatoSingoloVersamento;
import it.govpay.gpd.test.entity.VersamentoFullEntity;
import it.govpay.gpd.test.repository.ApplicazioneRepository;
import it.govpay.gpd.test.repository.DominioRepository;
import it.govpay.gpd.test.repository.IbanAccreditoRepository;
import it.govpay.gpd.test.repository.SingoloVersamentoFullRepository;
import it.govpay.gpd.test.repository.TributoRepository;
import it.govpay.gpd.test.repository.VersamentoFullRepository;

public class VersamentoUtils {
	
	public static String generaIupd(String codDominio, String codApplicazione, String codVersamentoEnte) {
		return codDominio + codApplicazione + codVersamentoEnte;
	}
	
	public static Long getNextVersamentoId(VersamentoFullRepository versamentoFullRepository) {
		VersamentoFullEntity versamentoFullEntity = versamentoFullRepository.findFirstByOrderByIdDesc();
		Long maxId = 0L;
		if(versamentoFullEntity != null) maxId = versamentoFullEntity.getId();
		
		return maxId + 1;
	}
	
	public static Long getNextSingoloVersamentoId(SingoloVersamentoFullRepository singoloVersamentoFullRepository) {
		SingoloVersamentoFullEntity singoloVersamentoFullEntity = singoloVersamentoFullRepository.findFirstByOrderByIdDesc();
		Long maxId = 0L;
		if(singoloVersamentoFullEntity != null) maxId = singoloVersamentoFullEntity.getId();
		
		return maxId + 1;
	}
	
	public static VersamentoFullEntity creaVersamentoNonEseguitoMultivoce(VersamentoFullRepository versamentoFullRepository, SingoloVersamentoFullRepository singoloVersamentoFullRepository, ApplicazioneRepository applicazioneRepository,
			DominioRepository dominioRepository, IbanAccreditoRepository ibanAccreditoRepository, ObjectMapper objectMapper, boolean generaMetadataSingoloVersamento) {
		String idPendenza = VersamentoUtils.generaIdPendenza();
		
		OffsetDateTime dataUltimaComunicazioneAca = null; 
		VersamentoFullEntity versamentoFullEntity = creaVersamento(idPendenza, StatoVersamento.NON_ESEGUITO,  dataUltimaComunicazioneAca, versamentoFullRepository, applicazioneRepository, dominioRepository);
		
		versamentoFullEntity = versamentoFullRepository.save(versamentoFullEntity);
		
		// transfer 1
		Long singoloVersamentoId = VersamentoUtils.getNextSingoloVersamentoId(singoloVersamentoFullRepository);
		SingoloVersamentoFullEntity singoloVersamentoFullEntity = creaSingoloVersamentoDefinito(singoloVersamentoId, Costanti.CODDOMINIO, 1, versamentoFullEntity.getImportoTotale() /2, StatoSingoloVersamento.NON_ESEGUITO, null, dominioRepository,
				Costanti.TIPO_CONTABILITA_ALTRO, Costanti.COD_CONTABILITA_GEN, Costanti.CODIBAN_ACCREDITO_DOM_1, null, ibanAccreditoRepository);
		singoloVersamentoFullEntity.setIdVersamento(versamentoFullEntity.getId());
		
		if(generaMetadataSingoloVersamento) {
			generaMetadata(objectMapper, singoloVersamentoFullEntity);
		}
		
		singoloVersamentoFullRepository.save(singoloVersamentoFullEntity);
		
		// transfer 2
		singoloVersamentoId = VersamentoUtils.getNextSingoloVersamentoId(singoloVersamentoFullRepository);
		
		singoloVersamentoFullEntity = creaSingoloVersamentoDefinito(singoloVersamentoId, Costanti.CODDOMINIO, 2, versamentoFullEntity.getImportoTotale()/2, StatoSingoloVersamento.NON_ESEGUITO, null, dominioRepository,
				Costanti.TIPO_CONTABILITA_ALTRO, Costanti.COD_CONTABILITA_GEN, Costanti.CODIBAN_ACCREDITO_POSTALE_DOM_1, null, ibanAccreditoRepository);
		singoloVersamentoFullEntity.setIdVersamento(versamentoFullEntity.getId());
		
		if(generaMetadataSingoloVersamento) {
			generaMetadata(objectMapper, singoloVersamentoFullEntity);
		}
		
		singoloVersamentoFullRepository.save(singoloVersamentoFullEntity);
		
		return versamentoFullEntity;
	}

	public static VersamentoFullEntity creaVersamentoNonEseguitoMonovoceDefinito(VersamentoFullRepository versamentoFullRepository, SingoloVersamentoFullRepository singoloVersamentoFullRepository, ApplicazioneRepository applicazioneRepository,
			DominioRepository dominioRepository, IbanAccreditoRepository ibanAccreditoRepository, ObjectMapper objectMapper, boolean generaMetadataSingoloVersamento) {
		String idPendenza = VersamentoUtils.generaIdPendenza();
		
		OffsetDateTime dataUltimaComunicazioneAca = null; 
		VersamentoFullEntity versamentoFullEntity = creaVersamento(idPendenza, StatoVersamento.NON_ESEGUITO,  dataUltimaComunicazioneAca, versamentoFullRepository, applicazioneRepository, dominioRepository);
		
		versamentoFullEntity = versamentoFullRepository.save(versamentoFullEntity);
		
		Long singoloVersamentoId = VersamentoUtils.getNextSingoloVersamentoId(singoloVersamentoFullRepository);
		SingoloVersamentoFullEntity singoloVersamentoFullEntity = creaSingoloVersamentoDefinito(singoloVersamentoId, Costanti.CODDOMINIO, 1, versamentoFullEntity.getImportoTotale(), StatoSingoloVersamento.NON_ESEGUITO, null, dominioRepository,
				Costanti.TIPO_CONTABILITA_ALTRO, Costanti.COD_CONTABILITA_GEN, Costanti.CODIBAN_ACCREDITO_DOM_1, null, ibanAccreditoRepository);
		singoloVersamentoFullEntity.setIdVersamento(versamentoFullEntity.getId());
		
		if(generaMetadataSingoloVersamento) {
			generaMetadata(objectMapper, singoloVersamentoFullEntity);
		}
		
		singoloVersamentoFullRepository.save(singoloVersamentoFullEntity);
		
		return versamentoFullEntity;
	}
	
	public static VersamentoFullEntity creaVersamentoNonEseguitoMonovoceDefinitoConIbanAppoggio(VersamentoFullRepository versamentoFullRepository, SingoloVersamentoFullRepository singoloVersamentoFullRepository, ApplicazioneRepository applicazioneRepository,
			DominioRepository dominioRepository, IbanAccreditoRepository ibanAccreditoRepository, ObjectMapper objectMapper, boolean generaMetadataSingoloVersamento) {
		String idPendenza = VersamentoUtils.generaIdPendenza();
		
		OffsetDateTime dataUltimaComunicazioneAca = null; 
		VersamentoFullEntity versamentoFullEntity = creaVersamento(idPendenza, StatoVersamento.NON_ESEGUITO,  dataUltimaComunicazioneAca, versamentoFullRepository, applicazioneRepository, dominioRepository);
		
		versamentoFullEntity = versamentoFullRepository.save(versamentoFullEntity);
		
		Long singoloVersamentoId = VersamentoUtils.getNextSingoloVersamentoId(singoloVersamentoFullRepository);
		SingoloVersamentoFullEntity singoloVersamentoFullEntity = creaSingoloVersamentoDefinito(singoloVersamentoId, Costanti.CODDOMINIO, 1, versamentoFullEntity.getImportoTotale(), StatoSingoloVersamento.NON_ESEGUITO, null, dominioRepository,
				Costanti.TIPO_CONTABILITA_ALTRO, Costanti.COD_CONTABILITA_GEN, null, Costanti.CODIBAN_ACCREDITO_POSTALE_DOM_1, ibanAccreditoRepository);
		singoloVersamentoFullEntity.setIdVersamento(versamentoFullEntity.getId());
		
		if(generaMetadataSingoloVersamento) {
			generaMetadata(objectMapper, singoloVersamentoFullEntity);
		}
		
		singoloVersamentoFullRepository.save(singoloVersamentoFullEntity);
		
		return versamentoFullEntity;
	}
	
	public static VersamentoFullEntity creaVersamentoNonEseguitoMonovoceRiferimento(VersamentoFullRepository versamentoFullRepository, SingoloVersamentoFullRepository singoloVersamentoFullRepository, ApplicazioneRepository applicazioneRepository,
			DominioRepository dominioRepository, TributoRepository tributoRepository, ObjectMapper objectMapper, boolean generaMetadataSingoloVersamento) {
		String idPendenza = VersamentoUtils.generaIdPendenza();
		
		OffsetDateTime dataUltimaComunicazioneAca = null; 
		VersamentoFullEntity versamentoFullEntity = creaVersamento(idPendenza, StatoVersamento.NON_ESEGUITO,  dataUltimaComunicazioneAca, versamentoFullRepository, applicazioneRepository, dominioRepository);
		
		versamentoFullEntity = versamentoFullRepository.save(versamentoFullEntity);
		
		Long singoloVersamentoId = VersamentoUtils.getNextSingoloVersamentoId(singoloVersamentoFullRepository);
		SingoloVersamentoFullEntity singoloVersamentoFullEntity = creaSingoloVersamentoRiferimento(singoloVersamentoId, Costanti.CODDOMINIO, 1, versamentoFullEntity.getImportoTotale(), StatoSingoloVersamento.NON_ESEGUITO, null, dominioRepository, Costanti.CODTRIBUTO_SEGRETERIA, tributoRepository);
		
		singoloVersamentoFullEntity.setIdVersamento(versamentoFullEntity.getId());
		
		if(generaMetadataSingoloVersamento) {
			generaMetadata(objectMapper, singoloVersamentoFullEntity);
		}
		
		singoloVersamentoFullRepository.save(singoloVersamentoFullEntity);
		
		return versamentoFullEntity;
	}
	
	public static VersamentoFullEntity creaVersamentoNonEseguitoMonovoceRiferimentoConIbanAppoggio(VersamentoFullRepository versamentoFullRepository, SingoloVersamentoFullRepository singoloVersamentoFullRepository, ApplicazioneRepository applicazioneRepository,
			DominioRepository dominioRepository, TributoRepository tributoRepository, ObjectMapper objectMapper, boolean generaMetadataSingoloVersamento) {
		String idPendenza = VersamentoUtils.generaIdPendenza();
		
		OffsetDateTime dataUltimaComunicazioneAca = null; 
		VersamentoFullEntity versamentoFullEntity = creaVersamento(idPendenza, StatoVersamento.NON_ESEGUITO,  dataUltimaComunicazioneAca, versamentoFullRepository, applicazioneRepository, dominioRepository);
		
		versamentoFullEntity = versamentoFullRepository.save(versamentoFullEntity);
		
		Long singoloVersamentoId = VersamentoUtils.getNextSingoloVersamentoId(singoloVersamentoFullRepository);
		SingoloVersamentoFullEntity singoloVersamentoFullEntity = creaSingoloVersamentoRiferimento(singoloVersamentoId, Costanti.CODDOMINIO, 1, versamentoFullEntity.getImportoTotale(), StatoSingoloVersamento.NON_ESEGUITO, null, dominioRepository, Costanti.CODTRIBUTO_DOVUTO_APPOGGIO, tributoRepository);
		
		singoloVersamentoFullEntity.setIdVersamento(versamentoFullEntity.getId());
		
		if(generaMetadataSingoloVersamento) {
			generaMetadata(objectMapper, singoloVersamentoFullEntity);
		}
		
		singoloVersamentoFullRepository.save(singoloVersamentoFullEntity);
		
		return versamentoFullEntity;
	}
	
	public static VersamentoFullEntity creaVersamentoNonEseguitoMonovoceMBT(VersamentoFullRepository versamentoFullRepository, SingoloVersamentoFullRepository singoloVersamentoFullRepository, ApplicazioneRepository applicazioneRepository,
			DominioRepository dominioRepository, ObjectMapper objectMapper, boolean generaMetadataSingoloVersamento) {
		String idPendenza = VersamentoUtils.generaIdPendenza();
		
		OffsetDateTime dataUltimaComunicazioneAca = null; 
		VersamentoFullEntity versamentoFullEntity = creaVersamento(idPendenza, StatoVersamento.NON_ESEGUITO,  dataUltimaComunicazioneAca, versamentoFullRepository, applicazioneRepository, dominioRepository);
		
		versamentoFullEntity = versamentoFullRepository.save(versamentoFullEntity);
		
		Long singoloVersamentoId = VersamentoUtils.getNextSingoloVersamentoId(singoloVersamentoFullRepository);
		SingoloVersamentoFullEntity singoloVersamentoFullEntity = creaSingoloVersamentoMBT(singoloVersamentoId, Costanti.CODDOMINIO, 1, versamentoFullEntity.getImportoTotale(), StatoSingoloVersamento.NON_ESEGUITO, null, dominioRepository,
				Costanti.MBT_HASH_DOCUMENTO, Costanti.MBT_PROVINCIA_RO, Costanti.MBT_TIPO_BOLLO_01);
		singoloVersamentoFullEntity.setIdVersamento(versamentoFullEntity.getId());
		
		if(generaMetadataSingoloVersamento) {
			generaMetadata(objectMapper, singoloVersamentoFullEntity);
		}
		
		singoloVersamentoFullRepository.save(singoloVersamentoFullEntity);
		
		return versamentoFullEntity;
	}
	
	public static VersamentoFullEntity creaVersamentoEseguito(VersamentoFullRepository versamentoFullRepository, ApplicazioneRepository applicazioneRepository,
			DominioRepository dominioRepository) {
		String idPendenza = VersamentoUtils.generaIdPendenza();
		
		OffsetDateTime dataUltimaComunicazioneAca = OffsetDateTime.now().minusMinutes(1);
		VersamentoFullEntity versamentoFullEntity = creaVersamento(idPendenza, StatoVersamento.ESEGUITO,  dataUltimaComunicazioneAca, versamentoFullRepository, applicazioneRepository, dominioRepository);
		
		versamentoFullEntity = versamentoFullRepository.save(versamentoFullEntity);
		
		return versamentoFullEntity;
	}
	
	private static VersamentoFullEntity creaVersamento(String idPendenza, StatoVersamento statoVersamento,
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
	
	public static SingoloVersamentoFullEntity creaSingoloVersamento(Long id, String codDominio, int indiceDati, Double importoTotale, StatoSingoloVersamento statoSingoloVersamento, String metadata, DominioRepository dominioRepository ) {
		SingoloVersamentoFullEntity singoloVersamentoFullEntity = new SingoloVersamentoFullEntity();
		if(codDominio != null) {
			singoloVersamentoFullEntity.setIdDominio(dominioRepository.findByCodDominio(codDominio).getId());
		}
		singoloVersamentoFullEntity.setIndiceDati(indiceDati);
		singoloVersamentoFullEntity.setImportoSingoloVersamento(importoTotale);
		singoloVersamentoFullEntity.setCodSingoloVersamentoEnte("" + indiceDati);
		singoloVersamentoFullEntity.setStatoSingoloVersamento(statoSingoloVersamento);
		singoloVersamentoFullEntity.setMetadata(metadata);
		singoloVersamentoFullEntity.setId(id);
		return singoloVersamentoFullEntity;
	}
	
	public static SingoloVersamentoFullEntity creaSingoloVersamentoMBT(Long id, String codDominio, int indiceDati, Double importoTotale, StatoSingoloVersamento statoSingoloVersamento, String metadata, DominioRepository dominioRepository,
			String hashDocumento, String provinciaResidenza, String tipoBollo) {
		SingoloVersamentoFullEntity singoloVersamentoFullEntity = creaSingoloVersamento(id, codDominio, indiceDati, importoTotale, statoSingoloVersamento, metadata, dominioRepository);

		singoloVersamentoFullEntity.setHashDocumento(hashDocumento);
		singoloVersamentoFullEntity.setProvinciaResidenza(provinciaResidenza);
		singoloVersamentoFullEntity.setTipoBollo(tipoBollo);
		
		return singoloVersamentoFullEntity;
	}
	
	public static SingoloVersamentoFullEntity creaSingoloVersamentoRiferimento(Long id, String codDominio, int indiceDati, Double importoTotale, StatoSingoloVersamento statoSingoloVersamento, 
			String metadata, DominioRepository dominioRepository, String codTributo, TributoRepository tributoRepository) {
		SingoloVersamentoFullEntity singoloVersamentoFullEntity = creaSingoloVersamento(id, codDominio, indiceDati, importoTotale, statoSingoloVersamento, metadata, dominioRepository);
		singoloVersamentoFullEntity.setIdTributo(tributoRepository.findByCodTributoAndCodDominio(codTributo, codDominio).getId());
		return singoloVersamentoFullEntity;
	}
	
	public static SingoloVersamentoFullEntity creaSingoloVersamentoDefinito(Long id, String codDominio, int indiceDati, Double importoTotale, StatoSingoloVersamento statoSingoloVersamento, 
			String metadata, DominioRepository dominioRepository, String tipoContabilita, String codiceContabilita, String ibanAccredito, String ibanAppoggio, IbanAccreditoRepository ibanAccreditoRepository) {
		SingoloVersamentoFullEntity singoloVersamentoFullEntity = creaSingoloVersamento(id, codDominio, indiceDati, importoTotale, statoSingoloVersamento, metadata, dominioRepository);
		singoloVersamentoFullEntity.setTipoContabilita(tipoContabilita);
		singoloVersamentoFullEntity.setCodContabilita(codiceContabilita);
		singoloVersamentoFullEntity.setIdIbanAccredito(getIdIbanAccredito(codDominio, ibanAccredito, ibanAccreditoRepository));
		singoloVersamentoFullEntity.setIdIbanAppoggio(getIdIbanAccredito(codDominio, ibanAppoggio, ibanAccreditoRepository));
		singoloVersamentoFullEntity.setDescrizioneCausaleRPT("Pagamemto XXXXXX");
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
	
	public static void generaMetadata(ObjectMapper objectMapper, SingoloVersamentoFullEntity singoloVersamentoFullEntity) {
		Metadata metadata = new Metadata();
		List<MapEntry> mapEntries = new ArrayList<MapEntry>();
		MapEntry mapEntry = new MapEntry();
		mapEntry.setKey("chiave");
		mapEntry.setValue("valore");
		mapEntries.add(mapEntry );
		metadata.setValue(mapEntries );
		
		try {
			singoloVersamentoFullEntity.setMetadata(objectMapper.writeValueAsString(metadata));
		} catch (JsonProcessingException e) {
			//donothing
		}
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
		
		// debug
		listaVersamentiDaSpedire(versamentoGpdRepository, numeroGiorni);
		
		return versamentoGpdRepository.count(spec); 
	}
	
	public static void listaVersamentiDaSpedire(VersamentoGpdRepository versamentoGpdRepository, Integer numeroGiorni) {
		Specification<VersamentoGpdEntity> spec = VersamentoFilters.creaFiltriRicercaVersamentiDaSpedire(numeroGiorni);
		List<VersamentoGpdEntity> all = versamentoGpdRepository.findAll(spec);
		
		for (VersamentoGpdEntity versamentoGpdEntity : all) {
			System.out.println("ID: " + versamentoGpdEntity.getId());
			System.out.println("Data ultima comunicazione aca: " + versamentoGpdEntity.getDataUltimaComunicazioneAca());
			System.out.println("Data ultima modifica aca: " + versamentoGpdEntity.getDataUltimaModificaAca());
		}
		
	}
	
}
