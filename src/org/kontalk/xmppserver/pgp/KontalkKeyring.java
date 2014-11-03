package org.kontalk.xmppserver.pgp;

import java.util.Iterator;

import org.kontalk.xmppserver.KontalkUser;

import tigase.xmpp.BareJID;

import com.freiheit.gnupg.GnuPGContext;
import com.freiheit.gnupg.GnuPGData;
import com.freiheit.gnupg.GnuPGKey;
import com.freiheit.gnupg.GnuPGSignature;


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

    /** Use {@link #getInstance(String, String)} instead. */
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
        data.destroy();

        GnuPGKey key = ctx.getKeyByFingerprint(fpr);

        BareJID jid = validate(key);

        if (jid != null) {
            return new KontalkUser(jid, key.getFingerprint());
        }

        return null;
    }

    /**
     * Post-authentication step: verifies that the given user is allowed to
     * login by checking the old key.
     * @param user user object returned by {@link #authenticate}
     * @param oldFingerprint old key fingerprint, null if none present
     * @return true if the new key can be accepted.
     */
    public boolean postAuthenticate(KontalkUser user, String oldFingerprint) {
        if (oldFingerprint == null || oldFingerprint.equalsIgnoreCase(user.getFingerprint())) {
            // no old fingerprint or same fingerprint -- access granted
            return true;
        }

        // retrive old user key
        GnuPGKey oldKey = ctx.getKeyByFingerprint(oldFingerprint);
        if (oldKey != null && validate(oldKey) != null) {
            // old key is still valid, check for timestamp

            GnuPGKey newKey = ctx.getKeyByFingerprint(user.getFingerprint());
            if (newKey != null && newKey.getTimestamp().getTime() >= oldKey.getTimestamp().getTime()) {
                return true;
            }
        }

        return false;
    }

    /** Validates the given key for expiration, revocation and signature by the server. */
    private BareJID validate(GnuPGKey key) {
        if (key.isRevoked() || key.isExpired() || key.isInvalid())
            return null;

        String email = key.getEmail();
        BareJID jid = BareJID.bareJIDInstanceNS(email);
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

    /** Initializes the keyring. */
    public static KontalkKeyring getInstance(String domain, String fingerprint) {
        if (instance == null)
            instance = new KontalkKeyring(domain, fingerprint);
        return instance;
    }

    /** Returns the singleton keyring instance. Need to call {@link #getInstance(String, String)} first! */
    public static KontalkKeyring getInstance() {
        return instance;
    }
}
