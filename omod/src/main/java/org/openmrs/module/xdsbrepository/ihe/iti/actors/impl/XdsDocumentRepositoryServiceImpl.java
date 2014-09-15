package org.openmrs.module.xdsbrepository.ihe.iti.actors.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dcm4chee.xds2.common.XDSConstants;
import org.dcm4chee.xds2.common.XDSUtil;
import org.dcm4chee.xds2.common.audit.AuditRequestInfo;
import org.dcm4chee.xds2.common.audit.XDSAudit;
import org.dcm4chee.xds2.common.exception.XDSException;
import org.dcm4chee.xds2.infoset.ihe.ProvideAndRegisterDocumentSetRequestType;
import org.dcm4chee.xds2.infoset.ihe.ProvideAndRegisterDocumentSetRequestType.Document;
import org.dcm4chee.xds2.infoset.ihe.RetrieveDocumentSetRequestType;
import org.dcm4chee.xds2.infoset.ihe.RetrieveDocumentSetRequestType.DocumentRequest;
import org.dcm4chee.xds2.infoset.ihe.RetrieveDocumentSetResponseType;
import org.dcm4chee.xds2.infoset.rim.*;
import org.dcm4chee.xds2.infoset.util.InfosetUtil;
import org.openmrs.*;
import org.openmrs.api.*;
import org.openmrs.api.context.Context;
import org.openmrs.module.shr.contenthandler.UnstructuredDataHandler;
import org.openmrs.module.shr.contenthandler.api.CodedValue;
import org.openmrs.module.shr.contenthandler.api.Content;
import org.openmrs.module.shr.contenthandler.api.ContentHandler;
import org.openmrs.module.shr.contenthandler.api.ContentHandlerService;
import org.openmrs.module.xdsbrepository.XDSbService;
import org.openmrs.module.xdsbrepository.ihe.iti.actors.XdsDocumentRepositoryService;
import org.openmrs.module.xdsbrepository.ihe.iti.actors.impl.exceptions.UnsupportedGenderException;
import org.openmrs.util.OpenmrsConstants;
import org.springframework.stereotype.Service;
import sun.rmi.log.LogHandler;

import javax.activation.DataHandler;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * XdsDocumentRepository Service Implementation
 * 
 * NB: This code is heavily borrowed from DCM4CHE
 */
@Service
public class XdsDocumentRepositoryServiceImpl implements XdsDocumentRepositoryService {

    private ObjectFactory factory = new ObjectFactory();
    private org.dcm4chee.xds2.infoset.ihe.ObjectFactory iheFactory = new org.dcm4chee.xds2.infoset.ihe.ObjectFactory();
	
	public static final String WS_USERNAME_GP = "xds-b-repository.ws.username";
	public static final String WS_PASSWORD_GP = "xds-b-repository.ws.password";
	public static final String REPOSITORY_UNIQUE_ID_GP = "xds-b-repository.xdsrepository.uniqueId";

	public static final String SLOT_NAME_AUTHOR_ROLE = "authorRole";
	public static final String SLOT_NAME_AUTHOR_INSTITUTION = "authorInstitution";
	public static final String SLOT_NAME_AUTHOR_SPECIALITY = "authorSpecialty";
	public static final String SLOT_NAME_AUTHOR_TELECOM = "authorTelecommunication";
	public static final String SLOT_NAME_CODING_SCHEME = "codingScheme";
	
	// Get the clinical statement service
	protected final Log log = LogFactory.getLog(this.getClass());

	/**
	 * Start an OpenMRS Session
	 */
	private void startSession() {
		AdministrationService as = Context.getAdministrationService();
		String username = as.getGlobalProperty(WS_USERNAME_GP);
		String password = as.getGlobalProperty(WS_PASSWORD_GP);
		
		Context.openSession();
		Context.authenticate(username, password);
		this.log.error(OpenmrsConstants.DATABASE_NAME);
    }
		
	/**
	 * Document repository service implementation
	 * @see XdsDocumentRepositoryService#provideAndRegisterDocumentSetB(org.dcm4chee.xds2.infoset.ihe.ProvideAndRegisterDocumentSetRequestType)
	 */
	@Override
    public RegistryResponseType provideAndRegisterDocumentSetB(ProvideAndRegisterDocumentSetRequestType request) {
		
		log.info("Start provideAndRegisterDocumentSetB");
		
		try	{
            if (!Context.isAuthenticated()) {
                this.startSession();
            }
			
			List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());

			// Save each document
			Map<String, Class<? extends ContentHandler>> contentHandlers = new HashMap<String, Class<? extends ContentHandler>>();
			for(ExtrinsicObjectType eot : extrinsicObjects) {
				contentHandlers.put(this.storeDocument(eot, request), UnstructuredDataHandler.class);
			}

			SubmitObjectsRequest submitObjectRequest = request.getSubmitObjectsRequest();
			XDSbService xdsService = Context.getService(XDSbService.class);
			
			return xdsService.registerDocuments(contentHandlers, submitObjectRequest);
		} catch (Exception e)	{
			// Log the error
			log.error(e);
			e.printStackTrace();
			
			Context.clearSession(); // TODO: How to rollback everything?
			// Error response
			RegistryResponseType response = new RegistryResponseType();
			response.setStatus(XDSConstants.XDS_B_STATUS_FAILURE);
			RegistryErrorList errorList = new RegistryErrorList();
			errorList.setHighestSeverity(XDSConstants.SEVERITY_ERROR);
			RegistryError error = new RegistryError();
			error.setErrorCode(XDSConstants.ERROR_XDS_REPOSITORY_ERROR);
			error.setCodeContext(e.getMessage());
			error.setSeverity(XDSConstants.SEVERITY_ERROR);
			errorList.getRegistryError().add(error);
			response.setRegistryErrorList(errorList);
			return response;
		} finally {
			log.info("Stop provideAndRegisterDocumentSetB");
			Context.closeSession();
		}
		
    }

	/**
	 * Store a document and return its UUID
	 * 
	 * @throws UnsupportedEncodingException 
	 * @throws UnsupportedGenderException 
	 * @throws ParseException 
	 * @throws JAXBException 
	 * @throws PatientIdentifierException 
	 */
	protected String storeDocument(ExtrinsicObjectType eot, ProvideAndRegisterDocumentSetRequestType request) throws UnsupportedEncodingException, PatientIdentifierException, JAXBException, ParseException, UnsupportedGenderException {
		String docId = eot.getId();
		Map<String, Document> docs = InfosetUtil.getDocuments(request);
		Document document = docs.get(docId);
		
		String docUniqueId = InfosetUtil.getExternalIdentifierValue(XDSConstants.UUID_XDSDocumentEntry_uniqueId, eot);
		
		CodedValue typeCode = null;
		CodedValue formatCode = null;
		String contentType = eot.getMimeType();
		List<ClassificationType> classificationList = eot.getClassification();
		for (ClassificationType ct : classificationList) {
			if (ct.getClassificationScheme().equals(XDSConstants.UUID_XDSDocumentEntry_typeCode)) {
				String codingScheme = InfosetUtil.getSlotValue(ct.getSlot(), SLOT_NAME_CODING_SCHEME, null);
				typeCode = new CodedValue(ct.getNodeRepresentation(), codingScheme);
			}
			if (ct.getClassificationScheme().equals(XDSConstants.UUID_XDSDocumentEntry_formatCode)) {
				String codingScheme = InfosetUtil.getSlotValue(ct.getSlot(), SLOT_NAME_CODING_SCHEME, null);
				formatCode = new CodedValue(ct.getNodeRepresentation(), codingScheme);
			}
		}
		
		Content content = new Content(docUniqueId, new String(document.getValue(), "UTF-8"), typeCode, formatCode, contentType);
		
		ContentHandlerService chs = Context.getService(ContentHandlerService.class);
		ContentHandler defaultHandler = chs.getDefaultUnstructuredHandler();
		ContentHandler discreteHandler = chs.getContentHandler(typeCode, formatCode);
		
		Patient patient = findOrCreatePatient(eot);
		Map<EncounterRole, Set<Provider>> providersByRole = findOrCreateProvidersByRole(eot);
		EncounterType encounterType = findOrCreateEncounterType(eot);
		
		// always send to the default unstructured data handler
		defaultHandler.saveContent(patient, providersByRole, encounterType, content);
		// If another handler exists send to that as well
		if (discreteHandler != null) {
			discreteHandler.saveContent(patient, providersByRole, encounterType, content);
		}
		
	    return docUniqueId;
    }

	/**
	 * Finds an existing encounter type or create a new one if one cannot be found
	 * 
	 * @param eo the ExtrinsicObject that represents the document in question
	 * @return an encounter type
	 * @throws JAXBException
	 */
	protected EncounterType findOrCreateEncounterType(ExtrinsicObjectType eo) throws JAXBException {
		// TODO: is it ok to only use classcode? should we use format code or type code as well?
		ClassificationType classCodeCT = this.getClassificationFromExtrinsicObject(XDSConstants.UUID_XDSDocumentEntry_classCode, eo);
		String classCode = classCodeCT.getNodeRepresentation();
		
		EncounterService es = Context.getEncounterService();
		EncounterType encounterType = es.getEncounterType(classCode);
		
		if (encounterType == null) {
			// create new encounter Type
			encounterType = new EncounterType();
			encounterType.setName(classCode);
			encounterType.setDescription("Created by XDS.b module.");
			encounterType = es.saveEncounterType(encounterType);
		}
		
		return encounterType;
	}

	/**
	 * Extracts provider and role information from the document metadata and creates a
	 * map of encounter roles to providers as needed by OpenMRS
	 * 
	 * @param eo the ExtrinsicObject that represents the document in question
	 * @return a map of encounter roles to a set of providers that participates in the encounter using that role
	 * @throws JAXBException
	 */
	protected Map<EncounterRole, Set<Provider>> findOrCreateProvidersByRole(ExtrinsicObjectType eo) throws JAXBException {
		EncounterService es = Context.getEncounterService();
		EncounterRole unkownRole = es.getEncounterRoleByUuid(EncounterRole.UNKNOWN_ENCOUNTER_ROLE_UUID);
		
		Map<EncounterRole, Set<Provider>> providersByRole = new HashMap<EncounterRole, Set<Provider>>();
		
		List<Map<String,SlotType1>> authorClassSlots = this.getClassificationSlotsFromExtrinsicObject(XDSConstants.UUID_XDSDocumentEntry_author, eo);
		for (Map<String, SlotType1> slotMap : authorClassSlots) {
			// find/create a provider for this classification instance
			Provider provider = findOrCreateProvider(slotMap);
			
			if (slotMap.containsKey(SLOT_NAME_AUTHOR_ROLE)) {
				// role(s) have been provided
				SlotType1 slot = slotMap.get(SLOT_NAME_AUTHOR_ROLE);
				List<String> valueList = slot.getValueList().getValue();
				for (String authorRole : valueList) {
					// iterate though roles for this author and find/create a provider for those roles
					// TODO: use the 'getEncounterRoleByName()' in the EncounterService when it is available (OMRS 1.11.0)
					EncounterRole role = this.getEncounterRoleByName(authorRole);
					if (role == null) {
						// Create new encounter role
						role = new EncounterRole();
						role.setName(authorRole);
						role.setDescription("Created by XDS.b module.");
						role = es.saveEncounterRole(role);
					}
					
					if (providersByRole.containsKey(role)) {
						providersByRole.get(role).add(provider);
					} else {
						Set<Provider> providers = new HashSet<Provider>();
						providers.add(provider);
						providersByRole.put(role, providers);
					}
				}
			} else {
				// no role provided, making do with an unknown role
				if (providersByRole.containsKey(unkownRole)) {
					providersByRole.get(unkownRole).add(provider);
				} else {
					Set<Provider> providers = new HashSet<Provider>();
					providers.add(provider);
					providersByRole.put(unkownRole, providers);
				}
			}
		}
		
		return providersByRole;
	}

	/**
	 * Fetches an encounter role by name
	 * 
	 * @param authorRole the name to use
	 * @return the encounter role
	 */
	private EncounterRole getEncounterRoleByName(String authorRole) {
		EncounterService es = Context.getEncounterService();
		for (EncounterRole role : es.getAllEncounterRoles(false)) {
			if (role.getName().equals(authorRole)) {
				return role;
			}
		}
		return null;
	}

	/**
	 * Find a provider or creates a new one if one cannot be found
	 * 
	 * @param authorSlotMap a map of slot names to SLot objects from the author classification
	 * @return
	 */
	private Provider findOrCreateProvider(Map<String, SlotType1> authorSlotMap) {
		ProviderService ps = Context.getProviderService();
		
		if (authorSlotMap.containsKey(XDSConstants.SLOT_NAME_AUTHOR_PERSON)) {
			SlotType1 slot = authorSlotMap.get(XDSConstants.SLOT_NAME_AUTHOR_PERSON);
			String authorXCN = slot.getValueList().getValue().get(0);
			String[] xcnComponents = authorXCN.split("\\^", -1);
			
			// attempt to find the provider
			if (!xcnComponents[0].isEmpty()) {
				// there is an identifier
				Provider pro = ps.getProviderByIdentifier(xcnComponents[0]);
				if (pro != null) {
					return pro;
				}
			} else {
				// we only have a name - this shouldn't happen under OpenHIE as we should always
				// have a provider id (EPID) - Warning this could get slow...
				List<Provider> allProviders = ps.getAllProviders();
				for (Provider pro : allProviders) {
					if (pro.getName().startsWith(xcnComponents[2]) && pro.getName().contains(xcnComponents[1])) {
						return pro;
					}
				}
			}
			
			// no provider found - let's create one
			return ps.saveProvider(createProvider(xcnComponents));
		}
		
		return null;
	}

	/**
	 * Create a provider
	 * 
	 * @param xcnComponents
	 * @return a new provider object
	 */
	private Provider createProvider(String[] xcnComponents) {
		Provider pro;
		// create a provider
		pro = new Provider();
		pro.setIdentifier(xcnComponents[0]);
		
		if (xcnComponents.length >= 3 && !xcnComponents[2].isEmpty() && !xcnComponents[1].isEmpty()) {
			// if there are name components
			StringBuffer sb = new StringBuffer();
			sb.append(xcnComponents[2] + " " + xcnComponents[1]);
			pro.setName(sb.toString());
		} else {
			// set the name to the id as that's add we have?
			pro.setName(xcnComponents[0]);
		}
		
		return pro;
	}

	/**
	 * @param classificationScheme - The classification scheme to look for
	 * @param eo - The extrinsic object to process
	 * @return A list of maps, each item in the list represents a classification definition for
	 * this scheme. There may be multiple of these. Each list item contains a map of SlotType1
	 * objects keyed by their slot name.
	 * @throws JAXBException
	 */
	private List<Map<String, SlotType1>> getClassificationSlotsFromExtrinsicObject(String classificationScheme, ExtrinsicObjectType eo) throws JAXBException {
		List<ClassificationType> classifications = eo.getClassification();
		
		List<Map<String, SlotType1>> classificationMaps = new ArrayList<Map<String, SlotType1>>();
		for (ClassificationType c : classifications) {
			if (c.getClassificationScheme().equals(classificationScheme)) {
				Map<String, SlotType1> slotsFromRegistryObject = InfosetUtil.getSlotsFromRegistryObject(c);
				classificationMaps.add(slotsFromRegistryObject);
			}
		}
		return classificationMaps;
	}
	
	/**
	 * @param classificationScheme - The classification scheme to look for
	 * @param eo - The extrinsic object to process
	 * @return The first classification of this type found
	 * @throws JAXBException
	 */
	private ClassificationType getClassificationFromExtrinsicObject(String classificationScheme, ExtrinsicObjectType eo) throws JAXBException {
		List<ClassificationType> allClassifications = eo.getClassification();
		
		for (ClassificationType c : allClassifications) {
			if (c.getClassificationScheme().equals(classificationScheme)) {
				return c;
			}
		}
		return null;
	}

	/**
	 * Attempt to find a patient, if one doesn't exist it creates a new patient
	 * 
	 * @param eo the ExtrinsicObject that represents the document in question
	 * @return a patient
	 * @throws PatientIdentifierException if there are multiple patient found with the id specified in eo
	 * @throws UnsupportedGenderException if the gender code is not supported by OpenMRS
	 * @throws ParseException 
	 * @throws JAXBException 
	 */
	protected Patient findOrCreatePatient(ExtrinsicObjectType eo) throws PatientIdentifierException, JAXBException, ParseException, UnsupportedGenderException {
		String patCX = InfosetUtil.getExternalIdentifierValue(XDSConstants.UUID_XDSDocumentEntry_patientId, eo);
		patCX.replaceAll("&amp;", "&");
		String patId = patCX.substring(0, patCX.indexOf('^'));
		String assigningAuthority = patCX.substring(patCX.indexOf('&') + 1, patCX.lastIndexOf('&'));
		
		PatientService ps = Context.getPatientService();
		// TODO: Is this correct, should we have patient identifier with the name as the assigning authority
		PatientIdentifierType idType = ps.getPatientIdentifierTypeByName(assigningAuthority);
		if (idType == null) {
			// create new idType
			idType = new PatientIdentifierType();
			idType.setName(assigningAuthority);
			idType.setDescription("ID type for assigning authority: '" + assigningAuthority + "'. Created by the xds-b-repository module.");
			idType.setValidator("");
			idType = ps.savePatientIdentifierType(idType);
		}
		
		List<Patient> patients = ps.getPatients(null, patId, Collections.singletonList(idType), true);
		
		if (patients.size() > 1) {
			throw new PatientIdentifierException("Multiple patients found for this identifier: " + patId + ", with id type: " + assigningAuthority);
		} else if (patients.size() < 1) {
			return ps.savePatient(createPatient(eo, patId, idType));
		} else {
			return patients.get(0);
		}
	}

	/**
	 * Create a new patient object from document metadata
	 * 
	 * @param eo the ExtrinsicObject that represents the document in question
	 * @param patId the patients unique ID
	 * @param idType the patient id type
	 * @return a newly created patient object
	 * @throws JAXBException
	 * @throws ParseException
	 * @throws UnsupportedGenderException
	 */
	private Patient createPatient(ExtrinsicObjectType eo, String patId, PatientIdentifierType idType)
			throws JAXBException, ParseException, UnsupportedGenderException {
		Map<String, SlotType1> slots = InfosetUtil.getSlotsFromRegistryObject(eo);
		SlotType1 patInfoSlot = slots.get(XDSConstants.SLOT_NAME_SOURCE_PATIENT_INFO);
		List<String> valueList = patInfoSlot.getValueList().getValue();
		
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
		Patient pat = new Patient();
		
		PatientIdentifier pi = new PatientIdentifier(patId, idType, Context.getLocationService().getDefaultLocation());
		pat.addIdentifier(pi);
		
		for (String val : valueList) {
			if (val.startsWith("PID-3|")) {
				// patient ID - ignore source patient id in favour of enterprise patient id
			} else if (val.startsWith("PID-5|")) {
				// patient name
				val = val.replace("PID-5|", "");
				String[] nameComponents = val.split("\\^", -1);
				PersonName pn = createPatientName(nameComponents);
				pat.addName(pn);
			} else if (val.startsWith("PID-7|")) {
				// patient date of birth
				val = val.replace("PID-7|", "");
				Date dob = sdf.parse(val);
				pat.setBirthdate(dob);
			} else if (val.startsWith("PID-8|")) {
				// patient gender
				val = val.replace("PID-8|", "");
				if (val.equalsIgnoreCase("O") || val.equalsIgnoreCase("U") || val.equalsIgnoreCase("A") || val.equalsIgnoreCase("N")) {
					throw new UnsupportedGenderException("OpenMRS does not support genders other than male or female.");
				}
				pat.setGender(val);
			} else if (val.startsWith("PID-11|")) {
				// patient address
				val = val.replace("PID-11|", "");
				String[] addrComponents = val.split("\\^", -1);
				PersonAddress pa = createPatientAddress(addrComponents);
				pat.addAddress(pa);
			} else {
				log.warn("Found an unknown value in the sourcePatientInfo slot: " + val);
			}
		}
		
		return pat;
	}

	/**
	 * Create a patient name
	 * 
	 * @param nameComponents
	 * @return
	 */
	private PersonName createPatientName(String[] nameComponents) {
		PersonName pn = new PersonName(nameComponents[1], null, nameComponents[0]);
		try {
			pn.setMiddleName(nameComponents[2]);
			pn.setFamilyNameSuffix(nameComponents[3]);
			pn.setPrefix(nameComponents[4]);
			pn.setDegree(nameComponents[5]);
		} catch (ArrayIndexOutOfBoundsException e) {
			// ignore, these aren't important if they don't exist
		}
		return pn;
	}

	/**
	 * Create a patient address
	 * 
	 * @param addrComponents
	 * @return
	 */
	private PersonAddress createPatientAddress(String[] addrComponents) {
		PersonAddress pa = new PersonAddress();
		pa.setAddress1(addrComponents[0]);
		pa.setAddress2(addrComponents[1]);
		pa.setCityVillage(addrComponents[2]);
		pa.setStateProvince(addrComponents[3]);
		pa.setPostalCode(addrComponents[4]);
		pa.setCountry(addrComponents[5]);
		return pa;
	}

    /**
     * Retrieve a document
     * Code mostly borrowed from: https://github.com/dcm4che/dcm4chee-xds/blob/master/dcm4chee-xds2-repository-ws/src/main/java/org/dcm4chee/xds2/repository/ws/XDSRepositoryBean.java#L204
     * @see XdsDocumentRepositoryService#retrieveDocumentSetB(org.dcm4chee.xds2.infoset.ihe.RetrieveDocumentSetRequestType)
     */
	@Override
    public RetrieveDocumentSetResponseType retrieveDocumentSetB(RetrieveDocumentSetRequestType req) {
        XDSbService xdsService = Context.getService(XDSbService.class);
        RetrieveDocumentSetResponseType rsp = iheFactory.createRetrieveDocumentSetResponseType();
        try {
            if (!Context.isAuthenticated()) {
                this.startSession();
            }

            String repositoryUID = getRepositoryUniqueId();
            String docUid, reqRepoUid;
            Content content;
            RetrieveDocumentSetResponseType.DocumentResponse docRsp;
            List<String> retrievedUIDs = new ArrayList<String>();
            int requestCount = req.getDocumentRequest().size();
            RegistryErrorList regErrors = factory.createRegistryErrorList();
            List<RegistryError> mainErrors = regErrors.getRegistryError();
            for ( DocumentRequest docReq : req.getDocumentRequest() ) {
                reqRepoUid = docReq.getRepositoryUniqueId();
                docUid = docReq.getDocumentUniqueId();
                if (reqRepoUid == null || docUid == null || reqRepoUid.trim().length() == 0 || docUid.trim().length() == 0) {
                    mainErrors.add(XDSUtil.getRegistryError(XDSException.XDS_ERR_SEVERITY_ERROR, XDSException.XDS_ERR_REPOSITORY_ERROR,
                            "Missing required request parameter! (Repository- or Document Unique ID)", null));
                    continue;
                }
                if (reqRepoUid.equals(repositoryUID)) {

                    Class<? extends ContentHandler> documentHandlerClass;
                    documentHandlerClass = xdsService.getDocumentHandlerClass(docUid);
                    ContentHandlerService chs = Context.getService(ContentHandlerService.class);
                    ContentHandler h = chs.getContentHandlerByClass(documentHandlerClass);
                    content = h.fetchContent(docUid);

                    if ( content != null ) {
                        try {
                            docRsp = getDocumentResponse(content, getRepositoryUniqueId());
                            rsp.getDocumentResponse().add(docRsp);
                            retrievedUIDs.add(docUid);
                        } catch (IOException e) {
                            String msg = "Error in building DocumentResponse for document:"+content;
                            log.error(msg);
                            mainErrors.add(XDSUtil.getRegistryError(XDSException.XDS_ERR_SEVERITY_ERROR,
                                    XDSException.XDS_ERR_REPOSITORY_ERROR, msg, docUid));
                        }
                    } else {
                        String msg = "Document not found! document UID:"+docUid;
                        log.warn(msg);
                        mainErrors.add(XDSUtil.getRegistryError(XDSException.XDS_ERR_SEVERITY_ERROR,
                                XDSException.XDS_ERR_MISSING_DOCUMENT, msg, docUid));
                    }
                } else {
                    String msg = "DocumentRepositoryUID="+reqRepoUid+" is unknown! This repository unique ID:"+repositoryUID;
                    log.warn(msg);
                    mainErrors.add(XDSUtil.getRegistryError(XDSException.XDS_ERR_SEVERITY_ERROR,
                            XDSException.XDS_ERR_UNKNOWN_REPOSITORY_ID, msg, docUid));
                }
            }
            RegistryResponseType regRsp = factory.createRegistryResponseType();

            int nrOfDocs = rsp.getDocumentResponse().size();
            if (nrOfDocs == 0) {
                if (mainErrors.size() == 0)
                    throw new XDSException(XDSException.XDS_ERR_MISSING_DOCUMENT,
                            "None of the requested documents were found. This repository unique ID " + repositoryUID, null);
                regRsp.setStatus(XDSConstants.XDS_B_STATUS_FAILURE);
            } else if (nrOfDocs < requestCount) {
                regRsp.setStatus(XDSConstants.XDS_B_STATUS_PARTIAL_SUCCESS);
            } else {
                regRsp.setStatus(XDSConstants.XDS_B_STATUS_SUCCESS);
            }

            if (mainErrors.size() > 0) {
                regRsp.setRegistryErrorList(regErrors);
            }
            rsp.setRegistryResponse(regRsp);

        } catch (Exception x) {
            if (x instanceof XDSException) {
                XDSUtil.addError(rsp, (XDSException) x);
            } else {
                XDSUtil.addError(rsp, new XDSException(XDSException.XDS_ERR_REPOSITORY_ERROR,
                        "Unexpected error in XDS service !: "+x.getMessage(),x));
            }
        } finally {
            Context.closeSession();
        }
        //AuditRequestInfo info = new AuditRequestInfo(LogHandler.getInboundSOAPHeader(), wsContext);
        //XDSAudit.logRepositoryRetrieveExport(req, rsp, info);
        return rsp;
    }

    private String getRepositoryUniqueId() {
        return Context.getAdministrationService().getGlobalProperty(REPOSITORY_UNIQUE_ID_GP);
    }

    private RetrieveDocumentSetResponseType.DocumentResponse getDocumentResponse(Content content, String repositoryUniqueId) throws IOException {
        RetrieveDocumentSetResponseType.DocumentResponse docRsp;
        docRsp = iheFactory.createRetrieveDocumentSetResponseTypeDocumentResponse();
        docRsp.setDocumentUniqueId(content.getContentId());
        docRsp.setMimeType(content.getContentType());
        docRsp.setRepositoryUniqueId(repositoryUniqueId);
        docRsp.setDocument(new DataHandler(content.getPayload(), content.getContentType()));
        return docRsp;
    }

}
