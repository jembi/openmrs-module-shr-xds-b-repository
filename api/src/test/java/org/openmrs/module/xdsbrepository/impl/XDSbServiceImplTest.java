package org.openmrs.module.xdsbrepository.impl;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.dcm4chee.xds2.infoset.ihe.ProvideAndRegisterDocumentSetRequestType;
import org.dcm4chee.xds2.infoset.rim.RegistryResponseType;
import org.junit.Rule;
import org.junit.Test;
import org.openmrs.module.shr.contenthandler.api.ContentHandler;
import org.openmrs.module.xdsbrepository.exceptions.RegistryNotAvailableException;
import org.openmrs.test.BaseModuleContextSensitiveTest;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.springframework.util.Assert.notNull;

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
    private ProvideAndRegisterDocumentSetRequestType parseRequestFromResourceName(String resourceName) throws JAXBException, FileNotFoundException {
        JAXBContext jaxbContext = JAXBContext.newInstance("org.dcm4chee.xds2.infoset.ihe:org.dcm4chee.xds2.infoset.rim");
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

        InputStream is = this.getClass().getClassLoader().getResourceAsStream(resourceName);
        JAXBElement<ProvideAndRegisterDocumentSetRequestType> request = (JAXBElement<ProvideAndRegisterDocumentSetRequestType>) unmarshaller.unmarshal(is);

        return request.getValue();
    }
	
	@Test
	public void sendMetadataToRegistry_shouldSendRequestToRegistry() throws Exception {
		stubFor(post(urlEqualTo("/ws/xdsregistry"))
				.willReturn(aResponse()
	                .withStatus(200)
	                .withHeader("Content-Type", "application/soap+xml")
	                .withBody(registryResponse)));
		
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
		} catch (RegistryNotAvailableException e) {
			// expected
		}
	}

    @Test
    public void registerDocument_shouldStoreTheDocumentMapping() throws Exception {
        ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest1.xml");
        ContentHandler mockHandler = mock(ContentHandler.class);
        XDSbServiceImpl service = new XDSbServiceImpl();

        RegistryResponseType registryResponseType = service.registerDocument("123456789", mockHandler.getClass(), request.getSubmitObjectsRequest());
        notNull(registryResponseType);
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
