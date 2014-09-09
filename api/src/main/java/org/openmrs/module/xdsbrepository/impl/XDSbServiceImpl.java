package org.openmrs.module.xdsbrepository.impl;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dcm4chee.xds2.infoset.rim.RegistryResponseType;
import org.dcm4chee.xds2.infoset.rim.SubmitObjectsRequest;
import org.dcm4chee.xds2.infoset.util.DocumentRegistryPortTypeFactory;
import org.dcm4chee.xds2.infoset.ws.registry.DocumentRegistryPortType;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.shr.contenthandler.api.ContentHandler;
import org.openmrs.module.xdsbrepository.XDSbService;
import org.openmrs.module.xdsbrepository.db.XDSbDAO;
import org.openmrs.module.xdsbrepository.exceptions.RegistryNotAvailableException;

public class XDSbServiceImpl implements XDSbService {
	
	protected final Log log = LogFactory.getLog(this.getClass());

    public static final String XDS_REGISTRY_URL_GP = "xds-b-repository.xdsregistry.url";
	
	private XDSbDAO dao;

	@Override
	public void onShutdown() {
	}

	@Override
	public void onStartup() {
	}

    /**
     * Get the URL of the registry
     * @throws MalformedURLException
     */
    private URL getRegistryUrl() throws MalformedURLException {
        AdministrationService as = Context.getAdministrationService();
        String url = as.getGlobalProperty(XDS_REGISTRY_URL_GP);

        return new URL(url);
    }

    @Override
	public RegistryResponseType registerDocument(String uniqueId, Class<? extends ContentHandler> contentHandler, SubmitObjectsRequest submitObjectRequest) throws Exception {
        dao.registerDocument(uniqueId, contentHandler);
		return sendMetadataToRegistry(getRegistryUrl(), submitObjectRequest);
	}

	@Override
	public RegistryResponseType registerDocuments(
			Map<String, Class<? extends ContentHandler>> contentHandlers,
			SubmitObjectsRequest submitObjectRequest) throws Exception {
		
		for (String id : contentHandlers.keySet()) {
			Class<? extends ContentHandler> contentHandler = contentHandlers.get(id);
			dao.registerDocument(id, contentHandler);
		}
		
		return sendMetadataToRegistry(getRegistryUrl(), submitObjectRequest);
	}

	@Override
	public Class<? extends ContentHandler> getDocumentHandlerClass(String documentUniqueId) throws ClassNotFoundException {
		return dao.getDocumentHandlerClass(documentUniqueId);
	}
	
	/**
	 * Register documents on registry 
	 * @throws Exception 
	 */
	protected RegistryResponseType sendMetadataToRegistry(URL registryUrl, SubmitObjectsRequest submitObjectRequest) throws Exception {
        DocumentRegistryPortType port = DocumentRegistryPortTypeFactory.getDocumentRegistryPortSoap12(registryUrl.toString());
        log.info("XDS.b: Send register document-b request to registry:" + registryUrl);
        RegistryResponseType rsp = null;
        try {
            rsp = port.documentRegistryRegisterDocumentSetB(submitObjectRequest);
        } catch (Exception e) {
            throw new RegistryNotAvailableException("Document Registry not available: " + registryUrl, e);
        }
        return rsp;
    }

	public XDSbDAO getDao() {
		return dao;
	}

	public void setDao(XDSbDAO dao) {
		this.dao = dao;
	}

}
