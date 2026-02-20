package it.govpay.gpd.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import it.govpay.common.batch.dto.BatchStatusInfo;
import it.govpay.common.batch.dto.LastExecutionInfo;
import it.govpay.common.batch.dto.NextExecutionInfo;
import it.govpay.common.batch.dto.Problem;
import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.common.batch.service.JobConcurrencyService;
import it.govpay.common.client.service.ConnettoreService;
import it.govpay.gpd.costanti.Costanti;

class BatchControllerTest {

    @Mock
    private JobExecutionHelper jobExecutionHelper;

    @Mock
    private JobConcurrencyService jobConcurrencyService;

    @Mock
    private JobExplorer jobExplorer;

    @Mock
    private Job pendenzaSenderJob;

    @Mock
    private ConnettoreService connettoreService;

    @Mock
    private Environment environment;

    private BatchController batchController;

    private static final String CLUSTER_ID = "TestCluster";
    private static final ZoneId ZONE_ID = ZoneId.of("Europe/Rome");
    private static final long SCHEDULER_INTERVAL_MILLIS = 600000L;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(jobExecutionHelper.getJobConcurrencyService()).thenReturn(jobConcurrencyService);
        batchController = new BatchController(jobExecutionHelper, jobExplorer, pendenzaSenderJob,
                connettoreService, environment, ZONE_ID, SCHEDULER_INTERVAL_MILLIS);
    }

    private JobExecution createJobExecution(String clusterId, BatchStatus status) {
        JobInstance jobInstance = new JobInstance(1L, Costanti.SEND_PENDENZE_GPD_JOBNAME);
        JobParameters params = new JobParametersBuilder()
                .addString(JobConcurrencyService.JOB_PARAM_CLUSTER_ID, clusterId)
                .toJobParameters();
        JobExecution execution = new JobExecution(jobInstance, 1L, params);
        execution.setStatus(status);
        execution.setStartTime(LocalDateTime.now().minusMinutes(5));
        execution.setLastUpdated(LocalDateTime.now());
        return execution;
    }

    // ============ Test avvio normale (nessun job in esecuzione) ============

    @Test
    void whenNoJobRunning_thenReturns202Accepted() throws Exception {
        when(jobConcurrencyService.getCurrentRunningJobExecution(Costanti.SEND_PENDENZE_GPD_JOBNAME))
                .thenReturn(null);

        JobExecution mockExecution = createJobExecution(CLUSTER_ID, BatchStatus.COMPLETED);
        when(jobExecutionHelper.runJob(eq(pendenzaSenderJob), eq(Costanti.SEND_PENDENZE_GPD_JOBNAME)))
                .thenReturn(mockExecution);

        ResponseEntity<Object> response = batchController.eseguiJobEndpoint(false);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNull(response.getBody());

        // Attendi che il job asincrono venga avviato
        Awaitility.await()
                .atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(jobExecutionHelper).runJob(eq(pendenzaSenderJob), eq(Costanti.SEND_PENDENZE_GPD_JOBNAME)));
    }

    @Test
    void whenNoJobRunningAndForzaEsecuzioneTrue_thenReturns202Accepted() throws Exception {
        when(jobConcurrencyService.getCurrentRunningJobExecution(Costanti.SEND_PENDENZE_GPD_JOBNAME))
                .thenReturn(null);

        JobExecution mockExecution = createJobExecution(CLUSTER_ID, BatchStatus.COMPLETED);
        when(jobExecutionHelper.runJob(eq(pendenzaSenderJob), eq(Costanti.SEND_PENDENZE_GPD_JOBNAME)))
                .thenReturn(mockExecution);

        ResponseEntity<Object> response = batchController.eseguiJobEndpoint(true);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNull(response.getBody());
    }

    // ============ Test job già in esecuzione (HTTP 409 Conflict) ============

    @Test
    void whenJobAlreadyRunning_thenReturns409Conflict() {
        JobExecution runningExecution = createJobExecution("OtherCluster", BatchStatus.STARTED);
        when(jobConcurrencyService.getCurrentRunningJobExecution(Costanti.SEND_PENDENZE_GPD_JOBNAME))
                .thenReturn(runningExecution);
        when(jobConcurrencyService.isJobExecutionStale(runningExecution))
                .thenReturn(false);
        when(jobConcurrencyService.getClusterIdFromExecution(runningExecution))
                .thenReturn("OtherCluster");

        ResponseEntity<Object> response = batchController.eseguiJobEndpoint(false);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        Problem problem = (Problem) response.getBody();
        assertEquals(409, problem.getStatus());
        assertEquals("Conflitto", problem.getTitle());
    }

    // ============ Test job stale (abbandono automatico) ============

    @Test
    void whenJobIsStale_thenAbandonAndReturns202Accepted() throws Exception {
        JobExecution staleExecution = createJobExecution("StaleCluster", BatchStatus.STARTED);
        when(jobConcurrencyService.getCurrentRunningJobExecution(Costanti.SEND_PENDENZE_GPD_JOBNAME))
                .thenReturn(staleExecution);
        when(jobConcurrencyService.isJobExecutionStale(staleExecution))
                .thenReturn(true);
        when(jobConcurrencyService.abandonStaleJobExecution(staleExecution))
                .thenReturn(true);

        JobExecution mockExecution = createJobExecution(CLUSTER_ID, BatchStatus.COMPLETED);
        when(jobExecutionHelper.runJob(eq(pendenzaSenderJob), eq(Costanti.SEND_PENDENZE_GPD_JOBNAME)))
                .thenReturn(mockExecution);

        ResponseEntity<Object> response = batchController.eseguiJobEndpoint(false);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(jobConcurrencyService).abandonStaleJobExecution(staleExecution);
    }

    @Test
    void whenJobIsStaleButAbandonFails_thenReturns503ServiceUnavailable() {
        JobExecution staleExecution = createJobExecution("StaleCluster", BatchStatus.STARTED);
        when(jobConcurrencyService.getCurrentRunningJobExecution(Costanti.SEND_PENDENZE_GPD_JOBNAME))
                .thenReturn(staleExecution);
        when(jobConcurrencyService.isJobExecutionStale(staleExecution))
                .thenReturn(true);
        when(jobConcurrencyService.abandonStaleJobExecution(staleExecution))
                .thenReturn(false);

        ResponseEntity<Object> response = batchController.eseguiJobEndpoint(false);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        Problem problem = (Problem) response.getBody();
        assertEquals(503, problem.getStatus());
    }

    // ============ Test force=true ============

    @Test
    void whenForzaEsecuzioneAndJobRunning_thenForceAbandonAndReturns202Accepted() throws Exception {
        JobExecution runningExecution = createJobExecution("OtherCluster", BatchStatus.STARTED);
        when(jobConcurrencyService.getCurrentRunningJobExecution(Costanti.SEND_PENDENZE_GPD_JOBNAME))
                .thenReturn(runningExecution);
        when(jobConcurrencyService.forceAbandonJobExecution(eq(runningExecution), anyString()))
                .thenReturn(true);

        JobExecution mockExecution = createJobExecution(CLUSTER_ID, BatchStatus.COMPLETED);
        when(jobExecutionHelper.runJob(eq(pendenzaSenderJob), eq(Costanti.SEND_PENDENZE_GPD_JOBNAME)))
                .thenReturn(mockExecution);

        ResponseEntity<Object> response = batchController.eseguiJobEndpoint(true);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(jobConcurrencyService).forceAbandonJobExecution(eq(runningExecution), anyString());
        // Non deve verificare se è stale quando forza l'esecuzione
        verify(jobConcurrencyService, never()).isJobExecutionStale(any());
    }

    @Test
    void whenForzaEsecuzioneButForceAbandonFails_thenReturns503ServiceUnavailable() {
        JobExecution runningExecution = createJobExecution("OtherCluster", BatchStatus.STARTED);
        when(jobConcurrencyService.getCurrentRunningJobExecution(Costanti.SEND_PENDENZE_GPD_JOBNAME))
                .thenReturn(runningExecution);
        when(jobConcurrencyService.forceAbandonJobExecution(eq(runningExecution), anyString()))
                .thenReturn(false);

        ResponseEntity<Object> response = batchController.eseguiJobEndpoint(true);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        Problem problem = (Problem) response.getBody();
        assertEquals(503, problem.getStatus());
    }

    // ============ Test errore durante l'avvio (HTTP 500) ============

    @Test
    void whenExceptionDuringJobCheck_thenReturns500InternalServerError() {
        when(jobConcurrencyService.getCurrentRunningJobExecution(Costanti.SEND_PENDENZE_GPD_JOBNAME))
                .thenThrow(new RuntimeException("Database connection error"));

        ResponseEntity<Object> response = batchController.eseguiJobEndpoint(false);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        Problem problem = (Problem) response.getBody();
        assertEquals(500, problem.getStatus());
        assertEquals("Errore interno del server", problem.getTitle());
    }

    // ============ Test errore asincrono durante l'esecuzione del job ============

    @Test
    void whenJobLauncherThrowsExceptionAsync_thenReturns202ButLogsError() throws Exception {
        when(jobConcurrencyService.getCurrentRunningJobExecution(Costanti.SEND_PENDENZE_GPD_JOBNAME))
                .thenReturn(null);

        when(jobExecutionHelper.runJob(eq(pendenzaSenderJob), eq(Costanti.SEND_PENDENZE_GPD_JOBNAME)))
                .thenThrow(new RuntimeException("Job execution failed"));

        ResponseEntity<Object> response = batchController.eseguiJobEndpoint(false);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());

        Awaitility.await()
                .atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(jobExecutionHelper).runJob(eq(pendenzaSenderJob), eq(Costanti.SEND_PENDENZE_GPD_JOBNAME)));
    }

    // ============ Test endpoint /status ============

    @Test
    void whenGetStatus_andNoJobRunning_thenReturnsNotRunning() {
        when(jobConcurrencyService.getCurrentRunningJobExecution(Costanti.SEND_PENDENZE_GPD_JOBNAME))
                .thenReturn(null);

        ResponseEntity<BatchStatusInfo> response = batchController.getStatusEndpoint();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isRunning());
        assertNull(response.getBody().getExecutionId());
    }

    @Test
    void whenGetStatus_andJobRunning_thenReturnsRunningStatus() {
        JobExecution runningExecution = createJobExecution(CLUSTER_ID, BatchStatus.STARTED);
        when(jobConcurrencyService.getCurrentRunningJobExecution(Costanti.SEND_PENDENZE_GPD_JOBNAME))
                .thenReturn(runningExecution);
        when(jobConcurrencyService.getClusterIdFromExecution(runningExecution))
                .thenReturn(CLUSTER_ID);

        ResponseEntity<BatchStatusInfo> response = batchController.getStatusEndpoint();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isRunning());
        assertEquals(1L, response.getBody().getExecutionId());
        assertEquals(CLUSTER_ID, response.getBody().getClusterId());
        assertEquals("STARTED", response.getBody().getStatus());
        assertNotNull(response.getBody().getRunningSeconds());
    }

    // ============ Test endpoint /lastExecution ============

    @Test
    void whenGetLastExecution_andNoExecutions_thenReturnsEmptyInfo() {
        when(jobExplorer.getJobInstances(Costanti.SEND_PENDENZE_GPD_JOBNAME, 0, 10))
                .thenReturn(Collections.emptyList());

        ResponseEntity<LastExecutionInfo> response = batchController.getLastExecutionEndpoint();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNull(response.getBody().getExecutionId());
    }

    @Test
    void whenGetLastExecution_andCompletedExecutionExists_thenReturnsLastExecution() {
        JobInstance jobInstance = new JobInstance(1L, Costanti.SEND_PENDENZE_GPD_JOBNAME);
        JobExecution completedExecution = createJobExecution(CLUSTER_ID, BatchStatus.COMPLETED);
        completedExecution.setEndTime(LocalDateTime.now());

        when(jobExplorer.getJobInstances(Costanti.SEND_PENDENZE_GPD_JOBNAME, 0, 10))
                .thenReturn(List.of(jobInstance));
        when(jobExplorer.getJobExecutions(jobInstance))
                .thenReturn(List.of(completedExecution));
        when(jobConcurrencyService.getClusterIdFromExecution(completedExecution))
                .thenReturn(CLUSTER_ID);

        ResponseEntity<LastExecutionInfo> response = batchController.getLastExecutionEndpoint();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getExecutionId());
        assertEquals(CLUSTER_ID, response.getBody().getClusterId());
        assertEquals("COMPLETED", response.getBody().getStatus());
        assertNotNull(response.getBody().getDurationSeconds());
    }

    @Test
    void whenGetLastExecution_andOnlyRunningExecutions_thenReturnsEmptyInfo() {
        JobInstance jobInstance = new JobInstance(1L, Costanti.SEND_PENDENZE_GPD_JOBNAME);
        JobExecution runningExecution = createJobExecution(CLUSTER_ID, BatchStatus.STARTED);

        when(jobExplorer.getJobInstances(Costanti.SEND_PENDENZE_GPD_JOBNAME, 0, 10))
                .thenReturn(List.of(jobInstance));
        when(jobExplorer.getJobExecutions(jobInstance))
                .thenReturn(List.of(runningExecution));

        ResponseEntity<LastExecutionInfo> response = batchController.getLastExecutionEndpoint();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNull(response.getBody().getExecutionId());
    }

    // ============ Test endpoint /nextExecution ============

    @Test
    void whenGetNextExecution_andCronMode_thenReturnsCronInfo() {
        when(environment.matchesProfiles("cron")).thenReturn(true);

        ResponseEntity<NextExecutionInfo> response = batchController.getNextExecutionEndpoint();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("cron", response.getBody().getSchedulingMode());
        assertNotNull(response.getBody().getMessage());
        assertNull(response.getBody().getNextExecutionTime());
    }

    @Test
    void whenGetNextExecution_andSchedulerMode_andNoExecutions_thenReturnsImmediateExecution() {
        when(environment.matchesProfiles("cron")).thenReturn(false);
        when(jobExplorer.getJobInstances(Costanti.SEND_PENDENZE_GPD_JOBNAME, 0, 5))
                .thenReturn(Collections.emptyList());

        ResponseEntity<NextExecutionInfo> response = batchController.getNextExecutionEndpoint();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("scheduler", response.getBody().getSchedulingMode());
        assertNotNull(response.getBody().getNextExecutionTime());
        assertEquals(600000L, response.getBody().getIntervalMillis());
        assertEquals("10 minuti", response.getBody().getIntervalFormatted());
    }

    @Test
    void whenGetNextExecution_andSchedulerMode_andPreviousExecution_thenCalculatesNextTime() {
        JobInstance jobInstance = new JobInstance(1L, Costanti.SEND_PENDENZE_GPD_JOBNAME);
        JobExecution completedExecution = createJobExecution(CLUSTER_ID, BatchStatus.COMPLETED);
        completedExecution.setEndTime(LocalDateTime.now().minusMinutes(5));

        when(environment.matchesProfiles("cron")).thenReturn(false);
        when(jobExplorer.getJobInstances(Costanti.SEND_PENDENZE_GPD_JOBNAME, 0, 5))
                .thenReturn(List.of(jobInstance));
        when(jobExplorer.getJobExecutions(jobInstance))
                .thenReturn(List.of(completedExecution));

        ResponseEntity<NextExecutionInfo> response = batchController.getNextExecutionEndpoint();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("scheduler", response.getBody().getSchedulingMode());
        assertNotNull(response.getBody().getLastCompletedTime());
        assertEquals(600000L, response.getBody().getIntervalMillis());
    }

    @Test
    void whenGetNextExecution_andJobCurrentlyRunning_thenNextTimeIsNull() {
        JobInstance jobInstance = new JobInstance(1L, Costanti.SEND_PENDENZE_GPD_JOBNAME);
        JobExecution completedExecution = createJobExecution(CLUSTER_ID, BatchStatus.COMPLETED);
        // Set end time far in the past so next execution would be in the past
        completedExecution.setEndTime(LocalDateTime.now().minusHours(5));

        JobExecution runningExecution = createJobExecution(CLUSTER_ID, BatchStatus.STARTED);

        when(environment.matchesProfiles("cron")).thenReturn(false);
        when(jobExplorer.getJobInstances(Costanti.SEND_PENDENZE_GPD_JOBNAME, 0, 5))
                .thenReturn(List.of(jobInstance));
        when(jobExplorer.getJobExecutions(jobInstance))
                .thenReturn(List.of(completedExecution));
        when(jobConcurrencyService.getCurrentRunningJobExecution(Costanti.SEND_PENDENZE_GPD_JOBNAME))
                .thenReturn(runningExecution);

        ResponseEntity<NextExecutionInfo> response = batchController.getNextExecutionEndpoint();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("scheduler", response.getBody().getSchedulingMode());
        assertNull(response.getBody().getNextExecutionTime());
    }
}
