package it.govpay.gpd.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.repository.JobRepository;

import org.springframework.test.util.ReflectionTestUtils;

import it.govpay.gpd.config.PreventConcurrentJobLauncher;
import it.govpay.gpd.costanti.Costanti;

class CronJobRunnerTest {

    @Mock
    private JobExplorer jobExplorer;
    @Mock
    private JobRepository jobRepository;

    private PreventConcurrentJobLauncher preventConcurrentJobLauncher;

    private static final String JOB_NAME = Costanti.SEND_PENDENZE_GPD_JOBNAME;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        preventConcurrentJobLauncher = new PreventConcurrentJobLauncher(jobExplorer, jobRepository);
        // Imposta il timeout a 24 ore (come da configurazione di default)
        ReflectionTestUtils.setField(preventConcurrentJobLauncher, "maxExecutionHours", 24);
    }

    private JobExecution mkExecutionWithCluster(String clusterIdValue) {
    	JobInstance jobinstance = new JobInstance(1L, JOB_NAME);

        JobParameters params = new JobParametersBuilder()
            .addString(Costanti.GOVPAY_GPD_JOB_PARAMETER_CLUSTER_ID, clusterIdValue)
            .toJobParameters();
        return new JobExecution(jobinstance, 1L, params);
    }

    private JobExecution mkExecutionWithClusterAndStatus(String clusterIdValue, BatchStatus status, LocalDateTime startTime) {
    	JobInstance jobinstance = new JobInstance(1L, JOB_NAME);

        JobParameters params = new JobParametersBuilder()
            .addString(Costanti.GOVPAY_GPD_JOB_PARAMETER_CLUSTER_ID, clusterIdValue)
            .toJobParameters();
        JobExecution execution = new JobExecution(jobinstance, 1L, params);
        execution.setStatus(status);
        execution.setStartTime(startTime);
        return execution;
    }

    @Test
    void whenJobRunningOnAnotherNode_thenDetected() {
        // simuliamo un job già in esecuzione con clusterId diverso
        Set<JobExecution> set = new HashSet<>();
        set.add(mkExecutionWithCluster("OtherNode"));

        when(jobExplorer.findRunningJobExecutions(JOB_NAME)).thenReturn(set);

        JobExecution currentRunningJobExecution = preventConcurrentJobLauncher.getCurrentRunningJobExecution(JOB_NAME);

        assertNotNull(currentRunningJobExecution);
        assertEquals("OtherNode", currentRunningJobExecution.getJobParameters().getString(Costanti.GOVPAY_GPD_JOB_PARAMETER_CLUSTER_ID));
    }

    @Test
    void whenJobRunningOnSameNode_thenDetected() {
        // simuliamo un job già in esecuzione con il nostro stesso clusterId
        Set<JobExecution> set = new HashSet<>();
        set.add(mkExecutionWithCluster("GovPay-ACA-Batch"));

        when(jobExplorer.findRunningJobExecutions(JOB_NAME)).thenReturn(set);

        JobExecution currentRunningJobExecution = preventConcurrentJobLauncher.getCurrentRunningJobExecution(JOB_NAME);

        assertNotNull(currentRunningJobExecution);
        assertEquals("GovPay-ACA-Batch", currentRunningJobExecution.getJobParameters().getString(Costanti.GOVPAY_GPD_JOB_PARAMETER_CLUSTER_ID));
    }

    @Test
    void whenNoJobRunning_thenReturnsNull() {
    	when(jobExplorer.findRunningJobExecutions(JOB_NAME)).thenReturn(new HashSet<>());

    	JobExecution currentRunningJobExecution = preventConcurrentJobLauncher.getCurrentRunningJobExecution(JOB_NAME);

    	assertNull(currentRunningJobExecution);
    }

    @Test
    void whenJobInUnknownStatus_thenIsStale() {
        JobExecution execution = mkExecutionWithClusterAndStatus("GovPay-ACA-Batch", BatchStatus.UNKNOWN, LocalDateTime.now());

        assertTrue(preventConcurrentJobLauncher.isJobExecutionStale(execution));
    }

    @Test
    void whenJobInAbandonedStatus_thenIsStale() {
        JobExecution execution = mkExecutionWithClusterAndStatus("GovPay-ACA-Batch", BatchStatus.ABANDONED, LocalDateTime.now());

        assertTrue(preventConcurrentJobLauncher.isJobExecutionStale(execution));
    }

    @Test
    void whenJobRunningForTooLong_thenIsStale() {
        // Job avviato 25 ore fa (oltre il limite di 24 ore)
        LocalDateTime startTime = LocalDateTime.now().minusHours(25);
        JobExecution execution = mkExecutionWithClusterAndStatus("GovPay-ACA-Batch", BatchStatus.STARTED, startTime);

        assertTrue(preventConcurrentJobLauncher.isJobExecutionStale(execution));
    }

    @Test
    void whenJobRunningWithinTimeout_thenNotStale() {
        // Job avviato 1 ora fa (entro il limite di 24 ore)
        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        JobExecution execution = mkExecutionWithClusterAndStatus("GovPay-ACA-Batch", BatchStatus.STARTED, startTime);

        assertFalse(preventConcurrentJobLauncher.isJobExecutionStale(execution));
    }

    @Test
    void whenJobCompleted_thenNotStale() {
        JobExecution execution = mkExecutionWithClusterAndStatus("GovPay-ACA-Batch", BatchStatus.COMPLETED, LocalDateTime.now().minusHours(25));

        assertFalse(preventConcurrentJobLauncher.isJobExecutionStale(execution));
    }

    @Test
    void whenNullExecution_thenNotStale() {
        assertFalse(preventConcurrentJobLauncher.isJobExecutionStale(null));
    }
}
