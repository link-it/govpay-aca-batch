DELETE FROM singoli_versamenti;
DELETE FROM versamenti;
DELETE FROM opzioni_pagamento;
DELETE FROM pd_debitori;
DELETE FROM debitori;
DELETE FROM documenti;
DELETE FROM uo;
DELETE FROM iban_accredito;
DELETE FROM domini;
DELETE FROM applicazioni;
DELETE FROM stazioni;
DELETE FROM intermediari;
DELETE FROM connettori;

INSERT INTO intermediari (id, cod_intermediario, cod_connettore_pdd, cod_connettore_aca, abilitato, denominazione, principal, principal_originale)
VALUES (1, '11111111111', 'PAGOPA_PDD', 'PAGOPA_ACA', true, 'Intermediario di Test', 'pagopa', 'pagopa');

INSERT INTO stazioni (id, cod_stazione, password, abilitato, application_code, versione, id_intermediario)
VALUES (1, '11111111111_01', 'password', true, 1, 'V1', 1);

INSERT INTO connettori (cod_connettore, cod_proprieta, valore) VALUES ('PAGOPA_ACA', 'URL', 'http://fakehost:8080/');
INSERT INTO connettori (cod_connettore, cod_proprieta, valore) VALUES ('PAGOPA_ACA', 'ABILITATO', 'true');
INSERT INTO connettori (cod_connettore, cod_proprieta, valore) VALUES ('PAGOPA_ACA', 'TIPOAUTENTICAZIONE', 'HTTP_HEADER');
INSERT INTO connettori (cod_connettore, cod_proprieta, valore) VALUES ('PAGOPA_ACA', 'HTTP_HEADER_AUTH_HEADER_NAME', 'Ocp-Apim-Subscription-Key');
INSERT INTO connettori (cod_connettore, cod_proprieta, valore) VALUES ('PAGOPA_ACA', 'HTTP_HEADER_AUTH_HEADER_VALUE', 'ABC123');

INSERT INTO connettori (cod_connettore, cod_proprieta, valore) VALUES ('govpay_gde_api', 'URL', 'http://fakehost:8080');
INSERT INTO connettori (cod_connettore, cod_proprieta, valore) VALUES ('govpay_gde_api', 'ABILITATO', 'true');
INSERT INTO connettori (cod_connettore, cod_proprieta, valore) VALUES ('govpay_gde_api', 'TIPOAUTENTICAZIONE', 'NONE');

INSERT INTO applicazioni (id, cod_applicazione, auto_iuv, firma_ricevuta, trusted, id_utenza, principal)
VALUES (1, 'IDA2A01', false, '0', false, 1, 'gpadmin');

INSERT INTO domini (id, cod_dominio, abilitato, ragione_sociale, aux_digit, iuv_prefix, segregation_code, intermediato, scarica_fr, id_stazione)
VALUES (1, '12345678901', true, 'Ente Creditore Test', 0, '%(a)', 0, true, false, 1);

INSERT INTO uo (id, cod_uo, uo_denominazione, id_dominio, abilitato) VALUES (1, 'EC', 'Ente Creditore Test', 1, true);
INSERT INTO uo (id, cod_uo, uo_denominazione, id_dominio, abilitato) VALUES (100, 'UFF1', 'Ufficio Tributi', 1, true);

INSERT INTO iban_accredito (id, cod_iban, postale, id_dominio, abilitato) VALUES (1, 'IT02L1234512345123451111111', false, 1, true);
