package org.openmrs.module.xdsbrepository.ihe.iti.actors.impl.exceptions;

/**
 * XdsRepositoryException base exception for all repository exceptions
 * thrown
 * @author Justin
 *
 */
public class XdsRepositoryException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new instance of the Xds Repository exception
	 */
	public XdsRepositoryException() {
		
	}
	
	/**
	 * Creates a new Xds Repository Exception
	 * @param message
	 */
	public XdsRepositoryException(String message)
	{
		super(message);
	}
	/**
	 * Creates a new XDS Repository Exception
	 */
	public XdsRepositoryException(String message, Throwable cause) {
		super(message, cause);
	}
	
}
