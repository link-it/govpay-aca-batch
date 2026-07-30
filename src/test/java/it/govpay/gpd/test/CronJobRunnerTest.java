package it.govpay.gpd.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.batch.core.job.Job;

import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.gpd.config.CronJobRunner;

class CronJobRunnerTest {

    @Mock
    private JobExecutionHelper jobExecutionHelper;
    @Mock
    private Job pendenzaSenderJob;

    private CronJobRunner runner;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        runner = new CronJobRunner(jobExecutionHelper, pendenzaSenderJob);
    }

    @Test
    void testConstructor() {
        assertNotNull(runner);
    }
}
