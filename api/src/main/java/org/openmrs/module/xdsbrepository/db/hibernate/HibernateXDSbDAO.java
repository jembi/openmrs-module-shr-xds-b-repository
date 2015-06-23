package org.openmrs.module.xdsbrepository.db.hibernate;

import org.hibernate.Query;
import org.hibernate.SessionFactory;
import org.openmrs.api.context.Context;
import org.openmrs.module.shr.contenthandler.api.ContentHandler;
import org.openmrs.module.xdsbrepository.model.DocHandlerMapping;
import org.openmrs.module.xdsbrepository.db.XDSbDAO;
import org.openmrs.module.xdsbrepository.model.QueueItem;

import java.util.List;


public class HibernateXDSbDAO implements XDSbDAO {

    private SessionFactory sessionFactory;

	@Override
	public void registerDocument(String docId,
			Class<? extends ContentHandler> contentHandler) {
		DocHandlerMapping docMap = new DocHandlerMapping();
		docMap.setDocId(docId);
		docMap.setHandlerClass(contentHandler.getName());
		sessionFactory.getCurrentSession().save(docMap);
		
	}

	@Override
	public Class<? extends ContentHandler> getDocumentHandlerClass(
			String documentUniqueId) throws ClassNotFoundException {
		Query query = sessionFactory.getCurrentSession().createQuery("from DocHandlerMapping where doc_id = :documentUniqueId");
		DocHandlerMapping docMap = (DocHandlerMapping) query.setString("documentUniqueId", documentUniqueId).uniqueResult();
        if (docMap == null) {
            return null;
        }
        return (Class<? extends ContentHandler>) Context.loadClass(docMap.getHandlerClass());
	}

	@Override
	public QueueItem queueDiscreteDataProcessing(QueueItem qi) {
		sessionFactory.getCurrentSession().save(qi);
		return qi;
	}

	@Override
	public QueueItem dequeueNextDiscreteDataForProcessing() {
		Query query = sessionFactory.getCurrentSession().createQuery("from QueueItem where status='QUEUED' order by date_added");
		List list = query.list();
		if (list.size() < 1) {
			return null;
		} else {
			// return the oldest queue item (FIFO queue)
			return (QueueItem) list.get(0);
		}
	}

	@Override
	public QueueItem updateQueueItem(QueueItem qi) {
		sessionFactory.getCurrentSession().update(qi);
		return qi;
	}

	public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }
}
