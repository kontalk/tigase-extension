package org.kontalk.xmppserver;

import tigase.util.TigaseStringprepException;
import tigase.xmpp.JID;

/**
 * A Kontalk user.
 * @author Daniele Ricci
 */
public class KontalkUser {

    private JID jid;
    private String fingerprint;

    public KontalkUser(String jid, String fingerprint) throws TigaseStringprepException {
        this(JID.jidInstance(jid), fingerprint);
    }

    public KontalkUser(JID jid, String fingerprint) {
        this.jid = jid;
        this.fingerprint = fingerprint;
    }

    public JID getJid() {
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
