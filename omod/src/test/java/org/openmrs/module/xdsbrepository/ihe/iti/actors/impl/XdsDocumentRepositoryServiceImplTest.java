package org.openmrs.module.xdsbrepository.ihe.iti.actors.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.dcm4chee.xds2.infoset.ihe.ProvideAndRegisterDocumentSetRequestType;
import org.dcm4chee.xds2.infoset.rim.ExtrinsicObjectType;
import org.dcm4chee.xds2.infoset.rim.RegistryResponseType;
import org.dcm4chee.xds2.infoset.rim.SubmitObjectsRequest;
import org.dcm4chee.xds2.infoset.util.InfosetUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openmrs.EncounterRole;
import org.openmrs.EncounterType;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAddress;
import org.openmrs.Provider;
import org.openmrs.api.EncounterService;
import org.openmrs.api.PatientIdentifierException;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.shr.contenthandler.api.CodedValue;
import org.openmrs.module.shr.contenthandler.api.Content;
import org.openmrs.module.shr.contenthandler.api.ContentHandler;
import org.openmrs.module.shr.contenthandler.api.ContentHandlerService;
import org.openmrs.module.xdsbrepository.ihe.iti.actors.impl.exceptions.RegistryNotAvailableException;
import org.openmrs.module.xdsbrepository.ihe.iti.actors.impl.exceptions.UnsupportedGenderException;
import org.openmrs.test.BaseModuleContextSensitiveTest;

import com.github.tomakehurst.wiremock.junit.WireMockRule;

public class XdsDocumentRepositoryServiceImplTest extends BaseModuleContextSensitiveTest {
	
	@Rule
	public WireMockRule wireMockRule = new WireMockRule(8089);
	
	@Before
	public void setup() throws Exception {
		executeDataSet("src/test/resources/provideAndRegRequest-dataset.xml");
	}
	
	@SuppressWarnings("unchecked")
	private ProvideAndRegisterDocumentSetRequestType parseRequestFromFile(File file) throws JAXBException, FileNotFoundException {
		JAXBContext jaxbContext = JAXBContext.newInstance("org.dcm4chee.xds2.infoset.ihe:org.dcm4chee.xds2.infoset.rim");
		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

		FileReader reader = new FileReader(file);
		JAXBElement<ProvideAndRegisterDocumentSetRequestType> request = (JAXBElement<ProvideAndRegisterDocumentSetRequestType>) unmarshaller.unmarshal(reader);
		
		return request.getValue();
	}

	@Test
	public void findOrCreatePatient_shouldCreateANewPatientIfNoPatientCanBeFound() throws FileNotFoundException, JAXBException, PatientIdentifierException, ParseException, UnsupportedGenderException {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		File file = new File("src/test/resources/provideAndRegRequest2.xml");
		ProvideAndRegisterDocumentSetRequestType request = parseRequestFromFile(file);
		List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
		ExtrinsicObjectType eo = extrinsicObjects.get(0);
	
		Patient pat = service.findOrCreatePatient(eo);
		
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
	public void findOrCreatePatient_shouldFindAnExistingPatient() throws PatientIdentifierException, JAXBException, ParseException, UnsupportedGenderException, FileNotFoundException {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		File file = new File("src/test/resources/provideAndRegRequest1.xml");
		ProvideAndRegisterDocumentSetRequestType request = parseRequestFromFile(file);
		List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
		ExtrinsicObjectType eo = extrinsicObjects.get(0);
		
		Patient pat = service.findOrCreatePatient(eo);
		
		assertNotNull(pat);
		assertEquals("F", pat.getGender());
		
		assertEquals("Jane", pat.getGivenName());
		assertEquals("Doe", pat.getFamilyName());
		// This is a name that only OpenMRS knows about
		assertEquals("Sarah", pat.getMiddleName());
	}
	
	@Test
	public void findOrCreatePatient_shouldThrowUnsupportedGenderException() throws FileNotFoundException, JAXBException, PatientIdentifierException, ParseException {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		try {
			File file = new File("src/test/resources/provideAndRegRequest-unsupported-gender.xml");
			ProvideAndRegisterDocumentSetRequestType request = parseRequestFromFile(file);
			List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
			ExtrinsicObjectType eo = extrinsicObjects.get(0);
			
			service.findOrCreatePatient(eo);
			
			fail("Should have thrown exception");
		} catch (UnsupportedGenderException e) {
			// expected
		}
	}
	
	@Test
	public void findOrCreateProvider_shouldCreateNewProvidersAndEncounterRolesIfNoneCanBeFound() throws JAXBException, FileNotFoundException {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		File file = new File("src/test/resources/provideAndRegRequest1.xml");
		ProvideAndRegisterDocumentSetRequestType request = parseRequestFromFile(file);
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
		File file = new File("src/test/resources/provideAndRegRequest2.xml");
		ProvideAndRegisterDocumentSetRequestType request = parseRequestFromFile(file);
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
		File file = new File("src/test/resources/provideAndRegRequest1.xml");
		ProvideAndRegisterDocumentSetRequestType request = parseRequestFromFile(file);
		List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
		ExtrinsicObjectType eo = extrinsicObjects.get(0);
		
		EncounterType encounterType = service.findOrCreateEncounterType(eo);
		
		assertEquals(new Integer(1), encounterType.getId());
	}
	
	@Test
	public void findOrCreateEncounterType_shouldCreateANewEncounterType() throws JAXBException, FileNotFoundException {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		File file = new File("src/test/resources/provideAndRegRequest2.xml");
		ProvideAndRegisterDocumentSetRequestType request = parseRequestFromFile(file);
		List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
		ExtrinsicObjectType eo = extrinsicObjects.get(0);
		
		EncounterType encounterType = service.findOrCreateEncounterType(eo);
		
		assertEquals("History and Physical - non existing", encounterType.getName());
	}
	
	@Test
	public void storeDocument_shouldReturnTheDocumentUniqueId() throws FileNotFoundException, JAXBException, PatientIdentifierException, UnsupportedEncodingException, ParseException, UnsupportedGenderException {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		File file = new File("src/test/resources/provideAndRegRequest1.xml");
		ProvideAndRegisterDocumentSetRequestType request = parseRequestFromFile(file);
		List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
		ExtrinsicObjectType eo = extrinsicObjects.get(0);
		
		String uniqueId = service.storeDocument(eo, request);
		
		assertEquals("2009.9.1.2455", uniqueId);
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void storeDocument_shouldCallARegisteredContenthandler() throws Exception {
		PatientService ps = Context.getPatientService();
		EncounterService es = Context.getEncounterService();
		
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		
		ContentHandlerService chs = Context.getService(org.openmrs.module.shr.contenthandler.api.ContentHandlerService.class);
		
		CodedValue typeCode = new CodedValue("testType", "testCodes", "Test Type");
		CodedValue formatCode = new CodedValue("testFormat", "testCodes", "Test Format");
		
		Content expectedContent = new Content("My test document", typeCode, formatCode, "text/plain");
		
		ContentHandler mockHandler = mock(ContentHandler.class);
		when(mockHandler.cloneHandler()).thenReturn(mockHandler);
		chs.registerContentHandler(typeCode, formatCode, mockHandler);
		
		File file = new File("src/test/resources/provideAndRegRequest1.xml");
		ProvideAndRegisterDocumentSetRequestType request = parseRequestFromFile(file);
		List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
		ExtrinsicObjectType eo = extrinsicObjects.get(0);
		
		String uniqueId = service.storeDocument(eo, request);
		
		assertEquals("2009.9.1.2455", uniqueId);
		verify(mockHandler).saveContent(eq(ps.getPatient(2)), (Map<EncounterRole, Set<Provider>>) any(), eq(es.getEncounterType(1)), eq(expectedContent));
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
	
	@Test
	public void sendMetadataToRegistry_shouldSendRequestToRegistry() throws MalformedURLException, Exception {
		stubFor(post(urlEqualTo("/ws/xdsregistry"))
				.willReturn(aResponse()
	                .withStatus(200)
	                .withHeader("Content-Type", "application/soap+xml")
	                .withBody(registryResponse)));
		
		File file = new File("src/test/resources/provideAndRegRequest1.xml");
		ProvideAndRegisterDocumentSetRequestType request = parseRequestFromFile(file);
		
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		RegistryResponseType res = service.sendMetadataToRegistry(new URL("http://localhost:8089/ws/xdsregistry"), request.getSubmitObjectsRequest());
		
		assertEquals("urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Success", res.getStatus());
		com.github.tomakehurst.wiremock.client.WireMock.verify(postRequestedFor(urlEqualTo("/ws/xdsregistry"))
		        .withHeader("Content-Type", containing("application/soap+xml"))
		        .withRequestBody(containing("SubmitObjectsRequest"))
				.withRequestBody(containing("1111111111^^^&amp;1.2.3&amp;ISO")));
	}
	
	@Test
	public void sendMetadataToRegistry_shouldThrowAnExceptionIfTheRegistryIsUnreachable() throws MalformedURLException, Exception {
		File file = new File("src/test/resources/provideAndRegRequest1.xml");
		ProvideAndRegisterDocumentSetRequestType request = parseRequestFromFile(file);
		
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		try {
			service.sendMetadataToRegistry(new URL("http://localhost:9999/ws/xdsregistry"), request.getSubmitObjectsRequest());
			fail("Expected an exception");
		} catch (RegistryNotAvailableException e) {
			// expected
		}
	}
	
}
