/*
 * Kontalk XMPP Tigase extension
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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

import com.freiheit.gnupg.GnuPGContext;
import com.freiheit.gnupg.GnuPGData;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.kontalk.xmppserver.pgp.PGPLocalKeyring;
import org.kontalk.xmppserver.pgp.PGPUserID;
import org.kontalk.xmppserver.pgp.PGPUtils;
import tigase.xmpp.BareJID;

import javax.xml.bind.DatatypeConverter;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


/**
 * Kontalk keyring singleton.
 * @author Daniele Ricci
 */
public class KontalkKeyring {
    private static final Map<String, KontalkKeyring> instances = new HashMap<String, KontalkKeyring>();

    private final String domain;
    private final PGPLocalKeyring keyring;

    private final PGPSecretKeyRing secretPrivateKey;
    private final PGPPublicKeyRing secretPublicKey;
    private final PGPPublicKey secretMasterKey;

    // used only for signing.
    private final GnuPGContext globalContext;

    /** Use {@link #getInstance(String)} instead. */
    public KontalkKeyring(String domain, String secretPrivateKeyFile, String secretPublicKeyFile, String keyring) throws IOException, PGPException {
        this.domain = domain;
        this.keyring = new PGPLocalKeyring(keyring);

        secretPrivateKey = PGPUtils.readSecretKeyring(new FileInputStream(secretPrivateKeyFile));
        secretPublicKey = PGPUtils.readPublicKeyring(new FileInputStream(secretPublicKeyFile));
        secretMasterKey = PGPUtils.getMasterKey(secretPublicKey);

        globalContext = new GnuPGContext();
        globalContext.setArmor(false);
    }

    /**
     * Authenticates the given public key in Kontalk.
     * @param keyData public key data to check
     * @return a user instance with JID and public key fingerprint.
     */
    public KontalkUser authenticate(byte[] keyData) throws IOException, PGPException {
        PGPPublicKeyRing key = keyring.importKey(keyData);
        BareJID jid = validate(key);
        return jid != null ? new KontalkUser(jid, PGPUtils.getFingerprint(key)) : null;
    }

    /**
     * Post-authentication step: verifies that the given user is allowed to
     * login by checking the old key.
     * @param user user object returned by {@link #authenticate}
     * @param oldFingerprint old key fingerprint, null if none present
     * @return true if the new key can be accepted.
     */
    public boolean postAuthenticate(KontalkUser user, String oldFingerprint) throws IOException, PGPException {
        if (oldFingerprint == null || oldFingerprint.equalsIgnoreCase(user.getFingerprint())) {
            // no old fingerprint or same fingerprint -- access granted
            return true;
        }

        PGPPublicKeyRing oldKey = keyring.getKey(oldFingerprint);
        if (oldKey != null && validate(oldKey) != null) {
            // old key is still valid, check for timestamp

            PGPPublicKeyRing newKey = keyring.getKey(user.getFingerprint());
            if (newKey != null && PGPUtils.isKeyNewer(oldKey, newKey)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Imports the given revoked key and checks if fingerprint matches and
     * key is revoked correctly.
     */
    public boolean revoked(byte[] keyData, String fingerprint) throws IOException, PGPException {
        PGPPublicKeyRing key = keyring.importKey(keyData);
        PGPPublicKey masterKey = PGPUtils.getMasterKey(key);

        return masterKey != null && PGPUtils.isRevoked(masterKey) &&
                Arrays.equals(DatatypeConverter.parseHexBinary(fingerprint), masterKey.getFingerprint());
    }

    /** Validates the given key for expiration, revocation and signature by the server. */
    private BareJID validate(PGPPublicKeyRing key) throws PGPException {
        PGPPublicKey masterKey = PGPUtils.getMasterKey(key);
        if (masterKey == null || PGPUtils.isRevoked(masterKey) || PGPUtils.isExpired(masterKey))
            return null;

        PGPUserID uid = PGPUtils.findUserID(masterKey, domain);
        if (uid != null) {
            if (PGPUtils.findValidKeySignature(masterKey, uid.toString(), secretMasterKey)) {
                return BareJID.bareJIDInstanceNS(uid.getEmail());
            }
        }

        return null;
    }

    public byte[] exportKey(String fingerprint) throws IOException, PGPException {
        PGPPublicKeyRing pk = keyring.getKey(fingerprint);
        return (pk != null) ? pk.getEncoded() : null;
    }

    private String importKeyGlobal(byte[] keyData) {
        String fpr;
        synchronized (globalContext) {
            GnuPGData data = globalContext.createDataObject(keyData);
            fpr = globalContext.importKey(data);
            data.destroy();
        }
        return fpr;
    }

    public byte[] exportKeyGlobal(String fingerprint) throws IOException {
        GnuPGData data;
        synchronized (globalContext) {
            data = globalContext.createDataObject();
            globalContext.export(fingerprint, 0, data);
        }

        ByteArrayOutputStream baos = null;

        try {
            synchronized (globalContext) {
                baos = new ByteArrayOutputStream(data.size());
                data.write(baos);
            }
            return baos.toByteArray();
        }
        finally {
            try {
                if (baos != null)
                    baos.close();
            }
            catch (IOException ignored) {
            }
        }
    }

    // TODO this needs to be implemented in Java
    public byte[] signKey(byte[] keyData) throws IOException, PGPException {
        String fpr = importKeyGlobal(keyData);

        if (fpr != null) {
            try {
                StringBuilder cmd = new StringBuilder("gpg2 --yes --batch -u ")
                        .append(PGPUtils.getFingerprint(secretMasterKey))
                        .append(" --sign-key ")
                        .append(fpr);

                try {
                    System.out.println("CMD: <" + cmd + ">");
                    synchronized (globalContext) {
                        Runtime.getRuntime().exec(cmd.toString()).waitFor();
                    }
                }
                catch (InterruptedException e) {
                    // interrupted
                    throw new IOException("Interrupted");
                }

                return exportKeyGlobal(fpr);
            }
            finally {
                // remove key from global context
                StringBuilder cmd = new StringBuilder("gpg2 --yes --batch --delete-key ")
                        .append(fpr);

                try {
                    System.out.println("CMD: <" + cmd + ">");
                    synchronized (globalContext) {
                        Runtime.getRuntime().exec(cmd.toString()).waitFor();
                    }
                }
                catch (InterruptedException e) {
                    // ignored
                }
            }
        }

        throw new PGPException("Invalid key data");
    }

    /**
     * Verifies Kontalk legacy authentication tokens.
     * @param token the authentication token
     * @param fingerprint fingerprint of the legacy server
     * @return a Kontalk user instance if successful.
     * @deprecated Legacy token support will be dropped soon.
     */
    @Deprecated
    public KontalkUser verifyLegacyToken(byte[] token, String fingerprint) {
        synchronized (globalContext) {
            GnuPGData signed = null;
            GnuPGData unused = null;
            GnuPGData plain = null;

            try {
                signed = globalContext.createDataObject(token);
                unused = globalContext.createDataObject();
                plain = globalContext.createDataObject();
                globalContext.verify(signed, unused, plain);

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

    public void close() throws IOException {
        keyring.close();
        synchronized (instances) {
            instances.remove(domain);
        }
    }

    private static String getConfiguredKeyringPath() {
        return System.getProperty("pgp.keyring");
    }

    private static String getConfiguredSecretPublicKeyPath() {
        return System.getProperty("pgp.secret.public");
    }

    private static String getConfiguredSecretPrivateKeyPath() {
        return System.getProperty("pgp.secret.private");
    }

    /** Initializes the keyring. */
    public static KontalkKeyring getInstance(String domain) throws IOException, PGPException {
        synchronized (instances) {
            KontalkKeyring instance = instances.get(domain);
            if (instances.get(domain) == null) {
                instance = new KontalkKeyring(domain, getConfiguredSecretPrivateKeyPath(),
                        getConfiguredSecretPublicKeyPath(), getConfiguredKeyringPath());
                instances.put(domain, instance);
            }
            return instance;
        }
    }

}
