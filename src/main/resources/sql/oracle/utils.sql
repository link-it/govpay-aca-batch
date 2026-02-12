-- forzare la terminazione di un job

UPDATE batch_step_execution SET end_time = TO_TIMESTAMP('2025-04-28 18:25:00', 'YYYY-MM-DD HH24:MI:SS'), 
last_updated = TO_TIMESTAMP('2025-04-28 18:25:00', 'YYYY-MM-DD HH24:MI:SS'), status = 'COMPLETED', exit_code = 'COMPLETED' where step_execution_id = 180;


UPDATE batch_job_execution SET end_time = TO_TIMESTAMP('2025-04-28 18:25:00', 'YYYY-MM-DD HH24:MI:SS'), 
last_updated = TO_TIMESTAMP('2025-04-28 18:25:00', 'YYYY-MM-DD HH24:MI:SS'), status = 'COMPLETED', exit_code = 'COMPLETED' where job_execution_id = 180;