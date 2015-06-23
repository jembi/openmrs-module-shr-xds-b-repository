package org.openmrs.module.xdsbrepository.db;

import org.openmrs.module.shr.contenthandler.api.ContentHandler;
import org.openmrs.module.xdsbrepository.model.QueueItem;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public interface XDSbDAO {
	
	public void registerDocument(String docId, Class<? extends ContentHandler> contentHandler);
	
	public Class<? extends ContentHandler> getDocumentHandlerClass(String documentUniqueId) throws ClassNotFoundException;

	public QueueItem queueDiscreteDataProcessing(QueueItem qi);

	public QueueItem dequeueNextDiscreteDataForProcessing();

	public QueueItem updateQueueItem(QueueItem qi);
}
