package it.govpay.gpd.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;

import it.govpay.common.batch.TriggerType;
import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.common.batch.runner.JobExecutionHelper.PreExecutionCheckResult;
import it.govpay.common.batch.runner.JobExecutionHelper.PreExecutionResult;
import it.govpay.gpd.config.ScheduledJobRunner;
import it.govpay.gpd.costanti.Costanti;

class ScheduledJobRunnerTest {

    @Mock
    private JobExecutionHelper jobExecutionHelper;
    @Mock
    private Job pendenzaSenderJob;

    private ScheduledJobRunner runner;

    private static final String JOB_NAME = Costanti.SEND_PENDENZE_GPD_JOBNAME;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        runner = new ScheduledJobRunner(jobExecutionHelper, pendenzaSenderJob);
    }

    @Test
    void whenJobRunningOnAnotherNode_thenSkipLaunching() throws Exception {
        when(jobExecutionHelper.checkBeforeExecution(JOB_NAME))
            .thenReturn(new PreExecutionResult(PreExecutionCheckResult.RUNNING_ON_OTHER_NODE, null, "OtherNode"));

        JobExecution result = runner.runBatchPendenzeJob();

        assertNull(result);
        verify(jobExecutionHelper, never()).runJob(any(), any());
    }

    @Test
    void whenJobRunningOnSameNode_thenAlsoSkip() throws Exception {
        when(jobExecutionHelper.checkBeforeExecution(JOB_NAME))
            .thenReturn(new PreExecutionResult(PreExecutionCheckResult.RUNNING_ON_THIS_NODE, null, "GovPay-ACA-Batch"));

        JobExecution result = runner.runBatchPendenzeJob();

        assertNull(result);
        verify(jobExecutionHelper, never()).runJob(any(), any());
    }

    @Test
    void whenNoJobRunning_thenLaunchJob() throws Exception {
        when(jobExecutionHelper.checkBeforeExecution(JOB_NAME))
            .thenReturn(new PreExecutionResult(PreExecutionCheckResult.CAN_PROCEED, null, null));

        JobExecution launched = new JobExecution(2L, new JobInstance(1L, JOB_NAME), new JobParameters());
        when(jobExecutionHelper.runJob(eq(pendenzaSenderJob), eq(JOB_NAME), eq(TriggerType.SCHEDULED)))
            .thenReturn(launched);

        JobExecution result = runner.runBatchPendenzeJob();

        assertNotNull(result);
        verify(jobExecutionHelper).runJob(eq(pendenzaSenderJob), eq(JOB_NAME), eq(TriggerType.SCHEDULED));
    }

    @Test
    void whenJobIsStaleAndAbandonmentSucceeds_thenLaunchNewJob() throws Exception {
        when(jobExecutionHelper.checkBeforeExecution(JOB_NAME))
            .thenReturn(new PreExecutionResult(PreExecutionCheckResult.STALE_ABANDONED_CAN_PROCEED, null, null));

        JobExecution launched = new JobExecution(2L, new JobInstance(1L, JOB_NAME), new JobParameters());
        when(jobExecutionHelper.runJob(eq(pendenzaSenderJob), eq(JOB_NAME), eq(TriggerType.SCHEDULED)))
            .thenReturn(launched);

        JobExecution result = runner.runBatchPendenzeJob();

        assertNotNull(result);
        verify(jobExecutionHelper).runJob(eq(pendenzaSenderJob), eq(JOB_NAME), eq(TriggerType.SCHEDULED));
    }

    @Test
    void whenJobIsStaleAndAbandonmentFails_thenDoNotLaunchNewJob() throws Exception {
        when(jobExecutionHelper.checkBeforeExecution(JOB_NAME))
            .thenReturn(new PreExecutionResult(PreExecutionCheckResult.STALE_ABANDON_FAILED, null, null));

        JobExecution result = runner.runBatchPendenzeJob();

        assertNull(result);
        verify(jobExecutionHelper, never()).runJob(any(), any());
    }
}
