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
 * DTO per le informazioni sull'ultima esecuzione del batch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
public class LastExecutionInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * ID dell'ultima esecuzione.
     */
    private Long executionId;

    /**
     * Cluster ID del nodo che ha eseguito il batch.
     */
    private String clusterId;

    /**
     * Data/ora di inizio dell'ultima esecuzione.
     */
    private LocalDateTime startTime;

    /**
     * Data/ora di fine dell'ultima esecuzione.
     */
    private LocalDateTime endTime;

    /**
     * Durata in secondi dell'ultima esecuzione.
     */
    private Long durationSeconds;

    /**
     * Stato finale dell'esecuzione (COMPLETED, FAILED, STOPPED, etc.).
     */
    private String status;

    /**
     * Exit code dell'esecuzione.
     */
    private String exitCode;

    /**
     * Descrizione dell'exit status.
     */
    private String exitDescription;
}
