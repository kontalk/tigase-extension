package org.kontalk.xmppserver;

import tigase.util.TigaseStringprepException;
import tigase.xmpp.BareJID;

/**
 * A Kontalk user.
 * @author Daniele Ricci
 */
public class KontalkUser {

    private BareJID jid;
    private String fingerprint;

    public KontalkUser(String jid, String fingerprint) throws TigaseStringprepException {
        this(BareJID.bareJIDInstance(jid), fingerprint);
    }

    public KontalkUser(BareJID jid, String fingerprint) {
        this.jid = jid;
        this.fingerprint = fingerprint;
    }

    public BareJID getJID() {
        return jid;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    @Override
    public String toString() {
        return jid + "/" + fingerprint;
    }
}
