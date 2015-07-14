package org.openmrs.module.xdsbrepository.tasks;

import org.hibernate.ObjectNotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.EncounterRole;
import org.openmrs.GlobalProperty;
import org.openmrs.Provider;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.shr.contenthandler.api.CodedValue;
import org.openmrs.module.shr.contenthandler.api.Content;
import org.openmrs.module.shr.contenthandler.api.ContentHandler;
import org.openmrs.module.shr.contenthandler.api.ContentHandlerService;
import org.openmrs.module.xdsbrepository.XDSbServiceConstants;
import org.openmrs.module.xdsbrepository.exceptions.HydrateRoleProviderMapException;
import org.openmrs.module.xdsbrepository.model.QueueItem;
import org.openmrs.test.BaseModuleContextSensitiveTest;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DiscreteDataProcessorTaskTest extends BaseModuleContextSensitiveTest {

    @Before
    public void setup() throws Exception {
        executeDataSet("providerMapDataset.xml");

        AdministrationService as = Context.getAdministrationService();

        GlobalProperty gp1 = new GlobalProperty(XDSbServiceConstants.REPOSITORY_UNIQUE_ID_GP, "1.19.6.24.109.42.1.5.1");
        as.saveGlobalProperty(gp1);
        GlobalProperty gp2 = new GlobalProperty(XDSbServiceConstants.XDS_REPOSITORY_AUTOCREATE_PATIENTS, "true");
        as.saveGlobalProperty(gp2);
        GlobalProperty gp3 = new GlobalProperty("shr.contenthandler.cacheConceptsByName", "false");
        as.saveGlobalProperty(gp3);
        GlobalProperty gp4 = new GlobalProperty(XDSbServiceConstants.XDS_REGISTRY_URL_GP, "http://localhost:8089/ws/xdsregistry");
        as.saveGlobalProperty(gp4);
    }

    @Test
    public void processQueueItem_shouldProcessAValidQueueItem() throws Exception {
        ContentHandlerService chs = Context.getService(org.openmrs.module.shr.contenthandler.api.ContentHandlerService.class);
        EncounterService es = Context.getEncounterService();
        PatientService ps = Context.getPatientService();
        DiscreteDataProcessorTask processor = new DiscreteDataProcessorTask();

        // setup test content
        CodedValue typeCode = new CodedValue("testType", "testCodes", "Test Type");
        CodedValue formatCode = new CodedValue("testFormat", "testCodes", "Test Format");
        Content expectedContent = new Content("987654321", "My test document".getBytes(), typeCode, formatCode, "text/plain");

        // mock out discrete handler
        ContentHandler mockHandler = mock(ContentHandler.class);
        when(mockHandler.cloneHandler()).thenReturn(mockHandler);
        // deregister any handler that may already exist
        chs.deregisterContentHandler(typeCode, formatCode);
        chs.registerContentHandler(typeCode, formatCode, mockHandler);

        // mock out unstructured handler
        ContentHandler mockDefaultHandler = mock(ContentHandler.class);
        when(mockDefaultHandler.cloneHandler()).thenReturn(mockDefaultHandler);
        when(mockDefaultHandler.fetchContent("987654321")).thenReturn(expectedContent);
        ContentHandler oldHandler = chs.getDefaultUnstructuredHandler();
        chs.setDefaultUnstructuredHandler(mockDefaultHandler);

        // setup queue item
        QueueItem qi = new QueueItem();
        qi.setDocUniqueId("987654321");
        qi.setRoleProviderMap("311:301,302|312:303");
        qi.setEncounterType(es.getEncounterType(1));
        qi.setPatient(ps.getPatient(2));

        processor.processQueueItem(qi);

        // verify discrete handler was called
        verify(mockHandler).saveContent(eq(ps.getPatient(2)), (Map<EncounterRole, Set<Provider>>) any(), eq(es.getEncounterType(1)), eq(expectedContent));

        // restore mock
        chs.setDefaultUnstructuredHandler(oldHandler);
    }

    @Test
    public void hydrateRoleProviderMap_shouldHydrateTheRoleProviderMapObjects() throws Exception {
        DiscreteDataProcessorTask processor = new DiscreteDataProcessorTask();
        Map<EncounterRole, Set<Provider>> encounterRoleSetMap = processor.hydrateRoleProviderMap("311:301,302|312:303");
        assertEquals(2, encounterRoleSetMap.size());
        Iterator<EncounterRole> iterator = encounterRoleSetMap.keySet().iterator();
        assertEquals(2, encounterRoleSetMap.get(iterator.next()).size());
        assertEquals(1, encounterRoleSetMap.get(iterator.next()).size());
    }

    @Test
    public void hydrateRoleProviderMap_shouldThrowAnExceptionIfProviderDoesNotExist() throws Exception {
        DiscreteDataProcessorTask processor = new DiscreteDataProcessorTask();
        try {
            processor.hydrateRoleProviderMap("311:301,999|312:303");
            fail("Did not throw an exception");
        } catch (HydrateRoleProviderMapException e) {
            // expected
            assertEquals("Could not fetch provider with id: 999", e.getMessage());
        } catch (Exception e) {
            fail("Did not throw correct exception");
        }
    }

    @Test
    public void hydrateRoleProviderMap_shouldThrowAnExceptionIfEncounterRoleDoesNotExist() throws Exception {
        DiscreteDataProcessorTask processor = new DiscreteDataProcessorTask();
        try {
            processor.hydrateRoleProviderMap("999:301,302|312:303");
            fail("Did not throw an exception");
        } catch (HydrateRoleProviderMapException e) {
            // expected
            assertEquals("Could not fetch encounter role with id: 999", e.getMessage());
        } catch (Exception e) {
            fail("Did not throw correct exception");
        }
    }

    @Test
    public void hydrateRoleProviderMap_shouldThrowAnExceptionIfAnInvalidFormatIsProvided() throws Exception {
        DiscreteDataProcessorTask processor = new DiscreteDataProcessorTask();
        testInvalid(processor, "12:1,2|");
        testInvalid(processor, "aasdasd1:21");
        testInvalid(processor, "1:21,,2");
        testInvalid(processor, "1:|1:2,3");
    }

    private void testInvalid(DiscreteDataProcessorTask processor, String format) {
        try {
            processor.hydrateRoleProviderMap(format);
            fail("Did not throw an exception");
        } catch (HydrateRoleProviderMapException e) {
            // expected
            assertEquals("The RoleProviderMap does not appear to be in the correct format. A correct pattern is as follows: <role_id>:<provider_id>,<provider_id>,...|<role_id>:<provider_id>,<provider_id>,...|...", e.getMessage());
        } catch (Exception e) {
            fail("Did not throw correct exception");
        }
    }
}