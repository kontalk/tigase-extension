package org.kontalk.xmppserver.push;


/**
 * Push registration information.
 * @author Daniele Ricci
 */
public class PushRegistrationInfo {
    private final String provider;
    private String registrationId;

    public PushRegistrationInfo(String provider, String registrationId) {
        this.provider = provider;
        this.registrationId = registrationId;
    }

    public String getProvider() {
        return provider;
    }

    public String getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(String registrationId) {
        this.registrationId = registrationId;
    }
}
