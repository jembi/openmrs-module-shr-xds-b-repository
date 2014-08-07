package org.openmrs.module.xdsbrepository.ihe.iti.actors.impl;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBElement;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dcm4chee.xds2.common.XDSConstants;
import org.dcm4chee.xds2.infoset.ihe.ProvideAndRegisterDocumentSetRequestType;
import org.dcm4chee.xds2.infoset.ihe.ProvideAndRegisterDocumentSetRequestType.Document;
import org.dcm4chee.xds2.infoset.ihe.RetrieveDocumentSetRequestType;
import org.dcm4chee.xds2.infoset.ihe.RetrieveDocumentSetResponseType;
import org.dcm4chee.xds2.infoset.rim.ClassificationType;
import org.dcm4chee.xds2.infoset.rim.ExtrinsicObjectType;
import org.dcm4chee.xds2.infoset.rim.IdentifiableType;
import org.dcm4chee.xds2.infoset.rim.RegistryError;
import org.dcm4chee.xds2.infoset.rim.RegistryErrorList;
import org.dcm4chee.xds2.infoset.rim.RegistryResponseType;
import org.dcm4chee.xds2.infoset.rim.SubmitObjectsRequest;
import org.dcm4chee.xds2.infoset.util.InfosetUtil;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.xdsbrepository.ihe.iti.actors.XdsDocumentRepositoryService;
import org.openmrs.util.OpenmrsConstants;
import org.springframework.stereotype.Service;

/**
 * XdsDocumentRepository Service Implementation
 * 
 * NB: This code is heavily borrowed from DCM4CHE
 */
@Service
public class XdsDocumentRepositoryServiceImpl implements XdsDocumentRepositoryService {
	
	public static final String WS_USERNAME_GP = "xds-b-repository.ws.username";
	public static final String WS_PASSWORD_GP = "xds-b-repository.ws.password";
	
	public static final String XDS_REGISTRY_URL_GP = "xds-b-repository.xdsregistry.url";
	
	// Get the clinical statement service
	protected final Log log = LogFactory.getLog(this.getClass());

	/**
	 * Start an OpenMRS Session
	 */
	private void startSession() {
		AdministrationService as = Context.getAdministrationService();
		String username = as.getGlobalProperty(WS_USERNAME_GP);
		String password = as.getGlobalProperty(WS_PASSWORD_GP);
		
		Context.openSession();
		Context.authenticate(username, password);
		this.log.error(OpenmrsConstants.DATABASE_NAME);
    }
		
	/**
	 * Document repository service implementation
	 * @see org.openmrs.module.xdsbrepository.ihe.iti.actors.XdsDocumentRepositoryService#provideAndRegisterDocumentSetB(org.openmrs.module.xdsbrepository.ihe.iti.actors.transport.xds.ProvideAndRegisterDocumentSetRequestType)
	 */
	@Override
    public RegistryResponseType provideAndRegisterDocumentSetB(ProvideAndRegisterDocumentSetRequestType request) {
		
		log.info("Start provideAndRegisterDocumentSetB");
		
		try	{
			this.startSession();
			
			URL registryURL = this.getRegistryUrl();
			List<ExtrinsicObjectType> extrinsicObjects = this.getExtrinsicObjects(request);
			extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());
			
			// Save each document
			List<String> storedIds = new ArrayList<String>();
			for(ExtrinsicObjectType eot : extrinsicObjects) {
				storedIds.add(this.storeDocument(eot, request));
			}

			SubmitObjectsRequest submitObjectRequest = request.getSubmitObjectsRequest();
			return this.sendMetadataToRegistry(registryURL, submitObjectRequest);
		} catch (Exception e)	{
			// Log the error
			log.error(e);
			
			Context.clearSession(); // TODO: How to rollback everything?
			// Error response
			RegistryResponseType response = new RegistryResponseType();
			response.setStatus(XDSConstants.XDS_B_STATUS_FAILURE);
			RegistryErrorList errorList = new RegistryErrorList();
			errorList.setHighestSeverity(XDSConstants.SEVERITY_ERROR);
			RegistryError error = new RegistryError();
			error.setErrorCode(XDSConstants.ERROR_XDS_REPOSITORY_ERROR);
			error.setCodeContext(e.getMessage());
			error.setSeverity(XDSConstants.SEVERITY_ERROR);
			errorList.getRegistryError().add(error);
			response.setRegistryErrorList(errorList);
			return response;
		} finally {
			log.info("Stop provideAndRegisterDocumentSetB");
			Context.closeSession();
		}
		
    }

	/**
	 * Register documents on registry 
	 */
	private RegistryResponseType sendMetadataToRegistry(URL registryUrl, SubmitObjectsRequest submitObjectRequest) {
		// TODO: This is a stub
		RegistryResponseType response = new RegistryResponseType();
		response.setStatus(XDSConstants.XDS_B_STATUS_SUCCESS);
		return response;
    }

	/**
	 * Store a document and return its UUID
	 */
	private String storeDocument(ExtrinsicObjectType eot, ProvideAndRegisterDocumentSetRequestType request) {
		String docId = eot.getId();
		
		List<Document> docList = request.getDocument();
		Document document = null;
		for (Document d : docList) {
			if (d.getId().equals(docId)) {
				document = d;
				break;
			}
		}
		
		String typeCode = null;
		String formatCode = null;
		String contentType = null;
		List<ClassificationType> classificationList = eot.getClassification();
		for (ClassificationType ct : classificationList) {
			if (ct.getClassificationScheme().equals("urn:uuid:aa543740-bdda-424e-8c96-df4873be8500")) {
				typeCode = ct.getNodeRepresentation();
			}
			if (ct.getClassificationScheme().equals("urn:uuid:a09d5840-386c-46f2-b5ad-9c3699a4309d")) {
				formatCode = ct.getNodeRepresentation();
			}
		}
		
		/*
		Content content = new Content(document.getValue().toString(), typeCode, formatCode, contentType);
		
		ContentHandlerService chs = Context.getService(ContentHandlerService.class);
		ContentHandler defaultHandler = chs.getDefaultHandler(typeCode, formatCode);
		ContentHandler discreteHandler = chs.getContentHandler(typeCode, formatCode);
				
		defaultHandler.saveContent(patient, provider, role, encounterType, content);
		discreteHandler.saveContent(patient, provider, role, encounterType, content);
		*/
		
	    return null;
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

	/**
	 * Get a list of all extrinsic objects in the submission package
	 */
	private List<ExtrinsicObjectType> getExtrinsicObjects(ProvideAndRegisterDocumentSetRequestType request) {
		List<ExtrinsicObjectType> extrObjs = new ArrayList<ExtrinsicObjectType>();
        List<JAXBElement<? extends IdentifiableType>> list = request.getSubmitObjectsRequest().getRegistryObjectList().getIdentifiable();
        IdentifiableType o;
        for ( int i = 0, len = list.size() ; i < len ; i++ ) {
            o = list.get(i).getValue();
            if ( o instanceof ExtrinsicObjectType) {
                extrObjs.add((ExtrinsicObjectType) o);
            }
        }
        return extrObjs;
    }

	/**
	 * Retrieve a document
	 * @see org.openmrs.module.xdsbrepository.ihe.iti.actors.XdsDocumentRepositoryService#retrieveDocumentSetB(org.openmrs.module.xdsbrepository.ihe.iti.actors.transport.xds.RetrieveDocumentSetRequestType)
	 */
	@Override
    public RetrieveDocumentSetResponseType retrieveDocumentSetB(RetrieveDocumentSetRequestType request) {
		// TODO:
		return null;
    }

}
