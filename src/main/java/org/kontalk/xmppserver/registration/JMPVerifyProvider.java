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

import tigase.db.TigaseDBException;
import tigase.xmpp.XMPPResourceConnection;

import java.io.IOException;


/**
 * Verification provider for JMP verification service.
 * TODO actual implementation
 * @see <a href="https://jmp.chat/">JMP - JIDs for Messaging with Phones</a>
 * @author Daniele Ricci
 */
public class JMPVerifyProvider extends BrandedSMSVerificationProvider {

    private static final String ACK_INSTRUCTIONS = "A SMS containing a verification code will be sent to the phone number you provided.";

    @Override
    public String getAckInstructions() {
        return ACK_INSTRUCTIONS;
    }

    @Override
    public RegistrationRequest startVerification(String domain, String phoneNumber) throws IOException, VerificationRepository.AlreadyRegisteredException, TigaseDBException {
        // TODO
        return new RegistrationRequest() {
            @Override
            public String getSenderId() {
                return "+00TODO";
            }
        };
    }

    @Override
    public boolean endVerification(XMPPResourceConnection session, RegistrationRequest request, String proof) throws IOException, TigaseDBException {
        return false;
    }

    @Override
    public boolean supportsRequest(RegistrationRequest request) {
        return false;
    }

    @Override
    public String getChallengeType() {
        return CHALLENGE_PIN;
    }
}
