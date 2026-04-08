-- =============================================================================
-- Script di svecchiamento tabelle Spring Batch per MySQL
--
-- Elimina le esecuzioni di job completati (COMPLETED, FAILED, STOPPED, ABANDONED)
-- più vecchi di un numero configurabile di giorni.
--
-- Le cancellazioni rispettano l'ordine imposto dalle foreign key.
-- =============================================================================

-- Configurazione: numero di giorni di retention
-- Modifica questo valore in base alle esigenze
SET @retention_days = 90;
SET @cutoff_date = DATE_SUB(NOW(), INTERVAL @retention_days DAY);

SELECT CONCAT('=== Spring Batch Cleanup ===') AS info;
SELECT CONCAT('Data di cutoff: ', @cutoff_date, ' (retention: ', @retention_days, ' giorni)') AS info;

-- 1. BATCH_STEP_EXECUTION_CONTEXT
DELETE FROM BATCH_STEP_EXECUTION_CONTEXT
WHERE STEP_EXECUTION_ID IN (
    SELECT se.STEP_EXECUTION_ID
    FROM BATCH_STEP_EXECUTION se
    JOIN BATCH_JOB_EXECUTION je ON se.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID
    WHERE je.END_TIME < @cutoff_date
      AND je.STATUS IN ('COMPLETED', 'FAILED', 'STOPPED', 'ABANDONED')
);
SELECT CONCAT('BATCH_STEP_EXECUTION_CONTEXT: ', ROW_COUNT(), ' righe eliminate') AS info;

-- 2. BATCH_STEP_EXECUTION
DELETE FROM BATCH_STEP_EXECUTION
WHERE JOB_EXECUTION_ID IN (
    SELECT JOB_EXECUTION_ID
    FROM BATCH_JOB_EXECUTION
    WHERE END_TIME < @cutoff_date
      AND STATUS IN ('COMPLETED', 'FAILED', 'STOPPED', 'ABANDONED')
);
SELECT CONCAT('BATCH_STEP_EXECUTION: ', ROW_COUNT(), ' righe eliminate') AS info;

-- 3. BATCH_JOB_EXECUTION_CONTEXT
DELETE FROM BATCH_JOB_EXECUTION_CONTEXT
WHERE JOB_EXECUTION_ID IN (
    SELECT JOB_EXECUTION_ID
    FROM BATCH_JOB_EXECUTION
    WHERE END_TIME < @cutoff_date
      AND STATUS IN ('COMPLETED', 'FAILED', 'STOPPED', 'ABANDONED')
);
SELECT CONCAT('BATCH_JOB_EXECUTION_CONTEXT: ', ROW_COUNT(), ' righe eliminate') AS info;

-- 4. BATCH_JOB_EXECUTION_PARAMS
DELETE FROM BATCH_JOB_EXECUTION_PARAMS
WHERE JOB_EXECUTION_ID IN (
    SELECT JOB_EXECUTION_ID
    FROM BATCH_JOB_EXECUTION
    WHERE END_TIME < @cutoff_date
      AND STATUS IN ('COMPLETED', 'FAILED', 'STOPPED', 'ABANDONED')
);
SELECT CONCAT('BATCH_JOB_EXECUTION_PARAMS: ', ROW_COUNT(), ' righe eliminate') AS info;

-- 5. BATCH_JOB_EXECUTION
DELETE FROM BATCH_JOB_EXECUTION
WHERE END_TIME < @cutoff_date
  AND STATUS IN ('COMPLETED', 'FAILED', 'STOPPED', 'ABANDONED');
SELECT CONCAT('BATCH_JOB_EXECUTION: ', ROW_COUNT(), ' righe eliminate') AS info;

-- 6. BATCH_JOB_INSTANCE (solo se non hanno più esecuzioni associate)
DELETE FROM BATCH_JOB_INSTANCE
WHERE JOB_INSTANCE_ID NOT IN (
    SELECT DISTINCT JOB_INSTANCE_ID
    FROM BATCH_JOB_EXECUTION
);
SELECT CONCAT('BATCH_JOB_INSTANCE (orfane): ', ROW_COUNT(), ' righe eliminate') AS info;

SELECT '=== Cleanup completato ===' AS info;
