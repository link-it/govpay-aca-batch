package it.govpay.gpd.entity;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Posizione debitoria (modello v3 di govpay-common-pendenze, tabella {@code documenti}),
 * vista {@code v_posizioni_debitorie_gpd}: una riga per posizione. Il debitore non e' sulla
 * posizione ma su ogni opzione ({@link OpzionePagamentoGpdEntity}).
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

	@Column(name = "num_opzioni_disponibili", nullable = false)
	private Long numOpzioniDisponibili;

	@Column(name = "num_opzioni_attivate", nullable = false)
	private Long numOpzioniAttivate;

	@Column(name = "num_opzioni_annullate", nullable = false)
	private Long numOpzioniAnnullate;

	// Debitori rimossi (pd_debitori.data_rimozione) dopo l'ultima comunicazione a GPD: la
	// posizione e' gia' nota a GPD con le loro opzioni, che vanno fatte sparire.
	@Column(name = "num_debitori_da_ritirare", nullable = false)
	private Long numDebitoriDaRitirare;

	@Column(name = "data_ultima_modifica_aca")
	private OffsetDateTime dataUltimaModificaAca;

	@Column(name = "data_ultima_comunicazione_aca")
	private OffsetDateTime dataUltimaComunicazioneAca;
}
