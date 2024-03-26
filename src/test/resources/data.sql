DELETE FROM domini;
DELETE FROM applicazioni;

INSERT INTO applicazioni (cod_applicazione, auto_iuv, firma_ricevuta, cod_connettore_integrazione, trusted, cod_applicazione_iuv, reg_exp, id, principal) 
VALUES ('IDA2A01', false, '0', 'IDA2A01_INTEGRAZIONE', false, '34', '34[0-9]*', 1, 'gpadmin');

INSERT INTO domini (cod_dominio,abilitato,ragione_sociale,aux_digit,iuv_prefix,segregation_code,intermediato, id) 
VALUES ('12345678901',true,'Ente Creditore Test',0,'%(a)',0,true, 1);

