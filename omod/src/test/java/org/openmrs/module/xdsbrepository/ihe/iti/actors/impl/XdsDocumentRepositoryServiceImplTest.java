package org.openmrs.module.xdsbrepository.ihe.iti.actors.impl;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.dcm4chee.xds2.infoset.ihe.ObjectFactory;
import org.dcm4chee.xds2.infoset.ihe.ProvideAndRegisterDocumentSetRequestType;
import org.dcm4chee.xds2.infoset.rim.ExtrinsicObjectType;
import org.dcm4chee.xds2.infoset.util.InfosetUtil;
import org.junit.Test;
import org.openmrs.Patient;
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
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

}
