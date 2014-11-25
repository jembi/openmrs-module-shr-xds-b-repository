package org.openmrs.module.xdsbrepository;


/**
 * Constants for the XDS.b Service
 */
public class XDSbServiceConstants {
	
	public static final String XDS_REGISTRY_URL_GP = "xds-b-repository.xdsregistry.url";
	public static final String REPOSITORY_UNIQUE_ID_GP = "xds-b-repository.xdsrepository.uniqueId";
	public static final String WS_USERNAME_GP = "xds-b-repository.ws.username";
	public static final String WS_PASSWORD_GP = "xds-b-repository.ws.password";
	public static final String XDS_REPOSITORY_AUTOCREATE_PATIENTS ="xds-b-repository.autoCreatePatients";
	public static final String XDS_REPOSITORY_AUTOCREATE_PROVIDERS = "xds-b-repository.autoCreateProviders";
	public static final String XDS_REPOSITORY_AUTOCREATE_LOCATIONS = "xds-b-repository.autoCreateLocations";	
	 // JF: Severity 
	public static final String SEVERITY_ERROR = "urn:oasis:names:tc:ebxml-regrep:ErrorSeverityType:Error"; 
	public static final String SEVERITY_WARNING = "urn:oasis:names:tc:ebxml-regrep:ErrorSeverityType:Warning"; 
	
	// JF: Errors 
	public static final String ERROR_XDS_REPOSITORY_ERROR = "XDSRepositoryError";
	public static final String XDS_HOME_COMMUNITY_ID = "xds-b-repository.homeCommunityId"; 

}
