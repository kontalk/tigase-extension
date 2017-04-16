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
import org.kontalk.xmppserver.registration.checkmobi.VerifyResult;
import tigase.db.TigaseDBException;
import tigase.xmpp.XMPPResourceConnection;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Verification provider for CheckMobi using reverse caller ID verification API.
 * @author Daniele Ricci
 */
public class CheckmobiReverseVerifyProvider extends AbstractSMSVerificationProvider {
    private static Logger log = Logger.getLogger(CheckmobiReverseVerifyProvider.class.getName());

    private static final int PROOF_LENGTH = 4;

    private static final String ACK_INSTRUCTIONS = "A missed call will be placed to the phone number you provided.";

    private String apiKey;

    @Override
    public void init(Map<String, Object> settings) throws TigaseDBException {
        super.init(settings);
        apiKey = (String) settings.get("apikey");
    }

    @Override
    public String getAckInstructions() {
        return ACK_INSTRUCTIONS;
    }

    @Override
    public RegistrationRequest startVerification(String domain, String phoneNumber) throws IOException, VerificationRepository.AlreadyRegisteredException, TigaseDBException {
        CheckmobiValidationClient client = CheckmobiValidationClient.reverseCallerID(apiKey);

        if (log.isLoggable(Level.FINEST)) {
            log.finest("Requesting CheckMobi verification for " + phoneNumber);
        }
        RequestResult result = client.request(phoneNumber);
        if (result != null) {
            if (result.getStatus() == RequestResult.STATUS_SUCCESS) {
                if (log.isLoggable(Level.FINEST)) {
                    log.finest("Requested to CheckMobi: " + result.getId());
                }
                return new CheckmobiRequest(result.getId());
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
    public boolean endVerification(XMPPResourceConnection session, RegistrationRequest request, String proof) throws IOException, TigaseDBException {
        if (proof == null || proof.length() == 0) {
            return false;
        }

        CheckmobiValidationClient client = CheckmobiValidationClient.reverseCallerID(apiKey);

        // take the last N characters (dummy proof :)
        String finalProof = proof.substring(Math.max(0, proof.length() - PROOF_LENGTH));
        if (log.isLoggable(Level.FINEST)) {
            log.finest("Confirming to CheckMobi: " + request + ", proof: " + finalProof);
        }
        CheckmobiRequest myRequest = (CheckmobiRequest) request;
        VerifyResult result = client.verify(myRequest.getId(), finalProof);
        if (result != null) {
            if (result.getStatus() == VerifyResult.STATUS_SUCCESS) {
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
        return CHALLENGE_MISSED_CALL;
    }

    private static final class CheckmobiRequest implements RegistrationRequest {
        private final String id;

        CheckmobiRequest(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        @Override
        public String getSenderId() {
            // 4 unknown digits
            return "+xx-xxxxxx????";
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [id=" + id + "]";
        }
    }

}
