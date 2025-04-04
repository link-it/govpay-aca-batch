package it.govpay.gpd.config;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PreventConcurrentJobLauncher {
	
	private static final Logger log = LoggerFactory.getLogger(PreventConcurrentJobLauncher.class);

    @Autowired
    private JobExplorer jobExplorer;

    
    /**
     * Controlla e restituisce l'esecuzione corrente del job, se esiste.
     *
     * @return l'esecuzione corrente del job oppure null se non ce ne sono
     */
    public JobExecution getCurrentRunningJobExecution(String jobName) {
        Set<JobExecution> runningJobs = jobExplorer.findRunningJobExecutions(jobName);
        if (runningJobs != null && !runningJobs.isEmpty()) {
            // Restituisce la prima esecuzione in corso.
            List<JobExecution> list = runningJobs.stream().toList();
            
            log.info("Trovati si seguenti Job in esecuzione: ");
			for (JobExecution je : list) {
				log.info("JobExecution corrente: {}", je.getJobInstance().getJobName());
			}
            
			return list.get(0);
        }
        return null;
    }
}
