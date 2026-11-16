package it.govpay.gpd.entity;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import org.hibernate.annotations.Immutable;

import it.govpay.pendenze.model.TipoSoggetto;
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
 * Posizione debitoria (modello v3 di govpay-common-pendenze, tabella {@code documenti}),
 * vista {@code v_posizioni_debitorie_gpd}. Debitore = soggetto di ordine 0 (GPD v3
 * ammette un solo debitore per opzione), quindi i campi {@code debitore*} sono
 * nullable: la vista li popola con una LEFT JOIN.
 */
@Setter
@Getter
@NoArgsConstructor
@Entity
@Immutable
@Table(name = "v_posizioni_debitorie_gpd")
public class PosizioneDebitoriaGpdEntity {

	@Id
	private Long id;

	@Column(name = "cod_documento", nullable = false)
	private String codDocumento;

	@Column(name = "descrizione")
	private String descrizione;

	@Column(name = "data_pubblicazione")
	private LocalDateTime dataPubblicazione;

	@Column(name = "cod_applicazione", nullable = false)
	private String codApplicazione;

	@Column(name = "cod_dominio", nullable = false)
	private String codDominio;

	@Column(name = "ragione_sociale_dominio", nullable = false)
	private String ragioneSocialeDominio;

	@Column(name = "cod_uo")
	private String codUo;

	@Column(name = "uo_denominazione")
	private String uoDenominazione;

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

	@Column(name = "num_opzioni_disponibili", nullable = false)
	private Long numOpzioniDisponibili;

	@Column(name = "num_opzioni_attivate", nullable = false)
	private Long numOpzioniAttivate;

	@Column(name = "num_opzioni_annullate", nullable = false)
	private Long numOpzioniAnnullate;

	@Column(name = "data_ultima_modifica_aca")
	private OffsetDateTime dataUltimaModificaAca;

	@Column(name = "data_ultima_comunicazione_aca")
	private OffsetDateTime dataUltimaComunicazioneAca;
}
