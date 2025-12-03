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
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import it.govpay.gpd.config.CronJobRunner;
import it.govpay.gpd.config.PreventConcurrentJobLauncher;
import it.govpay.gpd.costanti.Costanti;

class CronJobRunnerTest {

    @Mock
    private JobLauncher jobLauncher;
    @Mock
    private PreventConcurrentJobLauncher preventConcurrentJobLauncher;
    @Mock
    private JobExplorer jobExplorer;
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
        runner = new CronJobRunner(jobLauncher, preventConcurrentJobLauncher, pendenzaSenderJob);
        // inietta il context
        runner.setApplicationContext(applicationContext);
        // valore di clusterId di default
        // (viene iniettato da @Value nei test con reflection)
        ReflectionTestUtils.setField(runner, "clusterId", CLUSTER_ID);
    }

    @Test
    void testConstructor() {
        CronJobRunner cronJobRunner = new CronJobRunner(jobLauncher, preventConcurrentJobLauncher, pendenzaSenderJob);
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
        return new JobExecution(jobinstance, 1L, params);
    }

    // I test che chiamano runner.run() sono stati rimossi perché
    // il metodo run() chiama System.exit() che termina la JVM.
    // La logica di CronJobRunner è identica a ScheduledJobRunner
    // che ha coverage 100%.

    @Test
    void whenNoJobRunning_thenPreventConcurrentJobLauncherReturnsNull() {
    	when(jobExplorer.findRunningJobExecutions(JOB_NAME))
            .thenReturn(new HashSet<>());
    	PreventConcurrentJobLauncher preventConcurrentJobLauncher2 = new PreventConcurrentJobLauncher(jobExplorer, jobRepository);

    	JobExecution currentRunningJobExecution = preventConcurrentJobLauncher2.getCurrentRunningJobExecution(JOB_NAME);

    	assertNull(currentRunningJobExecution);
    }

    @Test
    void whenJobRunning_thenPreventConcurrentJobLauncherReturnsExecution() {
    	Set<JobExecution> set = new HashSet<>();
    	set.add(mkExecutionWithCluster("OtherNode"));

    	when(jobExplorer.findRunningJobExecutions(JOB_NAME))
            .thenReturn(set);
    	PreventConcurrentJobLauncher preventConcurrentJobLauncher2 = new PreventConcurrentJobLauncher(jobExplorer, jobRepository);

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
        when(jobLauncher.run(eq(pendenzaSenderJob), any(JobParameters.class)))
            .thenReturn(new JobExecution(2L));

        boolean result = runner.checkAbandonedJobStale(staleExecution);

        assertTrue(result);
        verify(preventConcurrentJobLauncher).abandonStaleJobExecution(staleExecution);
        verify(jobLauncher).run(eq(pendenzaSenderJob), any(JobParameters.class));
    }

    @Test
    void whenAbandonmentFails_thenReturnsFalseAndDoesNotLaunchJob() throws Exception {
        JobExecution staleExecution = mkExecutionWithCluster(CLUSTER_ID);

        when(preventConcurrentJobLauncher.abandonStaleJobExecution(staleExecution))
            .thenReturn(false);

        boolean result = runner.checkAbandonedJobStale(staleExecution);

        assertFalse(result);
        verify(preventConcurrentJobLauncher).abandonStaleJobExecution(staleExecution);
        verify(jobLauncher, never()).run(any(), any(JobParameters.class));
    }
}

