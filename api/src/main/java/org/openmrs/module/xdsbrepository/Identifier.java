/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openmrs.module.xdsbrepository;


import org.openmrs.module.xdsbrepository.exceptions.CXParseException;

/**
 * Borrowed from openhim-mediator-xds:
 * https://github.com/jembi/openhim-mediator-xds/blob/master/src/main/java/org/openhim/mediator/datatypes/Identifier.java
 */
public class Identifier {
    private String identifier;
    private AssigningAuthority assigningAuthority;
    private String typeCode;

    public Identifier(String identifier, AssigningAuthority assigningAuthority) {
        this.assigningAuthority = assigningAuthority;
        this.identifier = identifier;
    }

    public Identifier(String identifier, AssigningAuthority assigningAuthority, String typeCode) {
        this.assigningAuthority = assigningAuthority;
        this.identifier = identifier;
        this.typeCode = typeCode;
    }

    public Identifier(String CX) throws CXParseException {
        if (CX.trim().isEmpty()) {
            throw new CXParseException("Empty CX string");
        }

        String[] tokens = CX.split("\\^");

        if (tokens.length>0 && !tokens[0].isEmpty()) {
            identifier = tokens[0];
        }

        if (tokens.length>3 && !tokens[3].isEmpty()) {
            String[] authTokens = tokens[3].split("&");
            AssigningAuthority auth = new AssigningAuthority();

            if (authTokens.length>0 && !authTokens[0].isEmpty()) {
                auth.setAssigningAuthority(authTokens[0]);
            }
            if (authTokens.length>1 && !authTokens[1].isEmpty()) {
                auth.setAssigningAuthorityId(authTokens[1]);
            }
            if (authTokens.length>2 && !authTokens[2].isEmpty()) {
                auth.setAssigningAuthorityIdType(authTokens[2]);
            }

            assigningAuthority = auth;
        }

        if (tokens.length>4 && !tokens[4].isEmpty()) {
            typeCode = tokens[4];
        }
    }

    public AssigningAuthority getAssigningAuthority() {
        return assigningAuthority;
    }

    public void setAssigningAuthority(AssigningAuthority assigningAuthority) {
        this.assigningAuthority = assigningAuthority;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public String toString() {
        return toCX();
    }

    public String toCX() {
        String res = identifier;
        if (assigningAuthority!=null) {
            res += "^^^" + assigningAuthority.toHL7();
        }
        if (typeCode!=null && !typeCode.trim().isEmpty()) {
            if (assigningAuthority==null) {
                res += "^^^^" + typeCode;
            } else {
                res += "^" + typeCode;
            }
        }
        return res;
    }

    public String toXCN() {
        if (assigningAuthority==null) {
            return identifier;
        }
        return identifier + "^^^^^^^^" + assigningAuthority.toHL7();
    }

    public String toXON(String organisationName) {
        if (assigningAuthority==null) {
            return organisationName + "^^^^^^^^^" + identifier;
        }
        return organisationName + "^^^^^" + assigningAuthority.toHL7() + "^^^^" + identifier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Identifier that = (Identifier) o;
        return toCX().equals(that.toCX());
    }

    @Override
    public int hashCode() {
        int result = identifier != null ? identifier.hashCode() : 0;
        result = 31 * result + (assigningAuthority != null ? assigningAuthority.hashCode() : 0);
        result = 31 * result + (typeCode != null ? typeCode.hashCode() : 0);
        return result;
    }
}
