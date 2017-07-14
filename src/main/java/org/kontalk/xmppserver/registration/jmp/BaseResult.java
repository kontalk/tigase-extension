/*
 * Kontalk XMPP Tigase extension
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.xmppserver.registration.jmp;


/**
 * Verification base result class.
 * @author Daniele Ricci
 */
public abstract class BaseResult {

    /**
     * Verify was successfully submitted to the JMP service
     */
    public static final int STATUS_OK = 0;

    /**
     * Verify was rejected due to exceeding the maximum throughput allowed for this account.<br>
     * Verify can be re-requested after a short delay
     */
    public static final int STATUS_THROTTLED = 1;

    /**
     * Verify was rejected due to a failure within the JMP systems.<br>
     * Verify can be re-submitted after a short delay
     */
    public static final int STATUS_INTERNAL_ERROR = 2;

    /**
     * Concurrent verification to the same number within a short time
     */
    public static final int STATUS_CONCURRENT_REQUEST = 10;

    /**
     * The code inserted does not match the expected value
     */
    public static final int STATUS_INVALID_CODE = 16;

    /**
     * There are no matching verification requests
     */
    public static final int STATUS_NO_RESPONSE = 101;

    /**
     * A network error occured
     */
    public static final int STATUS_COMMS_FAILURE = -1;

    private final int status;
    private final String errorText;

    protected BaseResult(final int status,
                         final String errorText) {
        this.status = status;
        this.errorText = errorText;
    }

    public int getStatus() {
        return this.status;
    }

    public String getErrorText() {
        return this.errorText;
    }

}
