/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openmrs.module.xdsbrepository.exceptions;

public class CXParseException extends Exception {
    public CXParseException(Throwable cause) {
        super(cause);
    }

    public CXParseException() {
    }

    public CXParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public CXParseException(String message) {
        super(message);
    }
}
