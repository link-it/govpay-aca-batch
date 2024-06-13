package it.govpay.gpd.test;

import org.springframework.beans.factory.annotation.Autowired;

import it.govpay.gpd.repository.VersamentoGpdRepository;
import it.govpay.gpd.repository.VersamentoRepository;
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
	
	public VersamentoFullEntity creaVersamentoNonEseguito() {
		return VersamentoUtils.creaVersamentoNonEseguitoMonovoceRiferimento(this.versamentoFullRepository, this.applicazioneRepository, this.dominioRepository, this.tributoRepository);
	}
}
