package org.openmrs.module.xdsbrepository.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dcm4che3.audit.AuditMessages.EventTypeCode;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4chee.xds2.common.XDSConstants;
import org.dcm4chee.xds2.common.audit.AuditRequestInfo;
import org.dcm4chee.xds2.common.audit.XDSAudit;
import org.dcm4chee.xds2.common.exception.XDSException;
import org.dcm4chee.xds2.infoset.ihe.ProvideAndRegisterDocumentSetRequestType;
import org.dcm4chee.xds2.infoset.rim.*;
import org.dcm4chee.xds2.infoset.util.DocumentRegistryPortTypeFactory;
import org.dcm4chee.xds2.infoset.util.InfosetUtil;
import org.dcm4chee.xds2.infoset.ws.registry.DocumentRegistryPortType;
import org.openmrs.*;
import org.openmrs.api.*;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.shr.atna.api.AtnaAuditService;
import org.openmrs.module.shr.contenthandler.UnstructuredDataHandler;
import org.openmrs.module.shr.contenthandler.api.*;
import org.openmrs.module.xdsbrepository.Identifier;
import org.openmrs.module.xdsbrepository.XDSbService;
import org.openmrs.module.xdsbrepository.XDSbServiceConstants;
import org.openmrs.module.xdsbrepository.db.XDSbDAO;
import org.openmrs.module.xdsbrepository.exceptions.CXParseException;
import org.openmrs.module.xdsbrepository.exceptions.UnsupportedGenderException;
import org.openmrs.module.xdsbrepository.model.QueueItem;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.bind.JAXBException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.Map;

@Transactional
public class XDSbServiceImpl extends BaseOpenmrsService implements XDSbService {
	
	protected final Log log = LogFactory.getLog(this.getClass());


	private static final String SLOT_NAME_REPOSITORY_UNIQUE_ID = "repositoryUniqueId";

	public static final String SLOT_NAME_HASH = "hash";
	public static final String SLOT_NAME_SIZE = "size";
	public static final String SLOT_NAME_AUTHOR_ROLE = "authorRole";
	public static final String SLOT_NAME_AUTHOR_INSTITUTION = "authorInstitution";
	public static final String SLOT_NAME_AUTHOR_SPECIALITY = "authorSpecialty";
	public static final String SLOT_NAME_AUTHOR_TELECOM = "authorTelecommunication";
	public static final String SLOT_NAME_CODING_SCHEME = "codingScheme";

	private static final String ERROR_FAILURE = "urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Failure";

	private XDSbDAO dao;


	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}


	/**
	 * Get the URL of the registry
	 * @throws MalformedURLException
	 */
	private URL getRegistryUrl() throws MalformedURLException {
		AdministrationService as = Context.getAdministrationService();
		String url = as.getGlobalProperty(XDSbServiceConstants.XDS_REGISTRY_URL_GP);

		return new URL(url);
	}

	@Transactional(readOnly = false, rollbackFor = XDSException.class)
	@Override
	public RegistryResponseType registerDocument(String uniqueId, Class<? extends ContentHandler> contentHandler, SubmitObjectsRequest submitObjectRequest) throws XDSException {
		try {
			RegistryResponseType retVal = sendMetadataToRegistry(getRegistryUrl(), submitObjectRequest);

			if (retVal.getStatus().equals(XDSConstants.XDS_B_STATUS_SUCCESS)) {
				dao.registerDocument(uniqueId, contentHandler);
			}

			return retVal;
		} catch (MalformedURLException ex) {
			throw new XDSException(XDSException.XDS_ERR_REPOSITORY_ERROR, ex.getMessage(), ex);
		}
	}

	@Transactional(readOnly = false, rollbackFor = XDSException.class)
	@Override
	public RegistryResponseType registerDocuments(
			Map<String, Class<? extends ContentHandler>> contentHandlers,
			SubmitObjectsRequest submitObjectRequest) throws XDSException {

		try {
			RegistryResponseType retVal = sendMetadataToRegistry(getRegistryUrl(), submitObjectRequest);

			if(retVal.getStatus().equals(XDSConstants.XDS_B_STATUS_SUCCESS))
			{
				for (String id : contentHandlers.keySet()) {
					Class<? extends ContentHandler> contentHandler = contentHandlers.get(id);
					dao.registerDocument(id, contentHandler);
				}
			}
			return retVal;
		} catch (MalformedURLException ex) {
			throw new XDSException(XDSException.XDS_ERR_REPOSITORY_ERROR, ex.getMessage(), ex);
		}
	}

	@Transactional(readOnly = true)
	@Override
	public Class<? extends ContentHandler> getDocumentHandlerClass(String documentUniqueId) throws ClassNotFoundException {
		return dao.getDocumentHandlerClass(documentUniqueId);
	}


	@Transactional(readOnly = false, rollbackFor = {XDSException.class, ContentHandlerException.class} )
	@Override
	public RegistryResponseType provideAndRegisterDocumentSetB(ProvideAndRegisterDocumentSetRequestType request) throws XDSException, ContentHandlerException {
		boolean wasSuccess = false;

		// Get the required elements for auditing
		RegistryPackageType submissionSet = InfosetUtil.getRegistryPackage(request.getSubmitObjectsRequest(), XDSConstants.UUID_XDSSubmissionSet);
		String submissionSetUID = InfosetUtil.getExternalIdentifierValue(XDSConstants.UUID_XDSSubmissionSet_uniqueId, submissionSet),
				patID = InfosetUtil.getExternalIdentifierValue(XDSConstants.UUID_XDSSubmissionSet_patientId, submissionSet);
		AuditRequestInfo info = new AuditRequestInfo(null, null);

		RegistryResponseType response = new RegistryResponseType();

		try {

			List<ExtrinsicObjectType> extrinsicObjects = InfosetUtil.getExtrinsicObjects(request.getSubmitObjectsRequest());

			SubmitObjectsRequest submitObjectRequest = request.getSubmitObjectsRequest();
			XDSbService xdsService = Context.getService(XDSbService.class);

			Map<String, Class<? extends ContentHandler>> contentHandlers = new HashMap<String, Class<? extends ContentHandler>>();
			for (ExtrinsicObjectType eot : extrinsicObjects) {
				contentHandlers.put(this.processDocumentMetaData(eot, request), UnstructuredDataHandler.class);
			}

			response = xdsService.registerDocuments(contentHandlers, submitObjectRequest);

			// Save each document
			if (response.getStatus().equals(XDSConstants.XDS_B_STATUS_SUCCESS)) {
				for (ExtrinsicObjectType eot : extrinsicObjects) {
					this.storeDocument(eot, request);
				}
			}

			wasSuccess = true;

		} catch (UnsupportedGenderException ex) {
			throw new XDSException(XDSException.XDS_ERR_REPOSITORY_ERROR, ex.getMessage(), ex);

		} catch (JAXBException ex) {
			throw new XDSException(XDSException.XDS_ERR_REPOSITORY_ERROR, ex.getMessage(), ex);

		} finally {
			XDSAudit.setAuditLogger(Context.getService(AtnaAuditService.class).getLogger());
			XDSAudit.logRepositoryImport(submissionSetUID, patID, info, wasSuccess);
		}

		return response;
	}


	/**
	 * Store a document and return its UUID
	 */
	protected String processDocumentMetaData(ExtrinsicObjectType eot, ProvideAndRegisterDocumentSetRequestType request) throws XDSException {

		validateMetadata(eot);

		String docUniqueId = getDocumentUniqueId(eot);
		Content content = buildContentObjectFromDocument(docUniqueId, eot, request);

		addHashSlot(eot, content);
		addSizeSlot(eot, content);

		return docUniqueId;
	}

	/**
	 * Check that all the XDS.b metadata fields are present that are required in order to process the request
	 *
	 * @throws XDSException
	 */
	protected void validateMetadata(ExtrinsicObjectType eot) throws XDSException {
		if (InfosetUtil.getExternalIdentifierValue(XDSConstants.UUID_XDSDocumentEntry_uniqueId, eot) == null) {
			throw new XDSException(XDSException.XDS_ERR_REPOSITORY_METADATA_ERROR, "Document unique id not specified", null);
		}

		if (getClassificationFromExtrinsicObject(XDSConstants.UUID_XDSDocumentEntry_classCode, eot) == null) {
			throw new XDSException(XDSException.XDS_ERR_REPOSITORY_METADATA_ERROR, "DocumentEntry classCode not specified", null);
		}

		String id = InfosetUtil.getExternalIdentifierValue(XDSConstants.UUID_XDSDocumentEntry_patientId, eot);
		if (id == null) {
			throw new XDSException(XDSException.XDS_ERR_REPOSITORY_METADATA_ERROR, "DocumentEntry patientId not specified", null);
		}
		parsePatientIdentifier(id);

		id = InfosetUtil.getSlotValue(eot.getSlot(), XDSConstants.SLOT_NAME_SOURCE_PATIENT_ID, null);
		if (id == null) {
			throw new XDSException(XDSException.XDS_ERR_REPOSITORY_METADATA_ERROR, "Source patientId not specified", null);
		}
		parsePatientIdentifier(id);
	}

	protected String getDocumentUniqueId(ExtrinsicObjectType eot) throws XDSException {
		String docUniqueId = InfosetUtil.getExternalIdentifierValue(XDSConstants.UUID_XDSDocumentEntry_uniqueId, eot);

		// Do not store duplicates
		try {
			if (Context.getService(XDSbService.class).getDocumentHandlerClass(docUniqueId) != null) {
				throw new XDSException(XDSException.XDS_ERR_DOCUMENT_UNIQUE_ID_ERROR, String.format("Document id %s is duplicate", docUniqueId), null);
			}
		} catch (ClassNotFoundException e) {
			throw new XDSException(XDSException.XDS_ERR_REPOSITORY_ERROR, e.getMessage(), e);
		}

		return docUniqueId;
	}

	protected Content buildContentObjectFromDocument(String docUniqueId, ExtrinsicObjectType eot, ProvideAndRegisterDocumentSetRequestType request) throws XDSException {
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

		if (typeCode==null) {
			throw new XDSException(XDSException.XDS_ERR_REPOSITORY_METADATA_ERROR, "DocumentEntry typeCode not specified", null);
		}

		if (formatCode==null) {
			throw new XDSException(XDSException.XDS_ERR_REPOSITORY_METADATA_ERROR, "DocumentEntry formatCode not specified", null);
		}

		String docId = eot.getId();
		Map<String, ProvideAndRegisterDocumentSetRequestType.Document> docs = InfosetUtil.getDocuments(request);
		ProvideAndRegisterDocumentSetRequestType.Document document = docs.get(docId);

		return new Content(docUniqueId, document.getValue(), typeCode, formatCode, contentType);
	}

	protected void addHashSlot(ExtrinsicObjectType eot, Content content) {
		String hashValue = InfosetUtil.getSlotValue(eot.getSlot(), SLOT_NAME_HASH, null);
		if (hashValue == null) {
			SlotType1 hashSlot = new SlotType1();
			hashSlot.setName(SLOT_NAME_HASH);
			hashSlot.setValueList(new ValueListType());
			try {
				MessageDigest digest = MessageDigest.getInstance("SHA-1");
				digest.update(content.getPayload());

				hashSlot.getValueList().getValue().add(bytesToHex(digest.digest()));
				eot.getSlot().add(hashSlot);
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException(e);
			}
		}
	}

	protected void addSizeSlot(ExtrinsicObjectType eot, Content content) {
		String sizeValue = InfosetUtil.getSlotValue(eot.getSlot(), SLOT_NAME_SIZE, null);
		if (sizeValue == null) {
			SlotType1 sizeSlot = new SlotType1();
			sizeSlot.setName(SLOT_NAME_SIZE);
			sizeSlot.setValueList(new ValueListType());
			sizeSlot.getValueList().getValue().add(String.format("%d", content.getPayload().length));
			eot.getSlot().add(sizeSlot);
		}
	}


	/**
	 * Store a document and return its UUID
	 */
	protected String storeDocument(ExtrinsicObjectType eot, ProvideAndRegisterDocumentSetRequestType request) throws JAXBException, XDSException, UnsupportedGenderException, ContentHandlerException {

		String docId = eot.getId();
		Map<String, ProvideAndRegisterDocumentSetRequestType.Document> docs = InfosetUtil.getDocuments(request);
		ProvideAndRegisterDocumentSetRequestType.Document document = docs.get(docId);

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

		Content content = new Content(docUniqueId, document.getValue(), typeCode, formatCode, contentType);
		ContentHandlerService chs = Context.getService(ContentHandlerService.class);
		ContentHandler defaultHandler = chs.getDefaultUnstructuredHandler();
		ContentHandler discreteHandler = chs.getContentHandler(typeCode, formatCode);

		Patient patient = findOrCreatePatient(eot);
		Map<EncounterRole, Set<Provider>> providersByRole = findOrCreateProvidersByRole(eot);
		EncounterType encounterType = findOrCreateEncounterType(eot);

		// always send to the default unstructured data handler
		defaultHandler.saveContent(patient, providersByRole, encounterType, content);
		// If another handler exists send to that as well, do this async if config is set
		if (discreteHandler != null) {
			if (Context.getAdministrationService().getGlobalProperty(XDSbServiceConstants.XDS_REPOSITORY_DISCRETE_HANDLER_ASYNC, "false").equalsIgnoreCase("true")) {
				QueueItem qi = new QueueItem();
				qi.setDocUniqueId(docUniqueId);
				qi.setPatient(patient);
				qi.setEncounterType(encounterType);
				String rolesProvidersStr = stringifyRoleProvidersMap(providersByRole);
				qi.setRoleProviderMap(rolesProvidersStr);

				XDSbService xdsService = Context.getService(XDSbService.class);
				xdsService.queueDiscreteDataProcessing(qi);
			} else {
				discreteHandler.saveContent(patient, providersByRole, encounterType, content);
			}
		}

		return docUniqueId;
	}

	/**
	 * Represent the roles to provider map as a string using ids. This is done so that we don't have to
	 * perform complex hibernate mappings and so that we don't have to extend the OpenMRS provider object.
	 * @param providersByRole a map of roles to a set of providers
	 * @return a string format of the map eg. 2:23,24,26|4:19,12 where the format is:
	 * <role_id>:<provider_id>,<provider_id>,...|<role_id>:<provider_id>,<provider_id>,...|...
	 */
	protected String stringifyRoleProvidersMap(Map<EncounterRole, Set<Provider>> providersByRole) {
		StringBuffer sb = new StringBuffer();
		Iterator<EncounterRole> roleIterator = providersByRole.keySet().iterator();
		while (roleIterator.hasNext()) {
			EncounterRole role = roleIterator.next();
			sb.append(role.getId() + ":");
			Set<Provider> providers = providersByRole.get(role);
			Iterator<Provider> providerIterator = providers.iterator();
			while (providerIterator.hasNext()) {
				sb.append(providerIterator.next().getId());
				if (providerIterator.hasNext()) {
					sb.append(",");
				}
			}
			if (roleIterator.hasNext()) {
				sb.append("|");
			}
		}
		return sb.toString();
	}

	/**
	 * Finds an existing encounter type or create a new one if one cannot be found
	 *
	 * @param eo the ExtrinsicObject that represents the document in question
	 * @return an encounter type
	 * @throws JAXBException
	 */
	protected EncounterType findOrCreateEncounterType(ExtrinsicObjectType eo) {
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

		List<Map<String, SlotType1>> authorClassSlots = this.getClassificationSlotsFromExtrinsicObject(XDSConstants.UUID_XDSDocumentEntry_author, eo);
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
		Person person;
		PersonName name;
		Set names;

		// create a person and provider
		pro = new Provider();
		person = new Person();

		names = new TreeSet<PersonName>();

		pro.setIdentifier(xcnComponents[0]);

		if (xcnComponents.length >= 3 && !xcnComponents[2].isEmpty() && !xcnComponents[1].isEmpty()) {
			// if there are name components
			name = new PersonName(xcnComponents[2], "", xcnComponents[1]);
			names.add(name);
			person.setNames(names);
			person = Context.getPersonService().savePerson(person);
			pro.setPerson(person);
		} else {
			// set the name to the id as that's add we have?
			name = new PersonName(xcnComponents[0], "", "");
			names.add(name);
			person.setNames(names);
			person = Context.getPersonService().savePerson(person);
			pro.setPerson(person);
		}

		return pro;
	}

	/**
	 * @param classificationScheme - The classification scheme to look for
	 * @param eo                   - The extrinsic object to process
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
	 * @param eo                   - The extrinsic object to process
	 * @return The first classification of this type found
	 * @throws JAXBException
	 */
	private ClassificationType getClassificationFromExtrinsicObject(String classificationScheme, ExtrinsicObjectType eo) {
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
	protected Patient findOrCreatePatient(ExtrinsicObjectType eo) throws PatientIdentifierException, JAXBException, UnsupportedGenderException, XDSException {
		String patCX = InfosetUtil.getExternalIdentifierValue(XDSConstants.UUID_XDSDocumentEntry_patientId, eo);
		Identifier id = parsePatientIdentifier(patCX);

		PatientService ps = Context.getPatientService();
		// TODO: Is this correct, should we have patient identifier with the name as the assigning authority
		PatientIdentifierType idType = ps.getPatientIdentifierTypeByName(id.getAssigningAuthority().getAssigningAuthorityId());
		if (idType == null) {
			// create new idType
			idType = new PatientIdentifierType();
			idType.setName(id.getAssigningAuthority().getAssigningAuthorityId());
			idType.setDescription("ID type for assigning authority: '" + id.getAssigningAuthority().getAssigningAuthorityId() + "'. Created by the xds-b-repository module.");
			idType.setValidator("");
			idType = ps.savePatientIdentifierType(idType);
		}

		List<Patient> patients = ps.getPatients(null, id.getIdentifier(), Collections.singletonList(idType), true);

		Patient retVal = null;

		if (patients.size() > 1) {
			throw new PatientIdentifierException("Multiple patients found for this identifier: " + id.getIdentifier() + ", with id type: " + id.getAssigningAuthority().getAssigningAuthorityId());
		} else if (patients.size() < 1) {
			if (Context.getAdministrationService().getGlobalProperty(XDSbServiceConstants.XDS_REPOSITORY_AUTOCREATE_PATIENTS).equals("true")) {
				retVal = ps.savePatient(this.createPatient(eo, id.getIdentifier(), idType));
			} else {
				throw new XDSException(XDSException.XDS_ERR_UNKNOWN_PATID, String.format("Patient ID %s is not known to the repository", id.getIdentifier()), null);
			}
		} else {
			retVal = patients.get(0);
		}

		this.addLocalIdentifierToPatient(eo, retVal);
		return retVal;
	}

	/**
	 * Add local identifier to the patient.
	 */
	private void addLocalIdentifierToPatient(ExtrinsicObjectType eo, Patient pat) throws XDSException {

		String patCX = InfosetUtil.getSlotValue(eo.getSlot(), XDSConstants.SLOT_NAME_SOURCE_PATIENT_ID, null);
		Identifier id = parsePatientIdentifier(patCX);

		// Add the source identifier type if it does not exist!
		PatientIdentifierType pit = Context.getPatientService().getPatientIdentifierTypeByName(id.getAssigningAuthority().getAssigningAuthorityId());
		if (pit == null) {
			pit = new PatientIdentifierType();
			pit.setName(id.getAssigningAuthority().getAssigningAuthorityId());
			pit.setDescription("Automatically created by OpenSHR XDS");
			Context.getPatientService().savePatientIdentifierType(pit);

		}

		// Does the patient already have this identifier?
		boolean hasId = false;
		for (PatientIdentifier pid : pat.getIdentifiers()) {
			hasId |= pid.getIdentifierType().equals(pit) && pid.getIdentifier().equals(id.getIdentifier());
			if (hasId) break;
		}
		if (!hasId)
			pat.addIdentifier(new PatientIdentifier(id.getIdentifier(), pit, Context.getLocationService().getDefaultLocation()));
	}


	private Identifier parsePatientIdentifier(String id) throws XDSException {
		id = id.replaceAll("&amp;", "&");
		try {
			Identifier result = new Identifier(id);

			if (result.getIdentifier() == null) {
				throw new CXParseException("Empty identifier");
			}

			if (result.getAssigningAuthority()==null || result.getAssigningAuthority().getAssigningAuthorityId()==null) {
				throw new CXParseException("Assigning authority id not specified");
			}

			return result;
		} catch (CXParseException e) {
			throw new XDSException(XDSException.XDS_ERR_REPOSITORY_METADATA_ERROR, "Invalid DocumentEntry.patientId: " + e.getMessage(), null);
		}
	}


	/**
	 * Create a new patient object from document metadata
	 *
	 * @param eo     the ExtrinsicObject that represents the document in question
	 * @param patId  the patients unique ID
	 * @param idType the patient id type
	 * @return a newly created patient object
	 * @throws JAXBException
	 * @throws ParseException
	 * @throws UnsupportedGenderException
	 */
	private Patient createPatient(ExtrinsicObjectType eo, String patId, PatientIdentifierType idType)
			throws JAXBException, UnsupportedGenderException, XDSException {
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
				try {
					val = val.replace("PID-7|", "");
					Date dob = sdf.parse(val);
					pat.setBirthdate(dob);
				} catch (ParseException ex) {
					throw new XDSException(XDSException.XDS_ERR_REPOSITORY_METADATA_ERROR, "Unparseable date of birth value found: " + val, null);
				}
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

		if (pat.getGender() == null)
			pat.setGender("U");

		if (pat.getNames().size() == 0)
			pat.getNames().add(new PersonName("*", null, "*"));

		return pat;
	}

	/**
	 * Create a patient name
	 *
	 * @param nameComponents
	 * @return
	 */
	private PersonName createPatientName(String[] nameComponents) {
		if (nameComponents == null || nameComponents.length == 0) {
			return new PersonName("*", "*", "*");
		} else {
			PersonName pn = new PersonName();

			if (nameComponents[0] == null || "".equals(nameComponents[0])) {
				pn.setFamilyName("*");
			} else {
				pn.setFamilyName(nameComponents[0]);
			}

			if (nameComponents.length == 1 || "".equals(nameComponents[1])) {
				pn.setGivenName("*");
			} else {
				pn.setGivenName(nameComponents[1]);
			}

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
	}

	/**
	 * Create a patient address
	 *
	 * @param addrComponents
	 * @return
	 */
	private PersonAddress createPatientAddress(String[] addrComponents) {
		PersonAddress pa = new PersonAddress();
		try {

			pa.setAddress1(addrComponents[0]);
			pa.setAddress2(addrComponents[1]);
			pa.setCityVillage(addrComponents[2]);
			pa.setStateProvince(addrComponents[3]);
			pa.setPostalCode(addrComponents[4]);
			pa.setCountry(addrComponents[5]);
		} catch (ArrayIndexOutOfBoundsException e) {
			// ignore, these aren't important if they don't exist
		}

		return pa;
	}

	@Override
	public QueueItem queueDiscreteDataProcessing(QueueItem qi) {
		qi.setStatus(QueueItem.Status.QUEUED);
		qi.setDateAdded(new Date());
		return dao.queueDiscreteDataProcessing(qi);
	}

	@Override
	public QueueItem dequeueNextDiscreteDataForProcessing() {
		synchronized (this) {
			QueueItem qi = dao.dequeueNextDiscreteDataForProcessing();
			if (qi != null) {
				qi.setStatus(QueueItem.Status.PROCESSING);
				qi.setDateUpdated(new Date());
				return dao.updateQueueItem(qi);
			} else {
				return null;
			}
		}
	}

	@Override
	public QueueItem completeQueueItem(QueueItem qi, boolean successful) {
		if (successful) {
			qi.setStatus(QueueItem.Status.SUCCESSFUL);
		} else {
			qi.setStatus(QueueItem.Status.FAILED);
		}
		qi.setDateUpdated(new Date());
		return dao.updateQueueItem(qi);
	}

	/**
	* Register documents on registry
	* @throws Exception
	*/
	protected RegistryResponseType sendMetadataToRegistry(URL registryUrl, SubmitObjectsRequest submitObjectRequest) throws XDSException {
		
		DocumentRegistryPortType port = DocumentRegistryPortTypeFactory.getDocumentRegistryPortSoap12(registryUrl.toString());
		log.info("XDS.b: Send register document-b request to registry:" + registryUrl);
		// Auditing code
		EventTypeCode eventTypeCode = EventTypeCode.ITI_42_RegisterDocumentSetB;
		boolean wasSuccess = true;

		
		// JF: Fix meta-data issue
		for(ExtrinsicObjectType eot : InfosetUtil.getExtrinsicObjects(submitObjectRequest))
		{
			if(!eot.getObjectType().equals(XDSConstants.UUID_XDSDocumentEntry))
				eventTypeCode = new EventTypeCode("ITI-61", "IHE Transactions", "Register On-Demand Document Entry");

			try {
				InfosetUtil.addOrOverwriteSlot(eot, SLOT_NAME_REPOSITORY_UNIQUE_ID, Context.getAdministrationService().getGlobalProperty(XDSbServiceConstants.REPOSITORY_UNIQUE_ID_GP));
			} catch (JAXBException e) {
				e.printStackTrace();
			}

			//SlotType1 repositorySlot = new SlotType1();
			//repositorySlot.setName(SLOT_NAME_REPOSITORY_UNIQUE_ID);
			//repositorySlot.setValueList(new ValueListType());
			//repositorySlot.getValueList().getValue().add(Context.getAdministrationService().getGlobalProperty(XDSbServiceConstants.REPOSITORY_UNIQUE_ID_GP));
			//eot.getSlot().add(repositorySlot);
		}
				
		RegistryResponseType rsp;

		
		// Get the required elements for auditing
		RegistryPackageType submissionSet = InfosetUtil.getRegistryPackage(submitObjectRequest, XDSConstants.UUID_XDSSubmissionSet);
		String submissionSetUID = InfosetUtil.getExternalIdentifierValue(XDSConstants.UUID_XDSSubmissionSet_uniqueId, submissionSet),
				patID = InfosetUtil.getExternalIdentifierValue(XDSConstants.UUID_XDSSubmissionSet_patientId, submissionSet);
		AuditRequestInfo info = new AuditRequestInfo(null, null);

		try {
			
			rsp = port.documentRegistryRegisterDocumentSetB(submitObjectRequest);
			
		} catch (Exception e) {
			wasSuccess = false;
			throw new XDSException(XDSException.XDS_ERR_REG_NOT_AVAIL, "Document Registry not available: " + registryUrl, e);
		}
		finally
		{
			XDSAudit.setAuditLogger(Context.getService(AtnaAuditService.class).getLogger());
			XDSAudit.logExport(eventTypeCode, submissionSetUID, patID, XDSConstants.WS_ADDRESSING_ANONYMOUS, AuditLogger.processID(), info.getLocalHost(), registryUrl.toExternalForm(), null, registryUrl.getHost(), null, null, wasSuccess);
		}
		return rsp;
	}

	public XDSbDAO getDao() {
		return dao;
	}

	public void setDao(XDSbDAO dao) {
		this.dao = dao;
	}

}
