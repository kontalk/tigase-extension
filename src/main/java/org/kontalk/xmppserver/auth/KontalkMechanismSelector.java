package org.kontalk.xmppserver.auth;


import tigase.auth.DefaultMechanismSelector;
import tigase.xmpp.XMPPResourceConnection;

import javax.security.sasl.SaslServerFactory;
import java.util.logging.Logger;


/**
 * SASL mechanism selector for Kontalk legacy token.
 * @author Daniele Ricci
 */
public class KontalkMechanismSelector extends DefaultMechanismSelector {
    private static final Logger log = Logger.getLogger(KontalkMechanismSelector.class.getName());

    protected boolean match(SaslServerFactory factory, String mechanismName, XMPPResourceConnection session) {
        log.info("Matching factory " + factory + " with mechanism " + mechanismName);
        return super.match(factory, mechanismName, session) ||
            (factory instanceof KontalkSaslServerFactory && mechanismName.equals(SaslKontalkToken.MECHANISM));
    }

}
