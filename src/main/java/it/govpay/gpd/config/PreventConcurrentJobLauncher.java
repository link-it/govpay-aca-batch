package it.govpay.gpd.config;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PreventConcurrentJobLauncher {

	private static final Logger log = LoggerFactory.getLogger(PreventConcurrentJobLauncher.class);

    private JobExplorer jobExplorer;

    private JobRepository jobRepository;

    @Value("${it.govpay.gpd.batch.max-execution-hours:24}")
    private int maxExecutionHours;

    public PreventConcurrentJobLauncher(JobExplorer jobExplorer, JobRepository jobRepository) {
    	this.jobExplorer = jobExplorer;
    	this.jobRepository = jobRepository;
	}

    /**
     * Controlla e restituisce l'esecuzione corrente del job, se esiste.
     *
     * @return l'esecuzione corrente del job oppure null se non ce ne sono
     */
    public JobExecution getCurrentRunningJobExecution(String jobName) {
        Set<JobExecution> runningJobs = jobExplorer.findRunningJobExecutions(jobName);
        if (!runningJobs.isEmpty()) {
            // Restituisce la prima esecuzione in corso.
            List<JobExecution> list = runningJobs.stream().toList();

            log.info("Trovati si seguenti Job in esecuzione: ");
			for (JobExecution je : list) {
				log.info("JobExecution corrente: {}", je.getJobInstance().getJobName());
			}

			return list.get(0);
        }
        return null;
    }

    /**
     * Verifica se un'esecuzione del job è in stato anomalo (stale).
     * Un job è considerato stale se:
     * - È in stato UNKNOWN o ABANDONED
     * - È in esecuzione da più ore di maxExecutionHours
     *
     * @param jobExecution l'esecuzione del job da verificare
     * @return true se il job è stale, false altrimenti
     */
    public boolean isJobExecutionStale(JobExecution jobExecution) {
        if (jobExecution == null) {
            return false;
        }

        BatchStatus status = jobExecution.getStatus();
        LocalDateTime startTime = jobExecution.getStartTime();

        // Verifica stati anomali
        if (status == BatchStatus.UNKNOWN || status == BatchStatus.ABANDONED) {
            log.warn("JobExecution {} è in stato anomalo: {}", jobExecution.getId(), status);
            return true;
        }

        // Verifica timeout per job in esecuzione
        if (startTime != null && status == BatchStatus.STARTED) {
            LocalDateTime now = LocalDateTime.now();
            Duration duration = Duration.between(startTime, now);
            long hoursRunning = duration.toHours();

            if (hoursRunning > maxExecutionHours) {
                log.warn("JobExecution {} è in esecuzione da {} ore (limite: {} ore). Considerata stale.",
                    jobExecution.getId(), hoursRunning, maxExecutionHours);
                return true;
            }
        }

        return false;
    }

    /**
     * Abbandona un'esecuzione del job stale, marcandola come FAILED.
     * Aggiorna anche tutti gli step in esecuzione.
     *
     * @param jobExecution l'esecuzione del job da abbandonare
     * @return true se l'operazione è riuscita, false altrimenti
     */
    public boolean abandonStaleJobExecution(JobExecution jobExecution) {
        if (jobExecution == null) {
            return false;
        }

        try {
            log.info("Abbandono JobExecution stale: {} (stato: {}, avviata: {})",
                jobExecution.getId(), jobExecution.getStatus(), jobExecution.getStartTime());

            // Aggiorna lo stato a FAILED e imposta end time
            jobExecution.setStatus(BatchStatus.FAILED);
            jobExecution.setEndTime(LocalDateTime.now());
            jobExecution.setExitStatus(org.springframework.batch.core.ExitStatus.FAILED
                .addExitDescription("Job abbandonato automaticamente: esecuzione stale rilevata dopo "
                    + maxExecutionHours + " ore o stato anomalo"));

            // Aggiorna anche tutti gli step in esecuzione
            jobExecution.getStepExecutions().forEach(stepExecution -> {
                if (stepExecution.getStatus() == BatchStatus.STARTED) {
                    log.info("Abbandono StepExecution: {} (stato: {})",
                        stepExecution.getStepName(), stepExecution.getStatus());
                    stepExecution.setStatus(BatchStatus.FAILED);
                    stepExecution.setEndTime(LocalDateTime.now());
                    stepExecution.setExitStatus(org.springframework.batch.core.ExitStatus.FAILED
                        .addExitDescription("Step abbandonato: job stale"));
                    jobRepository.update(stepExecution);
                }
            });

            // Aggiorna il job execution nel repository
            jobRepository.update(jobExecution);

            log.info("JobExecution {} abbandonata con successo", jobExecution.getId());
            return true;
        } catch (Exception e) {
            log.error("Errore nell'abbandono di JobExecution {}: {}",
                jobExecution.getId(), e.getMessage(), e);
            return false;
        }
    }
}
