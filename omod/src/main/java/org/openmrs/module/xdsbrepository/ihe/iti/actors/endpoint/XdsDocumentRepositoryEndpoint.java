package org.openmrs.module.xdsbrepository.ihe.iti.actors.endpoint;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

import org.dcm4chee.xds2.infoset.ihe.ProvideAndRegisterDocumentSetRequestType;
import org.dcm4chee.xds2.infoset.ihe.RetrieveDocumentSetRequestType;
import org.dcm4chee.xds2.infoset.ihe.RetrieveDocumentSetResponseType;
import org.dcm4chee.xds2.infoset.rim.RegistryResponseType;
import org.openmrs.module.xdsbrepository.ihe.iti.actors.XdsDocumentRepositoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import org.springframework.ws.soap.addressing.server.annotation.Action;
import org.springframework.ws.soap.server.endpoint.annotation.SoapAction;

/**
 * Represents the repository endpoint for the XDS.b repository
 */
@Endpoint
public class XdsDocumentRepositoryEndpoint {
	
	// The service hosted by the endpoint
	private XdsDocumentRepositoryService m_service;
	
	/**
	 * Ctor auto-wires the endpoint to the service
	 */
	@Autowired
	public XdsDocumentRepositoryEndpoint(XdsDocumentRepositoryService service) {
		this.m_service = service;
	}
	
	/**
	 * 
	 * Auto generated method comment
	 * 
	 * @param request
	 * @param header
	 * @return
	 */
	@Action("urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b")
	@ResponsePayload
	public JAXBElement<RegistryResponseType> provideAndRegisterDocumentSetB(@RequestPayload JAXBElement<ProvideAndRegisterDocumentSetRequestType> request)
	{
		return new JAXBElement<RegistryResponseType>(new QName("urn:oasis:names:tc:ebxml-regrep:xsd:rs:3.0","RegistryResponse"), RegistryResponseType.class, this.m_service.provideAndRegisterDocumentSetB(request.getValue()));
	}
	
	/**
	 * 
	 * Retrieve Document endpoint
	 * 
	 * @param request
	 * @param header
	 * @return
	 */
	@Action("urn:ihe:iti:2007:RetrieveDocumentSet")
	@ResponsePayload
	public JAXBElement<RetrieveDocumentSetResponseType> retrieveDocumentSetB(@RequestPayload JAXBElement<RetrieveDocumentSetRequestType> request)
	{
		return new JAXBElement<RetrieveDocumentSetResponseType>(new QName("urn:ihe:iti:xds-b:2007","RetrieveDocumentSetResponse"), RetrieveDocumentSetResponseType.class, this.m_service.retrieveDocumentSetB(request.getValue()));
	}
	
}
