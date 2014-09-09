package org.openmrs.module.xdsbrepository.exceptions;

public class RegistryNotAvailableException extends Exception {
	
	private static final long serialVersionUID = 1L;

	public RegistryNotAvailableException(String msg) {
		super(msg);
	}

	public RegistryNotAvailableException(String msg, Throwable t) {
		super(msg, t);
	}

}
