package org.kontalk.xmppserver.registration;

import com.nexmo.messaging.sdk.NexmoSmsClient;
import com.nexmo.messaging.sdk.SmsSubmissionResult;
import com.nexmo.messaging.sdk.messages.TextMessage;

import java.io.IOException;
import java.util.Map;


/**
 * Verification provider for Nexmo.
 * @author Daniele Ricci
 */
public class NexmoProvider extends AbstractSMSVerificationProvider {

    private static final String ACK_INSTRUCTIONS = "A SMS containing a verification code will be sent to the phone number you provided.";

    private String username;
    private String password;

    @Override
    public void init(Map<String, Object> settings) {
        super.init(settings);
        username = (String) settings.get("username");
        password = (String) settings.get("password");
    }

    @Override
    public String getAckInstructions() {
        return ACK_INSTRUCTIONS;
    }

    @Override
    public void sendVerificationCode(String phoneNumber, String code) throws IOException {
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
