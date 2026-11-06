package it.govpay.gpd.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity scrivibile minimale su {@code documenti} (tabella del modello v3 di
 * govpay-common-pendenze), stesso ruolo di {@link VersamentoEntity} per
 * {@code versamenti}: serve solo all'UPDATE puntuale di
 * {@code data_ultima_comunicazione_aca} fatto dal writer, non alla lettura (quella
 * passa dalla vista {@link PosizioneDebitoriaGpdEntity}).
 */
@Setter
@Getter
@NoArgsConstructor
@Entity
@Table(name = "documenti")
public class DocumentoEntity {

	@Id
	private Long id;

	@Column(name = "data_ultima_modifica_aca")
	private OffsetDateTime dataUltimaModificaAca;

	@Column(name = "data_ultima_comunicazione_aca")
	private OffsetDateTime dataUltimaComunicazioneAca;
}
