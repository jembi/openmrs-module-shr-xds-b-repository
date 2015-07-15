package org.openmrs.module.xdsbrepository.model;

import org.openmrs.EncounterType;
import org.openmrs.Patient;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Date;

@Entity
@Table(name = "xdsbrepository_queue")
public class QueueItem {

    public enum Status {
        QUEUED, PROCESSING, FAILED, SUCCESSFUL
    }

    @Id
    @GeneratedValue
    @Column(name = "id")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Basic
    @Column(name = "role_provider_map")
    private String roleProviderMap;

    @ManyToOne
    @JoinColumn(name = "encounter_type_id")
    private EncounterType encounterType;

    @Basic
    @Column(name = "doc_id")
    private String docUniqueId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "date_added")
    private Date dateAdded;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "date_updated")
    private Date dateUpdated;

    public Date getDateAdded() {
        return dateAdded;
    }

    public void setDateAdded(Date dateAdded) {
        this.dateAdded = dateAdded;
    }

    public Date getDateUpdated() {
        return dateUpdated;
    }

    public void setDateUpdated(Date dateUpdated) {
        this.dateUpdated = dateUpdated;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public EncounterType getEncounterType() {
        return encounterType;
    }

    public void setEncounterType(EncounterType encounterType) {
        this.encounterType = encounterType;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public String getDocUniqueId() {
        return docUniqueId;
    }

    public void setDocUniqueId(String docUniqueId) {
        this.docUniqueId = docUniqueId;
    }

    public String getRoleProviderMap() {
        return roleProviderMap;
    }

    public void setRoleProviderMap(String roleProviderMap) {
        this.roleProviderMap = roleProviderMap;
    }
}
