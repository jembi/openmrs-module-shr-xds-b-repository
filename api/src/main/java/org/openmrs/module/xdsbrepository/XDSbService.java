package org.openmrs.module.xdsbrepository;

import java.net.MalformedURLException;
import java.util.Map;

import org.dcm4chee.xds2.infoset.rim.RegistryResponseType;
import org.dcm4chee.xds2.infoset.rim.SubmitObjectsRequest;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.shr.contenthandler.api.ContentHandler;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public interface XDSbService extends OpenmrsService {

    /**
     * Registers a document with the configured XDS.b registry and stores a mapping
     * from documentUniqueId to the supplied ContentHandler class.
     *
     * @param documentUniqueId - the documentUniqueId of the document to be registered
     * @param contentHandler - the Content Handler class to store
     * @return The response from the XDS registry
     * @throws Exception
     * @throws MalformedURLException
     */
    public RegistryResponseType registerDocument(String documentUniqueId, Class<? extends ContentHandler> contentHandler, SubmitObjectsRequest submitObjectRequest) throws Exception;
	
	/**
	 * Registers documents with the configured XDS.b registry and stores a mapping
	 * from documentUniqueId to the supplied ContentHandler class for each document.
	 * 
	 * @param contentHandlers - the map of document unique IDs to Content Handler class to be stored
	 * @return The response from the XDS registry
	 * @throws Exception 
	 * @throws MalformedURLException 
	 */
	public RegistryResponseType registerDocuments(Map<String, Class<? extends ContentHandler>> contentHandlers, SubmitObjectsRequest submitObjectRequest) throws Exception;
	
	/**
	 * Fetches the content handler class that can retrieve the given documentUniqueId.
	 * 
	 * @param documentUniqueId - the unique id of the document in question.
	 * @return The class of the content handler that can retrieve this document.
	 * @throws ClassNotFoundException if the found class cannot be loaded
	 */
	public Class<? extends ContentHandler> getDocumentHandlerClass(String documentUniqueId) throws ClassNotFoundException;

}
