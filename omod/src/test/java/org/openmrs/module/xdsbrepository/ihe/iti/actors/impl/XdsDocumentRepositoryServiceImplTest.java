package org.openmrs.module.xdsbrepository.ihe.iti.actors.impl;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.dcm4chee.xds2.common.XDSConstants;
import org.dcm4chee.xds2.common.exception.XDSException;
import org.dcm4chee.xds2.infoset.ihe.ProvideAndRegisterDocumentSetRequestType;
import org.dcm4chee.xds2.infoset.ihe.RetrieveDocumentSetRequestType;
import org.dcm4chee.xds2.infoset.ihe.RetrieveDocumentSetResponseType;
import org.dcm4chee.xds2.infoset.rim.ExtrinsicObjectType;
import org.dcm4chee.xds2.infoset.rim.RegistryError;
import org.dcm4chee.xds2.infoset.rim.RegistryResponseType;
import org.dcm4chee.xds2.infoset.util.InfosetUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openmrs.GlobalProperty;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.shr.contenthandler.api.CodedValue;
import org.openmrs.module.shr.contenthandler.api.Content;
import org.openmrs.module.shr.contenthandler.api.ContentHandler;
import org.openmrs.module.shr.contenthandler.api.ContentHandlerService;
import org.openmrs.module.xdsbrepository.XDSbService;
import org.openmrs.module.xdsbrepository.XDSbServiceConstants;
import org.openmrs.test.BaseModuleContextSensitiveTest;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import java.io.InputStream;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

public class XdsDocumentRepositoryServiceImplTest extends BaseModuleContextSensitiveTest {

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
        GlobalProperty gp4 = new GlobalProperty(XDSbServiceConstants.XDS_REGISTRY_URL_GP, "http://localhost:8089/ws/xdsregistry");
        as.saveGlobalProperty(gp4);
    }

    @SuppressWarnings("unchecked")
    private <T> T parseRequestFromResourceName(String resourceName) throws Exception {
        JAXBContext jaxbContext = JAXBContext.newInstance("org.dcm4chee.xds2.infoset.ihe:org.dcm4chee.xds2.infoset.rim");
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

        InputStream is = this.getClass().getClassLoader().getResourceAsStream(resourceName);
        JAXBElement<T> request = (JAXBElement<T>) unmarshaller.unmarshal(is);

        return request.getValue();
    }


    @Test
    public void retrieveDocumentSetB_shouldFetchContentFromTheRegisteredContentHandlerAndReturnSuccess() throws Exception {
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
    public void retrieveDocumentSetB_shouldReturnARegistryErrorIfDocumentNotFound() throws Exception {
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
    public void retrieveDocumentSetB_shouldReturnARegistryErrorIfRepositoryIdIsNotKnow() throws Exception {
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
    public void retrieveDocumentSetB_shouldReturnPartialSuccessIfNotAllDocWereFound() throws Exception {
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
    public void retrieveDocumentSetB_shouldCallTheDefaultHandlerIfNoRegisteredHandlersAreFound() throws Exception {
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


    @Test
    public void provideAndRegisterDocumentSetB_shouldRespondWithXDSbSuccessCode() throws Exception {
        stubRegistry();

        XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
        ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest1.xml");

        RegistryResponseType result = service.provideAndRegisterDocumentSetB(request);

        assertNotNull(result);
        assertEquals(XDSConstants.XDS_B_STATUS_SUCCESS, result.getStatus());
    }

    @Test
    public void provideAndRegisterDocumentSetB_shouldSendRegistryRequest() throws Exception {
        stubRegistry();

        XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
        ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest1.xml");

        service.provideAndRegisterDocumentSetB(request);

        com.github.tomakehurst.wiremock.client.WireMock.verify(postRequestedFor(urlEqualTo("/ws/xdsregistry"))
                .withHeader("Content-Type", containing("application/soap+xml"))
                .withRequestBody(containing("SubmitObjectsRequest"))
                .withRequestBody(containing("1111111111^^^&amp;1.2.3&amp;ISO")));
    }

    @Test
    public void provideAndRegisterDocumentSetB_invalidMetadata_shouldRespondWithXDSbError() throws Exception {
        stubRegistry();

        XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
        ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest_noClassCode.xml");

        RegistryResponseType result = service.provideAndRegisterDocumentSetB(request);

        assertNotNull(result);
        assertEquals(XDSConstants.XDS_B_STATUS_FAILURE, result.getStatus());
        assertNotNull(result.getRegistryErrorList().getRegistryError());
        assertTrue(result.getRegistryErrorList().getRegistryError().size() > 0);
        assertEquals(XDSException.XDS_ERR_REPOSITORY_METADATA_ERROR, result.getRegistryErrorList().getRegistryError().get(0).getErrorCode());
    }

    @Test
    public void provideAndRegisterDocumentSetB_invalidMetadata_shouldNotSendRegistryRequest() throws Exception {
        stubRegistry();

        XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
        ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest_noClassCode.xml");

        service.provideAndRegisterDocumentSetB(request);

        com.github.tomakehurst.wiremock.client.WireMock.verify(0, postRequestedFor(urlEqualTo("/ws/xdsregistry")));
    }

    @Test
    public void provideAndRegisterDocumentSetB_shouldRespondWithXDSbErrorIfRegistryUnavailable() throws Exception {
        //don't stub registry

        XdsDocumentRepositoryServiceImpl service = new XdsDocumentRepositoryServiceImpl();
        ProvideAndRegisterDocumentSetRequestType request = parseRequestFromResourceName("provideAndRegRequest1.xml");

        RegistryResponseType result = service.provideAndRegisterDocumentSetB(request);

        assertNotNull(result);
        assertEquals(XDSConstants.XDS_B_STATUS_FAILURE, result.getStatus());
        assertNotNull(result.getRegistryErrorList().getRegistryError());
        assertTrue(result.getRegistryErrorList().getRegistryError().size() > 0);
        assertEquals(XDSException.XDS_ERR_REG_NOT_AVAIL, result.getRegistryErrorList().getRegistryError().get(0).getErrorCode());
    }
}
