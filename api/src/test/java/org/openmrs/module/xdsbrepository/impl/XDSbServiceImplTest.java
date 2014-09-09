package org.openmrs.module.xdsbrepository.impl;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.dcm4chee.xds2.infoset.ihe.ProvideAndRegisterDocumentSetRequestType;
import org.dcm4chee.xds2.infoset.rim.RegistryResponseType;
import org.junit.Rule;
import org.junit.Test;
import org.openmrs.module.xdsbrepository.exceptions.RegistryNotAvailableException;
import org.openmrs.test.BaseModuleContextSensitiveTest;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.URL;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class XDSbServiceImplTest extends BaseModuleContextSensitiveTest {
	
	@Rule
	public WireMockRule wireMockRule = new WireMockRule(8089);

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
	private ProvideAndRegisterDocumentSetRequestType parseRequestFromFile(File file) throws JAXBException, FileNotFoundException {
		JAXBContext jaxbContext = JAXBContext.newInstance("org.dcm4chee.xds2.infoset.ihe:org.dcm4chee.xds2.infoset.rim");
		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

		FileReader reader = new FileReader(file);
		JAXBElement<ProvideAndRegisterDocumentSetRequestType> request = (JAXBElement<ProvideAndRegisterDocumentSetRequestType>) unmarshaller.unmarshal(reader);
		
		return request.getValue();
	}
	
	@Test
	public void sendMetadataToRegistry_shouldSendRequestToRegistry() throws Exception {
		stubFor(post(urlEqualTo("/ws/xdsregistry"))
				.willReturn(aResponse()
	                .withStatus(200)
	                .withHeader("Content-Type", "application/soap+xml")
	                .withBody(registryResponse)));
		
		File file = new File("src/test/resources/provideAndRegRequest1.xml");
		ProvideAndRegisterDocumentSetRequestType request = parseRequestFromFile(file);
		
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
		File file = new File("src/test/resources/provideAndRegRequest1.xml");
		ProvideAndRegisterDocumentSetRequestType request = parseRequestFromFile(file);
		
		XDSbServiceImpl service = new XDSbServiceImpl();
		try {
			service.sendMetadataToRegistry(new URL("http://localhost:9999/ws/xdsregistry"), request.getSubmitObjectsRequest());
			fail("Expected an exception");
		} catch (RegistryNotAvailableException e) {
			// expected
		}
	}

    @Test
    public void registerDocument_shouldStoreTheDocumentMapping() {
        fail();
    }

    @Test
    public void registerDocument_shouldSendTheDocumentMeatDataToTheRegistry() {
        fail();
    }

    @Test
    public void registerDocuments_shouldStoreEachDocuemntItRecieves() {
        fail();
    }

    @Test
    public void registerDocuments_shouldSendAllDocumentMetadataToTheRegistry() {
        fail();
    }

    @Test
    public void getDocumentHandlerClass_shouldReturnTheMappedHandlerClass() {
        fail();
    }

    @Test
    public void getDocumentHandlerClass_shouldThrowClassNotFoundExceptionIfTheClassCannotBeLoaded() {
        fail();
    }

    @Test
    public void getDocumentHandlerClass_shouldReturnNullIfNoMappingIsFound() {
        fail();
    }
	
}
