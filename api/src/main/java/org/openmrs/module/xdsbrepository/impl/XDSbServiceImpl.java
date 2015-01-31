package org.openmrs.module.xdsbrepository.impl;

import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dcm4che3.audit.AuditMessages.EventTypeCode;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4chee.xds2.common.XDSConstants;
import org.dcm4chee.xds2.common.audit.AuditRequestInfo;
import org.dcm4chee.xds2.common.audit.XDSAudit;
import org.dcm4chee.xds2.infoset.rim.ExtrinsicObjectType;
import org.dcm4chee.xds2.infoset.rim.RegistryPackageType;
import org.dcm4chee.xds2.infoset.rim.ExtrinsicObjectType;
import org.dcm4chee.xds2.infoset.rim.ExtrinsicObjectType;
import org.dcm4chee.xds2.infoset.rim.ExtrinsicObjectType;
import org.dcm4chee.xds2.infoset.rim.ExtrinsicObjectType;
import org.dcm4chee.xds2.infoset.rim.ExtrinsicObjectType;
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
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.shr.atna.api.AtnaAuditService;
import org.openmrs.module.shr.contenthandler.api.ContentHandler;
import org.openmrs.module.xdsbrepository.XDSbService;
import org.openmrs.module.xdsbrepository.XDSbServiceConstants;
import org.openmrs.module.xdsbrepository.db.XDSbDAO;
import org.openmrs.module.xdsbrepository.exceptions.RegistryNotAvailableException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class XDSbServiceImpl extends BaseOpenmrsService implements XDSbService {
	
	protected final Log log = LogFactory.getLog(this.getClass());


	private static final String SLOT_NAME_REPOSITORY_UNIQUE_ID = "repositoryUniqueId";
	private static final String ERROR_FAILURE = "urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Failure";

	private XDSbDAO dao;

	/**
	 * Get the URL of the registry
	 * @throws MalformedURLException
	 */
	private URL getRegistryUrl() throws MalformedURLException {
		AdministrationService as = Context.getAdministrationService();
		String url = as.getGlobalProperty(XDSbServiceConstants.XDS_REGISTRY_URL_GP);

		return new URL(url);
	}

	@Transactional(readOnly = false)
	@Override
	public RegistryResponseType registerDocument(String uniqueId, Class<? extends ContentHandler> contentHandler, SubmitObjectsRequest submitObjectRequest) throws Exception {
		dao.registerDocument(uniqueId, contentHandler);
		return sendMetadataToRegistry(getRegistryUrl(), submitObjectRequest);
	}

	@Transactional(readOnly = false)
	@Override
	public RegistryResponseType registerDocuments(
			Map<String, Class<? extends ContentHandler>> contentHandlers,
			SubmitObjectsRequest submitObjectRequest) throws Exception {
		
		

		RegistryResponseType retVal = sendMetadataToRegistry(getRegistryUrl(), submitObjectRequest);

		if(retVal.getStatus().equals(XDSConstants.XDS_B_STATUS_SUCCESS))
		{
			for (String id : contentHandlers.keySet()) {
				Class<? extends ContentHandler> contentHandler = contentHandlers.get(id);
				dao.registerDocument(id, contentHandler);
			}
		}
		return retVal;
	}

	@Transactional(readOnly = true)
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
		// Auditing code
		EventTypeCode eventTypeCode = EventTypeCode.ITI_42_RegisterDocumentSetB;
		boolean wasSuccess = true;

		
		// JF: Fix meta-data issue
		for(ExtrinsicObjectType eot : InfosetUtil.getExtrinsicObjects(submitObjectRequest))
		{
			if(!eot.getObjectType().equals(XDSConstants.UUID_XDSDocumentEntry))
				eventTypeCode = new EventTypeCode("ITI-61", "IHE Transactions", "Register On-Demand Document Entry");

			try {
				InfosetUtil.addOrOverwriteSlot(eot, SLOT_NAME_REPOSITORY_UNIQUE_ID, Context.getAdministrationService().getGlobalProperty(XDSbServiceConstants.REPOSITORY_UNIQUE_ID_GP));
			} catch (JAXBException e) {
				e.printStackTrace();
			}

			//SlotType1 repositorySlot = new SlotType1();
			//repositorySlot.setName(SLOT_NAME_REPOSITORY_UNIQUE_ID);
			//repositorySlot.setValueList(new ValueListType());
			//repositorySlot.getValueList().getValue().add(Context.getAdministrationService().getGlobalProperty(XDSbServiceConstants.REPOSITORY_UNIQUE_ID_GP));
			//eot.getSlot().add(repositorySlot);
		}
				
		RegistryResponseType rsp;

		
		// Get the required elements for auditing
		RegistryPackageType submissionSet = InfosetUtil.getRegistryPackage(submitObjectRequest, XDSConstants.UUID_XDSSubmissionSet);
		String submissionSetUID = InfosetUtil.getExternalIdentifierValue(XDSConstants.UUID_XDSSubmissionSet_uniqueId, submissionSet),
				patID = InfosetUtil.getExternalIdentifierValue(XDSConstants.UUID_XDSSubmissionSet_patientId, submissionSet);
		AuditRequestInfo info = new AuditRequestInfo(null, null);

		try {
			
			rsp = port.documentRegistryRegisterDocumentSetB(submitObjectRequest);
			
		} catch (Exception e) {
			wasSuccess = false;
			throw new RegistryNotAvailableException("Document Registry not available: " + registryUrl, e);
		}
		finally
		{
			XDSAudit.setAuditLogger(Context.getService(AtnaAuditService.class).getLogger());
			XDSAudit.logExport(eventTypeCode, submissionSetUID, patID, XDSConstants.WS_ADDRESSING_ANONYMOUS, AuditLogger.processID(), info.getLocalHost(), registryUrl.toExternalForm(), null, registryUrl.getHost(), null, null, wasSuccess);
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
