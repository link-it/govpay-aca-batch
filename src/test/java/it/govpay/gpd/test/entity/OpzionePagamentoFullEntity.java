package it.govpay.gpd.test.entity;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import it.govpay.pendenze.model.StatoOpzionePagamento;
import it.govpay.pendenze.model.TipologiaOpzionePagamento;

/** Fixture scrivibile su {@code opzioni_pagamento} (modello v3). */
@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "opzioni_pagamento")
public class OpzionePagamentoFullEntity {

	@Id
	private Long id;

	@Column(name = "id_documento", nullable = false)
	private long idDocumento;

	@Column(name = "tipologia", nullable = false)
	@Enumerated(EnumType.STRING)
	private TipologiaOpzionePagamento tipologia;

	@Column(name = "giorni")
	private Integer giorni;

	@Column(name = "stato", nullable = false)
	@Enumerated(EnumType.STRING)
	private StatoOpzionePagamento stato;

	@Builder.Default
	@Column(name = "versione", nullable = false)
	private Long versione = 0L;

	@Column(name = "data_inizio_validita")
	private LocalDateTime dataInizioValidita;

	@Column(name = "data_scadenza")
	private LocalDateTime dataScadenza;

	@Column(name = "data_creazione", nullable = false)
	private OffsetDateTime dataCreazione;

	@Column(name = "data_ultimo_aggiornamento", nullable = false)
	private OffsetDateTime dataUltimoAggiornamento;
}
