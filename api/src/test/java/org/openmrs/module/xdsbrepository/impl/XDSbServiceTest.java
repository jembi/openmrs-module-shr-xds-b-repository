package org.openmrs.module.xdsbrepository.impl;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.dcm4chee.xds2.common.XDSConstants;
import org.dcm4chee.xds2.common.exception.XDSException;
import org.dcm4chee.xds2.infoset.ihe.ProvideAndRegisterDocumentSetRequestType;
import org.dcm4chee.xds2.infoset.rim.ExtrinsicObjectType;
import org.dcm4chee.xds2.infoset.rim.RegistryResponseType;
import org.dcm4chee.xds2.infoset.util.InfosetUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openmrs.*;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.shr.contenthandler.api.CodedValue;
import org.openmrs.module.shr.contenthandler.api.Content;
import org.openmrs.module.shr.contenthandler.api.ContentHandler;
import org.openmrs.module.shr.contenthandler.api.ContentHandlerService;
import org.openmrs.module.xdsbrepository.XDSbService;
import org.openmrs.module.xdsbrepository.XDSbServiceConstants;
import org.openmrs.module.xdsbrepository.exceptions.UnsupportedGenderException;
import org.openmrs.module.xdsbrepository.model.QueueItem;
import org.openmrs.test.BaseModuleContextSensitiveTest;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.util.Assert.notNull;
import static org.mockito.Mockito.*;

public class XDSbServiceTest extends BaseModuleContextSensitiveTest {

	@Rule
	public WireMockRule wireMockRule = new WireMockRule(8089);

    private void stubRegistry() {
        stubFor(post(urlEqualTo("/ws/xdsregistry"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/soap+xml")
                        .withBody(registryResponse)));
    }

	private static final String registryResponse = "<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope' xmlns:a='http://www.w3.org/2005/08/addressing'>"
			+ "  <s:Header>"
			+ "		<a:Action s:mustUnderstand='1'>urn:ihe:iti:2007:RegisterDocumentSet-bResponse</a:Action>"
			+ "		<a:RelatesTo>urn:uuid:1ec52e14-4aad-4ba1-b7d3-fc9812a21340</a:RelatesTo>"
			+ "	</s:Header>"
			+ "  <s:Body>"
			+ "		<rs:RegistryResponse xsi:schemaLocation='urn:oasis:names:tc:ebxml-regrep:xsd:rs:3.0 ../../schema/ebRS/rs.xsd' status='urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Success' xmlns:rs='urn:oasis:names:tc:ebxml-regrep:xsd:rs:3.0' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'/>"
			+ "	</s:Body>"
			+ "</s:Envelope>";

    @SuppressWarnings("unchecked")
    private ProvideAndRegisterDocumentSetRequestType parseRequestFromResourceName(String resourceName) throws JAXBException, FileNotFoundException {
        JAXBContext jaxbContext = JAXBContext.newInstance("org.dcm4chee.xds2.infoset.ihe:org.dcm4chee.xds2.infoset.rim");
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

        InputStream is = this.getClass().getClassLoader().getResourceAsStream(resourceName);
        JAXBElement<ProvideAndRegisterDocumentSetRequestType> request = (JAXBElement<ProvideAndRegisterDocumentSetRequestType>) unmarshaller.unmarshal(is);

        return request.getValue();
    }

    @Before
    public void setup() throws Exception {
        executeDataSet("provideAndRegRequest-dataset.xml");

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
	public void sendMetadataToRegistry_shouldSendRequestToRegistry() throws Exception {
        stubRegistry();

        ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest1.xml");

		XDSbServiceImpl service = new XDSbServiceImpl();
		RegistryResponseType res = service.sendMetadataToRegistry(new URL("http://localhost:8089/ws/xdsregistry"), request.getSubmitObjectsRequest());

		assertEquals("urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Success", res.getStatus());
		com.github.tomakehurst.wiremock.client.WireMock.verify(postRequestedFor(urlEqualTo("/ws/xdsregistry"))
                .withHeader("Content-Type", containing("application/soap+xml"))
                .withRequestBody(containing("SubmitObjectsRequest"))
                .withRequestBody(containing("1111111111^^^&amp;1.2.3&amp;ISO")));
	}

    @Test
	public void sendMetadataToRegistry_shouldThrowAnExceptionIfTheRegistryIsUnreachable() throws Exception {
		ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest1.xml");

		XDSbServiceImpl service = new XDSbServiceImpl();
		try {
			service.sendMetadataToRegistry(new URL("http://localhost:9999/ws/xdsregistry"), request.getSubmitObjectsRequest());
			fail("Expected an exception");
		} catch (XDSException e) {
			// expected

            if (!e.getErrorCode().equals(XDSException.XDS_ERR_REG_NOT_AVAIL)) {
                fail();
            }
		}
	}

    @Test
    public void registerDocument_shouldStoreTheDocumentMapping() throws Exception {
        stubRegistry();
        XDSbService service = Context.getService(XDSbService.class);
        ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest1.xml");
        TestContentHandler1 testHandler = new TestContentHandler1();

        RegistryResponseType registryResponseType = service.registerDocument("123456789", testHandler.getClass(), request.getSubmitObjectsRequest());
        notNull(registryResponseType);

        Class<? extends ContentHandler> documentHandlerClass = service.getDocumentHandlerClass("123456789");
        assertEquals(testHandler.getClass(), documentHandlerClass);
    }

    @Test
    public void registerDocument_shouldSendTheDocumentMetaDataToTheRegistry() throws Exception {
        stubRegistry();
        XDSbService service = Context.getService(XDSbService.class);
        ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest1.xml");
        TestContentHandler1 testHandler = new TestContentHandler1();

        RegistryResponseType registryResponseType = service.registerDocument("123456789", testHandler.getClass(), request.getSubmitObjectsRequest());
        notNull(registryResponseType);
        assertEquals("urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Success", registryResponseType.getStatus());
    }

    @Test
    public void registerDocuments_shouldStoreEachDocuemntItRecieves() throws Exception {
        stubRegistry();
        XDSbService service = Context.getService(XDSbService.class);
        ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest1.xml");

        Map<String, Class<? extends ContentHandler>> docHandlerMap = new HashMap<String, Class<? extends ContentHandler>>();
        Class<? extends ContentHandler> testHandlerClass1 = TestContentHandler1.class;
        Class<? extends ContentHandler> testHandlerClass2 = TestContentHandler2.class;
        docHandlerMap.put("111111111", testHandlerClass1);
        docHandlerMap.put("222222222", testHandlerClass2);

        RegistryResponseType registryResponseType = service.registerDocuments(docHandlerMap, request.getSubmitObjectsRequest());
        notNull(registryResponseType);

        Class<? extends ContentHandler> documentHandlerClass1 = service.getDocumentHandlerClass("111111111");
        assertEquals(testHandlerClass1, documentHandlerClass1);
        Class<? extends ContentHandler> documentHandlerClass2 = service.getDocumentHandlerClass("222222222");
        assertEquals(testHandlerClass2, documentHandlerClass2);
    }

    @Test
    public void registerDocuments_shouldSendAllDocumentMetadataToTheRegistry() throws Exception {
        stubRegistry();
        XDSbService service = Context.getService(XDSbService.class);
        ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest1.xml");

        Map<String, Class<? extends ContentHandler>> docHandlerMap = new HashMap<String, Class<? extends ContentHandler>>();
        Class<? extends ContentHandler> testHandlerClass1 = TestContentHandler1.class;
        Class<? extends ContentHandler> testHandlerClass2 = TestContentHandler2.class;
        docHandlerMap.put("111111111", testHandlerClass1);
        docHandlerMap.put("222222222", testHandlerClass2);

        RegistryResponseType registryResponseType = service.registerDocuments(docHandlerMap, request.getSubmitObjectsRequest());
        notNull(registryResponseType);
        assertEquals("urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Success", registryResponseType.getStatus());
        //TODO: find a way to verify the number of docs received?
    }

    @Test
    public void getDocumentHandlerClass_shouldReturnTheMappedHandlerClass() throws Exception {
        //same as above
        registerDocuments_shouldStoreEachDocuemntItRecieves();
    }

    @Test
    public void getDocumentHandlerClass_shouldReturnNullIfNoMappingIsFound() throws Exception {
        XDSbService service = Context.getService(XDSbService.class);
        Class<? extends ContentHandler> handlerClass = service.getDocumentHandlerClass("unkown_id");
        assertNull(handlerClass);
    }

    @Test
    public void sendMetadataToRegistry_shouldThrowErrorIfRegistryUnavailable() throws Exception {
        ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest1.xml");

        XDSbServiceImpl service = new XDSbServiceImpl();

        try {
            service.sendMetadataToRegistry(new URL("http://localhost:9999/not/here"), request.getSubmitObjectsRequest());
            fail("Failed to fail");
        } catch (XDSException ex) {
            //expected

            if (!ex.getErrorCode().equals(XDSException.XDS_ERR_REG_NOT_AVAIL)) {
                fail("XDSException did not specify correct error code");
            }
        }
    }

    @Test
    public void queueDiscreteDataProcessing_shouldStoreItemInQueueWithQueuedStatus() {
        XDSbService service = Context.getService(XDSbService.class);

        QueueItem qi = new QueueItem();
        qi.setPatient(Context.getPatientService().getPatient(2));
        qi.setEncounterType(Context.getEncounterService().getEncounterType(1));
        qi.setRoleProviderMap("1:1,2|2:3");
        qi.setDocUniqueId("123456789");

        qi = service.queueDiscreteDataProcessing(qi);

        assertEquals("123456789", qi.getDocUniqueId());
        assertEquals(new Integer(2), qi.getPatient().getId());
        assertEquals(new Integer(1), qi.getEncounterType().getId());
        assertEquals("1:1,2|2:3", qi.getRoleProviderMap());
        assertEquals(QueueItem.Status.QUEUED, qi.getStatus());
        assertNotNull(qi.getDateAdded());

        // dequeue queue item to clear queued items
        service.dequeueNextDiscreteDataForProcessing();
    }

    @Test
    public void dequeueNextDiscreteDataForProcessing_shouldFetchQueueItemAndSetStatusToProcessing() {
        XDSbService service = Context.getService(XDSbService.class);

        // create a queue item
        QueueItem qi = new QueueItem();
        qi.setPatient(Context.getPatientService().getPatient(2));
        qi.setEncounterType(Context.getEncounterService().getEncounterType(1));
        qi.setRoleProviderMap("1:1,2|2:3");
        qi.setDocUniqueId("123456789");

        service.queueDiscreteDataProcessing(qi);

        qi = service.dequeueNextDiscreteDataForProcessing();
        assertNotNull(qi);
        assertEquals(QueueItem.Status.PROCESSING, qi.getStatus());

        // dequeue queue item to clear queued items
        service.dequeueNextDiscreteDataForProcessing();
    }

    @Test
    public void dequeueNextDiscreteDataForProcessing_shouldReturnNullWhenQueueIsEmpty() {
        XDSbService service = Context.getService(XDSbService.class);
        QueueItem qi = service.dequeueNextDiscreteDataForProcessing();
        assertNull(qi);
    }

    @Test
    public void dequeueNextDiscreteDataForProcessing_shouldFetchOldestQueueItem() {
        XDSbService service = Context.getService(XDSbService.class);

        // create an OLD queue item
        QueueItem qi1 = new QueueItem();
        qi1.setPatient(Context.getPatientService().getPatient(2));
        qi1.setEncounterType(Context.getEncounterService().getEncounterType(1));
        qi1.setRoleProviderMap("1:1,2|2:3");
        qi1.setDocUniqueId("I'm OLD");

        // create a new queue item
        QueueItem qi2 = new QueueItem();
        qi2.setPatient(Context.getPatientService().getPatient(2));
        qi2.setEncounterType(Context.getEncounterService().getEncounterType(1));
        qi2.setRoleProviderMap("1:1,2|2:3");
        qi2.setDocUniqueId("I'm NEW");

        service.queueDiscreteDataProcessing(qi1);
        service.queueDiscreteDataProcessing(qi2);

        QueueItem qi = service.dequeueNextDiscreteDataForProcessing();
        assertEquals("I'm OLD", qi.getDocUniqueId());
    }

    @Test
    public void completeQueueItem_shouldMarkQueueItemAsSuccessful() {
        XDSbService service = Context.getService(XDSbService.class);

        // create a queue item
        QueueItem qi = new QueueItem();
        qi.setPatient(Context.getPatientService().getPatient(2));
        qi.setEncounterType(Context.getEncounterService().getEncounterType(1));
        qi.setRoleProviderMap("1:1,2|2:3");
        qi.setDocUniqueId("Will succeed");

        service.queueDiscreteDataProcessing(qi);
        qi = service.dequeueNextDiscreteDataForProcessing();
        qi = service.completeQueueItem(qi, true);

        assertEquals(QueueItem.Status.SUCCESSFUL, qi.getStatus());
        assertEquals("Will succeed", qi.getDocUniqueId());
    }

    @Test
    public void completeQueueItem_shouldMarkQueueItemAsFailed() {
        XDSbService service = Context.getService(XDSbService.class);

        // create a queue item
        QueueItem qi = new QueueItem();
        qi.setPatient(Context.getPatientService().getPatient(2));
        qi.setEncounterType(Context.getEncounterService().getEncounterType(1));
        qi.setRoleProviderMap("1:1,2|2:3");
        qi.setDocUniqueId("Will fail");

        service.queueDiscreteDataProcessing(qi);
        qi = service.dequeueNextDiscreteDataForProcessing();
        qi = service.completeQueueItem(qi, false);

        assertEquals(QueueItem.Status.FAILED, qi.getStatus());
        assertEquals("Will fail", qi.getDocUniqueId());
    }

    @Test
    public void stringifyRoleProvidersMap_shouldReturnAStringRepresentationOfTheMap() {
        XDSbServiceImpl service = new XDSbServiceImpl();
        Map<EncounterRole, Set<Provider>> providersByRole = new HashMap<EncounterRole, Set<Provider>>();

        Provider p1 = new Provider();
        p1.setId(1);
        Provider p2 = new Provider();
        p2.setId(2);
        Provider p3 = new Provider();
        p3.setId(3);

        Set set1 = new HashSet();
        set1.add(p1);
        set1.add(p2);

        Set set2 = new HashSet();
        set2.add(p3);

        EncounterRole er1 = new EncounterRole();
        er1.setId(1);

        EncounterRole er2 = new EncounterRole();
        er2.setId(2);

        providersByRole.put(er1, set1);
        providersByRole.put(er2, set2);

        String s = service.stringifyRoleProvidersMap(providersByRole);

        // should be 2:3|1:2,1 or equivalent
        assertTrue(s.contains("2:3"));
        assertTrue(s.contains("1:"));
        assertTrue(s.contains("2,1") || s.contains("1,2"));
    }

    @Test
    public void findOrCreatePatient_shouldCreateANewPatientIfNoPatientCanBeFound() throws Exception {
        XDSbServiceImpl service = new XDSbServiceImpl();
        ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest2.xml");
        List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
        ExtrinsicObjectType eo = extrinsicObjects.get(0);


        Patient pat;
        pat = service.findOrCreatePatient(eo);

        // check patient was created correctly
        assertNotNull(pat);
        assertEquals("M", pat.getGender());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String dob = sdf.format(pat.getBirthdate());
        assertEquals("19560527", dob);

        assertEquals("John", pat.getGivenName());
        assertEquals("Doe", pat.getFamilyName());

        PersonAddress pa = pat.getAddresses().iterator().next();
        assertEquals("100 Main St", pa.getAddress1());
        assertEquals("Metropolis", pa.getCityVillage());
        assertEquals("Il", pa.getStateProvince());
        assertEquals("44130", pa.getPostalCode());
        assertEquals("USA", pa.getCountry());

        // check that the needed identifier type was created
        PatientService ps = Context.getPatientService();
        PatientIdentifierType patientIdentifierType = ps.getPatientIdentifierTypeByName("1.2.4");
        assertNotNull(patientIdentifierType);


    }

    @Test
    public void findOrCreatePatient_shouldCreateANewPatientWhenNoPatientNameIsAvailable() throws Exception {
        XDSbServiceImpl service = new XDSbServiceImpl();
        ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest_noPatientName.xml");
        List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
        ExtrinsicObjectType eo = extrinsicObjects.get(0);


        Patient pat;
        pat = service.findOrCreatePatient(eo);

        // check patient was created correctly
        assertNotNull(pat);
        assertEquals("M", pat.getGender());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String dob = sdf.format(pat.getBirthdate());
        assertEquals("19560527", dob);

        assertEquals("*", pat.getGivenName());
        assertEquals("*", pat.getFamilyName());

        PersonAddress pa = pat.getAddresses().iterator().next();
        assertEquals("100 Main St", pa.getAddress1());
        assertEquals("Metropolis", pa.getCityVillage());
        assertEquals("Il", pa.getStateProvince());
        assertEquals("44130", pa.getPostalCode());
        assertEquals("USA", pa.getCountry());

        // check that the needed identifier type was created
        PatientService ps = Context.getPatientService();
        PatientIdentifierType patientIdentifierType = ps.getPatientIdentifierTypeByName("1.2.4");
        assertNotNull(patientIdentifierType);
    }

    @Test
    public void findOrCreatePatient_shouldFindAnExistingPatient() throws Exception {
        XDSbServiceImpl service = new XDSbServiceImpl();
        ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest1.xml");
        List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
        ExtrinsicObjectType eo = extrinsicObjects.get(0);

        Patient pat;
        pat = service.findOrCreatePatient(eo);

        assertNotNull(pat);
        assertEquals("F", pat.getGender());

        assertEquals("Jane", pat.getGivenName());
        assertEquals("Doe", pat.getFamilyName());
        // This is a name that only OpenMRS knows about
        assertEquals("Sarah", pat.getMiddleName());
    }

    @Test
    public void findOrCreatePatient_shouldThrowUnsupportedGenderException() throws Exception {
        XDSbServiceImpl service = new XDSbServiceImpl();
        try {
            ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest-unsupported-gender.xml");
            List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
            ExtrinsicObjectType eo = extrinsicObjects.get(0);

            service.findOrCreatePatient(eo);

            fail("Should have thrown exception");
        } catch (UnsupportedGenderException e) {
            // expected
        }
    }

    public void testFindOrCreatePatientWithPatientId(String id) throws Exception {
        XDSbServiceImpl service = new XDSbServiceImpl();
        try {
            ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest1.xml");
            List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
            ExtrinsicObjectType eo = extrinsicObjects.get(0);
            InfosetUtil.setExternalIdentifierValue(XDSConstants.UUID_XDSDocumentEntry_patientId, id, eo);

            service.findOrCreatePatient(eo);

            fail("Should have thrown exception");
        } catch (XDSException e) {
            // expected
        }
    }

    @Test
    public void findOrCreatePatient_shouldThrowXDSExceptionIfPatientIdHasNoAssigningAuthority() throws Exception {
        testFindOrCreatePatientWithPatientId("12345");
    }

    @Test
    public void findOrCreatePatient_shouldThrowXDSExceptionIfPatientIdHasNoAssigningAuthority2() throws Exception {
        testFindOrCreatePatientWithPatientId("12345^^^^stuff");
    }

    @Test
    public void findOrCreatePatient_shouldThrowXDSExceptionIfPatientIdEmpty() throws Exception {
        testFindOrCreatePatientWithPatientId("^^^&1.2.3.4.5&ISO");
    }

    @Test
    public void findOrCreatePatient_shouldThrowXDSExceptionIfPatientIdIsInvalid() throws Exception {
        testFindOrCreatePatientWithPatientId("this is a bad id");
    }

    @Test
    public void findOrCreatePatient_shouldThrowXDSExceptionIfAssigningAuthorityIdEmpty() throws Exception {
        testFindOrCreatePatientWithPatientId("12345^^^test&&ISO");
    }

    @Test
    public void findOrCreateProvider_shouldCreateNewProvidersAndEncounterRolesIfNoneCanBeFound() throws Exception {
        XDSbServiceImpl service = new XDSbServiceImpl();
        ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest1.xml");
        List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
        ExtrinsicObjectType eo = extrinsicObjects.get(0);

        Map<EncounterRole, Set<Provider>> providersByRole = service.findOrCreateProvidersByRole(eo);

        for (EncounterRole role : providersByRole.keySet()) {
            Set<Provider> providers = providersByRole.get(role);
            if (role.getName().equals("Attending")) {
                assertEquals(1, providers.size());
                Provider provider = providers.iterator().next();
                assertEquals("Gerald Smitty", provider.getName());
            } else if (role.getName().equals("Primary Surgeon")) {
                assertEquals(2, providers.size());
                boolean sherryFound = false;
                boolean terryFound = false;
                for (Provider provider : providers) {
                    if (provider.getName().equals("Sherry Dopplemeyer")) {
                        sherryFound = true;
                    }
                    if (provider.getName().equals("Terry Doppleganger")) {
                        terryFound = true;
                    }
                }
                if (!sherryFound && terryFound) {
                    fail("Sherry or Terry was not found is the resulting set.");
                }
            } else {
                fail("An unexpected role was found.");
            }
        }
    }

    @Test
    public void findOrCreateProvider_shouldFindAnExistingProviderAndEncounterRole() throws Exception {
        XDSbServiceImpl service = new XDSbServiceImpl();
        ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest2.xml");
        List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
        ExtrinsicObjectType eo = extrinsicObjects.get(0);

        Map<EncounterRole, Set<Provider>> providersByRole = service.findOrCreateProvidersByRole(eo);

        boolean jackFound = false;
        for (EncounterRole role : providersByRole.keySet()) {
            Set<Provider> providers = providersByRole.get(role);
            if (role.getName().equals("Nurse")) {
                assertEquals(1, providers.size());
                Provider provider = providers.iterator().next();
                assertEquals("Jack Provider - omrs", provider.getName());
                jackFound = true;

                // test that the encounter role is the one defined in the dataset not a newly created one
                assertEquals(new Integer(2), role.getId());
            }
        }

        if (!jackFound) {
            fail("Provider 'Jack Provider' was not found in the resuting map.");
        }
    }

    @Test
    public void findOrCreateEncounterType_shouldFindAnExistingEncounterType() throws Exception {
        XDSbServiceImpl service = new XDSbServiceImpl();
        ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest1.xml");
        List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
        ExtrinsicObjectType eo = extrinsicObjects.get(0);

        EncounterType encounterType = service.findOrCreateEncounterType(eo);

        assertEquals(new Integer(1), encounterType.getId());
    }

    @Test
    public void findOrCreateEncounterType_shouldCreateANewEncounterType() throws Exception {
        XDSbServiceImpl service = new XDSbServiceImpl();
        ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest2.xml");
        List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
        ExtrinsicObjectType eo = extrinsicObjects.get(0);

        EncounterType encounterType = service.findOrCreateEncounterType(eo);

        assertEquals("History and Physical - non existing", encounterType.getName());
    }

    @Test
    public void storeDocument_shouldReturnTheDocumentUniqueId() throws Exception {
        XDSbServiceImpl service = new XDSbServiceImpl();
        ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest1.xml");
        List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
        ExtrinsicObjectType eo = extrinsicObjects.get(0);

        String uniqueId;
        uniqueId = service.storeDocument(eo, request);
        assertEquals("2009.9.1.2455", uniqueId);

    }

    @SuppressWarnings("unchecked")
    @Test
    public void storeDocument_shouldCallARegisteredContentHandler() throws Exception {
        PatientService ps = Context.getPatientService();
        EncounterService es = Context.getEncounterService();

        XDSbServiceImpl service = new XDSbServiceImpl();

        ContentHandlerService chs = Context.getService(org.openmrs.module.shr.contenthandler.api.ContentHandlerService.class);

        CodedValue typeCode = new CodedValue("testType", "testCodes", "Test Type");
        CodedValue formatCode = new CodedValue("testFormat", "testCodes", "Test Format");

        Content expectedContent = new Content("2009.9.1.2455", "My test document".getBytes(), typeCode, formatCode, "text/plain");

        ContentHandler mockHandler = mock(ContentHandler.class);
        when(mockHandler.cloneHandler()).thenReturn(mockHandler);
        chs.registerContentHandler(typeCode, formatCode, mockHandler);

        ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest1.xml");
        List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
        ExtrinsicObjectType eo = extrinsicObjects.get(0);

        String uniqueId = service.storeDocument(eo, request);

        assertEquals("2009.9.1.2455", uniqueId);
        verify(mockHandler).saveContent(eq(ps.getPatient(2)), (Map<EncounterRole, Set<Provider>>) any(), eq(es.getEncounterType(1)), eq(expectedContent));
    }

    public void testValidateSuccess(String testDocument) throws Exception {
        XDSbServiceImpl service = new XDSbServiceImpl();
        ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName(testDocument);
        List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
        ExtrinsicObjectType eo = extrinsicObjects.get(0);

        CodedValue typeCode = new CodedValue("testType", "testCodes", "Test Type");
        CodedValue formatCode = new CodedValue("testFormat", "testCodes", "Test Format");
        Content content = new Content("2009.9.1.2455", "My test document".getBytes(), typeCode, formatCode, "text/plain");

        service.validateMetadata(eo);
        service.validateContent(eo, content);
    }

    @Test
    public void validate_shouldDoNothingWhenDocumentValid() throws Exception {
        testValidateSuccess("provideAndRegRequest1.xml");
    }

    @Test
    public void validate_shouldDoNothingWhenHashIsCorrect() throws Exception {
        testValidateSuccess("provideAndRegRequest_correctHash.xml");
    }

    @Test
    public void validate_shouldDoNothingWhenSizeIsCorrect() throws Exception {
        testValidateSuccess("provideAndRegRequest_correctSize.xml");
    }

    private void testValidateReject(String testDocument) throws Exception {
        XDSbServiceImpl service = new XDSbServiceImpl();
        ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName(testDocument);
        List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
        ExtrinsicObjectType eo = extrinsicObjects.get(0);

        CodedValue typeCode = new CodedValue("testType", "testCodes", "Test Type");
        CodedValue formatCode = new CodedValue("testFormat", "testCodes", "Test Format");
        Content content = new Content("2009.9.1.2455", "My test document".getBytes(), typeCode, formatCode, "text/plain");

        try {
            service.validateMetadata(eo);
            service.validateContent(eo, content);
            fail("Failed to throw XDSException");
        } catch (XDSException ex) {
            //expected
        }
    }

    @Test
    public void validate_shouldRejectWhenNoUniqueId() throws Exception {
        testValidateReject("provideAndRegRequest_noUniqueId.xml");
    }

    @Test
    public void validate_shouldRejectWhenNoClassCode() throws Exception {
        testValidateReject("provideAndRegRequest_noClassCode.xml");
    }

    @Test
    public void validate_shouldRejectWhenNoDocumentEntryPatientId() throws Exception {
        testValidateReject("provideAndRegRequest_noDocumentEntryPatientId.xml");
    }

    @Test
    public void validate_shouldRejectWhenNoSourcePatientId() throws Exception {
        testValidateReject("provideAndRegRequest_noSourcePatientId.xml");
    }

    @Test
    public void validate_shouldRejectWhenHashIsIncorrect() throws Exception {
        testValidateReject("provideAndRegRequest_incorrectHash.xml");
    }

    @Test
    public void validate_shouldRejectWhenSizeIsIncorrect() throws Exception {
        testValidateReject("provideAndRegRequest_incorrectSize.xml");
    }

    @Test
    public void validateDocumentMatchMetadata_shouldNotThrowWhenMetadataMatchesDocuments() throws Exception {
        XDSbServiceImpl service = new XDSbServiceImpl();
        ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest_multiDoc.xml");
        List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
        service.validateDocumentMatchMetadata(extrinsicObjects, InfosetUtil.getDocuments(request));
    }

    @Test
    public void validateDocumentMatchMetadata_shouldThrowWhenMissingDocs() throws Exception {
        try {
            XDSbServiceImpl service = new XDSbServiceImpl();
            ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest_missingDoc.xml");
            List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
            service.validateDocumentMatchMetadata(extrinsicObjects, InfosetUtil.getDocuments(request));
            fail("Didn't throw expected XDSException");
        } catch (XDSException e) {
            // expected
            assertEquals("The following documents are referenced by metadata but are missing: Document01, Document02", e.getMessage());
        }
    }

    @Test
    public void validateDocumentMatchMetadata_shouldThrowWhenMissingMetadata() throws Exception {
        try {
            XDSbServiceImpl service = new XDSbServiceImpl();
            ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest_missingMeta.xml");
            List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
            service.validateDocumentMatchMetadata(extrinsicObjects, InfosetUtil.getDocuments(request));
            fail("Didn't throw expected XDSException");
        } catch (XDSException e) {
            // expected
            assertEquals("The following documents were found but their metadata is missing: Document01", e.getMessage());
        }
    }

    public class TestContentHandler1 implements ContentHandler {

        @Override
        public Encounter saveContent(Patient patient, Map<EncounterRole, Set<Provider>> encounterRoleSetMap, EncounterType encounterType, Content content) {
            return null;
        }

        @Override
        public Content fetchContent(String s) {
            return null;
        }

        @Override
        public ContentHandler cloneHandler() {
            return new TestContentHandler1();
        }
    }

    public class TestContentHandler2 implements ContentHandler {

        @Override
        public Encounter saveContent(Patient patient, Map<EncounterRole, Set<Provider>> encounterRoleSetMap, EncounterType encounterType, Content content) {
            return null;
        }

        @Override
        public Content fetchContent(String s) {
            return null;
        }

        @Override
        public ContentHandler cloneHandler() {
            return new TestContentHandler2();
        }
    }

}
