CREATE VIEW v_versamenti_aca AS 
SELECT versamenti.id,
    versamenti.cod_versamento_ente,
    versamenti.importo_totale,
    versamenti.stato_versamento,
    versamenti.data_validita,
    versamenti.data_scadenza,
    CAST(N'' AS XML).value('xs:base64Binary(substring(sql:column("versamenti.causale_versamento"), 3))', 'VARBINARY(MAX)') as causale_versamento,
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