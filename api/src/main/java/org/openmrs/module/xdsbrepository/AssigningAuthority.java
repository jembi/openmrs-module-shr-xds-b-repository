/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openmrs.module.xdsbrepository;

/**
 * Borrowed from openhim-mediator-xds:
 * https://github.com/jembi/openhim-mediator-xds/blob/master/src/main/java/org/openhim/mediator/datatypes/AssigningAuthority.java
 */
public class AssigningAuthority {
    private String assigningAuthority;
    private String assigningAuthorityId;
    private String assigningAuthorityIdType;

    public AssigningAuthority() {
    }

    public AssigningAuthority(String assigningAuthority) {
        this.assigningAuthority = assigningAuthority;
    }

    public AssigningAuthority(String assigningAuthority, String assigningAuthorityId) {
        this.assigningAuthority = assigningAuthority;
        this.assigningAuthorityId = assigningAuthorityId;
    }

    public AssigningAuthority(String assigningAuthority, String assigningAuthorityId, String assigningAuthorityIdType) {
        this.assigningAuthority = assigningAuthority;
        this.assigningAuthorityId = assigningAuthorityId;
        this.assigningAuthorityIdType = assigningAuthorityIdType;
    }

    public String getAssigningAuthority() {
        return assigningAuthority;
    }

    public void setAssigningAuthority(String assigningAuthority) {
        this.assigningAuthority = assigningAuthority;
    }

    public String getAssigningAuthorityId() {
        return assigningAuthorityId;
    }

    public void setAssigningAuthorityId(String assigningAuthorityId) {
        this.assigningAuthorityId = assigningAuthorityId;
    }

    public String getAssigningAuthorityIdType() {
        return assigningAuthorityIdType;
    }

    public void setAssigningAuthorityIdType(String assigningAuthorityIdType) {
        this.assigningAuthorityIdType = assigningAuthorityIdType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AssigningAuthority authority = (AssigningAuthority) o;
        return toHL7().equals(authority.toHL7());
    }

    @Override
    public int hashCode() {
        int result = assigningAuthority != null ? assigningAuthority.hashCode() : 0;
        result = 31 * result + (assigningAuthorityId != null ? assigningAuthorityId.hashCode() : 0);
        result = 31 * result + (assigningAuthorityIdType != null ? assigningAuthorityIdType.hashCode() : 0);
        return result;
    }

    public String toHL7() {
        String res = "";
        if (assigningAuthority!=null && !assigningAuthority.trim().isEmpty()) {
            res += assigningAuthority;
        }
        if (assigningAuthorityId!=null && !assigningAuthorityId.trim().isEmpty()) {
            res += "&" + assigningAuthorityId;
        }
        if (assigningAuthorityIdType!=null && !assigningAuthorityIdType.trim().isEmpty()) {
            if (assigningAuthorityId==null || assigningAuthorityId.trim().isEmpty()) {
                res += "&";
            }
            res += "&" + assigningAuthorityIdType;
        }
        return res;
    }

    @Override
    public String toString() {
        return toHL7();
    }
}
