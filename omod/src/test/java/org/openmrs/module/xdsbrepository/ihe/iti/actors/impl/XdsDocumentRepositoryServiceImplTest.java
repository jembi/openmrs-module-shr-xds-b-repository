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
import org.junit.Test;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAddress;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.test.BaseModuleContextSensitiveTest;

public class XdsDocumentRepositoryServiceImplTest extends BaseModuleContextSensitiveTest {
	
	private ProvideAndRegisterDocumentSetRequestType parseRequestFromFile(File file) throws JAXBException, FileNotFoundException {
		JAXBContext jaxbContext = JAXBContext.newInstance("org.dcm4chee.xds2.infoset.ihe:org.dcm4chee.xds2.infoset.rim");
		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

		FileReader reader = new FileReader(file);
		JAXBElement<ProvideAndRegisterDocumentSetRequestType> request = (JAXBElement<ProvideAndRegisterDocumentSetRequestType>) unmarshaller.unmarshal(reader);
		
		return request.getValue();
	}

	@Test
	public void findOrCreatePatient_shouldFindAnExistingPatient() {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		try {
			File file = new File("src/test/resources/provideAndRegRequest.xml");
			ProvideAndRegisterDocumentSetRequestType request = parseRequestFromFile(file);
			List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
			ExtrinsicObjectType eo = extrinsicObjects.get(0);
			
			Patient pat = service.findOrCreatePatient(eo);
			
			assertNotNull(pat);
			assertEquals("F", pat.getGender());
			
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
			String dob = sdf.format(pat.getBirthdate());
			assertEquals("19860101", dob);
			
			assertEquals("Jane", pat.getGivenName());
			assertEquals("Doe", pat.getFamilyName());
			
			PersonAddress pa = pat.getAddresses().iterator().next();
			assertEquals("100 Main St", pa.getAddress1());
			assertEquals("Metropolis", pa.getCityVillage());
			assertEquals("Il", pa.getStateProvince());
			assertEquals("44130", pa.getPostalCode());
			assertEquals("USA", pa.getCountry());
			fail(); // need to make sure this patient is found not created
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	@Test
	public void findOrCreatePatient_shouldCreateANewPatientIfNoPatientCanBeFound() {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		try {
			File file = new File("src/test/resources/provideAndRegRequest.xml");
			ProvideAndRegisterDocumentSetRequestType request = parseRequestFromFile(file);
			List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
			ExtrinsicObjectType eo = extrinsicObjects.get(0);
		
			Patient pat = service.findOrCreatePatient(eo);
			
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
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	@Test
	public void findOrCreatePatient_shouldCreateAPatientIdentifierTypeIfOneDoesntExist() {
		XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
		try {
			File file = new File("src/test/resources/provideAndRegRequest.xml");
			ProvideAndRegisterDocumentSetRequestType request = parseRequestFromFile(file);
			List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
			ExtrinsicObjectType eo = extrinsicObjects.get(0);
		
			Patient pat = service.findOrCreatePatient(eo);
			
			PatientService ps = Context.getPatientService();
			PatientIdentifierType patientIdentifierType = ps.getPatientIdentifierTypeByName("1.3.6.1.4.1.21367.2005.3.7");
			assertNotNull(patientIdentifierType);
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

}
