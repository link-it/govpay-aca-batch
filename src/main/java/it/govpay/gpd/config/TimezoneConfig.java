package it.govpay.gpd.config;

import java.time.ZoneId;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Configurazione del timezone dell'applicazione.
 * Imposta il timezone di default della JVM leggendo dalla proprietà spring.jackson.time-zone.
 * Questa configurazione ha priorità alta per assicurarsi che il timezone sia impostato
 * prima che altri componenti vengano inizializzati.
 */
@Slf4j
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TimezoneConfig {

    @Getter
    @Value("${spring.jackson.time-zone:Europe/Rome}")
    private String timezone;

    @PostConstruct
    public void init() {
        TimeZone timeZone = TimeZone.getTimeZone(timezone);
        TimeZone.setDefault(timeZone);
        log.info("Timezone di default impostato a: {}", timeZone.getID());
    }

    /**
     * Bean che espone lo ZoneId configurato per utilizzo in altri componenti.
     */
    @Bean
    public ZoneId applicationZoneId() {
        return ZoneId.of(timezone);
    }
}
