/*
 * Kontalk XMPP Tigase extension
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.xmppserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.bouncycastle.openpgp.PGPException;
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

    private static final Map<String, KontalkKeyring> instances = new HashMap<String, KontalkKeyring>();

    private final String domain;
    private final String fingerprint;
    private final GnuPGContext ctx;
    private GnuPGKey secretKey;

    /** Use {@link #getInstance(String, String)} instead. */
    public KontalkKeyring(String domain, String fingerprint) {
        this.domain = domain;
        this.fingerprint = fingerprint;
        this.ctx = new GnuPGContext();
        this.ctx.setArmor(false);
        if (fingerprint != null)
            this.secretKey = ctx.getKeyByFingerprint(fingerprint);
    }

    /**
     * Authenticates the given public key in Kontalk.
     * @param keyData public key data to check
     * @return a user instance with JID and public key fingerprint.
     */
    public synchronized KontalkUser authenticate(byte[] keyData) {
        String fpr = importKey(keyData);

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

        synchronized (ctx) {
            // retrive old user key
            GnuPGKey oldKey = ctx.getKeyByFingerprint(oldFingerprint);
            if (oldKey != null && validate(oldKey) != null) {
                // old key is still valid, check for timestamp

                GnuPGKey newKey = ctx.getKeyByFingerprint(user.getFingerprint());
                if (newKey != null && newKey.getTimestamp().getTime() >= oldKey.getTimestamp().getTime()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Imports the given revoked key and checks if fingerprint matches and
     * key is revoked correctly.
     */
    public boolean revoked(byte[] keyData, String fingerprint) {
        String fpr = importKey(keyData);
        GnuPGKey key = ctx.getKeyByFingerprint(fpr);
        return key.isRevoked() && key.getFingerprint().equalsIgnoreCase(fingerprint);
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

    public synchronized byte[] exportKey(String fingerprint) throws IOException {
        GnuPGData data = ctx.createDataObject();
        ctx.export(fingerprint, 0, data);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.size());
        try {
            data.write(baos);
            return baos.toByteArray();
        }
        finally {
            try {
                baos.close();
            }
            catch (Exception e) {
            }
        }
    }

    // TODO this needs to be implemented in Java
    public synchronized byte[] signKey(byte[] keyData) throws IOException, PGPException {
        String fpr = importKey(keyData);

        if (fpr != null) {
            GnuPGKey key = ctx.getKeyByFingerprint(fpr);
            if (key != null) {
                StringBuilder cmd = new StringBuilder("gpg --yes --batch -u ")
                        .append(fingerprint)
                        .append(" --sign-key ")
                        .append(fpr);

                try {
                    System.out.println("CMD: <" + cmd + ">");
                    Runtime.getRuntime().exec(cmd.toString()).waitFor();
                }
                catch (InterruptedException e) {
                    // interrupted
                    throw new IOException("Interrupted");
                }

                return exportKey(fpr);
            }
        }

        throw new PGPException("Invalid key data");
    }

    String importKey(byte[] keyData) {
        synchronized (ctx) {
            GnuPGData data = ctx.createDataObject(keyData);
            String fpr = ctx.importKey(data);
            data.destroy();
            return fpr;
        }
    }

    /**
     * Verifies Kontalk legacy authentication tokens.
     * @param token the authentication token
     * @param fingerprint fingerprint of the legacy server
     * @return a Kontalk user instance if successful.
     */
    public KontalkUser verifyLegacyToken(byte[] token, String fingerprint) {
        synchronized (ctx) {
            GnuPGData signed = null;
            GnuPGData unused = null;
            GnuPGData plain = null;

            try {
                signed = ctx.createDataObject(token);
                unused = ctx.createDataObject();
                plain = ctx.createDataObject();
                ctx.verify(signed, unused, plain);

                String text = plain.toString();
                String[] parsed = text.split("\\|");
                if (parsed.length == 2 && parsed[1].equals(fingerprint)) {
                    String localpart = parsed[0].length() > 40 ? parsed[0].substring(0, 40) : parsed[0];
                    BareJID jid = BareJID.bareJIDInstanceNS(localpart, domain);
                    return new KontalkUser(jid, null);
                }

            }
            finally {
                if (signed != null)
                    signed.destroy();
                if (unused != null)
                    unused.destroy();
                if (plain != null)
                    plain.destroy();
            }
        }

        return null;
    }

    /** Initializes the keyring. */
    public static KontalkKeyring getInstance(String domain, String fingerprint) {
        KontalkKeyring instance = instances.get(domain);
        if (instances.get(domain) == null) {
            instance = new KontalkKeyring(domain, fingerprint);
            instances.put(domain, instance);
        }
        return instance;
    }

    /** Returns the singleton keyring instance. Need to call {@link #getInstance(String, String)} first! */
    public static KontalkKeyring getInstance(String domain) {
        return instances.get(domain);
    }
}
