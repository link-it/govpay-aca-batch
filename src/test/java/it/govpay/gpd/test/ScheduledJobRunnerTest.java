package it.govpay.gpd.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.test.util.ReflectionTestUtils;

import it.govpay.gpd.config.PreventConcurrentJobLauncher;
import it.govpay.gpd.config.ScheduledJobRunner;
import it.govpay.gpd.costanti.Costanti;

class ScheduledJobRunnerTest {

    @Mock
    private JobLauncher jobLauncher;
    @Mock
    private PreventConcurrentJobLauncher preventConcurrentJobLauncher;
    @Mock
    private Job pendenzaSenderJob;

    @Captor
    private ArgumentCaptor<JobParameters> jobParametersCaptor;

    private ScheduledJobRunner runner;

    private static final String JOB_NAME   = Costanti.SEND_PENDENZE_GPD_JOBNAME;
    private static final String CLUSTER_ID = "GovPay-ACA-Batch";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Creo il runner con i mock
        runner = new ScheduledJobRunner(jobLauncher, preventConcurrentJobLauncher, pendenzaSenderJob);
        // Inietto il valore di clusterId (in produzione lo fa Spring via @Value)
        ReflectionTestUtils.setField(runner, "clusterId", CLUSTER_ID);
    }

    private JobExecution mkExecutionWithCluster(String cluster) {
        JobParameters params = new JobParametersBuilder()
            .addString(Costanti.GOVPAY_GPD_JOB_PARAMETER_CLUSTER_ID, cluster)
            .toJobParameters();
        return new JobExecution(1L, params);
    }

    @Test
    void whenJobRunningOnAnotherNode_thenSkipLaunching() throws Exception {
        JobExecution runningExecution = mkExecutionWithCluster("OtherNode");
        when(preventConcurrentJobLauncher.getCurrentRunningJobExecution(JOB_NAME))
            .thenReturn(runningExecution);
        when(preventConcurrentJobLauncher.isJobExecutionStale(runningExecution))
            .thenReturn(false);
        when(preventConcurrentJobLauncher.getClusterIdFromExecution(runningExecution))
            .thenReturn("OtherNode");

        runner.runBatchPendenzeJob();

        verify(jobLauncher, never()).run(any(), any(JobParameters.class));
    }

    @Test
    void whenJobRunningOnSameNode_thenAlsoSkip() throws Exception {
        JobExecution runningExecution = mkExecutionWithCluster(CLUSTER_ID);
        when(preventConcurrentJobLauncher.getCurrentRunningJobExecution(JOB_NAME))
            .thenReturn(runningExecution);
        when(preventConcurrentJobLauncher.isJobExecutionStale(runningExecution))
            .thenReturn(false);
        when(preventConcurrentJobLauncher.getClusterIdFromExecution(runningExecution))
            .thenReturn(CLUSTER_ID);

        runner.runBatchPendenzeJob();

        verify(jobLauncher, never()).run(any(), any(JobParameters.class));
    }

    @Test
    void whenNoJobRunning_thenLaunchJob() throws Exception {
        when(preventConcurrentJobLauncher.getCurrentRunningJobExecution(JOB_NAME))
            .thenReturn(null);

        JobExecution launched = new JobExecution(2L);
        when(jobLauncher.run(eq(pendenzaSenderJob), any(JobParameters.class)))
            .thenReturn(launched);

        runner.runBatchPendenzeJob();

        verify(jobLauncher).run(eq(pendenzaSenderJob), jobParametersCaptor.capture());

        // Verifica che i parametri del job siano correttamente costruiti
        JobParameters capturedParams = jobParametersCaptor.getValue();
        assertNotNull(capturedParams);
        assertEquals(JOB_NAME, capturedParams.getString(Costanti.GOVPAY_GPD_JOB_ID));
        assertEquals(CLUSTER_ID, capturedParams.getString(Costanti.GOVPAY_GPD_JOB_PARAMETER_CLUSTER_ID));
        assertNotNull(capturedParams.getString(Costanti.GOVPAY_GPD_JOB_PARAMETER_WHEN));
    }

    @Test
    void whenJobIsStaleAndAbandonmentSucceeds_thenLaunchNewJob() throws Exception {
        JobExecution staleExecution = mkExecutionWithCluster(CLUSTER_ID);
        when(preventConcurrentJobLauncher.getCurrentRunningJobExecution(JOB_NAME))
            .thenReturn(staleExecution);
        when(preventConcurrentJobLauncher.isJobExecutionStale(staleExecution))
            .thenReturn(true);
        when(preventConcurrentJobLauncher.abandonStaleJobExecution(staleExecution))
            .thenReturn(true);

        JobExecution launched = new JobExecution(2L);
        when(jobLauncher.run(eq(pendenzaSenderJob), any(JobParameters.class)))
            .thenReturn(launched);

        runner.runBatchPendenzeJob();

        verify(preventConcurrentJobLauncher).abandonStaleJobExecution(staleExecution);
        verify(jobLauncher).run(eq(pendenzaSenderJob), jobParametersCaptor.capture());

        // Verifica che i parametri del job siano correttamente costruiti
        JobParameters capturedParams = jobParametersCaptor.getValue();
        assertNotNull(capturedParams);
        assertEquals(JOB_NAME, capturedParams.getString(Costanti.GOVPAY_GPD_JOB_ID));
        assertEquals(CLUSTER_ID, capturedParams.getString(Costanti.GOVPAY_GPD_JOB_PARAMETER_CLUSTER_ID));
        assertNotNull(capturedParams.getString(Costanti.GOVPAY_GPD_JOB_PARAMETER_WHEN));
    }

    @Test
    void whenJobIsStaleAndAbandonmentFails_thenDoNotLaunchNewJob() throws Exception {
        JobExecution staleExecution = mkExecutionWithCluster(CLUSTER_ID);
        when(preventConcurrentJobLauncher.getCurrentRunningJobExecution(JOB_NAME))
            .thenReturn(staleExecution);
        when(preventConcurrentJobLauncher.isJobExecutionStale(staleExecution))
            .thenReturn(true);
        when(preventConcurrentJobLauncher.abandonStaleJobExecution(staleExecution))
            .thenReturn(false);

        runner.runBatchPendenzeJob();

        verify(preventConcurrentJobLauncher).abandonStaleJobExecution(staleExecution);
        verify(jobLauncher, never()).run(any(), any(JobParameters.class));
    }

    @Test
    void whenJobRunningWithNullClusterId_thenSkipLaunching() throws Exception {
        JobExecution runningExecution = mkExecutionWithCluster(CLUSTER_ID);
        when(preventConcurrentJobLauncher.getCurrentRunningJobExecution(JOB_NAME))
            .thenReturn(runningExecution);
        when(preventConcurrentJobLauncher.isJobExecutionStale(runningExecution))
            .thenReturn(false);
        when(preventConcurrentJobLauncher.getClusterIdFromExecution(runningExecution))
            .thenReturn(null);

        runner.runBatchPendenzeJob();

        verify(jobLauncher, never()).run(any(), any(JobParameters.class));
    }

    @Test
    void whenStaleJobOnDifferentCluster_thenAbandonAndLaunchNew() throws Exception {
        JobExecution staleExecution = mkExecutionWithCluster("OtherNode");
        when(preventConcurrentJobLauncher.getCurrentRunningJobExecution(JOB_NAME))
            .thenReturn(staleExecution);
        when(preventConcurrentJobLauncher.isJobExecutionStale(staleExecution))
            .thenReturn(true);
        when(preventConcurrentJobLauncher.abandonStaleJobExecution(staleExecution))
            .thenReturn(true);

        JobExecution launched = new JobExecution(2L);
        when(jobLauncher.run(eq(pendenzaSenderJob), any(JobParameters.class)))
            .thenReturn(launched);

        runner.runBatchPendenzeJob();

        verify(preventConcurrentJobLauncher).abandonStaleJobExecution(staleExecution);
        verify(jobLauncher).run(eq(pendenzaSenderJob), jobParametersCaptor.capture());

        // Verifica che i parametri del job siano correttamente costruiti
        JobParameters capturedParams = jobParametersCaptor.getValue();
        assertNotNull(capturedParams);
        assertEquals(JOB_NAME, capturedParams.getString(Costanti.GOVPAY_GPD_JOB_ID));
    }

    @Test
    void whenStaleJobOnDifferentClusterAndAbandonmentFails_thenDoNotLaunch() throws Exception {
        JobExecution staleExecution = mkExecutionWithCluster("OtherNode");
        when(preventConcurrentJobLauncher.getCurrentRunningJobExecution(JOB_NAME))
            .thenReturn(staleExecution);
        when(preventConcurrentJobLauncher.isJobExecutionStale(staleExecution))
            .thenReturn(true);
        when(preventConcurrentJobLauncher.abandonStaleJobExecution(staleExecution))
            .thenReturn(false);

        runner.runBatchPendenzeJob();

        verify(preventConcurrentJobLauncher).abandonStaleJobExecution(staleExecution);
        verify(jobLauncher, never()).run(any(), any(JobParameters.class));
    }
}
