-- Schema delle tabelle usate da govpay-common-pendenze: copia, senza commenti, di
-- src/test/resources/db/schema-pendenze-test.sql della libreria. Si aggiorna
-- ricopiando quel file; le colonne in piu' richieste dal batch stanno in
-- schema-libreria-batch.sql.

CREATE SEQUENCE IF NOT EXISTS seq_documenti start 1 increment 1 maxvalue 9223372036854775807 minvalue 1 cache 1 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS seq_versamenti start 1 increment 1 maxvalue 9223372036854775807 minvalue 1 cache 1 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS seq_singoli_versamenti start 1 increment 1 maxvalue 9223372036854775807 minvalue 1 cache 1 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS seq_opzioni_pagamento start 1 increment 1 maxvalue 9223372036854775807 minvalue 1 cache 1 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS seq_debitori start 1 increment 1 maxvalue 9223372036854775807 minvalue 1 cache 1 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS seq_pd_debitori start 1 increment 1 maxvalue 9223372036854775807 minvalue 1 cache 1 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS seq_tipi_versamento start 1 increment 1 maxvalue 9223372036854775807 minvalue 1 cache 1 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS seq_tipi_vers_domini start 1 increment 1 maxvalue 9223372036854775807 minvalue 1 cache 1 NO CYCLE;

CREATE TABLE IF NOT EXISTS tipi_versamento
(
	cod_tipo_versamento VARCHAR(35) NOT NULL,
	descrizione VARCHAR(255) NOT NULL,
	codifica_iuv VARCHAR(4),
	paga_terzi BOOLEAN NOT NULL DEFAULT false,
	abilitato BOOLEAN NOT NULL,
	bo_form_tipo VARCHAR(35),
	bo_form_definizione TEXT,
	bo_validazione_def TEXT,
	bo_trasformazione_tipo VARCHAR(35),
	bo_trasformazione_def TEXT,
	bo_cod_applicazione VARCHAR(35),
	bo_abilitato BOOLEAN NOT NULL DEFAULT false,
	pag_form_tipo VARCHAR(35),
	pag_form_definizione TEXT,
	pag_form_impaginazione TEXT,
	pag_validazione_def TEXT,
	pag_trasformazione_tipo VARCHAR(35),
	pag_trasformazione_def TEXT,
	pag_cod_applicazione VARCHAR(35),
	pag_abilitato BOOLEAN NOT NULL DEFAULT false,
	avv_mail_prom_avv_abilitato BOOLEAN NOT NULL DEFAULT false,
	avv_mail_prom_avv_pdf BOOLEAN,
	avv_mail_prom_avv_tipo VARCHAR(35),
	avv_mail_prom_avv_oggetto TEXT,
	avv_mail_prom_avv_messaggio TEXT,
	avv_mail_prom_ric_abilitato BOOLEAN NOT NULL DEFAULT false,
	avv_mail_prom_ric_pdf BOOLEAN,
	avv_mail_prom_ric_tipo VARCHAR(35),
	avv_mail_prom_ric_oggetto TEXT,
	avv_mail_prom_ric_messaggio TEXT,
	avv_mail_prom_ric_eseguiti BOOLEAN,
	avv_mail_prom_scad_abilitato BOOLEAN NOT NULL DEFAULT false,
	avv_mail_prom_scad_preavviso INT,
	avv_mail_prom_scad_tipo VARCHAR(35),
	avv_mail_prom_scad_oggetto TEXT,
	avv_mail_prom_scad_messaggio TEXT,
	visualizzazione_definizione TEXT,
	trac_csv_tipo VARCHAR(35),
	trac_csv_header_risposta TEXT,
	trac_csv_template_richiesta TEXT,
	trac_csv_template_risposta TEXT,
	avv_app_io_prom_avv_abilitato BOOLEAN NOT NULL DEFAULT false,
	avv_app_io_prom_avv_tipo VARCHAR(35),
	avv_app_io_prom_avv_oggetto TEXT,
	avv_app_io_prom_avv_messaggio TEXT,
	avv_app_io_prom_ric_abilitato BOOLEAN NOT NULL DEFAULT false,
	avv_app_io_prom_ric_tipo VARCHAR(35),
	avv_app_io_prom_ric_oggetto TEXT,
	avv_app_io_prom_ric_messaggio TEXT,
	avv_app_io_prom_ric_eseguiti BOOLEAN,
	avv_app_io_prom_scad_abilitato BOOLEAN NOT NULL DEFAULT false,
	avv_app_io_prom_scad_preavviso INT,
	avv_app_io_prom_scad_tipo VARCHAR(35),
	avv_app_io_prom_scad_oggetto TEXT,
	avv_app_io_prom_scad_messaggio TEXT,
	id BIGINT DEFAULT nextval('seq_tipi_versamento') NOT NULL,
	CONSTRAINT unique_tipi_versamento_1 UNIQUE (cod_tipo_versamento),
	CONSTRAINT pk_tipi_versamento PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS tipi_vers_domini
(
	codifica_iuv VARCHAR(4),
	paga_terzi BOOLEAN,
	abilitato BOOLEAN,
	bo_form_tipo VARCHAR(35),
	bo_form_definizione TEXT,
	bo_validazione_def TEXT,
	bo_trasformazione_tipo VARCHAR(35),
	bo_trasformazione_def TEXT,
	bo_cod_applicazione VARCHAR(35),
	bo_abilitato BOOLEAN,
	pag_form_tipo VARCHAR(35),
	pag_form_definizione TEXT,
	pag_form_impaginazione TEXT,
	pag_validazione_def TEXT,
	pag_trasformazione_tipo VARCHAR(35),
	pag_trasformazione_def TEXT,
	pag_cod_applicazione VARCHAR(35),
	pag_abilitato BOOLEAN,
	avv_mail_prom_avv_abilitato BOOLEAN,
	avv_mail_prom_avv_pdf BOOLEAN,
	avv_mail_prom_avv_tipo VARCHAR(35),
	avv_mail_prom_avv_oggetto TEXT,
	avv_mail_prom_avv_messaggio TEXT,
	avv_mail_prom_ric_abilitato BOOLEAN,
	avv_mail_prom_ric_pdf BOOLEAN,
	avv_mail_prom_ric_tipo VARCHAR(35),
	avv_mail_prom_ric_oggetto TEXT,
	avv_mail_prom_ric_messaggio TEXT,
	avv_mail_prom_ric_eseguiti BOOLEAN,
	avv_mail_prom_scad_abilitato BOOLEAN,
	avv_mail_prom_scad_preavviso INT,
	avv_mail_prom_scad_tipo VARCHAR(35),
	avv_mail_prom_scad_oggetto TEXT,
	avv_mail_prom_scad_messaggio TEXT,
	visualizzazione_definizione TEXT,
	trac_csv_tipo VARCHAR(35),
	trac_csv_header_risposta TEXT,
	trac_csv_template_richiesta TEXT,
	trac_csv_template_risposta TEXT,
	app_io_api_key VARCHAR(255),
	avv_app_io_prom_avv_abilitato BOOLEAN,
	avv_app_io_prom_avv_tipo VARCHAR(35),
	avv_app_io_prom_avv_oggetto TEXT,
	avv_app_io_prom_avv_messaggio TEXT,
	avv_app_io_prom_ric_abilitato BOOLEAN,
	avv_app_io_prom_ric_tipo VARCHAR(35),
	avv_app_io_prom_ric_oggetto TEXT,
	avv_app_io_prom_ric_messaggio TEXT,
	avv_app_io_prom_ric_eseguiti BOOLEAN,
	avv_app_io_prom_scad_abilitato BOOLEAN,
	avv_app_io_prom_scad_preavviso INT,
	avv_app_io_prom_scad_tipo VARCHAR(35),
	avv_app_io_prom_scad_oggetto TEXT,
	avv_app_io_prom_scad_messaggio TEXT,
	id BIGINT DEFAULT nextval('seq_tipi_vers_domini') NOT NULL,
	id_dominio BIGINT NOT NULL,
	id_tipo_versamento BIGINT NOT NULL,
	CONSTRAINT unique_tipi_vers_domini_1 UNIQUE (id_dominio, id_tipo_versamento),
	CONSTRAINT fk_tvd_id_tipo_versamento FOREIGN KEY (id_tipo_versamento) REFERENCES tipi_versamento(id),
	CONSTRAINT pk_tipi_vers_domini PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS seq_uo start 1 increment 1 maxvalue 9223372036854775807 minvalue 1 cache 1 NO CYCLE;

CREATE TABLE IF NOT EXISTS uo
(
	cod_uo VARCHAR(35) NOT NULL,
	abilitato BOOLEAN NOT NULL,
	uo_codice_identificativo VARCHAR(35),
	uo_denominazione VARCHAR(70),
	uo_indirizzo VARCHAR(70),
	uo_civico VARCHAR(16),
	uo_cap VARCHAR(16),
	uo_localita VARCHAR(35),
	uo_provincia VARCHAR(35),
	uo_nazione VARCHAR(2),
	uo_area VARCHAR(255),
	uo_url_sito_web VARCHAR(255),
	uo_email VARCHAR(255),
	uo_pec VARCHAR(255),
	uo_tel VARCHAR(255),
	uo_fax VARCHAR(255),
	id BIGINT DEFAULT nextval('seq_uo') NOT NULL,
	id_dominio BIGINT NOT NULL,
	CONSTRAINT unique_uo_1 UNIQUE (cod_uo, id_dominio),
	CONSTRAINT pk_uo PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS seq_iban_accredito start 1 increment 1 maxvalue 9223372036854775807 minvalue 1 cache 1 NO CYCLE;

CREATE TABLE IF NOT EXISTS iban_accredito
(
	cod_iban VARCHAR(255) NOT NULL,
	bic_accredito VARCHAR(255),
	postale BOOLEAN NOT NULL,
	abilitato BOOLEAN NOT NULL,
	descrizione VARCHAR(255),
	intestatario VARCHAR(255),
	aut_stampa_poste VARCHAR(255),
	id BIGINT DEFAULT nextval('seq_iban_accredito') NOT NULL,
	id_dominio BIGINT NOT NULL,
	CONSTRAINT unique_iban_accredito_1 UNIQUE (cod_iban, id_dominio),
	CONSTRAINT pk_iban_accredito PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS seq_tipi_tributo start 1 increment 1 maxvalue 9223372036854775807 minvalue 1 cache 1 NO CYCLE;

CREATE TABLE IF NOT EXISTS tipi_tributo
(
	cod_tributo VARCHAR(255) NOT NULL,
	descrizione VARCHAR(255),
	tipo_contabilita VARCHAR(1),
	cod_contabilita VARCHAR(255),
	id BIGINT DEFAULT nextval('seq_tipi_tributo') NOT NULL,
	CONSTRAINT unique_tipi_tributo_1 UNIQUE (cod_tributo),
	CONSTRAINT pk_tipi_tributo PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS seq_tributi start 1 increment 1 maxvalue 9223372036854775807 minvalue 1 cache 1 NO CYCLE;

CREATE TABLE IF NOT EXISTS tributi
(
	abilitato BOOLEAN NOT NULL,
	tipo_contabilita VARCHAR(1),
	codice_contabilita VARCHAR(255),
	id BIGINT DEFAULT nextval('seq_tributi') NOT NULL,
	id_dominio BIGINT NOT NULL,
	id_iban_accredito BIGINT,
	id_iban_appoggio BIGINT,
	id_tipo_tributo BIGINT NOT NULL,
	CONSTRAINT unique_tributi_1 UNIQUE (id_dominio, id_tipo_tributo),
	CONSTRAINT fk_trb_id_iban_accredito FOREIGN KEY (id_iban_accredito) REFERENCES iban_accredito(id),
	CONSTRAINT fk_trb_id_iban_appoggio FOREIGN KEY (id_iban_appoggio) REFERENCES iban_accredito(id),
	CONSTRAINT fk_trb_id_tipo_tributo FOREIGN KEY (id_tipo_tributo) REFERENCES tipi_tributo(id),
	CONSTRAINT pk_tributi PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS documenti
(
	cod_documento VARCHAR(35) NOT NULL,
	descrizione VARCHAR(1024) NOT NULL,
	data_pubblicazione DATE,
	data_ultima_modifica_aca TIMESTAMP,
	data_ultima_comunicazione_aca TIMESTAMP,
	data_creazione TIMESTAMP NOT NULL,
	data_ultimo_aggiornamento TIMESTAMP NOT NULL,
	versione BIGINT NOT NULL DEFAULT 0,
	anomalo BOOLEAN NOT NULL,
	descrizione_stato VARCHAR(255),
	id BIGINT DEFAULT nextval('seq_documenti') NOT NULL,
	id_dominio BIGINT NOT NULL,
	id_unita_operativa BIGINT,
	id_applicazione BIGINT NOT NULL,
	CONSTRAINT unique_documenti_1 UNIQUE (cod_documento, id_applicazione, id_dominio),
	CONSTRAINT pk_documenti PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS debitori
(
	tipo VARCHAR(1) NOT NULL,
	identificativo VARCHAR(35) NOT NULL,
	anagrafica VARCHAR(70),
	indirizzo VARCHAR(70),
	civico VARCHAR(16),
	cap VARCHAR(16),
	localita VARCHAR(35),
	provincia VARCHAR(35),
	nazione VARCHAR(2),
	email VARCHAR(256),
	cellulare VARCHAR(35),
	id BIGINT DEFAULT nextval('seq_debitori') NOT NULL,
	CONSTRAINT pk_debitori PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS pd_debitori
(
	posizione INT NOT NULL,
	data_rimozione TIMESTAMP,
	id BIGINT DEFAULT nextval('seq_pd_debitori') NOT NULL,
	id_documento BIGINT NOT NULL,
	id_debitore BIGINT NOT NULL,
	CONSTRAINT unique_pd_debitori_1 UNIQUE (id_documento, id_debitore),
	CONSTRAINT fk_pdd_id_documento FOREIGN KEY (id_documento) REFERENCES documenti(id),
	CONSTRAINT fk_pdd_id_debitore FOREIGN KEY (id_debitore) REFERENCES debitori(id),
	CONSTRAINT pk_pd_debitori PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS opzioni_pagamento
(
	id_opzione_pagamento UUID NOT NULL,
	tipologia VARCHAR(35) NOT NULL,
	giorni INT,
	stato VARCHAR(35) NOT NULL,
	versione BIGINT NOT NULL,
	data_inizio_validita DATE,
	data_scadenza DATE,
	data_creazione TIMESTAMP NOT NULL,
	data_ultimo_aggiornamento TIMESTAMP NOT NULL,
	id BIGINT DEFAULT nextval('seq_opzioni_pagamento') NOT NULL,
	id_pd_debitore BIGINT NOT NULL,
	CONSTRAINT unique_opzioni_pagamento_id_opzione UNIQUE (id_opzione_pagamento),
	CONSTRAINT fk_opz_id_pd_debitore FOREIGN KEY (id_pd_debitore) REFERENCES pd_debitori(id),
	CONSTRAINT pk_opzioni_pagamento PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS versamenti
(
	cod_versamento_ente VARCHAR(35) NOT NULL,
	nome VARCHAR(35),
	importo_totale DOUBLE PRECISION NOT NULL,
	stato_versamento VARCHAR(35) NOT NULL,
	descrizione_stato VARCHAR(255),
	aggiornabile BOOLEAN NOT NULL,
	data_creazione TIMESTAMP NOT NULL,
	data_validita TIMESTAMP,
	data_scadenza TIMESTAMP,
	data_ora_ultimo_aggiornamento TIMESTAMP NOT NULL,
	causale_versamento VARCHAR(1024),
	debitore_tipo VARCHAR(1),
	debitore_identificativo VARCHAR(35) NOT NULL,
	debitore_anagrafica VARCHAR(70) NOT NULL,
	debitore_indirizzo VARCHAR(70),
	debitore_civico VARCHAR(16),
	debitore_cap VARCHAR(16),
	debitore_localita VARCHAR(35),
	debitore_provincia VARCHAR(35),
	debitore_nazione VARCHAR(2),
	debitore_email VARCHAR(256),
	debitore_telefono VARCHAR(35),
	debitore_cellulare VARCHAR(35),
	debitore_fax VARCHAR(35),
	tassonomia_avviso VARCHAR(35),
	tassonomia VARCHAR(35),
	cod_lotto VARCHAR(35),
	cod_versamento_lotto VARCHAR(35),
	cod_anno_tributario VARCHAR(35),
	cod_bundlekey VARCHAR(256),
	dati_allegati TEXT,
	incasso VARCHAR(1),
	anomalie TEXT,
	iuv_versamento VARCHAR(35),
	numero_avviso VARCHAR(35),
	ack BOOLEAN NOT NULL,
	anomalo BOOLEAN NOT NULL,
	divisione VARCHAR(35),
	direzione VARCHAR(35),
	id_sessione VARCHAR(35),
	data_pagamento TIMESTAMP,
	importo_pagato DOUBLE PRECISION NOT NULL,
	importo_incassato DOUBLE PRECISION NOT NULL,
	stato_pagamento VARCHAR(35) NOT NULL,
	iuv_pagamento VARCHAR(35),
	src_iuv VARCHAR(35),
	src_debitore_identificativo VARCHAR(35) NOT NULL,
	cod_rata VARCHAR(35),
	numero_rata INT NOT NULL,
	tipo VARCHAR(35) NOT NULL,
	data_notifica_avviso TIMESTAMP,
	avviso_notificato BOOLEAN,
	avv_mail_data_prom_scadenza TIMESTAMP,
	avv_mail_prom_scad_notificato BOOLEAN,
	avv_app_io_data_prom_scadenza TIMESTAMP,
	avv_app_io_prom_scad_notificat BOOLEAN,
	proprieta TEXT,
	data_ultima_modifica_aca TIMESTAMP,
	data_ultima_comunicazione_aca TIMESTAMP,
	send_abilitato BOOLEAN NOT NULL,
	send_nav VARCHAR(18),
	send_importo_totale DOUBLE PRECISION,
	send_data_aggiornamento TIMESTAMP,
	id BIGINT DEFAULT nextval('seq_versamenti') NOT NULL,
	id_tipo_versamento_dominio BIGINT NOT NULL,
	id_tipo_versamento BIGINT NOT NULL,
	id_dominio BIGINT NOT NULL,
	id_uo BIGINT,
	id_applicazione BIGINT NOT NULL,
	id_documento BIGINT,
	id_opzione_pagamento BIGINT,
	CONSTRAINT unique_versamenti_1 UNIQUE (cod_versamento_ente, id_applicazione),
	CONSTRAINT fk_vrs_id_opzione_pagamento FOREIGN KEY (id_opzione_pagamento) REFERENCES opzioni_pagamento(id),
	CONSTRAINT pk_versamenti PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS singoli_versamenti
(
	cod_singolo_versamento_ente VARCHAR(70) NOT NULL,
	stato_singolo_versamento VARCHAR(35) NOT NULL,
	importo_singolo_versamento DOUBLE PRECISION NOT NULL,
	tipo_bollo VARCHAR(2),
	hash_documento VARCHAR(70),
	provincia_residenza VARCHAR(2),
	descrizione_causale_rpt VARCHAR(140),
	tipo_contabilita VARCHAR(1),
	codice_contabilita VARCHAR(255),
	descrizione VARCHAR(256),
	dati_allegati TEXT,
	indice_dati INT NOT NULL,
	contabilita TEXT,
	metadata TEXT,
	id BIGINT DEFAULT nextval('seq_singoli_versamenti') NOT NULL,
	id_versamento BIGINT NOT NULL,
	id_tributo BIGINT,
	id_iban_accredito BIGINT,
	id_iban_appoggio BIGINT,
	id_dominio BIGINT,
	CONSTRAINT unique_sng_id_voce UNIQUE (id_versamento, indice_dati),
	CONSTRAINT pk_singoli_versamenti PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS id_messaggio_relativo
(
	counter BIGINT NOT NULL,
	protocollo VARCHAR(255) NOT NULL,
	info_associata VARCHAR(255) NOT NULL,
	ora_registrazione TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT pk_id_messaggio_relativo PRIMARY KEY (protocollo, info_associata)
);

CREATE TABLE IF NOT EXISTS batch_job_instance (
    job_instance_id BIGINT PRIMARY KEY,
    job_name VARCHAR(100) NOT NULL,
    job_key VARCHAR(32) NOT NULL,
    version BIGINT
);

CREATE TABLE IF NOT EXISTS batch_job_execution (
    job_execution_id BIGINT PRIMARY KEY,
    job_instance_id BIGINT NOT NULL,
    create_time TIMESTAMP NOT NULL,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    status VARCHAR(10),
    exit_message VARCHAR(2500),
    version BIGINT,
    exit_code VARCHAR(2500),
    last_updated TIMESTAMP,
    CONSTRAINT fk_batch_job_execution_instance FOREIGN KEY (job_instance_id) REFERENCES batch_job_instance(job_instance_id)
);

CREATE TABLE IF NOT EXISTS batch_job_execution_params (
    job_execution_id BIGINT NOT NULL,
    parameter_name VARCHAR(100) NOT NULL,
    parameter_type VARCHAR(100) NOT NULL,
    parameter_value VARCHAR(2500),
    identifying CHAR(1) NOT NULL,
    CONSTRAINT pk_batch_job_execution_params PRIMARY KEY (job_execution_id, parameter_name),
    CONSTRAINT fk_batch_job_execution_params_exec FOREIGN KEY (job_execution_id) REFERENCES batch_job_execution(job_execution_id)
);

CREATE TABLE IF NOT EXISTS connettori (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    cod_connettore VARCHAR(255) NOT NULL,
    cod_proprieta VARCHAR(255) NOT NULL,
    valore VARCHAR(255) NOT NULL,
    CONSTRAINT uk_connettori UNIQUE (cod_connettore, cod_proprieta)
);

CREATE TABLE IF NOT EXISTS configurazione (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    valore VARCHAR,
    CONSTRAINT uk_configurazione_nome UNIQUE (nome)
);

CREATE TABLE IF NOT EXISTS intermediari (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    cod_intermediario VARCHAR(35) NOT NULL,
    cod_connettore_pdd VARCHAR(35) NOT NULL,
    cod_connettore_recupero_rt VARCHAR(35),
    cod_connettore_aca VARCHAR(35),
    cod_connettore_gpd VARCHAR(35),
    cod_connettore_fr VARCHAR(35),
    cod_connettore_backoffice_ec VARCHAR(35),
    denominazione VARCHAR(255) NOT NULL,
    principal VARCHAR(4000) NOT NULL,
    principal_originale VARCHAR(4000) NOT NULL,
    abilitato BOOLEAN NOT NULL,
    CONSTRAINT uk_intermediari_cod UNIQUE (cod_intermediario)
);

CREATE TABLE IF NOT EXISTS stazioni (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    cod_stazione VARCHAR(35) NOT NULL,
    password VARCHAR(35) NOT NULL,
    abilitato BOOLEAN NOT NULL,
    application_code INT NOT NULL,
    versione VARCHAR(35) NOT NULL,
    id_intermediario BIGINT NOT NULL,
    CONSTRAINT uk_stazioni_cod UNIQUE (cod_stazione),
    CONSTRAINT fk_stazioni_intermediario FOREIGN KEY (id_intermediario) REFERENCES intermediari(id)
);

CREATE TABLE IF NOT EXISTS applicazioni (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    cod_applicazione VARCHAR(35) NOT NULL,
    auto_iuv BOOLEAN NOT NULL,
    firma_ricevuta VARCHAR(1) NOT NULL,
    trusted BOOLEAN NOT NULL,
    cod_connettore_integrazione VARCHAR(255),
    cod_applicazione_iuv VARCHAR(3),
    reg_exp VARCHAR(1024),
    id_utenza BIGINT NOT NULL,
    CONSTRAINT uk_applicazioni_cod UNIQUE (cod_applicazione),
    CONSTRAINT uk_applicazioni_id_utenza UNIQUE (id_utenza)
);

CREATE TABLE IF NOT EXISTS domini (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    cod_dominio VARCHAR(35) NOT NULL,
    gln VARCHAR(35),
    abilitato BOOLEAN NOT NULL,
    ragione_sociale VARCHAR(70) NOT NULL,
    aux_digit INT NOT NULL DEFAULT 0,
    iuv_prefix VARCHAR(255),
    segregation_code INT,
    logo BYTEA,
    cbill VARCHAR(255),
    aut_stampa_poste VARCHAR(255),
    cod_connettore_my_pivot VARCHAR(255),
    cod_connettore_secim VARCHAR(255),
    cod_connettore_gov_pay VARCHAR(255),
    cod_connettore_hyper_sic_apk VARCHAR(255),
    cod_connettore_send VARCHAR(255),
    intermediato BOOLEAN NOT NULL,
    tassonomia_pago_pa VARCHAR(35),
    scarica_fr BOOLEAN NOT NULL,
    id_stazione BIGINT,
    id_applicazione_default BIGINT,
    pagamento_sito BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT uk_domini_cod UNIQUE (cod_dominio),
    CONSTRAINT fk_domini_stazione FOREIGN KEY (id_stazione) REFERENCES stazioni(id)
);

CREATE SEQUENCE IF NOT EXISTS seq_rpt start 1 increment 1 maxvalue 9223372036854775807 minvalue 1 cache 1 NO CYCLE;

CREATE TABLE IF NOT EXISTS rpt
(
	iuv VARCHAR(35) NOT NULL,
	ccp VARCHAR(35) NOT NULL,
	cod_dominio VARCHAR(35) NOT NULL,
	cod_stazione VARCHAR(35) NOT NULL,
	cod_msg_richiesta VARCHAR(35) NOT NULL,
	tipo_versamento VARCHAR(35),
	tipo_identificativo_attestante VARCHAR(1),
	identificativo_attestante VARCHAR(35),
	denominazione_attestante VARCHAR(70),
	stato VARCHAR(35) NOT NULL,
	descrizione_stato TEXT,
	cod_sessione VARCHAR(255),
	cod_sessione_portale VARCHAR(255),
	psp_redirect_url VARCHAR(512),
	xml_rt BYTEA,
	cod_canale VARCHAR(35),
	cod_psp VARCHAR(35),
	cod_intermediario_psp VARCHAR(35),
	xml_rpt BYTEA,
	data_msg_ricevuta TIMESTAMP,
	data_msg_richiesta TIMESTAMP NOT NULL,
	data_aggiornamento_stato TIMESTAMP NOT NULL,
	callback_url TEXT,
	cod_esito_pagamento INT,
	importo_totale_pagato DOUBLE PRECISION,
	versione VARCHAR(35) NOT NULL,
	cod_carrello VARCHAR(35),
	modello_pagamento VARCHAR(16),
	cod_msg_ricevuta VARCHAR(35),
	cod_transazione_rpt VARCHAR(36),
	cod_transazione_rt VARCHAR(36),
	stato_conservazione VARCHAR(35),
	descrizione_stato_cons VARCHAR(512),
	data_conservazione TIMESTAMP,
	bloccante BOOLEAN NOT NULL DEFAULT true,
	id BIGINT DEFAULT nextval('seq_rpt') NOT NULL,
	id_versamento BIGINT,
	id_applicazione BIGINT,
	CONSTRAINT unique_rpt_id_transazione UNIQUE (iuv, ccp, cod_dominio),
	CONSTRAINT pk_rpt PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS seq_pagamenti start 1 increment 1 maxvalue 9223372036854775807 minvalue 1 cache 1 NO CYCLE;

CREATE TABLE IF NOT EXISTS pagamenti
(
	cod_dominio VARCHAR(35) NOT NULL,
	iuv VARCHAR(35) NOT NULL,
	iur VARCHAR(35) NOT NULL,
	indice_dati INT NOT NULL DEFAULT 1,
	importo_pagato DOUBLE PRECISION NOT NULL,
	data_acquisizione TIMESTAMP NOT NULL,
	data_pagamento TIMESTAMP NOT NULL,
	commissioni_psp DOUBLE PRECISION,
	tipo_allegato VARCHAR(2),
	allegato BYTEA,
	data_acquisizione_revoca TIMESTAMP,
	causale_revoca VARCHAR(140),
	dati_revoca VARCHAR(140),
	importo_revocato DOUBLE PRECISION,
	esito_revoca VARCHAR(140),
	dati_esito_revoca VARCHAR(140),
	stato VARCHAR(35),
	tipo VARCHAR(35) NOT NULL,
	id BIGINT DEFAULT nextval('seq_pagamenti') NOT NULL,
	id_rpt BIGINT,
	id_singolo_versamento BIGINT,
	id_incasso BIGINT,
	CONSTRAINT unique_pag_id_riscossione UNIQUE (cod_dominio, iuv, iur, indice_dati),
	CONSTRAINT pk_pagamenti PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS seq_fr start 1 increment 1 maxvalue 9223372036854775807 minvalue 1 cache 1 NO CYCLE;

CREATE TABLE IF NOT EXISTS fr
(
	id_dominio BIGINT NOT NULL,
	cod_dominio VARCHAR(35) NOT NULL,
	cod_flusso VARCHAR(35) NOT NULL,
	data_ora_flusso TIMESTAMP NOT NULL,
	iur VARCHAR(35) NOT NULL,
	data_acquisizione TIMESTAMP NOT NULL,
	data_regolamento TIMESTAMP,
	cod_psp VARCHAR(35) NOT NULL,
	cod_bic_riversamento VARCHAR(35),
	xml BYTEA,
	ragione_sociale_psp VARCHAR(255),
	ragione_sociale_dominio VARCHAR(255),
	numero_pagamenti BIGINT,
	importo_totale_pagamenti DOUBLE PRECISION,
	stato VARCHAR(35) NOT NULL,
	descrizione_stato TEXT,
	revisione BIGINT,
	obsoleto BOOLEAN NOT NULL,
	data_ora_pubblicazione TIMESTAMP,
	data_ora_aggiornamento TIMESTAMP,
	id BIGINT DEFAULT nextval('seq_fr') NOT NULL,
	id_incasso BIGINT,
	CONSTRAINT unique_fr_1 UNIQUE (id_dominio, cod_flusso, data_ora_flusso),
	CONSTRAINT unique_fr_2 UNIQUE (id_dominio, cod_flusso, cod_psp, revisione),
	CONSTRAINT pk_fr PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS seq_rendicontazioni start 1 increment 1 maxvalue 9223372036854775807 minvalue 1 cache 1 NO CYCLE;

CREATE TABLE IF NOT EXISTS rendicontazioni
(
	iuv VARCHAR(35) NOT NULL,
	iur VARCHAR(35) NOT NULL,
	indice_dati INT,
	importo_pagato DOUBLE PRECISION,
	esito INT,
	data TIMESTAMP,
	stato VARCHAR(35) NOT NULL,
	anomalie TEXT,
	esegui_recupero_rt BOOLEAN NOT NULL DEFAULT true,
	notifica_inviata BOOLEAN NOT NULL DEFAULT false,
	id BIGINT DEFAULT nextval('seq_rendicontazioni') NOT NULL,
	id_fr BIGINT NOT NULL,
	id_pagamento BIGINT,
	id_singolo_versamento BIGINT,
	CONSTRAINT fk_rnd_id_fr FOREIGN KEY (id_fr) REFERENCES fr(id),
	CONSTRAINT fk_rnd_id_pagamento FOREIGN KEY (id_pagamento) REFERENCES pagamenti(id),
	CONSTRAINT fk_rnd_id_singolo_versamento FOREIGN KEY (id_singolo_versamento) REFERENCES singoli_versamenti(id),
	CONSTRAINT pk_rendicontazioni PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS seq_notifiche start 1 increment 1 maxvalue 9223372036854775807 minvalue 1 cache 1 NO CYCLE;

CREATE TABLE IF NOT EXISTS notifiche
(
	tipo_esito VARCHAR(16) NOT NULL,
	data_creazione TIMESTAMP NOT NULL,
	stato VARCHAR(16) NOT NULL,
	descrizione_stato VARCHAR(255),
	data_aggiornamento_stato TIMESTAMP NOT NULL,
	data_prossima_spedizione TIMESTAMP NOT NULL,
	tentativi_spedizione BIGINT,
	id BIGINT DEFAULT nextval('seq_notifiche') NOT NULL,
	id_applicazione BIGINT NOT NULL,
	id_rpt BIGINT,
	CONSTRAINT fk_ntf_id_applicazione FOREIGN KEY (id_applicazione) REFERENCES applicazioni(id),
	CONSTRAINT fk_ntf_id_rpt FOREIGN KEY (id_rpt) REFERENCES rpt(id),
	CONSTRAINT pk_notifiche PRIMARY KEY (id)
);
