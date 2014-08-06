package org.openmrs.module.xdsbrepository.ihe.iti.actors.endpoint;


import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

import org.openmrs.module.xdsbrepository.ihe.iti.actors.XdsDocumentRepositoryService;
import org.openmrs.module.xdsbrepository.ihe.iti.actors.transport.xds.ProvideAndRegisterDocumentSetRequestType;
import org.openmrs.module.xdsbrepository.ihe.iti.actors.transport.xds.RegistryErrorList;
import org.openmrs.module.xdsbrepository.ihe.iti.actors.transport.xds.RegistryResponseType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.SoapMessageFactory;
import org.springframework.ws.soap.addressing.server.annotation.Action;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
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
	@SoapAction("urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b")
	@ResponsePayload
	public JAXBElement<RegistryResponseType> provideAndRegisterDocumentSetB(@RequestPayload JAXBElement<ProvideAndRegisterDocumentSetRequestType> request)
	{
		return new JAXBElement<RegistryResponseType>(new QName("urn:oasis:names:tc:ebxml-regrep:xsd:rs:3.0","RegistryResponse"), RegistryResponseType.class, this.m_service.provideAndRegisterDocumentSetB(request.getValue()));
	}
	
}
