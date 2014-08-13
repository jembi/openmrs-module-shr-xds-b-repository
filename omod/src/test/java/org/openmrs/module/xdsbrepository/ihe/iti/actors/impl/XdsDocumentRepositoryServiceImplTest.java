package org.openmrs.module.xdsbrepository.ihe.iti.actors.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.dcm4chee.xds2.infoset.ihe.ProvideAndRegisterDocumentSetRequestType;
import org.dcm4chee.xds2.infoset.rim.ExtrinsicObjectType;
import org.dcm4chee.xds2.infoset.util.InfosetUtil;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAddress;
import org.openmrs.Provider;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
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
	public void findOrCreateProvider_shouldFindAnExistingProvider() {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		try {
			File file = new File("src/test/resources/provideAndRegRequest1.xml");
			ProvideAndRegisterDocumentSetRequestType request = parseRequestFromFile(file);
			List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
			ExtrinsicObjectType eo = extrinsicObjects.get(0);
			
			Provider pro = service.findOrCreateProvider(eo);
			
			assertNotNull(pro);
			assertEquals("pro111", pro.getIdentifier());
			assertEquals("Gerald Smitty", pro.getName());
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	@Test
	public void findOrCreateProvider_shouldCreateANewProviderIfNoProviderCanBeFound() {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		try {
			File file = new File("src/test/resources/provideAndRegRequest2.xml");
			ProvideAndRegisterDocumentSetRequestType request = parseRequestFromFile(file);
			List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
			ExtrinsicObjectType eo = extrinsicObjects.get(0);
			
			Provider pro = service.findOrCreateProvider(eo);
			
			assertNotNull(pro);
			assertEquals("pro222", pro.getIdentifier());
			assertEquals("Jack Provider - omrs", pro.getName());
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
}
