package org.kontalk.xmppserver;

import tigase.db.TigaseDBException;
import tigase.xmpp.BareJID;
import tigase.xmpp.NotAuthorizedException;
import tigase.xmpp.XMPPResourceConnection;

/**
 * Kontalk authentication stuff.
 * @author Daniele Ricci
 */
public class KontalkAuth {
    private static final String NODE_AUTH = "kontalk/auth";
    private static final String KEY_FINGERPRINT = "fingerprint";

    private KontalkAuth() {}

    public static String getUserFingerprint(XMPPResourceConnection session)
            throws TigaseDBException, NotAuthorizedException {
        return session.getData(NODE_AUTH, KEY_FINGERPRINT, null);
    }

    public static String getUserFingerprint(XMPPResourceConnection session, BareJID jid)
            throws TigaseDBException {
        return session.getUserRepository().getData(jid, NODE_AUTH, KEY_FINGERPRINT, null);
    }

    public static void setUserFingerprint(XMPPResourceConnection session, BareJID jid, String fingerprint)
            throws TigaseDBException {
        session.getUserRepository().setData(jid, NODE_AUTH, KEY_FINGERPRINT, fingerprint);
    }

}
