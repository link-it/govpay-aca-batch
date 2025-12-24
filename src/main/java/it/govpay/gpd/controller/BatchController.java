package it.govpay.gpd.controller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.gpd.config.PreventConcurrentJobLauncher;
import it.govpay.gpd.costanti.Costanti;

/**
 * Controller REST per l'esecuzione manuale e il monitoraggio dei job batch.
 */
@RestController
@RequestMapping("/api/batch")
public class BatchController {

    private static final Logger log = LoggerFactory.getLogger(BatchController.class);

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    private final PreventConcurrentJobLauncher preventConcurrentJobLauncher;
    private final Job pendenzaSenderJob;
    private final Environment environment;
    private final ZoneId applicationZoneId;

    @Value("${it.govpay.gpd.batch.clusterId:GovPay-ACA-Batch}")
    private String clusterId;

    @Value("${scheduler.gpdSenderJob.fixedDelayString:600000}")
    private long schedulerIntervalMillis;

    public BatchController(
            JobLauncher jobLauncher,
            JobExplorer jobExplorer,
            PreventConcurrentJobLauncher preventConcurrentJobLauncher,
            @Qualifier(Costanti.SEND_PENDENZE_GPD_JOBNAME) Job pendenzaSenderJob,
            Environment environment,
            @Value("${it.govpay.gpd.time-zone:Europe/Rome}") String timeZone) {
        this.jobLauncher = jobLauncher;
        this.jobExplorer = jobExplorer;
        this.preventConcurrentJobLauncher = preventConcurrentJobLauncher;
        this.pendenzaSenderJob = pendenzaSenderJob;
        this.environment = environment;
        this.applicationZoneId = ZoneId.of(timeZone);
    }

    /**
     * Esegue il job GPD Sender manualmente in modo asincrono.
     * <p>
     * Il servizio avvia il job e restituisce immediatamente la risposta senza attendere
     * la terminazione del batch. Lo stato del job può essere verificato tramite le
     * tabelle Spring Batch o i log.
     *
     * @param force Se true, termina forzatamente l'eventuale esecuzione corrente e avvia una nuova esecuzione
     * @return ResponseEntity con lo stato dell'avvio o Problem in caso di errore
     */
    @GetMapping("/run")
    public ResponseEntity<Object> eseguiJob(
            @RequestParam(name = "force", required = false, defaultValue = "false") boolean force) {
        log.info("Richiesta esecuzione manuale del job {} (force={})", Costanti.SEND_PENDENZE_GPD_JOBNAME, force);

        try {
            ResponseEntity<Object> runningJobResponse = gestisciJobInEsecuzione(force);
            if (runningJobResponse != null) {
                return runningJobResponse;
            }

            return avviaJobAsincrono();

        } catch (Exception e) {
            log.error("Errore durante l'avvio del job: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Problem.internalServerError("Errore durante l'avvio: " + e.getMessage()));
        }
    }

    /**
     * Gestisce l'eventuale job già in esecuzione.
     */
    private ResponseEntity<Object> gestisciJobInEsecuzione(boolean force) {
        JobExecution currentRunningJobExecution = this.preventConcurrentJobLauncher
                .getCurrentRunningJobExecution(Costanti.SEND_PENDENZE_GPD_JOBNAME);

        if (currentRunningJobExecution == null) {
            return null;
        }

        if (force) {
            return gestisciForzaEsecuzione(currentRunningJobExecution);
        }

        if (this.preventConcurrentJobLauncher.isJobExecutionStale(currentRunningJobExecution)) {
            return gestisciJobStale(currentRunningJobExecution);
        }

        return restituisciJobGiaInEsecuzione(currentRunningJobExecution);
    }

    /**
     * Gestisce la terminazione forzata di un job in esecuzione.
     */
    private ResponseEntity<Object> gestisciForzaEsecuzione(JobExecution currentRunningJobExecution) {
        log.warn("Parametro force=true: terminazione forzata di JobExecution {}",
                currentRunningJobExecution.getId());

        if (this.preventConcurrentJobLauncher.forceAbandonJobExecution(currentRunningJobExecution, "Richiesta esecuzione forzata via API REST")) {
            log.info("Job terminato forzatamente con successo. Avvio nuova esecuzione.");
            return null; // Procedi con l'esecuzione
        }

        return ResponseEntity.status(503).body(
                Problem.serviceUnavailable("Impossibile terminare forzatamente il job in esecuzione (JobExecution ID: " + currentRunningJobExecution.getId() + ")"));
    }

    /**
     * Gestisce l'abbandono di un job stale.
     */
    private ResponseEntity<Object> gestisciJobStale(JobExecution currentRunningJobExecution) {
        log.warn("JobExecution {} rilevata come STALE. Procedo con abbandono e riavvio.",
                currentRunningJobExecution.getId());

        if (this.preventConcurrentJobLauncher.abandonStaleJobExecution(currentRunningJobExecution)) {
            log.info("Job stale abbandonato con successo. Avvio nuova esecuzione.");
            return null; // Procedi con l'esecuzione
        }

        return ResponseEntity.status(503).body(
                Problem.serviceUnavailable("Impossibile abbandonare il job stale (JobExecution ID: " + currentRunningJobExecution.getId() + ")"));
    }

    /**
     * Restituisce la risposta quando il job è già in esecuzione.
     */
    private ResponseEntity<Object> restituisciJobGiaInEsecuzione(JobExecution currentRunningJobExecution) {
        String runningClusterId = this.preventConcurrentJobLauncher
                .getClusterIdFromExecution(currentRunningJobExecution);

        String detail = String.format(
                "Il job %s è già in esecuzione (JobExecution ID: %d, Cluster: %s). Usa il parametro force=true per terminarlo forzatamente.",
                Costanti.SEND_PENDENZE_GPD_JOBNAME,
                currentRunningJobExecution.getId(),
                runningClusterId);

        return ResponseEntity.status(409).body(Problem.conflict(detail));
    }

    /**
     * Avvia il job in modo asincrono e restituisce immediatamente la risposta.
     * <p>
     * Il job viene eseguito in un thread separato, permettendo al servizio REST
     * di restituire la risposta senza attendere la terminazione del batch.
     */
    private ResponseEntity<Object> avviaJobAsincrono() {
        JobParameters params = new JobParametersBuilder()
                .addString(Costanti.GOVPAY_GPD_JOB_ID, Costanti.SEND_PENDENZE_GPD_JOBNAME)
                .addString(Costanti.GOVPAY_GPD_JOB_PARAMETER_WHEN, OffsetDateTime.now(applicationZoneId).toString())
                .addString(Costanti.GOVPAY_GPD_JOB_PARAMETER_CLUSTER_ID, this.clusterId)
                .toJobParameters();

        // Avvia il job in modo asincrono
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Avvio asincrono del job {}", Costanti.SEND_PENDENZE_GPD_JOBNAME);
                JobExecution execution = jobLauncher.run(pendenzaSenderJob, params);
                log.info("Job {} terminato con stato: {}", Costanti.SEND_PENDENZE_GPD_JOBNAME, execution.getStatus());
            } catch (Exception e) {
                log.error("Errore durante l'esecuzione asincrona del job: {}", e.getMessage(), e);
            }
        });

        return ResponseEntity.accepted().build();
    }

    // ============ ENDPOINT DI MONITORAGGIO ============

    /**
     * Verifica se il batch è attualmente in esecuzione e da quanto tempo.
     *
     * @return BatchStatusInfo con le informazioni sullo stato corrente
     */
    @GetMapping("/status")
    public ResponseEntity<BatchStatusInfo> getStatus() {
        log.debug("Richiesta stato del batch {}", Costanti.SEND_PENDENZE_GPD_JOBNAME);

        JobExecution currentExecution = this.preventConcurrentJobLauncher
                .getCurrentRunningJobExecution(Costanti.SEND_PENDENZE_GPD_JOBNAME);

        if (currentExecution == null) {
            return ResponseEntity.ok(BatchStatusInfo.builder()
                    .running(false)
                    .build());
        }

        // Calcola la durata dell'esecuzione
        Long runningSeconds = null;
        if (currentExecution.getStartTime() != null) {
            Duration duration = Duration.between(currentExecution.getStartTime(), LocalDateTime.now(applicationZoneId));
            runningSeconds = duration.getSeconds();
        }

        // Trova lo step corrente in esecuzione
        String currentStep = currentExecution.getStepExecutions().stream()
                .filter(se -> se.getStatus() == BatchStatus.STARTED)
                .map(StepExecution::getStepName)
                .findFirst()
                .orElse(null);

        String runningClusterId = this.preventConcurrentJobLauncher.getClusterIdFromExecution(currentExecution);

        return ResponseEntity.ok(BatchStatusInfo.builder()
                .running(true)
                .executionId(currentExecution.getId())
                .clusterId(runningClusterId)
                .startTime(currentExecution.getStartTime())
                .runningSeconds(runningSeconds)
                .status(currentExecution.getStatus().name())
                .currentStep(currentStep)
                .build());
    }

    /**
     * Restituisce le informazioni sull'ultima esecuzione completata del batch.
     *
     * @return LastExecutionInfo con le informazioni sull'ultima esecuzione
     */
    @GetMapping("/lastExecution")
    public ResponseEntity<LastExecutionInfo> getLastExecution() {
        log.debug("Richiesta ultima esecuzione del batch {}", Costanti.SEND_PENDENZE_GPD_JOBNAME);

        JobExecution lastCompletedExecution = findLastCompletedExecution();

        if (lastCompletedExecution == null) {
            return ResponseEntity.ok(LastExecutionInfo.builder().build());
        }

        return ResponseEntity.ok(buildLastExecutionInfo(lastCompletedExecution));
    }

    /**
     * Trova l'ultima esecuzione completata del job.
     */
    private JobExecution findLastCompletedExecution() {
        List<JobInstance> jobInstances = jobExplorer.getJobInstances(Costanti.SEND_PENDENZE_GPD_JOBNAME, 0, 10);

        for (JobInstance jobInstance : jobInstances) {
            List<JobExecution> executions = jobExplorer.getJobExecutions(jobInstance);
            for (JobExecution execution : executions) {
                if (isCompletedExecution(execution)) {
                    return execution;
                }
            }
        }
        return null;
    }

    /**
     * Verifica se un'esecuzione è in uno stato completato (non in corso).
     */
    private boolean isCompletedExecution(JobExecution execution) {
        BatchStatus status = execution.getStatus();
        return status != BatchStatus.STARTED
            && status != BatchStatus.STARTING
            && status != BatchStatus.STOPPING;
    }

    /**
     * Costruisce l'oggetto LastExecutionInfo da una JobExecution.
     */
    private LastExecutionInfo buildLastExecutionInfo(JobExecution execution) {
        return LastExecutionInfo.builder()
                .executionId(execution.getId())
                .clusterId(this.preventConcurrentJobLauncher.getClusterIdFromExecution(execution))
                .startTime(execution.getStartTime())
                .endTime(execution.getEndTime())
                .durationSeconds(calculateDurationSeconds(execution))
                .status(execution.getStatus().name())
                .exitCode(getExitCode(execution))
                .exitDescription(getTruncatedExitDescription(execution))
                .build();
    }

    /**
     * Calcola la durata in secondi di un'esecuzione.
     */
    private Long calculateDurationSeconds(JobExecution execution) {
        if (execution.getStartTime() == null || execution.getEndTime() == null) {
            return null;
        }
        return Duration.between(execution.getStartTime(), execution.getEndTime()).getSeconds();
    }

    /**
     * Ottiene l'exit code di un'esecuzione.
     */
    private String getExitCode(JobExecution execution) {
        return execution.getExitStatus().getExitCode();
    }

    /**
     * Ottiene l'exit description troncata a 500 caratteri.
     */
    private String getTruncatedExitDescription(JobExecution execution) {
        String description = execution.getExitStatus().getExitDescription();
        if (description != null && description.length() > 500) {
            return description.substring(0, 500) + "...";
        }
        return description;
    }

    /**
     * Restituisce le informazioni sulla prossima esecuzione schedulata.
     *
     * @return NextExecutionInfo con le informazioni sulla prossima esecuzione
     */
    @GetMapping("/nextExecution")
    public ResponseEntity<NextExecutionInfo> getNextExecution() {
        log.debug("Richiesta prossima esecuzione del batch {}", Costanti.SEND_PENDENZE_GPD_JOBNAME);

        // Verifica se siamo in modalità cron (esterna)
        boolean isCronMode = environment.matchesProfiles("cron");

        if (isCronMode) {
            return ResponseEntity.ok(NextExecutionInfo.builder()
                    .schedulingMode("cron")
                    .message("Scheduling gestito da cron esterno (OS/container)")
                    .build());
        }

        // Modalità scheduler interno
        String intervalFormatted = formatInterval(schedulerIntervalMillis);

        // Trova l'ultima esecuzione completata per calcolare la prossima
        LocalDateTime lastCompletedTime = null;
        LocalDateTime nextExecutionTime = null;

        List<JobInstance> jobInstances = jobExplorer.getJobInstances(Costanti.SEND_PENDENZE_GPD_JOBNAME, 0, 5);
        for (JobInstance jobInstance : jobInstances) {
            List<JobExecution> executions = jobExplorer.getJobExecutions(jobInstance);
            for (JobExecution execution : executions) {
                if (execution.getEndTime() != null) {
                    lastCompletedTime = execution.getEndTime();
                    // La prossima esecuzione è dopo l'intervallo configurato
                    nextExecutionTime = lastCompletedTime.plusNanos(schedulerIntervalMillis * 1_000_000);
                    break;
                }
            }
            if (lastCompletedTime != null) break;
        }

        // Se non c'è mai stata un'esecuzione, la prossima sarà immediata (o quasi)
        LocalDateTime now = LocalDateTime.now(applicationZoneId);
        if (nextExecutionTime == null) {
            nextExecutionTime = now;
        }

        // Se la prossima esecuzione è nel passato, significa che il batch è in attesa
        if (nextExecutionTime.isBefore(now)) {
            // Verifica se c'è un job in esecuzione
            JobExecution currentExecution = this.preventConcurrentJobLauncher
                    .getCurrentRunningJobExecution(Costanti.SEND_PENDENZE_GPD_JOBNAME);
            if (currentExecution != null) {
                nextExecutionTime = null; // In esecuzione, non c'è prossima schedulata
            } else {
                nextExecutionTime = now; // Dovrebbe partire a breve
            }
        }

        return ResponseEntity.ok(NextExecutionInfo.builder()
                .schedulingMode("scheduler")
                .nextExecutionTime(nextExecutionTime)
                .intervalMillis(schedulerIntervalMillis)
                .intervalFormatted(intervalFormatted)
                .lastCompletedTime(lastCompletedTime)
                .build());
    }

    /**
     * Formatta un intervallo in millisecondi in formato human-readable.
     */
    private String formatInterval(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            long remainingMinutes = minutes % 60;
            if (remainingMinutes > 0) {
                return String.format("%d ore %d minuti", hours, remainingMinutes);
            }
            return String.format("%d ore", hours);
        } else if (minutes > 0) {
            return String.format("%d minuti", minutes);
        } else {
            return String.format("%d secondi", seconds);
        }
    }
}
