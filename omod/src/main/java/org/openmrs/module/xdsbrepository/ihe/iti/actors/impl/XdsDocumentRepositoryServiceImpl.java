package org.openmrs.module.xdsbrepository.ihe.iti.actors.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dcm4chee.xds2.common.XDSConstants;
import org.dcm4chee.xds2.common.XDSUtil;
import org.dcm4chee.xds2.common.audit.AuditRequestInfo;
import org.dcm4chee.xds2.common.audit.XDSAudit;
import org.dcm4chee.xds2.common.exception.XDSException;
import org.dcm4chee.xds2.infoset.ihe.ProvideAndRegisterDocumentSetRequestType;
import org.dcm4chee.xds2.infoset.ihe.RetrieveDocumentSetRequestType;
import org.dcm4chee.xds2.infoset.ihe.RetrieveDocumentSetRequestType.DocumentRequest;
import org.dcm4chee.xds2.infoset.ihe.RetrieveDocumentSetResponseType;
import org.dcm4chee.xds2.infoset.rim.ObjectFactory;
import org.dcm4chee.xds2.infoset.rim.RegistryError;
import org.dcm4chee.xds2.infoset.rim.RegistryErrorList;
import org.dcm4chee.xds2.infoset.rim.RegistryResponseType;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.shr.atna.api.AtnaAuditService;
import org.openmrs.module.shr.contenthandler.api.Content;
import org.openmrs.module.shr.contenthandler.api.ContentHandler;
import org.openmrs.module.shr.contenthandler.api.ContentHandlerService;
import org.openmrs.module.xdsbrepository.XDSbService;
import org.openmrs.module.xdsbrepository.XDSbServiceConstants;
import org.openmrs.module.xdsbrepository.ihe.iti.actors.XdsDocumentRepositoryService;
import org.springframework.stereotype.Service;

import javax.activation.DataHandler;
import javax.mail.util.ByteArrayDataSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * XdsDocumentRepository Service Implementation
 * <p/>
 * NB: This code is heavily borrowed from DCM4CHE
 */
@Service
public class XdsDocumentRepositoryServiceImpl implements XdsDocumentRepositoryService {

    private ObjectFactory factory = new ObjectFactory();
    private org.dcm4chee.xds2.infoset.ihe.ObjectFactory iheFactory = new org.dcm4chee.xds2.infoset.ihe.ObjectFactory();

    // Get the clinical statement service
    protected final Log log = LogFactory.getLog(this.getClass());


    /**
     * Start an OpenMRS Session
     */
    private void startSession() {
        AdministrationService as = Context.getAdministrationService();
        String username = as.getGlobalProperty(XDSbServiceConstants.WS_USERNAME_GP);
        String password = as.getGlobalProperty(XDSbServiceConstants.WS_PASSWORD_GP);

        Context.openSession();
        Context.authenticate(username, password);

    }

    /**
     * Document repository service implementation
     *
     * @see XdsDocumentRepositoryService#provideAndRegisterDocumentSetB(org.dcm4chee.xds2.infoset.ihe.ProvideAndRegisterDocumentSetRequestType)
     */
    @Override
    public RegistryResponseType provideAndRegisterDocumentSetB(ProvideAndRegisterDocumentSetRequestType request) {
        log.info("Start provideAndRegisterDocumentSetB");
        if (!Context.isAuthenticated()) {
            this.startSession();
        }

        RegistryResponseType response = new RegistryResponseType();

        try {
            response = Context.getService(XDSbService.class).provideAndRegisterDocumentSetB(request);

        } catch (XDSException ex) {
            processExceptionForResponse(response, ex);

        } finally {
            log.info("Stop provideAndRegisterDocumentSetB");
            Context.closeSession();
        }

        return response;
    }



    /**
     * Retrieve a document
     * Code mostly borrowed from: https://github.com/dcm4che/dcm4chee-xds/blob/master/dcm4chee-xds2-repository-ws/src/main/java/org/dcm4chee/xds2/repository/ws/XDSRepositoryBean.java#L204
     *
     * @see XdsDocumentRepositoryService#retrieveDocumentSetB(org.dcm4chee.xds2.infoset.ihe.RetrieveDocumentSetRequestType)
     */
    @Override
    public RetrieveDocumentSetResponseType retrieveDocumentSetB(RetrieveDocumentSetRequestType req) {
        XDSbService xdsService = Context.getService(XDSbService.class);
        RetrieveDocumentSetResponseType rsp = iheFactory.createRetrieveDocumentSetResponseType();
        RegistryResponseType regRsp = factory.createRegistryResponseType();

        try {
            if (!Context.isAuthenticated()) {
                this.startSession();
            }

            String repositoryUID = getRepositoryUniqueId();
            String docUid, reqRepoUid;
            Content content;
            for (DocumentRequest drq : req.getDocumentRequest())
                drq.setHomeCommunityId(Context.getAdministrationService().getGlobalProperty(XDSbServiceConstants.XDS_HOME_COMMUNITY_ID));
            RetrieveDocumentSetResponseType.DocumentResponse docRsp;
            List<String> retrievedUIDs = new ArrayList<String>();
            int requestCount = req.getDocumentRequest().size();
            RegistryErrorList regErrors = factory.createRegistryErrorList();
            List<RegistryError> mainErrors = regErrors.getRegistryError();
            for (DocumentRequest docReq : req.getDocumentRequest()) {
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
                    if (h == null) {
                        h = chs.getDefaultUnstructuredHandler();
                    }
                    content = h.fetchContent(docUid);

                    if (content != null) {
                        try {
                            docRsp = getDocumentResponse(content, docUid, getRepositoryUniqueId());
                            rsp.getDocumentResponse().add(docRsp);
                            retrievedUIDs.add(docUid);
                        } catch (IOException e) {
                            String msg = "Error in building DocumentResponse for document:" + content;
                            log.error(msg);
                            mainErrors.add(XDSUtil.getRegistryError(XDSException.XDS_ERR_SEVERITY_ERROR,
                                    XDSException.XDS_ERR_REPOSITORY_ERROR, msg, docUid));
                        }
                    } else {
                        String msg = "Document not found! document UID:" + docUid;
                        log.warn(msg);
                        mainErrors.add(XDSUtil.getRegistryError(XDSException.XDS_ERR_SEVERITY_ERROR,
                                XDSException.XDS_ERR_MISSING_DOCUMENT, msg, docUid));
                    }
                } else {
                    String msg = "DocumentRepositoryUID=" + reqRepoUid + " is unknown! This repository unique ID:" + repositoryUID;
                    log.warn(msg);
                    mainErrors.add(XDSUtil.getRegistryError(XDSException.XDS_ERR_SEVERITY_ERROR,
                            XDSException.XDS_ERR_UNKNOWN_REPOSITORY_ID, msg, docUid));
                }
            }

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

        } catch (Exception x) {
            processExceptionForResponse(regRsp, x);
            Context.clearSession(); //TODO this doesn't seem to rollback
        } finally {
            XDSAudit.setAuditLogger(Context.getService(AtnaAuditService.class).getLogger());
            XDSAudit.logRepositoryRetrieveExport(req, rsp, new AuditRequestInfo(null, null));
            Context.closeSession();

            rsp.setRegistryResponse(regRsp);
            return rsp;
        }
    }

    private String getRepositoryUniqueId() {
        return Context.getAdministrationService().getGlobalProperty(XDSbServiceConstants.REPOSITORY_UNIQUE_ID_GP);
    }

    private RetrieveDocumentSetResponseType.DocumentResponse getDocumentResponse(Content content, String documentUniqueId, String repositoryUniqueId) throws IOException {
        RetrieveDocumentSetResponseType.DocumentResponse docRsp;
        docRsp = iheFactory.createRetrieveDocumentSetResponseTypeDocumentResponse();
        docRsp.setDocumentUniqueId(documentUniqueId);

        // JF : HACK: New Document Unique Id if different
        if (!content.getContentId().equals(documentUniqueId)) {
            docRsp.setNewDocumentUniqueId(content.getContentId());
        }

        docRsp.setMimeType(content.getContentType());
        docRsp.setRepositoryUniqueId(repositoryUniqueId);
        log.error(String.format("Payload length %d", content.getPayload().length));

        ByteArrayDataSource ds = new ByteArrayDataSource(content.getPayload(), content.getContentType());
        docRsp.setDocument(new DataHandler(ds));
        return docRsp;
    }


    private void processExceptionForResponse(RegistryResponseType response, Throwable t) {
        log.error(t);
        response.setStatus(XDSConstants.XDS_B_STATUS_FAILURE);

        if (t instanceof XDSException) {
            XDSUtil.addError(response, (XDSException) t);
        } else {
            String err = t.getMessage() != null ? t.getMessage() : t.toString();
            err = "Unexpected error in XDS service !: " + err;

            XDSUtil.addError(response, new XDSException(XDSException.XDS_ERR_REPOSITORY_ERROR, err, t));

            t.printStackTrace();
        }
    }
}
