package it.govpay.gpd.controller;

import java.time.ZoneId;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.common.batch.controller.AbstractBatchController;
import it.govpay.common.batch.dto.BatchStatusInfo;
import it.govpay.common.batch.dto.LastExecutionInfo;
import it.govpay.common.batch.dto.NextExecutionInfo;
import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.common.client.service.ConnettoreService;
import it.govpay.gpd.costanti.Costanti;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller REST per l'esecuzione manuale e il monitoraggio dei job batch.
 */
@Slf4j
@RestController
@RequestMapping("/api/batch")
public class BatchController extends AbstractBatchController {

    private final Job pendenzaSenderJob;
    private final ConnettoreService connettoreService;

    public BatchController(
            JobExecutionHelper jobExecutionHelper,
            JobExplorer jobExplorer,
            @Qualifier(Costanti.SEND_PENDENZE_GPD_JOBNAME) Job pendenzaSenderJob,
            ConnettoreService connettoreService,
            Environment environment,
            ZoneId applicationZoneId,
            @Value("${scheduler.gpdSenderJob.fixedDelayString:600000}") long schedulerIntervalMillis) {
        super(jobExecutionHelper, jobExplorer, environment, applicationZoneId, schedulerIntervalMillis);
        this.pendenzaSenderJob = pendenzaSenderJob;
        this.connettoreService = connettoreService;
    }

    @Override
    protected Job getJob() {
        return pendenzaSenderJob;
    }

    @Override
    protected String getJobName() {
        return Costanti.SEND_PENDENZE_GPD_JOBNAME;
    }

    @GetMapping("/eseguiJob")
    public ResponseEntity<Object> eseguiJobEndpoint(
            @RequestParam(name = "force", required = false, defaultValue = "false") boolean force) {
        return eseguiJob(force);
    }

    @GetMapping("/status")
    public ResponseEntity<BatchStatusInfo> getStatusEndpoint() {
        return getStatus();
    }

    @GetMapping("/lastExecution")
    public ResponseEntity<LastExecutionInfo> getLastExecutionEndpoint() {
        return getLastExecution();
    }

    @GetMapping("/nextExecution")
    public ResponseEntity<NextExecutionInfo> getNextExecutionEndpoint() {
        return getNextExecution();
    }

    @Override
    protected ResponseEntity<String> clearCache() {
        connettoreService.clearCache();
        return ResponseEntity.ok("Cache connettori svuotata");
    }
}
