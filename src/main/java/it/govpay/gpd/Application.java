package it.govpay.gpd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypesScanner;

import java.util.ArrayList;
import java.util.List;

import it.govpay.common.entity.ApplicazioneEntity;
import it.govpay.common.entity.ConfigurazioneEntity;
import it.govpay.common.entity.ConnettoreEntity;
import it.govpay.common.entity.DominioEntity;
import it.govpay.common.entity.DominioLogoEntity;
import it.govpay.common.entity.IntermediarioEntity;
import it.govpay.common.entity.StazioneEntity;

@SpringBootApplication(scanBasePackages = {"it.govpay.gpd", "it.govpay.common.client"})
@EnableJpaRepositories(basePackages = {"it.govpay.gpd"})
public class Application extends SpringBootServletInitializer {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	/**
	 * Sostituisce {@code @EntityScan(basePackages = {"it.govpay.gpd", "it.govpay.common.entity"})}.
	 * <p>
	 * Lo scan di un package e' sempre ricorsivo e non ammette esclusioni, quindi
	 * trascinava dentro anche le entity di anagrafica di govpay-common (IBAN,
	 * tributi, tipi versamento, unita' operative). Quelle entity mappano le stesse
	 * tabelle delle fixture di test di questo modulo: registrarle entrambe rompe la
	 * persistence unit, prima con un conflitto di nome e poi, risolto quello, con
	 * ClassCastException quando una associazione risolve l'entity sbagliata.
	 * <p>
	 * Qui sono registrate le sole entity di govpay-common che servono ai repository
	 * che la libreria espone ({@code ApplicazioneRepository}, {@code DominioRepository},
	 * {@code IntermediarioRepository} e affini). Stesso approccio di console-api e
	 * portal-api.
	 */
	@Bean
	PersistenceManagedTypes persistenceManagedTypes(ResourceLoader resourceLoader) {
		PersistenceManagedTypes scanned = new PersistenceManagedTypesScanner(resourceLoader)
				.scan("it.govpay.gpd");
		List<String> classi = new ArrayList<>(scanned.getManagedClassNames());
		classi.add(ApplicazioneEntity.class.getName());
		classi.add(ConfigurazioneEntity.class.getName());
		classi.add(ConnettoreEntity.class.getName());
		classi.add(DominioEntity.class.getName());
		classi.add(DominioLogoEntity.class.getName());
		classi.add(IntermediarioEntity.class.getName());
		classi.add(StazioneEntity.class.getName());
		return PersistenceManagedTypes.of(classi, scanned.getManagedPackages());
	}
}
