package org.openmrs.module.xdsbrepository.impl;

import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dcm4chee.xds2.infoset.rim.ExtrinsicObjectType;
import org.dcm4chee.xds2.infoset.rim.RegistryResponseType;
import org.dcm4chee.xds2.infoset.rim.SlotType1;
import org.dcm4chee.xds2.infoset.rim.SubmitObjectsRequest;
import org.dcm4chee.xds2.infoset.rim.ValueListType;
import org.dcm4chee.xds2.infoset.util.DocumentRegistryPortTypeFactory;
import org.dcm4chee.xds2.infoset.util.InfosetUtil;
import org.dcm4chee.xds2.infoset.ws.registry.DocumentRegistryPortType;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.shr.contenthandler.api.ContentHandler;
import org.openmrs.module.xdsbrepository.XDSbService;
import org.openmrs.module.xdsbrepository.XDSbServiceConstants;
import org.openmrs.module.xdsbrepository.db.XDSbDAO;
import org.openmrs.module.xdsbrepository.exceptions.RegistryNotAvailableException;

public class XDSbServiceImpl implements XDSbService {
	
	protected final Log log = LogFactory.getLog(this.getClass());


	private static final String SLOT_NAME_REPOSITORY_UNIQUE_ID = "repositoryUniqueId";
	private static final String ERROR_FAILURE = "urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Failure";

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
		String url = as.getGlobalProperty(XDSbServiceConstants.XDS_REGISTRY_URL_GP);

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
	protected RegistryResponseType sendMetadataToRegistry(URL registryUrl, SubmitObjectsRequest submitObjectRequest) throws RegistryNotAvailableException {
		
		DocumentRegistryPortType port = DocumentRegistryPortTypeFactory.getDocumentRegistryPortSoap12(registryUrl.toString());
		log.info("XDS.b: Send register document-b request to registry:" + registryUrl);
		
		// JF: Fix meta-data issue
		for(ExtrinsicObjectType eot : InfosetUtil.getExtrinsicObjects(submitObjectRequest))
		{
			String repositoryUniqueId = InfosetUtil.getSlotValue(eot.getSlot(), SLOT_NAME_REPOSITORY_UNIQUE_ID, null);
			if(repositoryUniqueId == null)
			{
				SlotType1 repositorySlot = new SlotType1();
				repositorySlot.setName(SLOT_NAME_REPOSITORY_UNIQUE_ID);
				repositorySlot.setValueList(new ValueListType());
				repositorySlot.getValueList().getValue().add(Context.getAdministrationService().getGlobalProperty(XDSbServiceConstants.REPOSITORY_UNIQUE_ID_GP));
				eot.getSlot().add(repositorySlot);
			}
		}
				
		RegistryResponseType rsp;
		try {
			
			// Serialize request (for logging)
			StringWriter writer = new StringWriter();
			JAXBContext context = JAXBContext.newInstance(SubmitObjectsRequest.class);
			Marshaller m = context.createMarshaller();
			m.marshal(submitObjectRequest, writer);
			log.debug(writer.toString());
			
			// Send the request
			rsp = port.documentRegistryRegisterDocumentSetB(submitObjectRequest);
			
			// Was the response a success? 
			if(rsp.getStatus().equals(ERROR_FAILURE))
				throw new RegistryNotAvailableException("RegisterDocumentSet resulted in Error");
			
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
