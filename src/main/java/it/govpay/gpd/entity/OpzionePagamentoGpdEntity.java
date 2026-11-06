package it.govpay.gpd.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;

import it.govpay.pendenze.model.StatoOpzionePagamento;
import it.govpay.pendenze.model.TipologiaOpzionePagamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Opzione di pagamento (modello v3, tabella {@code opzioni_pagamento}), vista
 * {@code v_opzioni_pagamento_gpd} — una {@code PaymentOptionModelV3} per opzione
 * {@link StatoOpzionePagamento#DISPONIBILE}.
 */
@Setter
@Getter
@NoArgsConstructor
@Entity
@Immutable
@Table(name = "v_opzioni_pagamento_gpd")
public class OpzionePagamentoGpdEntity {

	@Id
	private Long id;

	@Column(name = "id_documento", nullable = false)
	private Long idDocumento;

	@Column(name = "tipologia", nullable = false)
	@Enumerated(EnumType.STRING)
	private TipologiaOpzionePagamento tipologia;

	@Column(name = "giorni")
	private Integer giorni;

	@Column(name = "stato", nullable = false)
	@Enumerated(EnumType.STRING)
	private StatoOpzionePagamento stato;

	@Column(name = "data_inizio_validita")
	private LocalDateTime dataInizioValidita;

	@Column(name = "data_scadenza")
	private LocalDateTime dataScadenza;
}
