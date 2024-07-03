package it.govpay.gpd.entity;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter	
@NoArgsConstructor
@Entity
@Immutable
@Table(name = "v_versamenti_gpd")
public class VersamentoGpdEntity {

	public enum StatoVersamento { 
		NON_ESEGUITO,
		ESEGUITO,
		PARZIALMENTE_ESEGUITO,
		ANNULLATO,
		ESEGUITO_ALTRO_CANALE,
		ANOMALO,
		ESEGUITO_SENZA_RPT,
		INCASSATO 
	}

	public enum TIPO {F,G}

	@Id
	private Long id;

	@Column(name = "cod_versamento_ente", nullable = false)
	private String codVersamentoEnte;

	@Column(name = "importo_totale", nullable = false)
	private Double importoTotale;

	@Column(name = "stato_versamento", nullable = false)
	@Enumerated(EnumType.STRING)
	private StatoVersamento statoVersamento;

	@Column(name = "data_validita")
	private LocalDateTime dataValidita;

	@Column(name = "data_scadenza")
	private LocalDateTime dataScadenza;

	@Column(name = "causale_versamento", nullable = false)
	private String causaleVersamento;

	@Column(name = "debitore_identificativo", nullable = false)
	private String debitoreIdentificativo;

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

	@Column(name = "debitore_telefono")
	private String debitoreTelefono;

	@Column(name = "debitore_cellulare")
	private String debitoreCellulare;

	@Column(name = "debitore_fax")
	private String debitoreFax;

	@Column(name = "debitore_tipo", nullable = false)
	@Enumerated(EnumType.STRING)
	private TIPO debitoreTipo;

	@Column(name = "debitore_anagrafica", nullable = false)
	private String debitoreAnagrafica;

	@Column(name = "iuv_versamento")
	private String iuvVersamento;
	
	@Column(name = "numero_avviso")
	private String numeroAvviso;
	
	@Column(name = "cod_rata")
	private String codRata;

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

	@Column(name = "data_ultima_modifica_aca")
	private OffsetDateTime dataUltimaModificaAca;

	@Column(name = "data_ultima_comunicazione_aca")
	private OffsetDateTime dataUltimaComunicazioneAca;
	
}

/**
CREATE VIEW v_versamenti_gpd AS 
SELECT versamenti.id,
    versamenti.cod_versamento_ente,
    versamenti.importo_totale,
    versamenti.stato_versamento,
    versamenti.data_validita,
    versamenti.data_scadenza,
    CONVERT_FROM(decode(substring(versamenti.causale_versamento, 3), 'base64'),'UTF-8') as causale_versamento,
    versamenti.debitore_identificativo,
    versamenti.debitore_tipo,
    versamenti.debitore_anagrafica,
    versamenti.debitore_indirizzo,
	versamenti.debitore_civico,
	versamenti.debitore_cap,
	versamenti.debitore_localita,
	versamenti.debitore_provincia,
	versamenti.debitore_nazione,
	versamenti.debitore_email,
	versamenti.debitore_telefono,
	versamenti.debitore_cellulare,
	versamenti.debitore_fax,
    versamenti.iuv_versamento,
    versamenti.numero_avviso,
    versamenti.cod_rata,
    applicazioni.cod_applicazione AS cod_applicazione,
    domini.cod_dominio AS cod_dominio,
    domini.ragione_sociale AS ragione_sociale_dominio,
    uo.cod_uo AS cod_uo,
    uo.uo_denominazione AS uo_denominazione,
    versamenti.data_ultima_modifica_aca,
    versamenti.data_ultima_comunicazione_aca
    FROM versamenti 
	JOIN domini ON versamenti.id_dominio = domini.id 
	JOIN applicazioni ON versamenti.id_applicazione = applicazioni.id
	LEFT JOIN uo ON versamenti.id_uo = uo.id;
 * */

