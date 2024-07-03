package it.govpay.gpd.entity;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "v_sng_versamenti_gpd")
public class SingoloVersamentoGpdEntity {

	@Id
	@Column(name = "sv_id", nullable = false)
	private Long id;

	@Column(name = "importo_singolo_versamento", nullable = false)
	private Double importoSingoloVersamento;

	@Column(name = "cod_dominio")
	private String codDominio;
	
	@Column(name = "sv_descrizione")
	private String descrizione;
	
	@Column(name = "descrizione_causale_rpt")
	private String descrizioneCausaleRPT;
	
	@Column(name = "sv_tipo_contabilita")
	private String tipoContabilita;
	
	@Column(name = "trb_tipo_contabilita")
	private String tributoTipoContabilita;
	
	@Column(name = "tipo_trb_tipo_contabilita")
	private String tipoTributoTipoContabilita;
	
	@Column(name = "sv_codice_contabilita")
	private String codContabilita;
	
	@Column(name = "trb_codice_contabilita")
	private String tributoCodContabilita;
	
	@Column(name = "tipo_trb_cod_contabilita")
	private String tipoTributoCodContabilita;
	
	@Column(name = "indice_dati")
	private Integer indiceDati;
	
	@Column(name = "tipo_bollo")
	private String tipoBollo;
	
	@Column(name = "hash_documento")
	private String hashDocumento;
	
	@Column(name = "provincia_residenza")
	private String provinciaResidenza;
	
	@Column(name = "sv_iban_accredito")
	private String ibanAccredito;
	
	@Column(name = "sv_iban_accr_postale")
	private Boolean ibanAccreditoPostale;
	
	@Column(name = "trb_iban_accredito")
	private String tributoIbanAccredito;
	
	@Column(name = "trb_iban_accr_postale")
	private Boolean tributoIbanAccreditoPostale;
	
	@Column(name = "sv_iban_appoggio")
	private String ibanAppoggio;
	
	@Column(name = "sv_iban_app_postale")
	private Boolean ibanAppoggioPostale;
	
	@Column(name = "trb_iban_appoggio")
	private String tributoIbanAppoggio;
	
	@Column(name = "trb_iban_app_postale")
	private Boolean tributoIbanAppoggioPostale;
	
	@Column(name = "metadata")
	private String metadata;
	
	@Column(name = "id_versamento")
	private Long idVersamento;
	
}

/**
 CREATE VIEW v_sng_versamenti_gpd AS
SELECT
    sv.id AS sv_id,
    sv.id_versamento,
    d.cod_dominio,
    sv.cod_singolo_versamento_ente,
    sv.importo_singolo_versamento,
    sv.indice_dati,
    sv.descrizione AS sv_descrizione,
    sv.descrizione_causale_rpt,
    sv.metadata,
    sv.tipo_bollo,
    sv.hash_documento,
    sv.provincia_residenza,
    sv.tipo_contabilita AS sv_tipo_contabilita,
    sv.codice_contabilita AS sv_codice_contabilita,
    t.tipo_contabilita AS trb_tipo_contabilita,
    t.codice_contabilita AS trb_codice_contabilita,
    tt.tipo_contabilita AS tipo_trb_tipo_contabilita,
    tt.cod_contabilita AS tipo_trb_cod_contabilita,
    ia.cod_iban AS sv_iban_accredito,
    ia.postale AS sv_iban_accr_postale,
    ia_ap.cod_iban AS sv_iban_appoggio,
    ia_ap.postale AS sv_iban_app_postale,
    ia_tr.cod_iban AS trb_iban_accredito,
    ia_tr.postale AS trb_iban_accr_postale,
    ia_tr_ap.cod_iban AS trb_iban_appoggio,
    ia_tr_ap.postale AS trb_iban_app_postale
FROM
    singoli_versamenti sv
    LEFT JOIN tributi t ON sv.id_tributo = t.id
    LEFT JOIN tipi_tributo tt ON t.id_tipo_tributo = tt.id
    LEFT JOIN iban_accredito ia ON sv.id_iban_accredito = ia.id
    LEFT JOIN iban_accredito ia_ap ON sv.id_iban_appoggio = ia_ap.id
    LEFT JOIN iban_accredito ia_tr ON t.id_iban_accredito = ia_tr.id
    LEFT JOIN iban_accredito ia_tr_ap ON t.id_iban_appoggio = ia_tr_ap.id
    LEFT JOIN domini d ON sv.id_dominio = d.id;
 * */


