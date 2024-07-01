package it.govpay.gpd.test;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.gpd.entity.MapEntry;
import it.govpay.gpd.entity.Metadata;
import it.govpay.gpd.repository.VersamentoGpdRepository;
import it.govpay.gpd.repository.VersamentoRepository;
import it.govpay.gpd.test.entity.SingoloVersamentoFullEntity;
import it.govpay.gpd.test.entity.VersamentoFullEntity;
import it.govpay.gpd.test.repository.ApplicazioneRepository;
import it.govpay.gpd.test.repository.DominioRepository;
import it.govpay.gpd.test.repository.IbanAccreditoRepository;
import it.govpay.gpd.test.repository.TipoTributoRepository;
import it.govpay.gpd.test.repository.TributoRepository;
import it.govpay.gpd.test.repository.UoRepository;
import it.govpay.gpd.test.repository.VersamentoFullRepository;
import it.govpay.gpd.test.utils.VersamentoUtils;

public abstract class UC_00_BaseTest {

	// repository per le letture delle entity  
	
	@Autowired
	VersamentoFullRepository versamentoFullRepository;

	@Autowired
	ApplicazioneRepository applicazioneRepository;

	@Autowired
	DominioRepository dominioRepository;
	
	@Autowired
	IbanAccreditoRepository ibanAccreditoRepository;
	
	@Autowired
	TributoRepository tributoRepository;
	
	@Autowired
	TipoTributoRepository tipoTributoRepository;
	
	@Autowired
	UoRepository uoRepository;
	
	// repository per i controlli

	@Autowired
	VersamentoGpdRepository versamentoGpdRepository;

	@Autowired
	VersamentoRepository versamentoRepository;
	
	// object mapper
	
	@Autowired
	ObjectMapper objectMapper;
	
	public VersamentoFullEntity creaVersamentoNonEseguito() {
		return VersamentoUtils.creaVersamentoNonEseguitoMonovoceRiferimento(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository, this.tributoRepository);
	}
	
	public VersamentoFullEntity creaVersamentoNonEseguitoMBT() {
		return VersamentoUtils.creaVersamentoNonEseguitoMonovoceMBT(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository);
	}
	
	public VersamentoFullEntity creaVersamentoNonEseguitoDefinito() {
		return VersamentoUtils.creaVersamentoNonEseguitoMonovoceDefinito(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository, this.ibanAccreditoRepository);
	}
	
	public VersamentoFullEntity creaVersamentoNonEseguitoConRata(String codRata) {
		VersamentoFullEntity creaVersamentoNonEseguitoMonovoceRiferimento = VersamentoUtils.creaVersamentoNonEseguitoMonovoceRiferimento(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository, this.tributoRepository);
		creaVersamentoNonEseguitoMonovoceRiferimento.setCodRata(codRata);
		return creaVersamentoNonEseguitoMonovoceRiferimento;
	}
	
	public VersamentoFullEntity creaVersamentoNonEseguitoConMetadata() {
		VersamentoFullEntity creaVersamentoNonEseguitoMonovoceRiferimento = VersamentoUtils.creaVersamentoNonEseguitoMonovoceRiferimento(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository, this.tributoRepository);
		for (SingoloVersamentoFullEntity singoloVersamentoFullEntity : creaVersamentoNonEseguitoMonovoceRiferimento.getSingoliVersamenti()) {
			
			Metadata metadata = new Metadata();
			List<MapEntry> mapEntries = new ArrayList<MapEntry>();
			MapEntry mapEntry = new MapEntry();
			mapEntry.setKey("chiave");
			mapEntry.setValue("valore");
			mapEntries.add(mapEntry );
			metadata.setValue(mapEntries );
			
			try {
				singoloVersamentoFullEntity.setMetadata(this.objectMapper.writeValueAsString(metadata));
			} catch (JsonProcessingException e) {
			}
		}
		
		return creaVersamentoNonEseguitoMonovoceRiferimento;
	}
}
