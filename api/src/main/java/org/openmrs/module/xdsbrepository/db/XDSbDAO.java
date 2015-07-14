package org.openmrs.module.xdsbrepository.db;

import org.openmrs.module.shr.contenthandler.api.ContentHandler;
import org.openmrs.module.xdsbrepository.model.QueueItem;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public interface XDSbDAO {
	
	void registerDocument(String docId, Class<? extends ContentHandler> contentHandler);
	
	Class<? extends ContentHandler> getDocumentHandlerClass(String documentUniqueId) throws ClassNotFoundException;

	QueueItem queueDiscreteDataProcessing(QueueItem qi);

	QueueItem dequeueNextDiscreteDataForProcessing();

	QueueItem updateQueueItem(QueueItem qi);
}
