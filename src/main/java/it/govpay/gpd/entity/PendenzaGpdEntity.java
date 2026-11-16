package it.govpay.gpd.entity;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import org.hibernate.annotations.Immutable;

import it.govpay.pendenze.model.StatoPendenza;
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
 * Pendenza come rata di un'opzione (modello v3, {@code versamenti.id_opzione_pagamento}
 * valorizzato), vista {@code v_pendenze_gpd} — un {@code InstallmentModel} per rata.
 */
@Setter
@Getter
@NoArgsConstructor
@Entity
@Immutable
@Table(name = "v_pendenze_gpd")
public class PendenzaGpdEntity {

	@Id
	private Long id;

	@Column(name = "id_opzione_pagamento", nullable = false)
	private Long idOpzionePagamento;

	@Column(name = "id_documento", nullable = false)
	private Long idDocumento;

	@Column(name = "cod_versamento_ente", nullable = false)
	private String codVersamentoEnte;

	@Column(name = "numero_rata")
	private Integer numeroRata;

	@Column(name = "importo_totale", nullable = false)
	private Double importoTotale;

	@Column(name = "stato_versamento", nullable = false)
	@Enumerated(EnumType.STRING)
	private StatoPendenza statoVersamento;

	@Column(name = "data_validita")
	private LocalDateTime dataValidita;

	@Column(name = "data_scadenza")
	private LocalDateTime dataScadenza;

	@Column(name = "iuv_versamento")
	private String iuvVersamento;

	@Column(name = "numero_avviso")
	private String numeroAvviso;

	// Formato grezzo di colonna (CausaleSemplice del monolite, "01 "+base64) - non
	// decodificato qui: va letto con CausaleVersamentoCodec.decode, nullable.
	@Column(name = "causale_versamento")
	private String causaleVersamento;

	@Column(name = "data_ultima_modifica_aca")
	private OffsetDateTime dataUltimaModificaAca;

	@Column(name = "data_ultima_comunicazione_aca")
	private OffsetDateTime dataUltimaComunicazioneAca;
}
