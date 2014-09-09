package org.openmrs.module.xdsbrepository;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "xdsbrepository_dochandlers")
public class DocHandlerMapping {

	@Id
	private int id;
	@Column(name="doc_id")
	private String docId;
	@Column(name="handler_class")
	private String handlerClass;

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getDocId() {
		return docId;
	}

	public void setDocId(String docId) {
		this.docId = docId;
	}

	public String getHandlerClass() {
		return handlerClass;
	}

	public void setHandlerClass(String handlerClass) {
		this.handlerClass = handlerClass;
	}

}
