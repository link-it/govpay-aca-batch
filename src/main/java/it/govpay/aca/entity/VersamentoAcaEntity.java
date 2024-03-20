package it.govpay.aca.entity;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter	
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "v_versamenti_aca")
public class VersamentoAcaEntity {

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
	@SequenceGenerator(name="seq_versamenti",sequenceName="seq_versamenti", initialValue=1, allocationSize=1)
	@GeneratedValue(strategy= GenerationType.SEQUENCE, generator="seq_versamenti")
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

	@Column(name = "debitore_tipo", nullable = false)
	@Enumerated(EnumType.STRING)
	private TIPO debitoreTipo;
	
	@Column(name = "debitore_anagrafica", nullable = false)
	private String debitoreAnagrafica;
	
	@Column(name = "iuv_versamento")
	private String iuvVersamento;
	
	@Column(name = "numero_avviso")
	private String numeroAvviso;
	
	@Column(name = "cod_applicazione", nullable = false)
	private String codApplicazione;
	
	@Column(name = "cod_dominio", nullable = false)
	private String codDominio;
	
	@Column(name = "data_ultima_modifica_aca")
	private LocalDateTime dataUltimaModificaAca;
	
	@Column(name = "data_ultima_comunicazione_aca")
	private LocalDateTime dataUltimaComunicazioneAca;
}

/**
 CREATE VIEW v_versamenti_aca AS 
SELECT versamenti.id,
    versamenti.cod_versamento_ente,
    versamenti.importo_totale,
    versamenti.stato_versamento,
    versamenti.data_validita,
    versamenti.data_scadenza,
    versamenti.causale_versamento,
    versamenti.debitore_identificativo,
    versamenti.debitore_tipo,
    versamenti.debitore_anagrafica,
    versamenti.iuv_versamento,
    versamenti.numero_avviso,
    applicazioni.cod_applicazione AS cod_applicazione,
    domini.cod_dominio AS cod_dominio,
    versamenti.data_ultima_modifica_aca,
    versamenti.data_ultima_comunicazione_aca
    FROM versamenti 
	JOIN domini ON versamenti.id_dominio = domini.id 
	JOIN applicazioni ON versamenti.id_applicazione = applicazioni.id
    WHERE versamenti.data_ultima_comunicazione_aca < versamenti.data_ultima_modifica_aca; 
 * */

