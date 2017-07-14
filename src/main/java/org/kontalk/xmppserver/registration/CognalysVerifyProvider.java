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

import org.kontalk.xmppserver.registration.cognalys.CognalysVerifyClient;
import org.kontalk.xmppserver.registration.cognalys.ConfirmResult;
import org.kontalk.xmppserver.registration.cognalys.RequestResult;
import tigase.conf.ConfigurationException;
import tigase.db.TigaseDBException;
import tigase.xmpp.XMPPResourceConnection;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;


/**
 * Verification provider for Cognalys using missed call verification API.
 * @author Daniele Ricci
 */
public class CognalysVerifyProvider extends AbstractSMSVerificationProvider {
    private static Logger log = Logger.getLogger(CognalysVerifyProvider.class.getName());

    private static final String ACK_INSTRUCTIONS = "A missed call will be placed to the phone number you provided.";

    private String username;
    private String password;

    @Override
    public void init(Map<String, Object> settings) throws TigaseDBException, ConfigurationException {
        super.init(settings);
        username = (String) settings.get("username");
        password = (String) settings.get("password");
    }

    @Override
    public String getAckInstructions() {
        return ACK_INSTRUCTIONS;
    }

    @Override
    public RegistrationRequest startVerification(String domain, String phoneNumber) throws IOException, VerificationRepository.AlreadyRegisteredException, TigaseDBException {
        CognalysVerifyClient client = new CognalysVerifyClient(username, password);

        // remove plus
        if (phoneNumber.charAt(0) == '+')
            phoneNumber = phoneNumber.substring(1);

        log.fine("Requesting Cognalys verification for " + phoneNumber);
        RequestResult result = client.request(phoneNumber);
        if (result != null) {
            if (result.getStatus() == RequestResult.STATUS_SUCCESS) {
                log.fine("Requested to Cognalys: " + result.getKeymatch());
                return new CognalysRequest(result.getKeymatch(), result.getOtpStart());
            }
            else {
                throw new IOException("verification did not start (" + result.getErrors() + ")");
            }
        }
        else {
            throw new IOException("Unknown response");
        }
    }

    @Override
    public boolean endVerification(XMPPResourceConnection session, RegistrationRequest request, String proof) throws IOException, TigaseDBException {
        CognalysVerifyClient client = new CognalysVerifyClient(username, password);

        log.fine("Confirming to Cognalys: " + request + ", OTP: " + proof);
        CognalysRequest myRequest = (CognalysRequest) request;
        ConfirmResult result = client.confirm(myRequest.getKeymatch(), myRequest.getOtp(proof));
        if (result != null) {
            if (result.getStatus() == ConfirmResult.STATUS_SUCCESS) {
                return true;
            }
            else if (result.getError(401) != null || result.getError(400) != null || result.getError(306) != null) {
                // 401: "OTP is Wrong"
                // 400: "Keymatch or OTP is not valid"
                // 306: "OTP is missing or not valid"
                log.fine("Confirmation error: " + result.getErrors());
                return false;
            }
            else {
                throw new IOException("verification did not start (" + result.getErrors() + ")");
            }
        }
        else {
            throw new IOException("Unknown response");
        }
    }

    @Override
    public boolean supportsRequest(RegistrationRequest request) {
        return request instanceof CognalysRequest;
    }

    @Override
    public String getChallengeType() {
        return CHALLENGE_MISSED_CALL;
    }

    private static final class CognalysRequest implements RegistrationRequest {
        private final String keymatch;
        private final String otpStart;

        public CognalysRequest(String keymatch, String otpStart) {
            this.keymatch = keymatch;
            // seems that the plus at the beginning doesn't work
            this.otpStart = otpStart;
        }

        public String getKeymatch() {
            return keymatch;
        }

        public String getOtp(String proof) {
            return ((otpStart != null && otpStart.charAt(0) == '+') ? otpStart.substring(1) : otpStart) + proof;
        }

        @Override
        public String getSenderId() {
            return otpStart + "?????";
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [keymatch=" + keymatch + ", otp_start=" + otpStart + "]";
        }
    }

}
