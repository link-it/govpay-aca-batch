package it.govpay.aca.step;

import java.util.Date;
import java.util.List;

import org.springframework.batch.core.JobParameters;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.aca.entity.VersamentoAcaEntity;
import it.govpay.aca.entity.VersamentoEntity;
import it.govpay.aca.repository.VersamentoRepository;
import it.govpay.aca.service.AcaBatchService;
import it.govpay.aca.utils.Utils;

@Component
public class PendenzaWriter implements ItemWriter<VersamentoAcaEntity>{

	private JobParameters jobParameters;
	
	@Autowired
	VersamentoRepository versamentoRepository;
	
	@Override
	@Transactional
	public void write(List<? extends VersamentoAcaEntity> items) throws Exception {
		Date dataEsecuzioneJob = this.jobParameters.getDate(AcaBatchService.GOVPAY_ACA_JOB_PARAMETER_WHEN);
		
		for (VersamentoAcaEntity item : items) {
            // Esegui l'aggiornamento puntuale delle due date aggiornate del versamento
			VersamentoEntity existingEntity = versamentoRepository.findById(item.getId()).orElse(null);
            if (existingEntity != null) {
            	// Data ultima comunicazione ACA corrisponde al JobParameter di nome 'WHEN'
                existingEntity.setDataUltimaComunicazioneAca(Utils.toLocalDateTime(dataEsecuzioneJob));
                versamentoRepository.save(existingEntity);
            }
        }
	}
}
