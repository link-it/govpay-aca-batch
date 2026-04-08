-- =============================================================================
-- Script di svecchiamento tabelle Spring Batch per Oracle
--
-- Elimina le esecuzioni di job completati (COMPLETED, FAILED, STOPPED, ABANDONED)
-- più vecchi di un numero configurabile di giorni.
--
-- Le cancellazioni rispettano l'ordine imposto dalle foreign key.
-- =============================================================================

-- Configurazione: numero di giorni di retention
-- Modifica questo valore in base alle esigenze
DEFINE retention_days = 90

SET SERVEROUTPUT ON
SET FEEDBACK OFF

DECLARE
    v_retention_days NUMBER := &retention_days;
    v_cutoff_date    TIMESTAMP;
    v_deleted_count  NUMBER := 0;
    v_total_deleted  NUMBER := 0;
BEGIN
    v_cutoff_date := SYSTIMESTAMP - NUMTODSINTERVAL(v_retention_days, 'DAY');

    DBMS_OUTPUT.PUT_LINE('=== Spring Batch Cleanup ===');
    DBMS_OUTPUT.PUT_LINE('Data di cutoff: ' || TO_CHAR(v_cutoff_date, 'YYYY-MM-DD HH24:MI:SS') || ' (retention: ' || v_retention_days || ' giorni)');
    DBMS_OUTPUT.PUT_LINE('');

    -- 1. BATCH_STEP_EXECUTION_CONTEXT
    DELETE FROM BATCH_STEP_EXECUTION_CONTEXT
    WHERE STEP_EXECUTION_ID IN (
        SELECT se.STEP_EXECUTION_ID
        FROM BATCH_STEP_EXECUTION se
        JOIN BATCH_JOB_EXECUTION je ON se.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID
        WHERE je.END_TIME < v_cutoff_date
          AND je.STATUS IN ('COMPLETED', 'FAILED', 'STOPPED', 'ABANDONED')
    );
    v_deleted_count := SQL%ROWCOUNT;
    v_total_deleted := v_total_deleted + v_deleted_count;
    DBMS_OUTPUT.PUT_LINE('BATCH_STEP_EXECUTION_CONTEXT: ' || v_deleted_count || ' righe eliminate');

    -- 2. BATCH_STEP_EXECUTION
    DELETE FROM BATCH_STEP_EXECUTION
    WHERE JOB_EXECUTION_ID IN (
        SELECT JOB_EXECUTION_ID
        FROM BATCH_JOB_EXECUTION
        WHERE END_TIME < v_cutoff_date
          AND STATUS IN ('COMPLETED', 'FAILED', 'STOPPED', 'ABANDONED')
    );
    v_deleted_count := SQL%ROWCOUNT;
    v_total_deleted := v_total_deleted + v_deleted_count;
    DBMS_OUTPUT.PUT_LINE('BATCH_STEP_EXECUTION: ' || v_deleted_count || ' righe eliminate');

    -- 3. BATCH_JOB_EXECUTION_CONTEXT
    DELETE FROM BATCH_JOB_EXECUTION_CONTEXT
    WHERE JOB_EXECUTION_ID IN (
        SELECT JOB_EXECUTION_ID
        FROM BATCH_JOB_EXECUTION
        WHERE END_TIME < v_cutoff_date
          AND STATUS IN ('COMPLETED', 'FAILED', 'STOPPED', 'ABANDONED')
    );
    v_deleted_count := SQL%ROWCOUNT;
    v_total_deleted := v_total_deleted + v_deleted_count;
    DBMS_OUTPUT.PUT_LINE('BATCH_JOB_EXECUTION_CONTEXT: ' || v_deleted_count || ' righe eliminate');

    -- 4. BATCH_JOB_EXECUTION_PARAMS
    DELETE FROM BATCH_JOB_EXECUTION_PARAMS
    WHERE JOB_EXECUTION_ID IN (
        SELECT JOB_EXECUTION_ID
        FROM BATCH_JOB_EXECUTION
        WHERE END_TIME < v_cutoff_date
          AND STATUS IN ('COMPLETED', 'FAILED', 'STOPPED', 'ABANDONED')
    );
    v_deleted_count := SQL%ROWCOUNT;
    v_total_deleted := v_total_deleted + v_deleted_count;
    DBMS_OUTPUT.PUT_LINE('BATCH_JOB_EXECUTION_PARAMS: ' || v_deleted_count || ' righe eliminate');

    -- 5. BATCH_JOB_EXECUTION
    DELETE FROM BATCH_JOB_EXECUTION
    WHERE END_TIME < v_cutoff_date
      AND STATUS IN ('COMPLETED', 'FAILED', 'STOPPED', 'ABANDONED');
    v_deleted_count := SQL%ROWCOUNT;
    v_total_deleted := v_total_deleted + v_deleted_count;
    DBMS_OUTPUT.PUT_LINE('BATCH_JOB_EXECUTION: ' || v_deleted_count || ' righe eliminate');

    -- 6. BATCH_JOB_INSTANCE (solo se non hanno più esecuzioni associate)
    DELETE FROM BATCH_JOB_INSTANCE
    WHERE JOB_INSTANCE_ID NOT IN (
        SELECT DISTINCT JOB_INSTANCE_ID
        FROM BATCH_JOB_EXECUTION
    );
    v_deleted_count := SQL%ROWCOUNT;
    v_total_deleted := v_total_deleted + v_deleted_count;
    DBMS_OUTPUT.PUT_LINE('BATCH_JOB_INSTANCE (orfane): ' || v_deleted_count || ' righe eliminate');

    DBMS_OUTPUT.PUT_LINE('');
    DBMS_OUTPUT.PUT_LINE('Totale righe eliminate: ' || v_total_deleted);
    DBMS_OUTPUT.PUT_LINE('=== Cleanup completato ===');

    COMMIT;
END;
/
