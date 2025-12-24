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
 * DTO per le informazioni sullo stato del batch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
public class BatchStatusInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Indica se il batch è attualmente in esecuzione.
     */
    private boolean running;

    /**
     * ID dell'esecuzione corrente (se in esecuzione).
     */
    private Long executionId;

    /**
     * Cluster ID del nodo che sta eseguendo il batch.
     */
    private String clusterId;

    /**
     * Data/ora di inizio dell'esecuzione corrente.
     */
    private LocalDateTime startTime;

    /**
     * Durata in secondi dell'esecuzione corrente.
     */
    private Long runningSeconds;

    /**
     * Stato corrente dell'esecuzione.
     */
    private String status;

    /**
     * Ultimo step in esecuzione.
     */
    private String currentStep;
}
