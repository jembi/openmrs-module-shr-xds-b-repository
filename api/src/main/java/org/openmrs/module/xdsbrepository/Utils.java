package org.openmrs.module.xdsbrepository;

import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;

/**
 * Created by ryan on 2015/06/19.
 */
public class Utils {

    /**
     * Start an OpenMRS Session
     */
    public static void startSession() {
        AdministrationService as = Context.getAdministrationService();
        Context.openSession();

        if (!Context.isAuthenticated()) {
            String username = as.getGlobalProperty(XDSbServiceConstants.WS_USERNAME_GP);
            String password = as.getGlobalProperty(XDSbServiceConstants.WS_PASSWORD_GP);
            Context.authenticate(username, password);
        }
    }

}
