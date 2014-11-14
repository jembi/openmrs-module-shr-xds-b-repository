package org.openmrs.module.xdsbrepository.impl;

import com.github.tomakehurst.wiremock.junit.WireMockRule;

import org.dcm4chee.xds2.infoset.ihe.ProvideAndRegisterDocumentSetRequestType;
import org.dcm4chee.xds2.infoset.rim.RegistryResponseType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openmrs.*;
import org.openmrs.api.context.Context;
import org.openmrs.module.shr.contenthandler.api.Content;
import org.openmrs.module.shr.contenthandler.api.ContentHandler;
import org.openmrs.module.xdsbrepository.XDSbService;
import org.openmrs.module.xdsbrepository.XDSbServiceConstants;
import org.openmrs.module.xdsbrepository.db.hibernate.HibernateXDSbDAO;
import org.openmrs.module.xdsbrepository.exceptions.RegistryNotAvailableException;
import org.openmrs.test.BaseModuleContextSensitiveTest;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.springframework.util.Assert.notNull;

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
    public void setup() {
        GlobalProperty gp = new GlobalProperty(XDSbServiceConstants.XDS_REGISTRY_URL_GP, "http://localhost:8089/ws/xdsregistry");
        Context.getAdministrationService().saveGlobalProperty(gp);
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
		} catch (RegistryNotAvailableException e) {
			// expected
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
            return new TestContentHandler1();
        }
    }

}
