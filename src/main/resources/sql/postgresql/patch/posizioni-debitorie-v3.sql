-- Prerequisito: script di migrazione 01..04 di govpay-common-pendenze gia' applicati.

-- 1. Vista legacy ristretta alle sole pendenze non appartenenti a una posizione debitoria v3
CREATE OR REPLACE VIEW v_versamenti_gpd AS
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
	LEFT JOIN uo ON versamenti.id_uo = uo.id
    WHERE versamenti.id_opzione_pagamento IS NULL;

-- 2. Nuove viste del modello v3
-- Posizione debitoria (documenti) gestita col modello v3 di govpay-common-pendenze:
-- una riga per posizione. Il debitore non e' sulla posizione ma su ogni opzione
-- (v_opzioni_pagamento_gpd): una posizione ha uno o piu' debitori, ciascuno con le
-- proprie opzioni. Le posizioni con data di pubblicazione futura non sono selezionate.
CREATE VIEW v_posizioni_debitorie_gpd AS
SELECT documenti.id,
    documenti.cod_documento,
    documenti.descrizione,
    documenti.data_pubblicazione,
    applicazioni.cod_applicazione AS cod_applicazione,
    domini.cod_dominio AS cod_dominio,
    domini.ragione_sociale AS ragione_sociale_dominio,
    uo.cod_uo AS cod_uo,
    uo.uo_denominazione AS uo_denominazione,
    (SELECT COUNT(*) FROM opzioni_pagamento op JOIN pd_debitori pdd ON op.id_pd_debitore = pdd.id WHERE pdd.id_documento = documenti.id AND op.stato = 'DISPONIBILE') AS num_opzioni_disponibili,
    (SELECT COUNT(*) FROM opzioni_pagamento op JOIN pd_debitori pdd ON op.id_pd_debitore = pdd.id WHERE pdd.id_documento = documenti.id AND op.stato = 'ATTIVATA') AS num_opzioni_attivate,
    (SELECT COUNT(*) FROM opzioni_pagamento op JOIN pd_debitori pdd ON op.id_pd_debitore = pdd.id WHERE pdd.id_documento = documenti.id AND op.stato = 'ANNULLATA') AS num_opzioni_annullate,
    (SELECT COUNT(*) FROM pd_debitori pdd WHERE pdd.id_documento = documenti.id AND pdd.data_rimozione IS NOT NULL AND documenti.data_ultima_comunicazione_aca IS NOT NULL AND pdd.data_rimozione > documenti.data_ultima_comunicazione_aca) AS num_debitori_da_ritirare,
    documenti.data_ultima_modifica_aca,
    documenti.data_ultima_comunicazione_aca
    FROM documenti
	JOIN domini ON documenti.id_dominio = domini.id
	JOIN applicazioni ON documenti.id_applicazione = applicazioni.id
	LEFT JOIN uo ON documenti.id_unita_operativa = uo.id
    WHERE EXISTS (SELECT 1 FROM opzioni_pagamento op JOIN pd_debitori pdd ON op.id_pd_debitore = pdd.id WHERE pdd.id_documento = documenti.id)
      AND (documenti.data_pubblicazione IS NULL OR documenti.data_pubblicazione <= CURRENT_DATE);


-- Opzioni di pagamento (PaymentOptionModelV3)
CREATE VIEW v_opzioni_pagamento_gpd AS
SELECT op.id,
    pdd.id_documento,
    op.id_pd_debitore,
    op.tipologia,
    op.giorni,
    op.stato,
    op.data_inizio_validita,
    op.data_scadenza,
    d.tipo AS debitore_tipo,
    d.identificativo AS debitore_identificativo,
    d.anagrafica AS debitore_anagrafica,
    d.indirizzo AS debitore_indirizzo,
    d.civico AS debitore_civico,
    d.cap AS debitore_cap,
    d.localita AS debitore_localita,
    d.provincia AS debitore_provincia,
    d.nazione AS debitore_nazione,
    d.email AS debitore_email
    FROM opzioni_pagamento op
	JOIN pd_debitori pdd ON op.id_pd_debitore = pdd.id
	JOIN debitori d ON pdd.id_debitore = d.id;


-- Pendenze come rate dell'opzione (InstallmentModel)
CREATE VIEW v_pendenze_gpd AS
SELECT versamenti.id,
    versamenti.id_opzione_pagamento,
    pd_debitori.id_documento,
    versamenti.cod_versamento_ente,
    versamenti.numero_rata,
    versamenti.importo_totale,
    versamenti.stato_versamento,
    versamenti.data_validita,
    versamenti.data_scadenza,
    versamenti.iuv_versamento,
    versamenti.numero_avviso,
    versamenti.causale_versamento,
    versamenti.data_ultima_modifica_aca,
    versamenti.data_ultima_comunicazione_aca
    FROM versamenti
	JOIN opzioni_pagamento ON versamenti.id_opzione_pagamento = opzioni_pagamento.id
	JOIN pd_debitori ON opzioni_pagamento.id_pd_debitore = pd_debitori.id;

-- 3. Indice per la query di selezione del batch
CREATE INDEX idx_doc_data_modifica_aca ON documenti (data_ultima_modifica_aca);
