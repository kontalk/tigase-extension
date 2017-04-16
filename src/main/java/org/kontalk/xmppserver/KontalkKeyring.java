/*
 * Kontalk XMPP Tigase extension
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

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

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.kontalk.xmppserver.pgp.GnuPGInterface;
import org.kontalk.xmppserver.pgp.PGPLocalKeyring;
import org.kontalk.xmppserver.pgp.PGPUserID;
import org.kontalk.xmppserver.pgp.PGPUtils;
import tigase.xmpp.BareJID;

import javax.xml.bind.DatatypeConverter;
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
    private static final Map<String, KontalkKeyring> instances = new HashMap<>();

    private final String domain;
    private final PGPLocalKeyring keyring;

    private final PGPPublicKey secretMasterKey;
    private final PGPPublicKeyRing secretPublicKeyring;
    private final String secretKeyFingerprint;

    /** Use {@link #getInstance(String)} instead. */
    private KontalkKeyring(String domain, String secretPrivateKeyFile, String secretPublicKeyFile, String keyring) throws IOException, PGPException {
        this.domain = domain;
        this.keyring = new PGPLocalKeyring(keyring);

        // import into GnuPG
        GnuPGInterface.getInstance().importKey(secretPrivateKeyFile);
        GnuPGInterface.getInstance().importKey(secretPublicKeyFile);

        // calculate secret key fingerprint for signing
        secretPublicKeyring = PGPUtils.readPublicKeyring(new FileInputStream(secretPublicKeyFile));
        secretMasterKey = PGPUtils.getMasterKey(secretPublicKeyring);
        secretKeyFingerprint = PGPUtils.getFingerprint(secretMasterKey);
    }

    public PGPPublicKeyRing getSecretPublicKey() {
        return secretPublicKeyring;
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

    // TODO this needs to be implemented in Java
    public byte[] signKey(byte[] keyData) throws IOException, PGPException {
        return GnuPGInterface.getInstance().signKey(keyData, secretKeyFingerprint);
    }

    public byte[] signData(byte[] data) throws IOException, PGPException {
        return GnuPGInterface.getInstance().signData(data, secretKeyFingerprint);
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
