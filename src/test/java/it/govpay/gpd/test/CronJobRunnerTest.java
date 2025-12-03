package it.govpay.gpd.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.launch.JobLauncher;

import it.govpay.gpd.config.CronJobRunner;
import it.govpay.gpd.config.PreventConcurrentJobLauncher;

class CronJobRunnerTest {

    @Mock
    private JobLauncher jobLauncher;
    @Mock
    private PreventConcurrentJobLauncher preventConcurrentJobLauncher;
    @Mock
    private Job pendenzaSenderJob;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testConstructor() {
        CronJobRunner runner = new CronJobRunner(jobLauncher, preventConcurrentJobLauncher, pendenzaSenderJob);
        assertNotNull(runner);
    }
}
