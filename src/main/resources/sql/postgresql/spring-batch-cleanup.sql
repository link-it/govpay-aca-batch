-- =============================================================================
-- Script di svecchiamento tabelle Spring Batch per PostgreSQL
--
-- Elimina le esecuzioni di job completati (COMPLETED, FAILED, STOPPED, ABANDONED)
-- più vecchi di un numero configurabile di giorni.
--
-- Le cancellazioni rispettano l'ordine imposto dalle foreign key.
-- =============================================================================

-- Configurazione: numero di giorni di retention
-- Modifica questo valore in base alle esigenze
DO $$
DECLARE
    retention_days INTEGER := 90;
    cutoff_date    TIMESTAMP;
    deleted_count  BIGINT := 0;
    total_deleted  BIGINT := 0;
BEGIN
    cutoff_date := NOW() - (retention_days || ' days')::INTERVAL;

    RAISE NOTICE '=== Spring Batch Cleanup ===';
    RAISE NOTICE 'Data di cutoff: % (retention: % giorni)', cutoff_date, retention_days;
    RAISE NOTICE '';

    -- 1. BATCH_STEP_EXECUTION_CONTEXT
    DELETE FROM BATCH_STEP_EXECUTION_CONTEXT
    WHERE STEP_EXECUTION_ID IN (
        SELECT se.STEP_EXECUTION_ID
        FROM BATCH_STEP_EXECUTION se
        JOIN BATCH_JOB_EXECUTION je ON se.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID
        WHERE je.END_TIME < cutoff_date
          AND je.STATUS IN ('COMPLETED', 'FAILED', 'STOPPED', 'ABANDONED')
    );
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    total_deleted := total_deleted + deleted_count;
    RAISE NOTICE 'BATCH_STEP_EXECUTION_CONTEXT: % righe eliminate', deleted_count;

    -- 2. BATCH_STEP_EXECUTION
    DELETE FROM BATCH_STEP_EXECUTION
    WHERE JOB_EXECUTION_ID IN (
        SELECT JOB_EXECUTION_ID
        FROM BATCH_JOB_EXECUTION
        WHERE END_TIME < cutoff_date
          AND STATUS IN ('COMPLETED', 'FAILED', 'STOPPED', 'ABANDONED')
    );
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    total_deleted := total_deleted + deleted_count;
    RAISE NOTICE 'BATCH_STEP_EXECUTION: % righe eliminate', deleted_count;

    -- 3. BATCH_JOB_EXECUTION_CONTEXT
    DELETE FROM BATCH_JOB_EXECUTION_CONTEXT
    WHERE JOB_EXECUTION_ID IN (
        SELECT JOB_EXECUTION_ID
        FROM BATCH_JOB_EXECUTION
        WHERE END_TIME < cutoff_date
          AND STATUS IN ('COMPLETED', 'FAILED', 'STOPPED', 'ABANDONED')
    );
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    total_deleted := total_deleted + deleted_count;
    RAISE NOTICE 'BATCH_JOB_EXECUTION_CONTEXT: % righe eliminate', deleted_count;

    -- 4. BATCH_JOB_EXECUTION_PARAMS
    DELETE FROM BATCH_JOB_EXECUTION_PARAMS
    WHERE JOB_EXECUTION_ID IN (
        SELECT JOB_EXECUTION_ID
        FROM BATCH_JOB_EXECUTION
        WHERE END_TIME < cutoff_date
          AND STATUS IN ('COMPLETED', 'FAILED', 'STOPPED', 'ABANDONED')
    );
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    total_deleted := total_deleted + deleted_count;
    RAISE NOTICE 'BATCH_JOB_EXECUTION_PARAMS: % righe eliminate', deleted_count;

    -- 5. BATCH_JOB_EXECUTION
    DELETE FROM BATCH_JOB_EXECUTION
    WHERE END_TIME < cutoff_date
      AND STATUS IN ('COMPLETED', 'FAILED', 'STOPPED', 'ABANDONED');
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    total_deleted := total_deleted + deleted_count;
    RAISE NOTICE 'BATCH_JOB_EXECUTION: % righe eliminate', deleted_count;

    -- 6. BATCH_JOB_INSTANCE (solo se non hanno più esecuzioni associate)
    DELETE FROM BATCH_JOB_INSTANCE
    WHERE JOB_INSTANCE_ID NOT IN (
        SELECT DISTINCT JOB_INSTANCE_ID
        FROM BATCH_JOB_EXECUTION
    );
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    total_deleted := total_deleted + deleted_count;
    RAISE NOTICE 'BATCH_JOB_INSTANCE (orfane): % righe eliminate', deleted_count;

    RAISE NOTICE '';
    RAISE NOTICE 'Totale righe eliminate: %', total_deleted;
    RAISE NOTICE '=== Cleanup completato ===';
END $$;
