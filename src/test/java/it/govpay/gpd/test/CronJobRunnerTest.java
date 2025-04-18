package it.govpay.gpd.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
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

    private JobExecution mkExecutionWithCluster(String clusterIdValue) {
    	JobInstance jobinstance = new JobInstance(1L, JOB_NAME);
    	
        JobParameters params = new JobParametersBuilder()
            .addString(Costanti.GOVPAY_GPD_JOB_PARAMETER_CLUSTER_ID, clusterIdValue)
            .toJobParameters();
        return new JobExecution(jobinstance, 1L, params);
    }

    @Test
    void whenJobRunningOnAnotherNode_thenSkipLaunching() throws Exception {
        // simuliamo un job già in esecuzione con clusterId diverso
        when(preventConcurrentJobLauncher.getCurrentRunningJobExecution(JOB_NAME))
            .thenReturn(mkExecutionWithCluster("OtherNode"));

        runner.run();

        // non deve mai lanciare il job
        verify(jobLauncher, never()).run(any(), any());
    }

    @Test
    void whenJobRunningOnSameNode_thenAlsoSkip() throws Exception {
        // simuliamo un job già in esecuzione con il nostro stesso clusterId
        when(preventConcurrentJobLauncher.getCurrentRunningJobExecution(JOB_NAME))
            .thenReturn(mkExecutionWithCluster(CLUSTER_ID));

        runner.run();

        verify(jobLauncher, never()).run(any(), any());
    }

    @Test
    void whenNoJobRunning_thenLaunchAndExit() {
    	
    	when(jobExplorer.findRunningJobExecutions(JOB_NAME))
        .thenReturn(new HashSet<>());
    	PreventConcurrentJobLauncher preventConcurrentJobLauncher2 = new PreventConcurrentJobLauncher(jobExplorer);
    	
    	JobExecution currentRunningJobExecution = preventConcurrentJobLauncher2.getCurrentRunningJobExecution(JOB_NAME);
    	
    	assertNull(currentRunningJobExecution);
    	
    	Set<JobExecution> set = new HashSet<>();
    	set.add(mkExecutionWithCluster("OtherNode"));
    	
    	when(jobExplorer.findRunningJobExecutions(JOB_NAME))
        .thenReturn(set);
    	
    	currentRunningJobExecution = preventConcurrentJobLauncher2.getCurrentRunningJobExecution(JOB_NAME);
    	
    	assertNotNull(currentRunningJobExecution);
    	assertEquals("OtherNode", currentRunningJobExecution.getJobParameters().getString(Costanti.GOVPAY_GPD_JOB_PARAMETER_CLUSTER_ID));
    }
}
