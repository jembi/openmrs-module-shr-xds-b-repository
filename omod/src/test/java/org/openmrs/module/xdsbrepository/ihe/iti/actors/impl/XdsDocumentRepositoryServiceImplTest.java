package org.openmrs.module.xdsbrepository.ihe.iti.actors.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.dcm4chee.xds2.common.XDSConstants;
import org.dcm4chee.xds2.common.exception.XDSException;
import org.dcm4chee.xds2.infoset.ihe.ProvideAndRegisterDocumentSetRequestType;
import org.dcm4chee.xds2.infoset.ihe.RetrieveDocumentSetRequestType;
import org.dcm4chee.xds2.infoset.ihe.RetrieveDocumentSetResponseType;
import org.dcm4chee.xds2.infoset.rim.ExtrinsicObjectType;
import org.dcm4chee.xds2.infoset.rim.RegistryError;
import org.dcm4chee.xds2.infoset.util.InfosetUtil;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.EncounterRole;
import org.openmrs.EncounterType;
import org.openmrs.GlobalProperty;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAddress;
import org.openmrs.Provider;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.PatientIdentifierException;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.shr.contenthandler.api.AlreadyRegisteredException;
import org.openmrs.module.shr.contenthandler.api.CodedValue;
import org.openmrs.module.shr.contenthandler.api.Content;
import org.openmrs.module.shr.contenthandler.api.ContentHandler;
import org.openmrs.module.shr.contenthandler.api.ContentHandlerService;
import org.openmrs.module.shr.contenthandler.api.InvalidCodedValueException;
import org.openmrs.module.xdsbrepository.XDSbService;
import org.openmrs.module.xdsbrepository.XDSbServiceConstants;
import org.openmrs.module.xdsbrepository.ihe.iti.actors.impl.exceptions.UnknownPatientException;
import org.openmrs.module.xdsbrepository.ihe.iti.actors.impl.exceptions.UnsupportedGenderException;
import org.openmrs.test.BaseModuleContextSensitiveTest;

public class XdsDocumentRepositoryServiceImplTest extends BaseModuleContextSensitiveTest {

	@Before
	public void setup() throws Exception {
		executeDataSet("src/test/resources/provideAndRegRequest-dataset.xml");

        AdministrationService as = Context.getAdministrationService();

        GlobalProperty gp1 = new GlobalProperty(XDSbServiceConstants.REPOSITORY_UNIQUE_ID_GP, "1.19.6.24.109.42.1.5.1");
        as.saveGlobalProperty(gp1);
        GlobalProperty gp2 = new GlobalProperty(XDSbServiceConstants.XDS_REPOSITORY_AUTOCREATE_PATIENTS, "true");
        as.saveGlobalProperty(gp2);
        GlobalProperty gp3 = new GlobalProperty("shr.contenthandler.cacheConceptsByName", "false");
        as.saveGlobalProperty(gp3);
	}

    @SuppressWarnings("unchecked")
    private <T> T parseRequestFromResourceName(String resourceName) throws JAXBException, FileNotFoundException {
        JAXBContext jaxbContext = JAXBContext.newInstance("org.dcm4chee.xds2.infoset.ihe:org.dcm4chee.xds2.infoset.rim");
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

        InputStream is = this.getClass().getClassLoader().getResourceAsStream(resourceName);
        JAXBElement<T> request = (JAXBElement<T>) unmarshaller.unmarshal(is);

        return request.getValue();
    }

	@Test
	public void findOrCreatePatient_shouldCreateANewPatientIfNoPatientCanBeFound() throws FileNotFoundException, JAXBException, PatientIdentifierException, ParseException, UnsupportedGenderException {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest2.xml");
		List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
		ExtrinsicObjectType eo = extrinsicObjects.get(0);
	
		
		Patient pat;
		try {
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

		} catch (UnknownPatientException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail();
		}

		}
	
	@Test
	public void findOrCreatePatient_shouldFindAnExistingPatient() throws PatientIdentifierException, JAXBException, ParseException, UnsupportedGenderException, FileNotFoundException {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest1.xml");
		List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
		ExtrinsicObjectType eo = extrinsicObjects.get(0);
		
		Patient pat;
		try {
			pat = service.findOrCreatePatient(eo);
		
			assertNotNull(pat);
			assertEquals("F", pat.getGender());
			
			assertEquals("Jane", pat.getGivenName());
			assertEquals("Doe", pat.getFamilyName());
			// This is a name that only OpenMRS knows about
			assertEquals("Sarah", pat.getMiddleName());
		} catch (UnknownPatientException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail();
		}
	}
	
	@Test
	public void findOrCreatePatient_shouldThrowUnsupportedGenderException() throws FileNotFoundException, JAXBException, PatientIdentifierException, ParseException {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		try {
			ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest-unsupported-gender.xml");
			List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
			ExtrinsicObjectType eo = extrinsicObjects.get(0);
			
			service.findOrCreatePatient(eo);
			
			fail("Should have thrown exception");
		} catch (UnsupportedGenderException e) {
			// expected
		} catch (UnknownPatientException e) {
			fail();
		}
	}
	
	@Test
	public void findOrCreateProvider_shouldCreateNewProvidersAndEncounterRolesIfNoneCanBeFound() throws JAXBException, FileNotFoundException {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
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
	public void findOrCreateProvider_shouldFindAnExistingProviderAndEncounterRole() throws JAXBException, FileNotFoundException {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
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
	public void findOrCreateEncounterType_shouldFindAnExistingEncounterType() throws JAXBException, FileNotFoundException {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest1.xml");
		List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
		ExtrinsicObjectType eo = extrinsicObjects.get(0);
		
		EncounterType encounterType = service.findOrCreateEncounterType(eo);
		
		assertEquals(new Integer(1), encounterType.getId());
	}
	
	@Test
	public void findOrCreateEncounterType_shouldCreateANewEncounterType() throws JAXBException, FileNotFoundException {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest2.xml");
		List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
		ExtrinsicObjectType eo = extrinsicObjects.get(0);
		
		EncounterType encounterType = service.findOrCreateEncounterType(eo);
		
		assertEquals("History and Physical - non existing", encounterType.getName());
	}
	
	@Test
	public void storeDocument_shouldReturnTheDocumentUniqueId() throws FileNotFoundException, JAXBException, PatientIdentifierException, UnsupportedEncodingException, ParseException, UnsupportedGenderException {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest1.xml");
		List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
		ExtrinsicObjectType eo = extrinsicObjects.get(0);
		
		String uniqueId;
		try {
			uniqueId = service.storeDocument(eo, request);
			assertEquals("2009.9.1.2455", uniqueId);
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnknownPatientException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail();
		}
		
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void storeDocument_shouldCallARegisteredContentHandler() throws Exception {
		PatientService ps = Context.getPatientService();
		EncounterService es = Context.getEncounterService();
		
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		
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

    @Test
    public void retrieveDocumentSetB_shouldFetchContentFromTheRegisteredContentHandlerAndReturnSuccess() throws JAXBException, FileNotFoundException, ParseException, UnsupportedEncodingException, UnsupportedGenderException, AlreadyRegisteredException, InvalidCodedValueException, ClassNotFoundException {
        // given
        CodedValue typeCode = new CodedValue("testType", "testCodes", "Test Type");
        CodedValue formatCode = new CodedValue("testFormat", "testCodes", "Test Format");
        Content content = new Content("testId", "My test document".getBytes(), typeCode, formatCode, "text/plain");

        ContentHandler mockHandler = mock(ContentHandler.class);
        when(mockHandler.fetchContent("testId")).thenReturn(content);
        XDSbService mockXdsService = mock(XDSbService.class);
        contextMockHelper.setService(XDSbService.class, mockXdsService);

        Class<? extends ContentHandler> cls = mockHandler.getClass();
        doReturn(cls).when(mockXdsService).getDocumentHandlerClass("testId");

        ContentHandlerService mockHandlerService = mock(ContentHandlerService.class);
        contextMockHelper.setService(ContentHandlerService.class, mockHandlerService);
        when(mockHandlerService.getContentHandlerByClass(cls)).thenReturn(mockHandler);

        XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
        RetrieveDocumentSetRequestType recRequest = parseRequestFromResourceName("retrieveDocumentsRequest-single.xml");

        // when
        RetrieveDocumentSetResponseType response = service.retrieveDocumentSetB(recRequest);

        // then
        verify(mockHandler).fetchContent("testId");
        verify(mockHandlerService).getContentHandlerByClass(cls);
        verify(mockHandlerService, never()).getDefaultUnstructuredHandler();
        verify(mockXdsService).getDocumentHandlerClass("testId");
        assertEquals(1, response.getDocumentResponse().size());
        assertEquals(XDSConstants.XDS_B_STATUS_SUCCESS, response.getRegistryResponse().getStatus());
    }

    @Test
    public void retrieveDocumentSetB_shouldReturnARegistryErrorIfDocumentNotFound() throws JAXBException, FileNotFoundException, ParseException, UnsupportedEncodingException, UnsupportedGenderException, AlreadyRegisteredException, InvalidCodedValueException, ClassNotFoundException {
        // given
        ContentHandler mockHandler = mock(ContentHandler.class);
        when(mockHandler.fetchContent("testId")).thenReturn(null);
        XDSbService mockXdsService = mock(XDSbService.class);
        contextMockHelper.setService(XDSbService.class, mockXdsService);

        Class<? extends ContentHandler> cls = mockHandler.getClass();
        doReturn(cls).when(mockXdsService).getDocumentHandlerClass("testId");

        ContentHandlerService mockHandlerService = mock(ContentHandlerService.class);
        contextMockHelper.setService(ContentHandlerService.class, mockHandlerService);
        when(mockHandlerService.getContentHandlerByClass(cls)).thenReturn(mockHandler);

        XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
        RetrieveDocumentSetRequestType recRequest = parseRequestFromResourceName("retrieveDocumentsRequest-single.xml");

        // when
        RetrieveDocumentSetResponseType response = service.retrieveDocumentSetB(recRequest);

        // then
        verify(mockHandler).fetchContent("testId");
        verify(mockHandlerService).getContentHandlerByClass(cls);
        verify(mockXdsService).getDocumentHandlerClass("testId");
        assertEquals(0, response.getDocumentResponse().size());
        RegistryError registryError = response.getRegistryResponse().getRegistryErrorList().getRegistryError().get(0);
        assertEquals(XDSException.XDS_ERR_MISSING_DOCUMENT, registryError.getErrorCode());
    }

    @Test
    public void retrieveDocumentSetB_shouldReturnARegistryErrorIfRepositoryIdIsNotKnow() throws JAXBException, FileNotFoundException, ParseException, UnsupportedEncodingException, UnsupportedGenderException, AlreadyRegisteredException, InvalidCodedValueException, ClassNotFoundException {
        // given
        XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
        RetrieveDocumentSetRequestType recRequest = parseRequestFromResourceName("retrieveDocumentsRequest-unknown-repo.xml");

        // when
        RetrieveDocumentSetResponseType response = service.retrieveDocumentSetB(recRequest);

        // then
        assertEquals(0, response.getDocumentResponse().size());
        RegistryError registryError = response.getRegistryResponse().getRegistryErrorList().getRegistryError().get(0);
        assertEquals(XDSException.XDS_ERR_UNKNOWN_REPOSITORY_ID, registryError.getErrorCode());
    }

    @Test
    public void retrieveDocumentSetB_shouldReturnPartialSuccessIfNotAllDocWereFound() throws JAXBException, FileNotFoundException, ParseException, UnsupportedEncodingException, UnsupportedGenderException, AlreadyRegisteredException, InvalidCodedValueException, ClassNotFoundException {
        // given
        CodedValue typeCode = new CodedValue("testType", "testCodes", "Test Type");
        CodedValue formatCode = new CodedValue("testFormat", "testCodes", "Test Format");
        Content content = new Content("testId1", "My test document".getBytes(), typeCode, formatCode, "text/plain");

        ContentHandler mockHandler = mock(ContentHandler.class);
        when(mockHandler.fetchContent("testId1")).thenReturn(content);
        when(mockHandler.fetchContent("testId2")).thenReturn(null);
        XDSbService mockXdsService = mock(XDSbService.class);
        contextMockHelper.setService(XDSbService.class, mockXdsService);

        Class<? extends ContentHandler> cls = mockHandler.getClass();
        doReturn(cls).when(mockXdsService).getDocumentHandlerClass("testId1");
        doReturn(cls).when(mockXdsService).getDocumentHandlerClass("testId2");

        ContentHandlerService mockHandlerService = mock(ContentHandlerService.class);
        contextMockHelper.setService(ContentHandlerService.class, mockHandlerService);
        when(mockHandlerService.getContentHandlerByClass(cls)).thenReturn(mockHandler);

        XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
        RetrieveDocumentSetRequestType recRequest = parseRequestFromResourceName("retrieveDocumentsRequest-multiple.xml");

        // when
        RetrieveDocumentSetResponseType response = service.retrieveDocumentSetB(recRequest);

        // then
        verify(mockHandler).fetchContent("testId1");
        verify(mockHandler).fetchContent("testId2");
        verify(mockHandlerService, times(2)).getContentHandlerByClass(cls);
        verify(mockXdsService).getDocumentHandlerClass("testId1");
        verify(mockXdsService).getDocumentHandlerClass("testId2");
        assertEquals(1, response.getDocumentResponse().size());
        assertEquals(XDSConstants.XDS_B_STATUS_PARTIAL_SUCCESS, response.getRegistryResponse().getStatus());
    }

    @Test
    public void retrieveDocumentSetB_shouldCallTheDefaultHandlerIfNoRegisteredHandlersAreFound() throws JAXBException, FileNotFoundException, ParseException, UnsupportedEncodingException, UnsupportedGenderException, AlreadyRegisteredException, InvalidCodedValueException, ClassNotFoundException {
        // given
        CodedValue typeCode = new CodedValue("testType", "testCodes", "Test Type");
        CodedValue formatCode = new CodedValue("testFormat", "testCodes", "Test Format");
        Content content = new Content("testId1", "My test document".getBytes(), typeCode, formatCode, "text/plain");

        ContentHandler mockHandler = mock(ContentHandler.class);
        when(mockHandler.fetchContent("testId1")).thenReturn(content);
        when(mockHandler.fetchContent("testId2")).thenReturn(null);
        XDSbService mockXdsService = mock(XDSbService.class);
        contextMockHelper.setService(XDSbService.class, mockXdsService);

        Class<? extends ContentHandler> cls = mockHandler.getClass();
        doReturn(cls).when(mockXdsService).getDocumentHandlerClass("testId1");
        doReturn(cls).when(mockXdsService).getDocumentHandlerClass("testId2");

        ContentHandlerService mockHandlerService = mock(ContentHandlerService.class);
        contextMockHelper.setService(ContentHandlerService.class, mockHandlerService);
        when(mockHandlerService.getContentHandlerByClass(cls)).thenReturn(null);
        when(mockHandlerService.getDefaultUnstructuredHandler()).thenReturn(mockHandler);

        XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
        RetrieveDocumentSetRequestType recRequest = parseRequestFromResourceName("retrieveDocumentsRequest-multiple.xml");

        // when
        RetrieveDocumentSetResponseType response = service.retrieveDocumentSetB(recRequest);

        // then
        verify(mockHandler).fetchContent("testId1");
        verify(mockHandler).fetchContent("testId2");
        verify(mockHandlerService, times(2)).getContentHandlerByClass(cls);
        verify(mockHandlerService, times(2)).getDefaultUnstructuredHandler();
        verify(mockXdsService).getDocumentHandlerClass("testId1");
        verify(mockXdsService).getDocumentHandlerClass("testId2");
        assertEquals(1, response.getDocumentResponse().size());
        assertEquals(XDSConstants.XDS_B_STATUS_PARTIAL_SUCCESS, response.getRegistryResponse().getStatus());
    }
	
}
