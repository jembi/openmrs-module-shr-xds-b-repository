package org.openmrs.module.xdsbrepository.ihe.iti.actors;

import org.dcm4chee.xds2.infoset.ihe.ProvideAndRegisterDocumentSetRequestType;
import org.dcm4chee.xds2.infoset.ihe.RetrieveDocumentSetRequestType;
import org.dcm4chee.xds2.infoset.ihe.RetrieveDocumentSetResponseType;
import org.dcm4chee.xds2.infoset.rim.RegistryResponseType;

/**
 * Interface for the Spring-WS XDS Repository
 */
public interface XdsDocumentRepositoryService {
	
	/**
	 * Simple provide and register document
	 */
	public RegistryResponseType provideAndRegisterDocumentSetB(ProvideAndRegisterDocumentSetRequestType request);
	
	/**
	 * Retrieve document set
	 */
	public RetrieveDocumentSetResponseType retrieveDocumentSetB(RetrieveDocumentSetRequestType request);
}
