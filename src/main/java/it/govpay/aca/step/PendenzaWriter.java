package it.govpay.aca.step;

import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.aca.entity.VersamentoAcaEntity;
import it.govpay.aca.entity.VersamentoEntity;
import it.govpay.aca.repository.VersamentoRepository;
import it.govpay.aca.utils.Utils;

@Component
public class PendenzaWriter implements ItemWriter<VersamentoAcaEntity>{

	private Logger logger = LoggerFactory.getLogger(PendenzaWriter.class);

	@Value("${it.govpay.aca.time-zone:Europe/Rome}")
	String timeZone;
	
	@Autowired
	VersamentoRepository versamentoRepository;
	
	@Transactional
	@Override
	public void write(Chunk<? extends VersamentoAcaEntity> chunk) throws Exception {
		OffsetDateTime dataEsecuzioneJob = null;
		
		if(StepSynchronizationManager.getContext() != null 
				&& StepSynchronizationManager.getContext().getStepExecution() != null 
				&& StepSynchronizationManager.getContext().getStepExecution().getJobExecution() != null) {
			JobExecution jobExecution = StepSynchronizationManager.getContext().getStepExecution().getJobExecution();
			dataEsecuzioneJob = Utils.toOffsetDateTime(jobExecution.getStartTime(), this.timeZone);
		}
		
		logger.debug("Salvataggio pendenze: verra' impostata come DataUltimaComunicazioneACA la data di inizio esecuzione del JOB.");
		
		for (VersamentoAcaEntity item : chunk) {
			logger.debug("Ricerca pendenza con id {}", item.getId());
			// Esegui l'aggiornamento puntuale delle due date aggiornate del versamento
			VersamentoEntity existingEntity = versamentoRepository.findById(item.getId()).orElse(null);
			logger.debug("trovata pendenza {}", existingEntity);
			if (existingEntity != null) {
				logger.debug("Aggiorno pendenza {}, modifica DataUltimaComunicazioneACA [corrente: {}, nuova: {}]", existingEntity.getId(), existingEntity.getDataUltimaComunicazioneAca(), dataEsecuzioneJob);
				// Data ultima comunicazione ACA corrisponde al JobParameter di nome 'WHEN'
				existingEntity.setDataUltimaComunicazioneAca(dataEsecuzioneJob);
				versamentoRepository.save(existingEntity);
			}
		}
	}
}
