package org.kontalk.xmppserver.registration;

import java.io.IOException;
import java.util.Map;


/**
 * An interface for phone number verification providers.
 * @author Daniele Ricci
 */
public interface PhoneNumberVerificationProvider {

    public void init(Map<String, Object> settings);

    public String getSenderId();

    public String getAckInstructions();

    public void sendVerificationCode(String phoneNumber, String code) throws IOException;

}
