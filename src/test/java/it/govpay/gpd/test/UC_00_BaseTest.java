package it.govpay.gpd.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.gpd.entity.SingoloVersamentoGpdEntity;
import it.govpay.gpd.repository.SingoloVersamentoFilters;
import it.govpay.gpd.repository.SingoloVersamentoGpdRepository;
import it.govpay.gpd.repository.VersamentoGpdRepository;
import it.govpay.gpd.repository.VersamentoRepository;
import it.govpay.gpd.test.entity.VersamentoFullEntity;
import it.govpay.gpd.test.repository.ApplicazioneRepository;
import it.govpay.gpd.test.repository.DominioRepository;
import it.govpay.gpd.test.repository.IbanAccreditoRepository;
import it.govpay.gpd.test.repository.SingoloVersamentoFullRepository;
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
	SingoloVersamentoFullRepository singoloVersamentoFullRepository;

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
	
	@Autowired
	SingoloVersamentoGpdRepository singoloVersamentoGpdRepository;
	
	// object mapper
	
	@Autowired
	ObjectMapper objectMapper;
	
	public VersamentoFullEntity creaVersamentoNonEseguito() {
		VersamentoFullEntity versamentoFullEntity = VersamentoUtils.creaVersamentoNonEseguitoMonovoceRiferimento(this.versamentoFullRepository, this.singoloVersamentoFullRepository, this.applicazioneRepository, this.dominioRepository, this.tributoRepository, this.objectMapper, false);
		this.checkVersamentoFullEntity(versamentoFullEntity);
		return versamentoFullEntity;
	}
	
	public VersamentoFullEntity creaVersamentoNonEseguitoRifermentoConTributoIbanAppoggio() {
		VersamentoFullEntity versamentoFullEntity = VersamentoUtils.creaVersamentoNonEseguitoMonovoceRiferimentoConIbanAppoggio(this.versamentoFullRepository, this.singoloVersamentoFullRepository, this.applicazioneRepository, this.dominioRepository, this.tributoRepository, this.objectMapper, false);
		this.checkVersamentoFullEntity(versamentoFullEntity);
		return versamentoFullEntity;
	}
	
	public VersamentoFullEntity creaVersamentoNonEseguitoMBT() {
		VersamentoFullEntity versamentoFullEntity = VersamentoUtils.creaVersamentoNonEseguitoMonovoceMBT(this.versamentoFullRepository, this.singoloVersamentoFullRepository, this.applicazioneRepository, this.dominioRepository, this.objectMapper, false);
		this.checkVersamentoFullEntity(versamentoFullEntity);
		return versamentoFullEntity;
	}
	
	public VersamentoFullEntity creaVersamentoNonEseguitoDefinito() {
		VersamentoFullEntity versamentoFullEntity = VersamentoUtils.creaVersamentoNonEseguitoMonovoceDefinito(this.versamentoFullRepository, this.singoloVersamentoFullRepository, this.applicazioneRepository, this.dominioRepository, this.ibanAccreditoRepository, this.objectMapper, false);
		this.checkVersamentoFullEntity(versamentoFullEntity);
		return versamentoFullEntity;
	}
	
	public VersamentoFullEntity creaVersamentoNonEseguitoDefinitoConIbanAppoggio() {
		VersamentoFullEntity versamentoFullEntity = VersamentoUtils.creaVersamentoNonEseguitoMonovoceDefinitoConIbanAppoggio(this.versamentoFullRepository, this.singoloVersamentoFullRepository, this.applicazioneRepository, this.dominioRepository, this.ibanAccreditoRepository, this.objectMapper, false);
		this.checkVersamentoFullEntity(versamentoFullEntity);
		return versamentoFullEntity;
	}
	
	public VersamentoFullEntity creaVersamentoNonEseguitoConRata(String codRata) {
		VersamentoFullEntity versamentoFullEntity = VersamentoUtils.creaVersamentoNonEseguitoMonovoceRiferimento(this.versamentoFullRepository, this.singoloVersamentoFullRepository, this.applicazioneRepository, this.dominioRepository, this.tributoRepository, this.objectMapper, false);
		versamentoFullEntity.setCodRata(codRata);
		versamentoFullEntity = this.versamentoFullRepository.save(versamentoFullEntity);
		this.checkVersamentoFullEntity(versamentoFullEntity);
		return versamentoFullEntity;
	}
	
	public VersamentoFullEntity creaVersamentoNonEseguitoConMetadata() {
		VersamentoFullEntity versamentoFullEntity = VersamentoUtils.creaVersamentoNonEseguitoMonovoceRiferimento(this.versamentoFullRepository, this.singoloVersamentoFullRepository, this.applicazioneRepository, this.dominioRepository, this.tributoRepository, this.objectMapper, true);
		this.checkVersamentoFullEntity(versamentoFullEntity);
		return versamentoFullEntity;
	}

	public VersamentoFullEntity creaVersamentoNonEseguitoMultivoceDefinito() {
		VersamentoFullEntity versamentoFullEntity = VersamentoUtils.creaVersamentoNonEseguitoMultivoce(this.versamentoFullRepository, this.singoloVersamentoFullRepository, this.applicazioneRepository, this.dominioRepository, this.ibanAccreditoRepository, this.objectMapper, false);
		this.checkVersamentoFullEntity(versamentoFullEntity);
		return versamentoFullEntity;
	}
	
	
	public void checkVersamentoFullEntity(VersamentoFullEntity versamentoFullEntity) {
		Long id = versamentoFullEntity.getId();
		assertTrue(id != null && id > 0);
		
		List<SingoloVersamentoGpdEntity> list = this.singoloVersamentoGpdRepository.findAll(SingoloVersamentoFilters.byVersamentoId(id));
		assertTrue(list != null && !list.isEmpty());
	}
	
	public void cleanDB() {
		this.singoloVersamentoFullRepository.deleteAll();
		this.versamentoFullRepository.deleteAll();
	}
}
