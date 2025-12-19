package it.govpay.gpd.controller;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO per le informazioni sulla prossima esecuzione schedulata del batch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
public class NextExecutionInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Modalità di scheduling: "scheduler" o "cron".
     */
    private String schedulingMode;

    /**
     * Data/ora stimata della prossima esecuzione.
     * Null se il batch è in modalità cron (gestito esternamente).
     */
    private LocalDateTime nextExecutionTime;

    /**
     * Intervallo di scheduling in millisecondi (solo per modalità scheduler).
     */
    private Long intervalMillis;

    /**
     * Intervallo di scheduling in formato human-readable.
     */
    private String intervalFormatted;

    /**
     * Data/ora dell'ultima esecuzione completata (usata per calcolare la prossima).
     */
    private LocalDateTime lastCompletedTime;

    /**
     * Messaggio aggiuntivo (es. "Gestito da cron esterno").
     */
    private String message;
}
