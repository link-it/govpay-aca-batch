DELETE FROM tributi;
DELETE FROM tipi_tributo;
DELETE FROM uo;
DELETE FROM iban_accredito;
DELETE FROM domini;
DELETE FROM applicazioni;

INSERT INTO applicazioni (cod_applicazione, auto_iuv, firma_ricevuta, cod_connettore_integrazione, trusted, cod_applicazione_iuv, reg_exp, id, principal) 
VALUES ('IDA2A01', false, '0', 'IDA2A01_INTEGRAZIONE', false, '34', '34[0-9]*', 1, 'gpadmin');

INSERT INTO domini (cod_dominio,abilitato,ragione_sociale,aux_digit,iuv_prefix,segregation_code,intermediato, id) 
VALUES ('12345678901',true,'Ente Creditore Test',0,'%(a)',0,true, 1);

INSERT INTO domini (cod_dominio,abilitato,ragione_sociale,aux_digit,iuv_prefix,segregation_code,intermediato, id) 
VALUES ('12345678902',true,'Ente Creditore Test 2',0,'%(a)',0,true, 2);

-- Insert into uo
INSERT INTO uo (id, cod_uo, uo_denominazione, id_dominio) VALUES (1, 'EC', 'Ente Creditore Test', 1);
INSERT INTO uo (id, cod_uo, uo_denominazione, id_dominio) VALUES (2, 'EC', 'Ente Creditore Test 2', 2);
INSERT INTO uo (id, cod_uo, uo_denominazione, id_dominio) VALUES (3, 'UFF1', 'Ufficio 1', 2);

INSERT INTO iban_accredito (cod_iban, postale, id_dominio, id) VALUES ('IT02L1234512345123451111111', false, 1, 1);
INSERT INTO iban_accredito (cod_iban, postale, id_dominio, id) VALUES ('IT02L0760112345123452222211', true, 1, 2);
INSERT INTO iban_accredito (cod_iban, postale, id_dominio, id) VALUES ('IT02L1234512345123451111122', false, 2, 3);
INSERT INTO iban_accredito (cod_iban, postale, id_dominio, id) VALUES ('IT02L0760112345123452222222', true, 2, 4);
	
	-- Insert into tipi_tributo
INSERT INTO tipi_tributo (id, cod_tributo, tipo_contabilita, cod_contabilita) VALUES
(1, 'BOLLOT', '9', 'MBT'),
(2, 'SEGRETERIA', '9', 'SEGRETERIA'),
(3, 'SPONTANEO', '9', 'SPONTANEO'),
(4, 'DOVUTO', '9', 'DOVUTO');

-- Insert into tributi
INSERT INTO tributi (id, abilitato, tipo_contabilita, codice_contabilita, id_dominio, id_iban_accredito, id_iban_appoggio, id_tipo_tributo) VALUES
(1, true, '9', 'MBT', 1, null, null, 1), 
(2, true, '9', 'MBT', 2, null, null, 1),
(3, true, null, null, 1, 1, 2, 2),
(4, true, null, null, 2, 3, 4, 2),
(5, true, null, null, 1, 1, null, 3),
(6, true, null, null, 2, 3, null, 3),
(7, true, null, null, 1, 1, null, 4);

