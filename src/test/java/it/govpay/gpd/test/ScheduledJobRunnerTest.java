package it.govpay.gpd.test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        // Preparo il preventConcurrent per restituire un'esecuzione in corso su un altro nodo
        when(preventConcurrentJobLauncher.getCurrentRunningJobExecution(JOB_NAME))
            .thenReturn(mkExecutionWithCluster("OtherNode"));

        // Invoco il metodo schedulato
        runner.runBatchPendenzeJob();

        // Verifico che jobLauncher.run NON venga chiamato
        verify(jobLauncher, never()).run(any(), any(JobParameters.class));
    }

    @Test
    void whenJobRunningOnSameNode_thenAlsoSkip() throws Exception {
        // Preparo il preventConcurrent per restituire un'esecuzione in corso sullo stesso nodo
        when(preventConcurrentJobLauncher.getCurrentRunningJobExecution(JOB_NAME))
            .thenReturn(mkExecutionWithCluster(CLUSTER_ID));

        runner.runBatchPendenzeJob();

        verify(jobLauncher, never()).run(any(), any(JobParameters.class));
    }

    @Test
    void whenNoJobRunning_thenLaunchJob() throws Exception {
        // Preparo il preventConcurrent per non trovare esecuzioni in corso
        when(preventConcurrentJobLauncher.getCurrentRunningJobExecution(JOB_NAME))
            .thenReturn(null);

        // Stub di jobLauncher.run per non far cadere il metodo
        JobExecution launched = new JobExecution(2L);
        when(jobLauncher.run(eq(pendenzaSenderJob), any(JobParameters.class)))
            .thenReturn(launched);

        runner.runBatchPendenzeJob();

        // Verifico che jobLauncher.run(pendenzaSenderJob, params) sia stato chiamato
        verify(jobLauncher).run(eq(pendenzaSenderJob), any(JobParameters.class));
    }
}
