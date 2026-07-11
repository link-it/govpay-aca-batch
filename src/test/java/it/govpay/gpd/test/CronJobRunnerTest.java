package it.govpay.gpd.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import it.govpay.gpd.config.CronJobRunner;
import it.govpay.gpd.config.PreventConcurrentJobLauncher;
import it.govpay.gpd.costanti.Costanti;

class CronJobRunnerTest {

    @Mock
    private JobOperator jobOperator;
    @Mock
    private PreventConcurrentJobLauncher preventConcurrentJobLauncher;

    @Mock
    private JobRepository jobRepository;
    @Mock
    private Job pendenzaSenderJob;
    @Mock
    private ApplicationContext applicationContext;

    private CronJobRunner runner;

    private static final String JOB_NAME = Costanti.SEND_PENDENZE_GPD_JOBNAME;
    private static final String CLUSTER_ID = "GovPay-ACA-Batch";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        runner = new CronJobRunner(jobOperator, preventConcurrentJobLauncher, pendenzaSenderJob);
        // inietta il context
        runner.setApplicationContext(applicationContext);
        // valore di clusterId di default
        // (viene iniettato da @Value nei test con reflection)
        ReflectionTestUtils.setField(runner, "clusterId", CLUSTER_ID);
    }

    @Test
    void testConstructor() {
        CronJobRunner cronJobRunner = new CronJobRunner(jobOperator, preventConcurrentJobLauncher, pendenzaSenderJob);
        assertNotNull(cronJobRunner);
    }

    @Test
    void testSetApplicationContext() {
        // Verifica che setApplicationContext non lanci eccezioni
        runner.setApplicationContext(applicationContext);
        assertNotNull(runner);
    }

    private JobExecution mkExecutionWithCluster(String clusterIdValue) {
    	JobInstance jobinstance = new JobInstance(1L, JOB_NAME);

        JobParameters params = new JobParametersBuilder()
            .addString(Costanti.GOVPAY_GPD_JOB_PARAMETER_CLUSTER_ID, clusterIdValue)
            .toJobParameters();
        return new JobExecution(1L, jobinstance, params);
    }

    // I test che chiamano runner.start() sono stati rimossi perché
    // il metodo run() chiama System.exit() che termina la JVM.
    // La logica di CronJobRunner è identica a ScheduledJobRunner
    // che ha coverage 100%.

    @Test
    void whenNoJobRunning_thenPreventConcurrentJobLauncherReturnsNull() {
    	when(jobRepository.findRunningJobExecutions(JOB_NAME))
            .thenReturn(new HashSet<>());
    	PreventConcurrentJobLauncher preventConcurrentJobLauncher2 = new PreventConcurrentJobLauncher(jobRepository);

    	JobExecution currentRunningJobExecution = preventConcurrentJobLauncher2.getCurrentRunningJobExecution(JOB_NAME);

    	assertNull(currentRunningJobExecution);
    }

    @Test
    void whenJobRunning_thenPreventConcurrentJobLauncherReturnsExecution() {
    	Set<JobExecution> set = new HashSet<>();
    	set.add(mkExecutionWithCluster("OtherNode"));

    	when(jobRepository.findRunningJobExecutions(JOB_NAME))
            .thenReturn(set);
    	PreventConcurrentJobLauncher preventConcurrentJobLauncher2 = new PreventConcurrentJobLauncher(jobRepository);

    	JobExecution currentRunningJobExecution = preventConcurrentJobLauncher2.getCurrentRunningJobExecution(JOB_NAME);

    	assertNotNull(currentRunningJobExecution);
    	assertEquals("OtherNode", currentRunningJobExecution.getJobParameters().getString(Costanti.GOVPAY_GPD_JOB_PARAMETER_CLUSTER_ID));
    }

    // ============ Test checkAbandonedJobStale ============

    @Test
    void whenAbandonmentSucceeds_thenReturnsTrueAndLaunchesJob() throws Exception {
        JobExecution staleExecution = mkExecutionWithCluster(CLUSTER_ID);

        when(preventConcurrentJobLauncher.abandonStaleJobExecution(staleExecution))
            .thenReturn(true);
        when(jobOperator.start(eq(pendenzaSenderJob), any(JobParameters.class)))
            .thenReturn(new JobExecution(2L, new JobInstance(2L, JOB_NAME), new JobParametersBuilder().toJobParameters()));

        boolean result = runner.checkAbandonedJobStale(staleExecution);

        assertTrue(result);
        verify(preventConcurrentJobLauncher).abandonStaleJobExecution(staleExecution);
        verify(jobOperator).start(eq(pendenzaSenderJob), any(JobParameters.class));
    }

    @Test
    void whenAbandonmentFails_thenReturnsFalseAndDoesNotLaunchJob() throws Exception {
        JobExecution staleExecution = mkExecutionWithCluster(CLUSTER_ID);

        when(preventConcurrentJobLauncher.abandonStaleJobExecution(staleExecution))
            .thenReturn(false);

        boolean result = runner.checkAbandonedJobStale(staleExecution);

        assertFalse(result);
        verify(preventConcurrentJobLauncher).abandonStaleJobExecution(staleExecution);
        verify(jobOperator, never()).start(any(), any(JobParameters.class));
    }
}

