package it.govpay.gpd.controller;

import java.io.Serializable;
import java.net.URI;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Classe che rappresenta un errore REST secondo il formato RFC 7807 (Problem Details for HTTP APIs).
 * <p>
 * Esempio di risposta:
 * <pre>
 * {
 *   "type": "https://example.com/problems/job-already-running",
 *   "title": "Job già in esecuzione",
 *   "status": 409,
 *   "detail": "Il job sendPendenzeGpdJob è già in esecuzione sul nodo cluster-1",
 *   "instance": "/api/batch/run"
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
public class Problem implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * URI che identifica il tipo di problema.
     */
    private URI type;

    /**
     * Breve descrizione del problema (human-readable).
     */
    private String title;

    /**
     * Codice di stato HTTP.
     */
    private Integer status;

    /**
     * Descrizione dettagliata del problema specifico.
     */
    private String detail;

    /**
     * URI che identifica l'istanza specifica del problema.
     */
    private URI instance;

    /**
     * Crea un Problem per errore interno del server.
     */
    public static Problem internalServerError(String detail) {
        return Problem.builder()
                .title("Errore interno del server")
                .status(500)
                .detail(detail)
                .build();
    }

    /**
     * Crea un Problem per servizio non disponibile.
     */
    public static Problem serviceUnavailable(String detail) {
        return Problem.builder()
                .title("Servizio non disponibile")
                .status(503)
                .detail(detail)
                .build();
    }

    /**
     * Crea un Problem per conflitto (risorsa già esistente o in uso).
     */
    public static Problem conflict(String detail) {
        return Problem.builder()
                .title("Conflitto")
                .status(409)
                .detail(detail)
                .build();
    }

}
