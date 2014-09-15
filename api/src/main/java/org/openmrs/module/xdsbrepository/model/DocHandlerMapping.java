package org.openmrs.module.xdsbrepository.model;

import org.openmrs.BaseOpenmrsData;

import javax.persistence.*;
import javax.print.attribute.IntegerSyntax;

@Entity
@Table(name = "xdsbrepository_dochandlers")
public class DocHandlerMapping {

	@Id
    @GeneratedValue
    @Column(name = "id")
	private Integer id;

    @Basic
	@Column(name = "doc_id")
	private String docId;

    @Basic
	@Column(name = "handler_class")
	private String handlerClass;

	public Integer getId() {
		return id;
	}

    public void setId(Integer integer) {
        this.id = integer;
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
