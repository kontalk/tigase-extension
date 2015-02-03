/*
 * Kontalk XMPP Tigase extension
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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
    public String startVerification(XMPPResourceConnection session, String phoneNumber) throws IOException, VerificationRepository.AlreadyRegisteredException, TigaseDBException {
        // TODO
        return null;
    }

    @Override
    public boolean endVerification(XMPPResourceConnection session, String requestId, String proof) throws IOException, TigaseDBException {
        // TODO
        return false;
    }

}
