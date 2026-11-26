-- Colonne lette dal batch e dalle fixture di test, assenti da schema-libreria.sql.
ALTER TABLE applicazioni ADD COLUMN IF NOT EXISTS principal VARCHAR(255);
ALTER TABLE intermediari ADD COLUMN IF NOT EXISTS cod_connettore_ftp VARCHAR(255);
