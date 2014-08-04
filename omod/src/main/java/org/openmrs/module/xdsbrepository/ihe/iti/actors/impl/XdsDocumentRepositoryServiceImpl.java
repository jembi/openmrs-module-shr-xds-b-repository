package org.openmrs.module.xdsbrepository.ihe.iti.actors.impl;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dcm4chee.xds2.common.XDSConstants;
import org.openmrs.api.context.Context;
import org.openmrs.module.xdsbrepository.ihe.iti.actors.XdsDocumentRepositoryService;
import org.openmrs.module.xdsbrepository.ihe.iti.actors.transport.xds.ExtrinsicObjectType;
import org.openmrs.module.xdsbrepository.ihe.iti.actors.transport.xds.ProvideAndRegisterDocumentSetRequestType;
import org.openmrs.module.xdsbrepository.ihe.iti.actors.transport.xds.RegistryError;
import org.openmrs.module.xdsbrepository.ihe.iti.actors.transport.xds.RegistryErrorList;
import org.openmrs.module.xdsbrepository.ihe.iti.actors.transport.xds.RegistryResponseType;
import org.openmrs.module.xdsbrepository.ihe.iti.actors.transport.xds.RetrieveDocumentSetRequestType;
import org.openmrs.module.xdsbrepository.ihe.iti.actors.transport.xds.RetrieveDocumentSetResponseType;
import org.openmrs.module.xdsbrepository.ihe.iti.actors.transport.xds.SubmitObjectsRequest;
import org.openmrs.util.OpenmrsConstants;
import org.springframework.stereotype.Service;

/**
 * XdsDocumentRepository Service Implementation
 * 
 * NB: This code is heavily borrowed from DCM4CHE
 */
@Service
public class XdsDocumentRepositoryServiceImpl implements XdsDocumentRepositoryService {
	
	// Get the clinical statement service
	protected final Log log = LogFactory.getLog(this.getClass());

	/**
	 * Start an OpenMRS Session
	 */
	private void startSession() {
	    // TODO: Move this to a config parameter web services user
		Context.openSession();
		Context.authenticate("admin", "test");
		this.log.error(OpenmrsConstants.DATABASE_NAME);
    }
		
	/**
	 * Document repository service implementation
	 * @see org.openmrs.module.xdsbrepository.ihe.iti.actors.XdsDocumentRepositoryService#provideAndRegisterDocumentSetB(org.openmrs.module.xdsbrepository.ihe.iti.actors.transport.xds.ProvideAndRegisterDocumentSetRequestType)
	 */
	@Override
    public RegistryResponseType provideAndRegisterDocumentSetB(ProvideAndRegisterDocumentSetRequestType request) {
		
		log.info("Start provideAndRegisterDocumentSetB");
		
		URL registryURL = null; // TODO : get this from global properties
		try
		{
			this.startSession();
			
			registryURL = this.getRegistryUrl(this.getSourceID(request));
			List<ExtrinsicObjectType> extrinsicObjects = this.getExtrinsicObjects(request);
			
			// Save each document
			List<String> storedIds = new ArrayList<String>();
			for(ExtrinsicObjectType eot : extrinsicObjects)
				storedIds.add(this.storeDocument(eot, request));

			SubmitObjectsRequest submitObjectRequest = request.getSubmitObjectsRequest();
			return this.sendMetadataToRegistry(registryURL, submitObjectRequest);
		}
		catch(Exception e)
		{
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
		}
		finally
		{
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
	    // TODO Auto-generated method stub
	    return null;
    }

	/**
	 * Get the URL of the repository
	 */
	private URL getRegistryUrl(Object sourceID) {
	    // TODO Auto-generated method stub
	    return null;
    }

	/**
	 * Get a list of all extrinsic objects in the submission package
	 */
	private List<ExtrinsicObjectType> getExtrinsicObjects(ProvideAndRegisterDocumentSetRequestType request) {
	    // TODO Auto-generated method stub
	    return null;
    }

	/**
	 * Get the source identifier 
	 * Auto generated method comment
	 */
	private Object getSourceID(ProvideAndRegisterDocumentSetRequestType request) {
	    // TODO Auto-generated method stub
	    return null;
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
