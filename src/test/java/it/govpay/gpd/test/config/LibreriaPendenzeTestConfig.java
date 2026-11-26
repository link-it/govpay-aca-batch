package it.govpay.gpd.test.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypesScanner;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

import it.govpay.pendenze.service.PosizioneDebitoriaService;

/**
 * Aggiunge al contesto del batch il servizio delle posizioni debitorie di
 * govpay-common-pendenze, con le sue entity e i suoi repository, sulla stessa
 * persistence unit e sullo stesso database. I repository della libreria hanno il nome
 * qualificato, per non coincidere con quelli di test.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(PosizioneDebitoriaService.class)
public class LibreriaPendenzeTestConfig {

	/** Orologio della libreria, spostabile nel passato per far maturare una data di pubblicazione. */
	public static class OrologioDiProva extends Clock {
		private static final ZoneId FUSO = ZoneId.of("Europe/Rome");
		private Duration scarto = Duration.ZERO;

		public void sposta(Duration nuovoScarto) {
			this.scarto = nuovoScarto;
		}

		@Override
		public Instant instant() {
			return Instant.now().plus(this.scarto);
		}

		@Override
		public ZoneId getZone() {
			return FUSO;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return Clock.offset(Clock.system(zone), this.scarto);
		}
	}

	@Bean
	OrologioDiProva orologioDiProva() {
		return new OrologioDiProva();
	}

	@Bean
	@Primary
	PersistenceManagedTypes persistenceManagedTypesConLibreria(
			@Qualifier("persistenceManagedTypes") PersistenceManagedTypes delBatch, ResourceLoader resourceLoader) {
		List<String> classi = new ArrayList<>(delBatch.getManagedClassNames());
		classi.addAll(new PersistenceManagedTypesScanner(resourceLoader)
				.scan("it.govpay.pendenze.entity")
				.getManagedClassNames());
		return PersistenceManagedTypes.of(classi, delBatch.getManagedPackages());
	}

	@Configuration(proxyBeanMethods = false)
	@EnableJpaRepositories(basePackages = "it.govpay.pendenze.repository",
			nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
	static class RepositoryLibreria {
	}
}
