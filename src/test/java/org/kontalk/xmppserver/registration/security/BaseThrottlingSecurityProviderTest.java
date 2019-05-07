package org.kontalk.xmppserver.registration.security;

import org.junit.Before;
import org.junit.Test;
import org.kontalk.xmppserver.registration.AbstractSMSVerificationProvider;
import org.kontalk.xmppserver.registration.PhoneNumberVerificationProvider;
import org.kontalk.xmppserver.registration.RegistrationRequest;
import org.kontalk.xmppserver.registration.VerificationRepository;
import tigase.db.TigaseDBException;
import tigase.xmpp.XMPPResourceConnection;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public abstract class BaseThrottlingSecurityProviderTest {

    protected static final int DELAY_SECONDS = 1;
    protected static final int ATTEMPTS = 3;

    protected SecurityProvider provider;
    protected PhoneNumberVerificationProvider verificationProvider;

    @Before
    public void setUp() throws Exception {
        provider = buildSecurityProvider();
        Map<String, Object> settings = new HashMap<>();
        settings.put("delay", DELAY_SECONDS);
        settings.put("trigger-attempts", ATTEMPTS);
        addConfiguration(settings);
        provider.init(settings);

        verificationProvider = new MyVerificationProvider();
    }

    protected abstract SecurityProvider buildSecurityProvider();

    protected abstract void addConfiguration(Map<String, Object> settings);

    @Test
    public abstract void testPass();

    @Test
    public abstract void testNotPass();

    @Test
    public abstract void testNotPassExpire();

    protected void sleep(long seconds) {
        try {
            Thread.sleep(seconds*1000);
        }
        catch (InterruptedException ignored) {
        }
    }

    private static final class MyVerificationProvider extends AbstractSMSVerificationProvider {

        @Override
        public String getAckInstructions() {
            return null;
        }

        @Override
        public RegistrationRequest startVerification(String domain, String phoneNumber) throws IOException, VerificationRepository.AlreadyRegisteredException, TigaseDBException {
            return new RegistrationRequest() {
                @Override
                public String getSenderId() {
                    return "TEST";
                }
            };
        }

        @Override
        public boolean endVerification(XMPPResourceConnection session, RegistrationRequest request, String proof) throws IOException, TigaseDBException {
            return true;
        }

        @Override
        public boolean supportsRequest(RegistrationRequest request) {
            return true;
        }

        @Override
        public String getChallengeType() {
            return PhoneNumberVerificationProvider.CHALLENGE_PIN;
        }
    }

}
