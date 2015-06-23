package org.openmrs.module.xdsbrepository.tasks;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.ObjectNotFoundException;
import org.openmrs.EncounterRole;
import org.openmrs.EncounterType;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.api.EncounterService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.shr.contenthandler.api.Content;
import org.openmrs.module.shr.contenthandler.api.ContentHandler;
import org.openmrs.module.shr.contenthandler.api.ContentHandlerService;
import org.openmrs.module.xdsbrepository.Utils;
import org.openmrs.module.xdsbrepository.XDSbService;
import org.openmrs.module.xdsbrepository.XDSbServiceConstants;
import org.openmrs.module.xdsbrepository.exceptions.HydrateRoleProviderMapException;
import org.openmrs.module.xdsbrepository.model.QueueItem;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DiscreteDataProcessorTask extends Thread {

    private Log log = LogFactory.getLog(DiscreteDataProcessorTask.class);

    private int pollPeriod;

    public DiscreteDataProcessorTask() {
        pollPeriod = Integer.parseInt(Context.getAdministrationService().getGlobalProperty(
                XDSbServiceConstants.XDS_REPOSITORY_DISCRETE_HANDLER_ASYNC_POLL_PERIOD, "200"));
    }

    @Override
    public void run() {
        QueueItem currentQueueItem;
        XDSbService service = Context.getService(XDSbService.class);
        Utils.startSession();

        while (true) {
            currentQueueItem = service.dequeueNextDiscreteDataForProcessing();
            if (currentQueueItem != null) {
                try {
                    processQueueItem(currentQueueItem);
                    service.completeQueueItem(currentQueueItem, true);
                } catch (Exception e) {
                    log.error("Error processing discrete data asynchronously for queue item "
                            + currentQueueItem.getId() + " for documentUniqueId " + currentQueueItem.getDocUniqueId(), e);
                    if (currentQueueItem != null) {
                        service.completeQueueItem(currentQueueItem, false);
                    }
                }
            } else {
                try {
                    Thread.sleep(pollPeriod);
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
        }
    }

    protected void processQueueItem(QueueItem queueItem) throws HydrateRoleProviderMapException {
        ContentHandlerService chs = Context.getService(ContentHandlerService.class);
        ContentHandler defaultHandler = chs.getDefaultUnstructuredHandler();

        // fetch content object from unstructured handler
        Content content = defaultHandler.fetchContent(queueItem.getDocUniqueId());
        ContentHandler discreteHandler = chs.getContentHandler(content.getTypeCode(), content.getFormatCode());

        // get metadata objects
        Patient patient = queueItem.getPatient();
        EncounterType encounterType = queueItem.getEncounterType();
        String roleProviderMap = queueItem.getRoleProviderMap();
        Map<EncounterRole, Set<Provider>> providersByRole = hydrateRoleProviderMap(roleProviderMap);

        // attempt discrete save
        discreteHandler.saveContent(patient, providersByRole, encounterType, content);
    }

    protected Map<EncounterRole, Set<Provider>> hydrateRoleProviderMap(String roleProviderMap) throws HydrateRoleProviderMapException {
        // See https://regex101.com/r/wD9oZ4/2 for an explanation of the regex
        boolean valid = roleProviderMap.matches("^(?:\\d+:\\d+(?:,\\d+)*)(?:\\|(?:\\d+:\\d+(?:,\\d+)*))*$");
        if (!valid) {
            throw new HydrateRoleProviderMapException("The RoleProviderMap does not appear to be in the correct format. A correct pattern is as follows: <role_id>:<provider_id>,<provider_id>,...|<role_id>:<provider_id>,<provider_id>,...|...");
        }

        EncounterService es = Context.getEncounterService();
        ProviderService ps = Context.getProviderService();
        Map<EncounterRole, Set<Provider>> providersByRole = new HashMap<EncounterRole, Set<Provider>>();

        String[] role2Providers = roleProviderMap.split("\\|");
        for (int i = 0; i < role2Providers.length; i++) {
            String[] split = role2Providers[i].split(":");
            String[] providers = split[1].split(",");

            Set<Provider> providersSet = new HashSet<Provider>();
            for (int j = 0; j < providers.length; j++) {
                try {
                    providersSet.add(ps.getProvider(Integer.parseInt(providers[j])));
                } catch (ObjectNotFoundException e) {
                    throw new HydrateRoleProviderMapException("Could not fetch provider with id: " + providers[j], e);
                }
            }

            EncounterRole encounterRole = es.getEncounterRole(Integer.parseInt(split[0]));
            if (encounterRole == null) {
                throw new HydrateRoleProviderMapException("Could not fetch encounter role with id: " + split[0]);
            }
            providersByRole.put(encounterRole, providersSet);
        }
        return providersByRole;
    }

}
