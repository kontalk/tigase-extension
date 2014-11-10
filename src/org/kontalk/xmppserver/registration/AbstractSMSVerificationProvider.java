package org.kontalk.xmppserver.registration;

import java.util.Map;


public abstract class AbstractSMSVerificationProvider implements PhoneNumberVerificationProvider {

    protected String senderId;

    @Override
    public void init(Map<String, Object> settings) {
        senderId = (String) settings.get("sender");
    }

    @Override
    public String getSenderId() {
        return senderId;
    }

}
