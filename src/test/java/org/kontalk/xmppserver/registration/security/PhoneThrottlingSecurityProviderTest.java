package org.kontalk.xmppserver.registration.security;

import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class PhoneThrottlingSecurityProviderTest extends BaseThrottlingSecurityProviderTest {

    @Override
    protected SecurityProvider buildSecurityProvider() {
        return new PhoneThrottlingSecurityProvider();
    }

    @Override
    protected void addConfiguration(Map<String, Object> settings) {
    }

    @Override
    public void testPass() {
        for (int i = 0; i < (ATTEMPTS + 2); i++) {
            assertTrue("attempt " + (i+1), provider.pass(JID.jidInstanceNS("c2s@4c8f50fc1b22/172.28.0.4_5222_87.67.240.34_46728"),
                    BareJID.bareJIDInstanceNS("test@local"), "+1555521444", verificationProvider));
            sleep(DELAY_SECONDS);
        }
        assertTrue(provider.pass(JID.jidInstanceNS("c2s@4c8f50fc1b22/172.28.0.4_5222_220.67.240.34_46728"),
                BareJID.bareJIDInstanceNS("test@local"), "+1555521333", verificationProvider));
    }

    @Override
    public void testNotPass() {
        for (int i = 0; i < ATTEMPTS; i++) {
            assertTrue("attempt " + (i+1), provider.pass(JID.jidInstanceNS("c2s@4c8f50fc1b22/172.28.0.4_5222_20.67.240.34_46728"),
                    BareJID.bareJIDInstanceNS("test@local"), "+1555521444", verificationProvider));
        }
        assertFalse(provider.pass(JID.jidInstanceNS("c2s@4c8f50fc1b22/172.28.0.4_5222_88.67.240.34_46728"),
                BareJID.bareJIDInstanceNS("test@local"), "+1555521444", verificationProvider));
        assertFalse(provider.pass(JID.jidInstanceNS("c2s@4c8f50fc1b22/172.28.0.4_5222_23.67.240.34_46728"),
                BareJID.bareJIDInstanceNS("test@local"), "+1555521444", verificationProvider));
    }

    @Override
    public void testNotPassExpire() {
        for (int i = 0; i < ATTEMPTS; i++) {
            assertTrue("attempt " + (i+1), provider.pass(JID.jidInstanceNS("c2s@4c8f50fc1b22/172.28.0.4_5222_19.67.240.34_46728"),
                    BareJID.bareJIDInstanceNS("test@local"), "+1555521444", verificationProvider));
        }
        assertFalse(provider.pass(JID.jidInstanceNS("c2s@4c8f50fc1b22/172.28.0.4_5222_77.67.240.34_46728"),
                BareJID.bareJIDInstanceNS("test@local"), "+1555521444", verificationProvider));
        sleep(DELAY_SECONDS);
        assertTrue(provider.pass(JID.jidInstanceNS("c2s@4c8f50fc1b22/172.28.0.4_5222_50.67.240.34_46728"),
                BareJID.bareJIDInstanceNS("test@local"), "+1555521444", verificationProvider));
    }

}
