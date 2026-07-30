package it.govpay.gpd.config;

import org.springframework.batch.core.job.Job;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import it.govpay.common.batch.runner.AbstractCronJobRunner;
import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.gpd.costanti.Costanti;

@Component
@Profile("cron")
public class CronJobRunner extends AbstractCronJobRunner {

	public CronJobRunner(
			JobExecutionHelper jobExecutionHelper,
			@Qualifier(Costanti.SEND_PENDENZE_GPD_JOBNAME) Job pendenzaSenderJob) {
		super(jobExecutionHelper, pendenzaSenderJob, Costanti.SEND_PENDENZE_GPD_JOBNAME);
	}
}
