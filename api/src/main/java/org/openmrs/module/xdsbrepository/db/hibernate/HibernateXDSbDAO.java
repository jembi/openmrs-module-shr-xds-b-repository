package org.openmrs.module.xdsbrepository.db.hibernate;

import org.hibernate.Query;
import org.hibernate.SessionFactory;
import org.openmrs.module.shr.contenthandler.api.ContentHandler;
import org.openmrs.module.xdsbrepository.DocHandlerMapping;
import org.openmrs.module.xdsbrepository.db.XDSbDAO;

public class HibernateXDSbDAO implements XDSbDAO {

    private SessionFactory sessionFactory;

	@Override
	public void registerDocument(String docId,
			Class<? extends ContentHandler> contentHandler) {
		DocHandlerMapping docMap = new DocHandlerMapping();
		docMap.setDocId(docId);
		docMap.setHandlerClass(contentHandler.getClass().getName());
		sessionFactory.getCurrentSession().save(docMap);
	}

	@Override
	public Class<? extends ContentHandler> getDocumentHandlerClass(
			String documentUniqueId) throws ClassNotFoundException {
		Query query = sessionFactory.getCurrentSession().createQuery("from xdsbrepository_dochandlers where xdsbrepository_dochandlers.doc_id = :documentUniqueId");
		DocHandlerMapping docMap = (DocHandlerMapping) query.setString("documentUniqueId", documentUniqueId).uniqueResult();
		return (Class<? extends ContentHandler>) this.getClass().getClassLoader().loadClass(docMap.getHandlerClass());
	}

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }
}
