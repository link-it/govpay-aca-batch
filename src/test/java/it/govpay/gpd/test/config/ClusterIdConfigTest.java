package it.govpay.gpd.test.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import it.govpay.common.batch.config.ClusterIdPredefinito;
import it.govpay.common.batch.metrics.BatchMetricsNodo;
import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.gpd.Application;
import it.govpay.gpd.gde.service.GdeService;

/**
 * Verifica che il cluster-id del nodo sia lo stesso per il {@link JobExecutionHelper},
 * per gli eventi del GDE e per il tag {@code nodo} delle metriche (govpay-common#35, #36).
 * Il cluster-id dell'ACA e' {@code it.govpay.gpd.batch.clusterId}: il tag {@code nodo} lo riceve
 * tramite {@code govpay.metrics.batch.nodo}.
 */
class ClusterIdConfigTest {

    @Nested
    @SpringBootTest(classes = Application.class)
    @ActiveProfiles("test")
    class SenzaProperty {

        @Autowired
        private JobExecutionHelper jobExecutionHelper;

        @Autowired
        private GdeService gdeService;

        @Autowired
        private BatchMetricsNodo batchMetricsNodo;

        @Test
        void usaIlClusterIdPredefinitoDelNodo() {
            String atteso = ClusterIdPredefinito.valore();

            assertThat(jobExecutionHelper.getClusterId()).isEqualTo(atteso);
            assertThat(ReflectionTestUtils.getField(gdeService, "clusterId")).isEqualTo(atteso);
            assertThat(batchMetricsNodo.valore()).isEqualTo(atteso);
        }
    }

    @Nested
    @SpringBootTest(classes = Application.class)
    @ActiveProfiles("test")
    @TestPropertySource(properties = "it.govpay.gpd.batch.clusterId=nodo-1")
    class ConProperty {

        @Autowired
        private JobExecutionHelper jobExecutionHelper;

        @Autowired
        private GdeService gdeService;

        @Autowired
        private BatchMetricsNodo batchMetricsNodo;

        @Test
        void usaIlClusterIdConfigurato() {
            assertThat(jobExecutionHelper.getClusterId()).isEqualTo("nodo-1");
            assertThat(ReflectionTestUtils.getField(gdeService, "clusterId")).isEqualTo("nodo-1");
            assertThat(batchMetricsNodo.valore()).isEqualTo("nodo-1");
            // il tag nodo arriva da govpay.metrics.batch.nodo, valorizzata con la property del cluster-id
            assertThat(batchMetricsNodo.fonte()).isEqualTo("govpay.metrics.batch.nodo");
        }
    }
}
