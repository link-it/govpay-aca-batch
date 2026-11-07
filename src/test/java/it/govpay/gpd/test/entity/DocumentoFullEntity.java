package it.govpay.gpd.test.entity;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fixture scrivibile su {@code documenti} (tabella del modello v3 di
 * govpay-common-pendenze), stesso ruolo di {@link VersamentoFullEntity} per
 * {@code versamenti} - usata solo dai test per costruire posizioni debitorie, non dal
 * codice di produzione (che legge solo dalla vista {@code v_posizioni_debitorie_gpd}).
 */
@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "documenti")
public class DocumentoFullEntity {

	@Id
	private Long id;

	@Column(name = "id_dominio", nullable = false)
	private long idDominio;

	@Column(name = "id_applicazione", nullable = false)
	private long idApplicazione;

	@Column(name = "id_unita_operativa")
	private Long idUnitaOperativa;

	@Column(name = "cod_documento", nullable = false)
	private String codDocumento;

	@Column(name = "descrizione")
	private String descrizione;

	@Column(name = "data_pubblicazione")
	private LocalDateTime dataPubblicazione;

	@Column(name = "data_creazione", nullable = false)
	private OffsetDateTime dataCreazione;

	@Column(name = "data_ultimo_aggiornamento", nullable = false)
	private OffsetDateTime dataUltimoAggiornamento;

	@Builder.Default
	@Column(name = "versione", nullable = false)
	private Long versione = 0L;

	@Builder.Default
	@Column(name = "notifica_send", nullable = false)
	private Boolean notificaSend = false;

	@Column(name = "data_ultima_modifica_aca")
	private OffsetDateTime dataUltimaModificaAca;

	@Column(name = "data_ultima_comunicazione_aca")
	private OffsetDateTime dataUltimaComunicazioneAca;

	@Builder.Default
	@Column(name = "aca_legacy", nullable = false)
	private Boolean acaLegacy = false;
}
