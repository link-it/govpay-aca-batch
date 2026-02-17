package it.govpay.gpd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"it.govpay.gpd", "it.govpay.common.client"})
@EnableJpaRepositories(basePackages = {"it.govpay.gpd"})
@EntityScan(basePackages = {"it.govpay.gpd", "it.govpay.common.entity"})
public class Application extends SpringBootServletInitializer {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
