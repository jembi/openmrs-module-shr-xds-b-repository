/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openmrs.module.xdsbrepository.exceptions;

public class HydrateRoleProviderMapException extends Exception {
    public HydrateRoleProviderMapException(Throwable cause) {
        super(cause);
    }

    public HydrateRoleProviderMapException() {
    }

    public HydrateRoleProviderMapException(String message, Throwable cause) {
        super(message, cause);
    }

    public HydrateRoleProviderMapException(String message) {
        super(message);
    }
}
