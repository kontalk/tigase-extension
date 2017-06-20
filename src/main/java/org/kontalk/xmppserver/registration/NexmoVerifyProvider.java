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

import com.nexmo.client.NexmoClient;
import com.nexmo.client.NexmoClientException;
import com.nexmo.client.NexmoUnexpectedException;
import com.nexmo.client.auth.TokenAuthMethod;
import com.nexmo.client.verify.CheckResult;
import com.nexmo.client.verify.VerifyClient;
import com.nexmo.client.verify.VerifyResult;
import tigase.conf.ConfigurationException;
import tigase.db.TigaseDBException;
import tigase.xmpp.XMPPResourceConnection;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;


/**
 * Verification provider for Nexmo using verification API.
 * @author Daniele Ricci
 */
public class NexmoVerifyProvider extends AbstractSMSVerificationProvider {
    private static Logger log = Logger.getLogger(NexmoVerifyProvider.class.getName());

    private static final String ACK_INSTRUCTIONS = "A SMS containing a verification code will be sent to the phone number you provided.";

    private String brand;

    private VerifyClient verifyClient;

    @Override
    public void init(Map<String, Object> settings) throws TigaseDBException, ConfigurationException {
        super.init(settings);
        brand = (String) settings.get("brand");

        String username = (String) settings.get("username");
        String password = (String) settings.get("password");
        if (username == null || password == null)
            throw new ConfigurationException("username and password are mandatory");

        final NexmoClient client = new NexmoClient(new TokenAuthMethod(username, password));
        verifyClient = client.getVerifyClient();
    }

    @Override
    public String getAckInstructions() {
        return ACK_INSTRUCTIONS;
    }

    @Override
    public RegistrationRequest startVerification(String domain, String phoneNumber) throws IOException, VerificationRepository.AlreadyRegisteredException, TigaseDBException {
        VerifyResult result;

        try {
            result = verifyClient.verify(phoneNumber, brand, senderId,
                    VerificationRepository.VERIFICATION_CODE_LENGTH, null);
        }
        catch (NexmoClientException | NexmoUnexpectedException e) {
            throw new IOException("Error requesting verification", e);
        }

        if (result != null) {
            if (result.getStatus() == VerifyResult.STATUS_OK) {
                return new NexmoVerifyRequest(result.getRequestId());
            }
            else {
                throw new IOException("verification did not start (" + result.getErrorText() + ")");
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

        CheckResult result;

        try {
            NexmoVerifyRequest myRequest = (NexmoVerifyRequest) request;
            result = verifyClient.check(myRequest.getId(), proof);
        }
        catch (NexmoClientException | NexmoUnexpectedException e) {
            throw new IOException("Error requesting verification", e);
        }

        if (result != null) {
            if (result.getStatus() == CheckResult.STATUS_OK) {
                return true;
            }
            else if (result.getStatus() == CheckResult.STATUS_INVALID_CODE) {
                return false;
            }
            else {
                throw new IOException("verification did not start (" + result.getErrorText() + ")");
            }
        }
        else {
            throw new IOException("Unknown response");
        }
    }

    @Override
    public boolean supportsRequest(RegistrationRequest request) {
        return request instanceof NexmoVerifyRequest;
    }

    @Override
    public String getChallengeType() {
        return CHALLENGE_PIN;
    }

    private static final class NexmoVerifyRequest implements RegistrationRequest {
        private final String id;
        public NexmoVerifyRequest(String id) {
            this.id = id;
        }

        @Override
        public String getSenderId() {
            return null;
        }

        public String getId() {
            return id;
        }
    }

}
