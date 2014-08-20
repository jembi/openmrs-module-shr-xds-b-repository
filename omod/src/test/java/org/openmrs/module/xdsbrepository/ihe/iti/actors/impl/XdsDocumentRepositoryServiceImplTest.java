package org.openmrs.module.xdsbrepository.ihe.iti.actors.impl;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.dcm4chee.xds2.infoset.ihe.ProvideAndRegisterDocumentSetRequestType;
import org.dcm4chee.xds2.infoset.rim.ExtrinsicObjectType;
import org.dcm4chee.xds2.infoset.util.InfosetUtil;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.EncounterRole;
import org.openmrs.EncounterType;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAddress;
import org.openmrs.Provider;
import org.openmrs.api.EncounterService;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.shr.contenthandler.api.CodedValue;
import org.openmrs.module.shr.contenthandler.api.Content;
import org.openmrs.module.shr.contenthandler.api.ContentHandler;
import org.openmrs.module.shr.contenthandler.api.ContentHandlerService;
import org.openmrs.test.BaseModuleContextSensitiveTest;

public class XdsDocumentRepositoryServiceImplTest extends BaseModuleContextSensitiveTest {
	
	@Before
	public void setup() throws Exception {
		executeDataSet("src/test/resources/provideAndRegRequest-dataset.xml");
	}
	
	private ProvideAndRegisterDocumentSetRequestType parseRequestFromFile(File file) throws JAXBException, FileNotFoundException {
		JAXBContext jaxbContext = JAXBContext.newInstance("org.dcm4chee.xds2.infoset.ihe:org.dcm4chee.xds2.infoset.rim");
		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

		FileReader reader = new FileReader(file);
		JAXBElement<ProvideAndRegisterDocumentSetRequestType> request = (JAXBElement<ProvideAndRegisterDocumentSetRequestType>) unmarshaller.unmarshal(reader);
		
		return request.getValue();
	}

	@Test
	public void findOrCreatePatient_shouldCreateANewPatientIfNoPatientCanBeFound() {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		try {
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
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	@Test
	public void findOrCreatePatient_shouldFindAnExistingPatient() {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		try {
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
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	@Test
	public void findOrCreateProvider_shouldCreateNewProvidersAndEncounterRolesIfNoneCanBeFound() {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		try {
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
			
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	@Test
	public void findOrCreateProvider_shouldFindAnExistingProviderAndEncounterRole() {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		try {
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
			
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	@Test
	public void findOrCreateEncounterType_shouldFindAnExistingEncounterType() {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		try {
			File file = new File("src/test/resources/provideAndRegRequest1.xml");
			ProvideAndRegisterDocumentSetRequestType request = parseRequestFromFile(file);
			List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
			ExtrinsicObjectType eo = extrinsicObjects.get(0);
			
			EncounterType encounterType = service.findOrCreateEncounterType(eo);
			
			assertEquals(new Integer(1), encounterType.getId());
			
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	@Test
	public void findOrCreateEncounterType_shouldCreateANewEncounterType() {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		try {
			File file = new File("src/test/resources/provideAndRegRequest2.xml");
			ProvideAndRegisterDocumentSetRequestType request = parseRequestFromFile(file);
			List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
			ExtrinsicObjectType eo = extrinsicObjects.get(0);
			
			EncounterType encounterType = service.findOrCreateEncounterType(eo);
			
			assertEquals("History and Physical - non existing", encounterType.getName());
			
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	@Test
	public void storeDocument_shouldReturnTheDocumentUniqueId() {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		try {
			File file = new File("src/test/resources/provideAndRegRequest1.xml");
			ProvideAndRegisterDocumentSetRequestType request = parseRequestFromFile(file);
			List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
			ExtrinsicObjectType eo = extrinsicObjects.get(0);
			
			String uniqueId = service.storeDocument(eo, request);
			
			assertEquals("2009.9.1.2455", uniqueId);
			
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
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
	
}
