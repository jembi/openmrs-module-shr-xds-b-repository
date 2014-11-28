package org.openmrs.module.xdsbrepository.ihe.iti.actors.impl.exceptions;

/**
 * Unknown patient
 * @author Justin
 *
 */
public class UnknownPatientException extends XdsRepositoryException {

	/**
	 * Creates a new unknown patient exception
	 */
	public UnknownPatientException() {
		super();
		// TODO Auto-generated constructor stub
	}

	/**
	 * Creates a new unknown patient exception
	 * @param message
	 * @param cause
	 */
	public UnknownPatientException(String patientId) {
		super(String.format("Patient ID %s is not known to the repository", patientId));
		// TODO Auto-generated constructor stub
	}

	
}
