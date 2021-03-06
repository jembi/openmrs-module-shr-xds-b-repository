/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.xdsbrepository;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dcm4che3.audit.AuditMessages.EventTypeCode;
import org.dcm4chee.xds2.common.audit.XDSAudit;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.ModuleActivator;
import org.openmrs.module.shr.atna.api.AtnaAuditService;
import org.openmrs.module.shr.atna.configuration.AtnaConfiguration;
import org.openmrs.module.xdsbrepository.tasks.DiscreteDataProcessorTask;
import org.openmrs.scheduler.SchedulerException;
import org.openmrs.scheduler.SchedulerService;
import org.openmrs.scheduler.TaskDefinition;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This class contains the logic that is run every time this module is either started or stopped.
 */
public class XDSbRepositoryInterfaceActivator implements ModuleActivator {

	private ScheduledExecutorService scheduledExecutorService;
	
	protected Log log = LogFactory.getLog(getClass());
		
	/**
	 * @see ModuleActivator#willRefreshContext()
	 */
	public void willRefreshContext() {
		log.info("Refreshing XDSb Repository Interface Module");
	}
	
	/**
	 * @see ModuleActivator#contextRefreshed()
	 */
	public void contextRefreshed() {
		log.info("XDSb Repository Interface Module refreshed");
	}
	
	/**
	 * @see ModuleActivator#willStart()
	 */
	public void willStart() {
		log.info("Starting XDSb Repository Interface Module");
	}
	
	/**
	 * @see ModuleActivator#started()
	 */
	public void started() {
		XDSAudit.setAuditLogger(Context.getService(AtnaAuditService.class).getLogger());
		XDSAudit.logApplicationActivity(AtnaConfiguration.getInstance().getDeviceName(), EventTypeCode.ApplicationStart,
				true);
		log.info("XDSb Repository Interface Module started");

		AdministrationService as = Context.getAdministrationService();
		boolean async = Boolean.parseBoolean(as.getGlobalProperty(
				XDSbServiceConstants.XDS_REPOSITORY_DISCRETE_HANDLER_ASYNC));
		int pollPeriod = Integer.parseInt(Context.getAdministrationService().getGlobalProperty(
				XDSbServiceConstants.XDS_REPOSITORY_DISCRETE_HANDLER_ASYNC_POLL_PERIOD, "100"));

		if (async) {
			Integer maxTasks = Integer.parseInt(as.getGlobalProperty(
					XDSbServiceConstants.XDS_REPOSITORY_DISCRETE_HANDLER_ASYNC_MAX_TASKS));
			ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(maxTasks);
			// for each thread schedule a recurring task
			for (int i = 0; i < maxTasks; i++) {
				scheduledExecutorService.scheduleWithFixedDelay(new DiscreteDataProcessorTask(), pollPeriod, pollPeriod,
						TimeUnit.MILLISECONDS);
			}
		}
	}
	
	/**
	 * @see ModuleActivator#willStop()
	 */
	public void willStop() {
		log.info("Stopping XDSb Repository Interface Module");

		if (scheduledExecutorService != null) {
			try {
				if (!scheduledExecutorService.awaitTermination(60, TimeUnit.SECONDS)) {
					// timeout waiting for tasks to complete
					log.error("Timeout waiting for discrete data processor tasks to terminate before module shutdown.");
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * @see ModuleActivator#stopped()
	 */
	public void stopped() {

		XDSAudit.setAuditLogger(Context.getService(AtnaAuditService.class).getLogger());
		XDSAudit.logApplicationActivity(AtnaConfiguration.getInstance().getDeviceName(), EventTypeCode.ApplicationStop,
				true);
		log.info("XDSb Repository Interface Module stopped");
	}
		
}
