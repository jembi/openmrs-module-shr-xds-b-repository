package org.openmrs.module.xdsbrepository;

import org.dcm4chee.xds2.common.exception.XDSException;
import org.dcm4chee.xds2.infoset.ihe.ProvideAndRegisterDocumentSetRequestType;
import org.dcm4chee.xds2.infoset.rim.RegistryResponseType;
import org.dcm4chee.xds2.infoset.rim.SubmitObjectsRequest;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.shr.contenthandler.api.ContentHandler;
import org.openmrs.module.shr.contenthandler.api.ContentHandlerException;
import org.openmrs.module.xdsbrepository.model.QueueItem;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

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
    RegistryResponseType registerDocument(String documentUniqueId, Class<? extends ContentHandler> contentHandler, SubmitObjectsRequest submitObjectRequest) throws XDSException;

	RegistryResponseType registerDocument(URL registryURL, String documentUniqueId, Class<? extends ContentHandler> contentHandler, SubmitObjectsRequest submitObjectRequest) throws XDSException;

	/**
	 * Registers documents with the configured XDS.b registry and stores a mapping
	 * from documentUniqueId to the supplied ContentHandler class for each document.
	 * 
	 * @param contentHandlers - the map of document unique IDs to Content Handler class to be stored
	 * @return The response from the XDS registry
	 * @throws Exception 
	 * @throws MalformedURLException 
	 */
	RegistryResponseType registerDocuments(Map<String, Class<? extends ContentHandler>> contentHandlers, SubmitObjectsRequest submitObjectRequest) throws XDSException;
	
	/**
	 * Fetches the content handler class that can retrieve the given documentUniqueId.
	 * 
	 * @param documentUniqueId - the unique id of the document in question.
	 * @return The class of the content handler that can retrieve this document.
	 * @throws ClassNotFoundException if the found class cannot be loaded
	 */
	Class<? extends ContentHandler> getDocumentHandlerClass(String documentUniqueId) throws ClassNotFoundException;

	/**
	 * Processes an XDS.b Provide and register document request
	 */
	RegistryResponseType provideAndRegisterDocumentSetB(ProvideAndRegisterDocumentSetRequestType request) throws XDSException, ContentHandlerException;
	
	/**
	 * @param qi - the QueueItem to add to  the queue.
	 * @return The QueueItem that was saved to the queue.
	 */
	QueueItem queueDiscreteDataProcessing(QueueItem qi);

	/**
	 * Returns the oldest queue item for processing.
	 * @return The QueueItem to be processed.
	 */
	QueueItem dequeueNextDiscreteDataForProcessing();

	/**
	 * Completes this queue item (mark it as done). You must also indicate if the item was processed succeefully or not.
	 * @param qi - the QueueItem to complete.
	 * @param successful - a boolean to idicate if the processing was successful or not
	 * @return the updated QueueItem
	 */
	QueueItem completeQueueItem(QueueItem qi, boolean successful);

}
