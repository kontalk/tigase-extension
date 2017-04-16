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

package org.kontalk.xmppserver.registration;

import org.kontalk.xmppserver.registration.checkmobi.CheckmobiValidationClient;
import org.kontalk.xmppserver.registration.checkmobi.RequestResult;
import org.kontalk.xmppserver.registration.checkmobi.StatusResult;
import tigase.db.TigaseDBException;
import tigase.xmpp.XMPPResourceConnection;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Verification provider for CheckMobi using caller ID verification API.
 * @author Daniele Ricci
 */
public class CheckmobiCLIVerifyProvider implements PhoneNumberVerificationProvider {
    private static Logger log = Logger.getLogger(CheckmobiCLIVerifyProvider.class.getName());

    private static final String ACK_INSTRUCTIONS = "Please place a call to this phone number. It will not be answered, just wait until the automatic hang up.";

    private String apiKey;

    @Override
    public void init(Map<String, Object> settings) throws TigaseDBException {
        apiKey = (String) settings.get("apikey");
    }

    @Override
    public String getAckInstructions() {
        return ACK_INSTRUCTIONS;
    }

    /** Not used. */
    @Override
    public String getSenderId() {
        return null;
    }

    @Override
    public RegistrationRequest startVerification(String domain, String phoneNumber) throws IOException, VerificationRepository.AlreadyRegisteredException, TigaseDBException {
        CheckmobiValidationClient client = CheckmobiValidationClient.callerID(apiKey);

        if (log.isLoggable(Level.FINEST)) {
            log.finest("Requesting CheckMobi caller ID for " + phoneNumber);
        }
        RequestResult result = client.request(phoneNumber);
        if (result != null) {
            if (result.getStatus() == RequestResult.STATUS_SUCCESS) {
                if (log.isLoggable(Level.FINEST)) {
                    log.finest("Requested to CheckMobi: " + result.getId());
                }
                return new CheckmobiRequest(result.getId(), result.getDialingNumber());
            }
            else {
                throw new IOException("verification did not start (" + result.getError() + ")");
            }
        }
        else {
            throw new IOException("Unknown response");
        }
    }

    /** Parameters <code>proof</code> is ignored. */
    @Override
    public boolean endVerification(XMPPResourceConnection session, RegistrationRequest request, String proof) throws IOException, TigaseDBException {
        CheckmobiValidationClient client = CheckmobiValidationClient.callerID(apiKey);

        if (log.isLoggable(Level.FINEST)) {
            log.finest("Checking verification status from CheckMobi: " + request);
        }

        CheckmobiRequest myRequest = (CheckmobiRequest) request;
        StatusResult result = client.status(myRequest.getId());
        if (result != null) {
            if (result.getStatus() == StatusResult.STATUS_SUCCESS) {
                return result.isValidated();
            }
            else {
                throw new IOException("verification did not start (" + result.getError() + ")");
            }
        }
        else {
            throw new IOException("Unknown response");
        }
    }

    @Override
    public boolean supportsRequest(RegistrationRequest request) {
        return request instanceof CheckmobiRequest;
    }

    @Override
    public String getChallengeType() {
        return CHALLENGE_CALLER_ID;
    }

    private static final class CheckmobiRequest implements RegistrationRequest {
        private final String id;
        private final String dialingNumber;

        CheckmobiRequest(String id, String dialingNumber) {
            this.id = id;
            this.dialingNumber = dialingNumber;
        }

        public String getId() {
            return id;
        }

        @Override
        public String getSenderId() {
            return dialingNumber;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [id=" + id + "]";
        }
    }

}
