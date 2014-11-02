package org.kontalk.xmppserver.pgp;

import com.freiheit.gnupg.GnuPGContext;
import com.freiheit.gnupg.GnuPGData;
import com.freiheit.gnupg.GnuPGKey;
import com.freiheit.gnupg.GnuPGSignature;
import org.kontalk.xmppserver.KontalkUser;
import tigase.xmpp.JID;

import java.util.Iterator;


/**
 * Kontalk keyring singleton.
 * @author Daniele Ricci
 */
public class KontalkKeyring {

    private static KontalkKeyring instance;

    private String domain;
    private String fingerprint;
    private GnuPGContext ctx;
    private GnuPGKey secretKey;

    /** Use {@link #init(String, String)} and {@link #getInstance()} instead. */
    public KontalkKeyring(String domain, String fingerprint) {
        this.domain = domain;
        this.fingerprint = fingerprint;
        this.ctx = new GnuPGContext();
        this.secretKey = ctx.getKeyByFingerprint(fingerprint);
    }

    /**
     * Authenticates the given public key in Kontalk.
     * @param keyData public key data to check
     * @return a user instance with JID and public key fingerprint.
     */
    public KontalkUser authenticate(byte[] keyData) {
        GnuPGData data = ctx.createDataObject(keyData);
        String fpr = ctx.importKey(data);
        GnuPGKey key = ctx.getKeyByFingerprint(fpr);

        JID jid = validate(key);

        if (jid != null) {
            System.out.println("key validated! " + jid);
            if (cacheKey(key, jid))
                return new KontalkUser(jid, key.getFingerprint());
        }

        return null;
    }

    /** Validates the given key for expiration, revocation and signature by the server. */
    private JID validate(GnuPGKey key) {
        if (key.isRevoked() || key.isExpired() || key.isInvalid())
            return null;

        String email = key.getEmail();
        JID jid = JID.jidInstanceNS(email);
        if (jid.getDomain().equalsIgnoreCase(domain)) {
            Iterator<GnuPGSignature> signatures = key.getSignatures();
            while (signatures != null && signatures.hasNext()) {
                GnuPGSignature sig = signatures.next();
                if (sig.isRevoked() || sig.isExpired() || sig.isInvalid())
                    return null;

                GnuPGKey skey = ctx.getKeyByFingerprint(sig.getKeyID());
                if (skey != null && skey.getFingerprint().equalsIgnoreCase(fingerprint))
                    return jid;
            }

        }

        return null;
    }

    /** Checks for an older key linked to the given user and saves the given one if possible. */
    private boolean cacheKey(GnuPGKey key, JID jid) {
        // TODO check for previous key presence and validity (and date)
        // TODO save new fingerprint to users table
        return false;
    }

    /** Initializes the keyring. */
    public static void init(String domain, String fingerprint) {
        if (instance != null)
            throw new IllegalArgumentException("keyring already initialized");
        instance = new KontalkKeyring(domain, fingerprint);
    }

    /** Returns the singleton keyring instance. Need to call {@link #init(String, String)} first! */
    public static KontalkKeyring getInstance() {
        return instance;
    }
}
