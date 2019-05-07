package org.kontalk.xmppserver.registration.security;

import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class IPThrottlingSecurityProviderTest extends BaseThrottlingSecurityProviderTest {

    @Override
    protected SecurityProvider buildSecurityProvider() {
        return new IPThrottlingSecurityProvider();
    }

    @Override
    protected void addConfiguration(Map<String, Object> settings) {
    }

    @Override
    public void testPass() {
        for (int i = 0; i < (ATTEMPTS + 2); i++) {
            assertTrue("attempt " + (i+1), provider.pass(JID.jidInstanceNS("c2s@4c8f50fc1b22/172.28.0.4_5222_91.67.240.34_46728"),
                    BareJID.bareJIDInstanceNS("test@local"), "+1555521444", verificationProvider));
            sleep(DELAY_SECONDS);
        }
        assertTrue(provider.pass(JID.jidInstanceNS("c2s@4c8f50fc1b22/172.28.0.4_5222_90.67.240.34_46728"),
                BareJID.bareJIDInstanceNS("test@local"), "+1555521444", verificationProvider));
    }

    @Override
    public void testNotPass() {
        for (int i = 0; i < ATTEMPTS; i++) {
            assertTrue("attempt " + (i+1), provider.pass(JID.jidInstanceNS("c2s@4c8f50fc1b22/172.28.0.4_5222_91.67.240.34_46728"),
                    BareJID.bareJIDInstanceNS("test@local"), "+1555521444", verificationProvider));
        }
        assertFalse(provider.pass(JID.jidInstanceNS("c2s@4c8f50fc1b22/172.28.0.4_5222_91.67.240.34_46728"),
                BareJID.bareJIDInstanceNS("test@local"), "+1555521444", verificationProvider));
        assertFalse(provider.pass(JID.jidInstanceNS("c2s@4c8f50fc1b22/172.28.0.4_5222_91.67.240.34_46728"),
                BareJID.bareJIDInstanceNS("test@local"), "+1555521444", verificationProvider));
    }

    @Override
    public void testNotPassExpire() {
        for (int i = 0; i < ATTEMPTS; i++) {
            assertTrue("attempt " + (i+1), provider.pass(JID.jidInstanceNS("c2s@4c8f50fc1b22/172.28.0.4_5222_91.67.240.34_46728"),
                    BareJID.bareJIDInstanceNS("test@local"), "+1555521444", verificationProvider));
        }
        assertFalse(provider.pass(JID.jidInstanceNS("c2s@4c8f50fc1b22/172.28.0.4_5222_91.67.240.34_46728"),
                BareJID.bareJIDInstanceNS("test@local"), "+1555521444", verificationProvider));
        sleep(DELAY_SECONDS);
        assertTrue(provider.pass(JID.jidInstanceNS("c2s@4c8f50fc1b22/172.28.0.4_5222_91.67.240.34_46728"),
                BareJID.bareJIDInstanceNS("test@local"), "+1555521444", verificationProvider));
    }

}
