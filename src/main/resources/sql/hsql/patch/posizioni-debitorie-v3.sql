-- Prerequisito: script di migrazione 01..04 di govpay-common-pendenze gia' applicati.

-- 1. Vista legacy ristretta alle sole pendenze non appartenenti a una posizione debitoria v3
DROP VIEW v_versamenti_gpd IF EXISTS;
CREATE VIEW v_versamenti_gpd AS
SELECT versamenti.id,
    versamenti.cod_versamento_ente,
    versamenti.importo_totale,
    versamenti.stato_versamento,
    versamenti.data_validita,
    versamenti.data_scadenza,
    CAST(DECRYPT(SUBSTRING(versamenti.causale_versamento, 3), 'password') AS VARCHAR(1024)) AS causale_versamento,
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
    WHERE versamenti.id_opzione_pagamento IS NULL
       OR EXISTS (SELECT 1 FROM opzioni_pagamento op
                   JOIN documenti d ON d.id = op.id_documento
                  WHERE op.id = versamenti.id_opzione_pagamento
                    AND d.aca_legacy = true);

-- 2. Nuove viste del modello v3
-- Posizione debitoria (documenti) gestita col modello v3: una riga per posizione.
-- Debitore = soggetto di ordine 0 (GPD v3 ammette un solo debitore per opzione).
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
    sd.tipo AS debitore_tipo,
    sd.identificativo AS debitore_identificativo,
    sd.anagrafica AS debitore_anagrafica,
    sd.indirizzo AS debitore_indirizzo,
    sd.civico AS debitore_civico,
    sd.cap AS debitore_cap,
    sd.localita AS debitore_localita,
    sd.provincia AS debitore_provincia,
    sd.nazione AS debitore_nazione,
    sd.email AS debitore_email,
    (SELECT COUNT(*) FROM opzioni_pagamento op WHERE op.id_documento = documenti.id AND op.stato = 'DISPONIBILE') AS num_opzioni_disponibili,
    (SELECT COUNT(*) FROM opzioni_pagamento op WHERE op.id_documento = documenti.id AND op.stato = 'ATTIVATA') AS num_opzioni_attivate,
    (SELECT COUNT(*) FROM opzioni_pagamento op WHERE op.id_documento = documenti.id AND op.stato = 'ANNULLATA') AS num_opzioni_annullate,
    documenti.data_ultima_modifica_aca,
    documenti.data_ultima_comunicazione_aca
    FROM documenti
	JOIN domini ON documenti.id_dominio = domini.id
	JOIN applicazioni ON documenti.id_applicazione = applicazioni.id
	LEFT JOIN uo ON documenti.id_unita_operativa = uo.id
	LEFT JOIN soggetti_debitori sd ON sd.id_documento = documenti.id AND sd.ordine = 0
    WHERE EXISTS (SELECT 1 FROM opzioni_pagamento op WHERE op.id_documento = documenti.id)
      AND documenti.aca_legacy = false;


-- Opzioni di pagamento (PaymentOptionModelV3)
CREATE VIEW v_opzioni_pagamento_gpd AS
SELECT op.id,
    op.id_documento,
    op.tipologia,
    op.giorni,
    op.stato,
    op.data_inizio_validita,
    op.data_scadenza
    FROM opzioni_pagamento op;


-- Pendenze come rate dell'opzione (InstallmentModel)
CREATE VIEW v_pendenze_gpd AS
SELECT versamenti.id,
    versamenti.id_opzione_pagamento,
    opzioni_pagamento.id_documento,
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
	JOIN opzioni_pagamento ON versamenti.id_opzione_pagamento = opzioni_pagamento.id;

-- 3. Indice per la query di selezione del batch
CREATE INDEX idx_doc_data_modifica_aca ON documenti (data_ultima_modifica_aca);
