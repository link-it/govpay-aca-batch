package it.govpay.gpd.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;

import it.govpay.pendenze.model.StatoOpzionePagamento;
import it.govpay.pendenze.model.TipoSoggetto;
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
 * {@link StatoOpzionePagamento#DISPONIBILE}, con il debitore a cui appartiene.
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

	@Column(name = "id_pd_debitore", nullable = false)
	private Long idPdDebitore;

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

	@Column(name = "debitore_tipo")
	@Enumerated(EnumType.STRING)
	private TipoSoggetto debitoreTipo;

	@Column(name = "debitore_identificativo")
	private String debitoreIdentificativo;

	@Column(name = "debitore_anagrafica")
	private String debitoreAnagrafica;

	@Column(name = "debitore_indirizzo")
	private String debitoreIndirizzo;

	@Column(name = "debitore_civico")
	private String debitoreCivico;

	@Column(name = "debitore_cap")
	private String debitoreCap;

	@Column(name = "debitore_localita")
	private String debitoreLocalita;

	@Column(name = "debitore_provincia")
	private String debitoreProvincia;

	@Column(name = "debitore_nazione")
	private String debitoreNazione;

	@Column(name = "debitore_email")
	private String debitoreEmail;
}
