package it.govpay.gpd.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.gpd.Application;
import it.govpay.gpd.service.GpdApiService;

/**
 * Verifica end-to-end dell'esposizione delle metriche Prometheus:
 * <ul>
 *   <li>lo scrape {@code GET /actuator/prometheus} risponde in formato
 *       testuale Prometheus, con il tag comune {@code application};</li>
 *   <li>l'esecuzione del job pubblica le metriche standard di Spring Batch
 *       ({@code spring_batch_job}).</li>
 * </ul>
 *
 * <p>Il job viene lanciato tramite {@link JobLauncherTestUtils} (come nelle
 * altre {@code UC_*Test} del modulo) invece che tramite {@code ScheduledJobRunner}:
 * bypassa lo {@code @Scheduled} reale, evitando qualunque race con esso.
 * Il DB e' vuoto (nessun versamento creato): il reader non trova nulla da
 * spedire, {@code GpdApiService} non viene mai invocato.
 *
 * <p>Il servizio non ha una porta management separata: essendo l'unico
 * server web presente quello dell'actuator, scrape e health rispondono sulla
 * stessa porta (qui random per il test).
 */
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableAutoConfiguration
@DisplayName("Metriche Prometheus")
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
@SpringBatchTest
@TestPropertySource(properties = {
        "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
        "management.metrics.tags.application=govpay-aca-batch",
        "it.govpay.gpd.batch.clusterId=" + PrometheusScrapeIntegrationTest.NODO
})
class PrometheusScrapeIntegrationTest {

    static final String NODO = "nodo-test";

    @LocalServerPort
    private int serverPort;

    @MockitoBean
    private GpdApiService gpdApiService;

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    @Qualifier("gpdSenderJob")
    private Job job;

    @Autowired
    private JobExecutionHelper jobExecutionHelper;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(job);
    }

    @AfterEach
    void tearDown() {
        jobRepositoryTestUtils.removeJobExecutions();
    }

    @Test
    void scrapeReturnsPrometheusFormatWithApplicationTag() throws Exception {
        HttpResponse<String> response = get("/actuator/prometheus");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("text/plain");
        assertThat(response.body()).contains("# TYPE jvm_memory_used_bytes gauge");
        assertThat(response.body()).contains("application=\"govpay-aca-batch\"");
    }

    @Test
    void batchJobExecutionProducesSpringBatchMetrics() throws Exception {
        // Job "a vuoto": nessun versamento da spedire. Basta a far girare il
        // job (anche se termina senza item) e a produrre le metriche standard
        // spring_batch_job/step di Micrometer.
        JobExecution execution = jobLauncherTestUtils.launchJob();
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");

        String scrape = get("/actuator/prometheus").body();
        assertThat(scrape).contains("spring_batch_job_seconds_count");
        assertThat(scrape).contains("spring_batch_job_name=\"gpdSenderJob\"");
    }

    @Test
    void batchJobExecutionProducesGovpayBatchMetrics() throws Exception {
        // Avvio come lo scheduler interno: controllo pre-esecuzione (tick) e avvio con i
        // parametri standard di JobExecutionHelper
        JobExecution execution = jobExecutionHelper.executeIfPossible(job, "gpdSenderJob");
        assertThat(execution).isNotNull();
        assertThat(execution.getJobParameters().getString(JobExecutionHelper.JOB_PARAM_CLUSTER_ID)).isEqualTo(NODO);
        assertThat(execution.getJobParameters().getString(JobExecutionHelper.JOB_PARAM_TRIGGER_TYPE)).isEqualTo("SCHEDULED");
        assertThat(execution.getJobParameters().getString(JobExecutionHelper.JOB_PARAM_CORRELATION_ID)).isNotBlank();

        String scrape = get("/actuator/prometheus").body();
        String nodo = "nodo=\"" + NODO + "\"";
        assertThat(righe(scrape, "govpay_batch_job_seconds_count{"))
                .anyMatch(r -> r.contains("job_name=\"gpdSenderJob\"") && r.contains("trigger=\"SCHEDULED\"")
                        && r.contains("esito=\"COMPLETED\"") && r.contains(nodo));
        assertThat(righe(scrape, "govpay_batch_schedule_ticks_total{"))
                .anyMatch(r -> r.contains("esito_tick=\"CAN_PROCEED\"") && r.contains(nodo));
        assertThat(righe(scrape, "govpay_batch_stale_threshold_seconds{")).anyMatch(r -> r.contains(nodo));
        assertThat(righe(scrape, "govpay_batch_job_last_success_timestamp_seconds{")).anyMatch(r -> r.contains(nodo));
    }

    private static List<String> righe(String scrape, String prefisso) {
        return scrape.lines().filter(r -> r.startsWith(prefisso)).toList();
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort + path))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
