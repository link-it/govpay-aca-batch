package it.govpay.gpd.config;

import java.time.ZoneId;

import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.common.batch.service.JobConcurrencyService;

/**
 * Configurazione dei bean infrastrutturali per la gestione batch multi-nodo.
 * <p>
 * Fornisce i bean di govpay-common necessari per:
 * <ul>
 *   <li>{@link JobConcurrencyService} — prevenzione esecuzione concorrente e gestione job stale</li>
 *   <li>{@link JobExecutionHelper} — esecuzione job con parametri standard e controllo pre-esecuzione</li>
 * </ul>
 */
@Configuration
public class BatchInfraConfig {

    @Bean
    public JobConcurrencyService jobConcurrencyService(
            JobExplorer jobExplorer,
            JobRepository jobRepository,
            @Value("${govpay.batch.stale-threshold-minutes:120}") int staleThresholdMinutes) {
        return new JobConcurrencyService(jobExplorer, jobRepository, staleThresholdMinutes);
    }

    @Bean
    public JobExecutionHelper jobExecutionHelper(
            JobLauncher jobLauncher,
            JobConcurrencyService jobConcurrencyService,
            @Value("${it.govpay.gpd.batch.clusterId:GovPay-ACA-Batch}") String clusterId,
            ZoneId applicationZoneId) {
        return new JobExecutionHelper(jobLauncher, jobConcurrencyService, clusterId, applicationZoneId);
    }
}
