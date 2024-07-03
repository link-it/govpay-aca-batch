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
    
    