package org.openmrs.module.xdsbrepository.ihe.iti.actors;

import javax.xml.transform.dom.DOMSource;

import org.openmrs.module.xdsbrepository.ihe.iti.actors.transport.xds.ProvideAndRegisterDocumentSetRequestType;
import org.openmrs.module.xdsbrepository.ihe.iti.actors.transport.xds.RegistryResponseType;
import org.openmrs.module.xdsbrepository.ihe.iti.actors.transport.xds.RetrieveDocumentSetRequestType;
import org.openmrs.module.xdsbrepository.ihe.iti.actors.transport.xds.RetrieveDocumentSetResponseType;
import org.springframework.ws.mime.Attachment;
import org.springframework.ws.soap.SoapHeader;
import org.springframework.ws.soap.SoapMessage;
import org.w3c.dom.Element;

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
