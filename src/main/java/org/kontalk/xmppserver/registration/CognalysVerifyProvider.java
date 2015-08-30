/*
 * Kontalk XMPP Tigase extension
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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
    public void init(Map<String, Object> settings) throws TigaseDBException {
        super.init(settings);
        username = (String) settings.get("username");
        password = (String) settings.get("password");
    }

    @Override
    public String getAckInstructions() {
        return ACK_INSTRUCTIONS;
    }

    @Override
    public String startVerification(String domain, String phoneNumber) throws IOException, VerificationRepository.AlreadyRegisteredException, TigaseDBException {
        CognalysVerifyClient client = new CognalysVerifyClient(username, password);

        RequestResult result = client.request(phoneNumber);
        if (result != null) {
            if (result.getStatus() == RequestResult.STATUS_SUCCESS) {
                return result.getKeymatch();
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
    public boolean endVerification(XMPPResourceConnection session, String requestId, String proof) throws IOException, TigaseDBException {
        CognalysVerifyClient client = new CognalysVerifyClient(username, password);

        ConfirmResult result = client.confirm(requestId, proof);
        if (result != null) {
            if (result.getStatus() == ConfirmResult.STATUS_SUCCESS) {
                return true;
            }
            else if (result.getError(401) != null || result.getError(400) != null) {
                // 401: "OTP is Wrong", 400: "Keymatch or OTP is not valid"
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

}
