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

import org.kontalk.xmppserver.registration.jmp.CheckResult;
import org.kontalk.xmppserver.registration.jmp.JmpVerifyClient;
import org.kontalk.xmppserver.registration.jmp.VerifyResult;
import tigase.conf.ConfigurationException;
import tigase.db.TigaseDBException;
import tigase.xmpp.XMPPResourceConnection;

import java.io.IOException;
import java.util.Map;


/**
 * Verification provider for JMP verification service.
 * @see <a href="https://jmp.chat/">JMP - JIDs for Messaging with Phones</a>
 * @author Daniele Ricci
 */
public class JMPVerifyProvider extends BrandedSMSVerificationProvider {

    private static final String ACK_INSTRUCTIONS = "A SMS containing a verification code will be sent to the phone number you provided.";

    private String username;
    private String password;
    private String brand;

    @Override
    public void init(Map<String, Object> settings) throws TigaseDBException, ConfigurationException {
        super.init(settings);
        username = (String) settings.get("username");
        password = (String) settings.get("password");
        brand = (String) settings.get("brand");
    }

    @Override
    public String getAckInstructions() {
        return ACK_INSTRUCTIONS;
    }

    @Override
    public RegistrationRequest startVerification(String domain, String phoneNumber) throws IOException, VerificationRepository.AlreadyRegisteredException, TigaseDBException {
        JmpVerifyClient client;

        client = new JmpVerifyClient(username, password);

        VerifyResult result;

        try {
            // remove plus
            if (phoneNumber.charAt(0) == '+')
                phoneNumber = phoneNumber.substring(1);

            result = client.verify(phoneNumber, brand, senderId, VerificationRepository.VERIFICATION_CODE_LENGTH, null);
        }
        catch (IOException e) {
            throw new IOException("Error requesting verification", e);
        }

        if (result != null) {
            if (result.getStatus() == VerifyResult.STATUS_OK) {
                return new JMPVerifyRequest(result.getRequestId());
            }
            else if (result.getStatus() == VerifyResult.STATUS_CONCURRENT_REQUEST) {
                throw new VerificationRepository.AlreadyRegisteredException();
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

        JmpVerifyClient client;

        client = new JmpVerifyClient(username, password);

        CheckResult result;

        try {
            JMPVerifyRequest myRequest = (JMPVerifyRequest) request;
            result = client.check(myRequest.getId(), proof);
        }
        catch (IOException e) {
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
        return request instanceof JMPVerifyRequest;
    }

    @Override
    public String getChallengeType() {
        return CHALLENGE_PIN;
    }

    private static final class JMPVerifyRequest implements RegistrationRequest {
        private final String id;
        public JMPVerifyRequest(String id) {
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
