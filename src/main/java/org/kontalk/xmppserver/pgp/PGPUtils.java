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

package org.kontalk.xmppserver.pgp;

import java.io.IOException;
import java.security.PublicKey;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyConverter;
import org.kontalk.xmppserver.Security;

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
        PGPObjectFactory reader = new PGPObjectFactory(publicKeyring, new BcKeyFingerprintCalculator());
        Object o = reader.nextObject();
        while (o != null) {
            if (o instanceof PGPPublicKeyRing)
                return (PGPPublicKeyRing) o;

            o = reader.nextObject();
        }

        throw new PGPException("invalid keyring data.");
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

}
