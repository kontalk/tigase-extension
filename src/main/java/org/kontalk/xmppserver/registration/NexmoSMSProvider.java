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

import com.nexmo.messaging.sdk.NexmoSmsClient;
import com.nexmo.messaging.sdk.SmsSubmissionResult;
import com.nexmo.messaging.sdk.messages.TextMessage;
import tigase.db.TigaseDBException;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;


/**
 * Verification provider for Nexmo using SMS API.
 * @author Daniele Ricci
 */
public class NexmoSMSProvider extends SMSDataStoreVerificationProvider {
    private static Logger log = Logger.getLogger(NexmoSMSProvider.class.getName());

    private static final String ACK_INSTRUCTIONS = "A SMS containing a verification code will be sent to the phone number you provided.";

    private String username;
    private String password;

    @Override
    public void init(Map<String, Object> settings) throws TigaseDBException {
        super.init(log, settings);
        username = (String) settings.get("username");
        password = (String) settings.get("password");
    }

    @Override
    public String getAckInstructions() {
        return ACK_INSTRUCTIONS;
    }

    @Override
    protected void sendVerificationCode(String phoneNumber, String code) throws IOException {
        NexmoSmsClient client;
        try {
            client = new NexmoSmsClient(username, password);
        }
        catch (Exception e) {
            throw new IOException("Error initializing Nexmo client", e);
        }

        TextMessage msg = new TextMessage(senderId, phoneNumber, code);

        SmsSubmissionResult[] results;
        try {
            results = client.submitMessage(msg);
        }
        catch (Exception e) {
            throw new IOException("Error sending SMS", e);
        }

        if (results != null && results.length > 0) {
            SmsSubmissionResult result = results[0];
            if (result.getStatus() != SmsSubmissionResult.STATUS_OK) {
                throw new IOException("SMS was not sent (" + result.getErrorText() + ")");
            }
        }
        else {
            throw new IOException("Unknown response");
        }
    }

}
