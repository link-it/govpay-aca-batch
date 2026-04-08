-- =============================================================================
-- Script di svecchiamento tabelle Spring Batch per SQL Server
--
-- Elimina le esecuzioni di job completati (COMPLETED, FAILED, STOPPED, ABANDONED)
-- più vecchi di un numero configurabile di giorni.
--
-- Le cancellazioni rispettano l'ordine imposto dalle foreign key.
-- =============================================================================

-- Configurazione: numero di giorni di retention
-- Modifica questo valore in base alle esigenze
DECLARE @retention_days INT = 90;
DECLARE @cutoff_date DATETIME2 = DATEADD(DAY, -@retention_days, GETDATE());
DECLARE @deleted_count BIGINT = 0;
DECLARE @total_deleted BIGINT = 0;

PRINT '=== Spring Batch Cleanup ===';
PRINT 'Data di cutoff: ' + CONVERT(VARCHAR(30), @cutoff_date, 120) + ' (retention: ' + CAST(@retention_days AS VARCHAR) + ' giorni)';
PRINT '';

BEGIN TRANSACTION;

BEGIN TRY
    -- 1. BATCH_STEP_EXECUTION_CONTEXT
    DELETE FROM BATCH_STEP_EXECUTION_CONTEXT
    WHERE STEP_EXECUTION_ID IN (
        SELECT se.STEP_EXECUTION_ID
        FROM BATCH_STEP_EXECUTION se
        JOIN BATCH_JOB_EXECUTION je ON se.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID
        WHERE je.END_TIME < @cutoff_date
          AND je.STATUS IN ('COMPLETED', 'FAILED', 'STOPPED', 'ABANDONED')
    );
    SET @deleted_count = @@ROWCOUNT;
    SET @total_deleted = @total_deleted + @deleted_count;
    PRINT 'BATCH_STEP_EXECUTION_CONTEXT: ' + CAST(@deleted_count AS VARCHAR) + ' righe eliminate';

    -- 2. BATCH_STEP_EXECUTION
    DELETE FROM BATCH_STEP_EXECUTION
    WHERE JOB_EXECUTION_ID IN (
        SELECT JOB_EXECUTION_ID
        FROM BATCH_JOB_EXECUTION
        WHERE END_TIME < @cutoff_date
          AND STATUS IN ('COMPLETED', 'FAILED', 'STOPPED', 'ABANDONED')
    );
    SET @deleted_count = @@ROWCOUNT;
    SET @total_deleted = @total_deleted + @deleted_count;
    PRINT 'BATCH_STEP_EXECUTION: ' + CAST(@deleted_count AS VARCHAR) + ' righe eliminate';

    -- 3. BATCH_JOB_EXECUTION_CONTEXT
    DELETE FROM BATCH_JOB_EXECUTION_CONTEXT
    WHERE JOB_EXECUTION_ID IN (
        SELECT JOB_EXECUTION_ID
        FROM BATCH_JOB_EXECUTION
        WHERE END_TIME < @cutoff_date
          AND STATUS IN ('COMPLETED', 'FAILED', 'STOPPED', 'ABANDONED')
    );
    SET @deleted_count = @@ROWCOUNT;
    SET @total_deleted = @total_deleted + @deleted_count;
    PRINT 'BATCH_JOB_EXECUTION_CONTEXT: ' + CAST(@deleted_count AS VARCHAR) + ' righe eliminate';

    -- 4. BATCH_JOB_EXECUTION_PARAMS
    DELETE FROM BATCH_JOB_EXECUTION_PARAMS
    WHERE JOB_EXECUTION_ID IN (
        SELECT JOB_EXECUTION_ID
        FROM BATCH_JOB_EXECUTION
        WHERE END_TIME < @cutoff_date
          AND STATUS IN ('COMPLETED', 'FAILED', 'STOPPED', 'ABANDONED')
    );
    SET @deleted_count = @@ROWCOUNT;
    SET @total_deleted = @total_deleted + @deleted_count;
    PRINT 'BATCH_JOB_EXECUTION_PARAMS: ' + CAST(@deleted_count AS VARCHAR) + ' righe eliminate';

    -- 5. BATCH_JOB_EXECUTION
    DELETE FROM BATCH_JOB_EXECUTION
    WHERE END_TIME < @cutoff_date
      AND STATUS IN ('COMPLETED', 'FAILED', 'STOPPED', 'ABANDONED');
    SET @deleted_count = @@ROWCOUNT;
    SET @total_deleted = @total_deleted + @deleted_count;
    PRINT 'BATCH_JOB_EXECUTION: ' + CAST(@deleted_count AS VARCHAR) + ' righe eliminate';

    -- 6. BATCH_JOB_INSTANCE (solo se non hanno più esecuzioni associate)
    DELETE FROM BATCH_JOB_INSTANCE
    WHERE JOB_INSTANCE_ID NOT IN (
        SELECT DISTINCT JOB_INSTANCE_ID
        FROM BATCH_JOB_EXECUTION
    );
    SET @deleted_count = @@ROWCOUNT;
    SET @total_deleted = @total_deleted + @deleted_count;
    PRINT 'BATCH_JOB_INSTANCE (orfane): ' + CAST(@deleted_count AS VARCHAR) + ' righe eliminate';

    COMMIT TRANSACTION;

    PRINT '';
    PRINT 'Totale righe eliminate: ' + CAST(@total_deleted AS VARCHAR);
    PRINT '=== Cleanup completato ===';
END TRY
BEGIN CATCH
    ROLLBACK TRANSACTION;
    PRINT 'ERRORE: ' + ERROR_MESSAGE();
    THROW;
END CATCH;
