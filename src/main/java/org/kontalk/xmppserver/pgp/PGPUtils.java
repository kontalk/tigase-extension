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

package org.kontalk.xmppserver.pgp;

import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyConverter;
import org.kontalk.xmppserver.Security;
import tigase.xmpp.BareJID;

import java.io.IOException;
import java.io.InputStream;
import java.security.PublicKey;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PGP related utilities.
 * @author Daniele Ricci
 */
public class PGPUtils {

    static {
        Security.init();
    }

    /** Singleton for converting a PGP key to a JCA key. */
    private static JcaPGPKeyConverter sKeyConverter;

    private static final Pattern PATTERN_UID_FULL = Pattern.compile("^(.*) \\((.*)\\) <(.*)>$");
    private static final Pattern PATTERN_UID_NO_COMMENT = Pattern.compile("^(.*) <(.*)>$");

    private PGPUtils() {
    }

    /** Returns the first master key found in the given public keyring. */
    public static PGPPublicKey getMasterKey(PGPPublicKeyRing publicKeyring) {
        @SuppressWarnings("unchecked")
        Iterator<PGPPublicKey> iter = publicKeyring.getPublicKeys();
        while (iter.hasNext()) {
            PGPPublicKey pk = iter.next();
            if (pk.isMasterKey())
                return pk;
        }

        return null;
    }

    /** Returns the first master key found in the given public keyring. */
    public static PGPPublicKey getMasterKey(byte[] publicKeyring) throws IOException, PGPException {
        return getMasterKey(readPublicKeyring(publicKeyring));
    }

    public static PGPPublicKeyRing readPublicKeyring(byte[] publicKeyring) throws IOException, PGPException {
        return readPublicKeyring(new PGPObjectFactory(publicKeyring, new BcKeyFingerprintCalculator()));
    }

    public static PGPPublicKeyRing readPublicKeyring(InputStream publicKeyring) throws IOException, PGPException {
        return readPublicKeyring(new PGPObjectFactory(publicKeyring, new BcKeyFingerprintCalculator()));
    }

    private static PGPPublicKeyRing readPublicKeyring(PGPObjectFactory reader) throws IOException, PGPException {
        Object o = reader.nextObject();
        while (o != null) {
            if (o instanceof PGPPublicKeyRing)
                return (PGPPublicKeyRing) o;

            o = reader.nextObject();
        }

        throw new PGPException("invalid keyring data.");
    }

    public static PGPSecretKeyRing readSecretKeyring(byte[] secretKeyring) throws IOException, PGPException {
        return readSecretKeyring(new PGPObjectFactory(secretKeyring, new BcKeyFingerprintCalculator()));
    }

    public static PGPSecretKeyRing readSecretKeyring(InputStream publicKeyring) throws IOException, PGPException {
        return readSecretKeyring(new PGPObjectFactory(publicKeyring, new BcKeyFingerprintCalculator()));
    }

    private static PGPSecretKeyRing readSecretKeyring(PGPObjectFactory reader) throws IOException, PGPException {
        Object o = reader.nextObject();
        while (o != null) {
            if (o instanceof PGPSecretKeyRing)
                return (PGPSecretKeyRing) o;

            o = reader.nextObject();
        }

        throw new PGPException("invalid keyring data.");
    }

    public static boolean isExpired(PGPPublicKey key) {
        // TODO
        return false;
    }

    /** Converts a PGP public key into a public key. */
    public static byte[] convertPublicKey(byte[] publicKeyData) throws PGPException, IOException {
        PGPPublicKey pk = PGPUtils.getMasterKey(publicKeyData);
        return convertPublicKey(pk).getEncoded();
    }

    public static PublicKey convertPublicKey(PGPPublicKey key) throws PGPException {
        ensureKeyConverter();
        return sKeyConverter.getPublicKey(key);
    }

    private static void ensureKeyConverter() {
        if (sKeyConverter == null)
            sKeyConverter = new JcaPGPKeyConverter().setProvider(org.kontalk.xmppserver.Security.PROVIDER);
    }

    public static boolean findValidKeySignature(PGPPublicKey key, String uid, PGPPublicKey signerKey) throws PGPException {
        PGPSignature valid = null;
        long keyId = signerKey.getKeyID();

        @SuppressWarnings("unchecked")
        Iterator<PGPSignature> sigs = key.getSignaturesForID(uid);
        while (sigs != null && sigs.hasNext()) {
            PGPSignature sig = sigs.next();
            if (sig.getKeyID() == keyId && verifyKeySignature(key, sig, signerKey, uid)) {
                if (sig.getSignatureType() == PGPSignature.DEFAULT_CERTIFICATION) {
                    if (valid == null || valid.getCreationTime().before(sig.getCreationTime()))
                        valid = sig;
                }
                // TODO else if (sig.getSignatureType() == PGPSignature.CERTIFICATION_REVOCATION) ...
            }
        }

        return valid != null;
    }

    private static boolean verifyKeySignature(PGPPublicKey publicKey, PGPSignature sig, PGPPublicKey signerKey, String uid) throws PGPException {
        sig.init(new BcPGPContentVerifierBuilderProvider(), signerKey);
        return sig.verifyCertification(uid, publicKey);
    }

    public static boolean isKeyNewer(PGPPublicKeyRing oldKey, PGPPublicKeyRing newKey) {
        return getMasterKey(oldKey).getCreationTime()
                .before(getMasterKey(newKey).getCreationTime());
    }

    public static PGPUserID findUserID(PGPPublicKey key, String domain) {
        @SuppressWarnings("unchecked")
        Iterator<String> iter = key.getUserIDs();
        while (iter.hasNext()) {
            String _uid = iter.next();
            PGPUserID uid = parseUserID(_uid);
            if (uid != null) {
                String email = uid.getEmail();
                if (email != null) {
                    BareJID jid = BareJID.bareJIDInstanceNS(email);
                    if (domain.equalsIgnoreCase(jid.getDomain())) {
                        return uid;
                    }
                }
            }
        }
        return null;
    }

    public static PGPUserID parseUserID(PGPPublicKey key) {
        return parseUserID((String) key.getUserIDs().next());
    }

    public static PGPUserID parseUserID(String uid) {
        Matcher match;

        match = PATTERN_UID_FULL.matcher(uid);
        while (match.find()) {
            if (match.groupCount() >= 3) {
                String name = match.group(1);
                String comment = match.group(2);
                String email = match.group(3);
                return new PGPUserID(name, comment, email);
            }
        }

        // try again without comment
        match = PATTERN_UID_NO_COMMENT.matcher(uid);
        while (match.find()) {
            if (match.groupCount() >= 2) {
                String name = match.group(1);
                String email = match.group(2);
                return new PGPUserID(name, null, email);
            }
        }

        // no match found
        return null;
    }

    private static String bytesToHex(byte[] data) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < data.length; i++) {
            int halfbyte = (data[i] >>> 4) & 0x0F;
            int two_halfs = 0;
            do {
                if ((0 <= halfbyte) && (halfbyte <= 9))
                    buf.append((char) ('0' + halfbyte));
                else
                    buf.append((char) ('a' + (halfbyte - 10)));
                halfbyte = data[i] & 0x0F;
            } while(two_halfs++ < 1);
        }
        return buf.toString();
    }

    public static String getFingerprint(PGPPublicKeyRing publicKey) {
        return bytesToHex(getMasterKey(publicKey).getFingerprint()).toUpperCase(Locale.US);
    }

    public static String getFingerprint(PGPPublicKey publicKey) {
        return bytesToHex(publicKey.getFingerprint()).toUpperCase(Locale.US);
    }

}
