package it.govpay.gpd.step;

import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.gpd.costanti.Costanti;
import it.govpay.gpd.entity.VersamentoGpdEntity;
import it.govpay.gpd.entity.VersamentoEntity;
import it.govpay.gpd.repository.VersamentoRepository;
import it.govpay.gpd.utils.Utils;

@Component
public class PendenzaWriter implements ItemWriter<VersamentoGpdEntity>{

	private Logger logger = LoggerFactory.getLogger(PendenzaWriter.class);

	@Value("${it.govpay.gpd.time-zone}")
	String timeZone;

	private VersamentoRepository versamentoRepository;
	
	public PendenzaWriter(VersamentoRepository versamentoRepository) {
		this.versamentoRepository = versamentoRepository;
	}

	@Transactional
	@Override
	public void write(Chunk<? extends VersamentoGpdEntity> chunk) throws Exception {
		OffsetDateTime dataEsecuzioneJob = null;

		StepContext context = StepSynchronizationManager.getContext();
		if(context != null) {
			StepExecution stepExecution = context.getStepExecution();
			JobExecution jobExecution = stepExecution.getJobExecution();
			dataEsecuzioneJob = Utils.toOffsetDateTime(jobExecution.getStartTime(), this.timeZone != null ? this.timeZone : Costanti.DEFAULT_TIME_ZONE);
		}

		logger.debug("Salvataggio pendenze: verra' impostata come DataUltimaComunicazioneACA la data di inizio esecuzione del JOB.");

		for (VersamentoGpdEntity item : chunk) {
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
