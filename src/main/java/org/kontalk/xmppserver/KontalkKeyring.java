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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.freiheit.gnupg.*;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.util.encoders.Hex;
import org.kontalk.xmppserver.pgp.PGPUtils;
import tigase.xmpp.BareJID;


/**
 * Kontalk keyring singleton.
 * @author Daniele Ricci
 */
public class KontalkKeyring {

    private static final String PARTITION_FILENAME_FORMAT = ".gnupg-%d";
    private static final int MAX_PARTITIONS = 16;

    private static final Map<String, KontalkKeyring> instances = new HashMap<String, KontalkKeyring>();

    private final String domain;
    private final String fingerprint;
    private GnuPGContext[] contexts;

    private final GnuPGContext globalContext;
    private final GnuPGKey secretKey;

    /** Use {@link #getInstance(String, String)} instead. */
    public KontalkKeyring(String domain, String fingerprint, String homeDir, int partitions) {
        this.domain = domain;
        this.fingerprint = fingerprint;

        if (homeDir != null && partitions > 0) {
            initPartitions(homeDir, partitions);
        }

        // use another keyring for the secret key
        globalContext = new GnuPGContext();
        globalContext.setArmor(false);
        if (fingerprint != null) {
            this.secretKey = globalContext.getKeyByFingerprint(fingerprint);
        }
        else {
            // not using a secret key (WHAT?)
            this.secretKey = null;
        }
    }

    private void initPartitions(String homeDir, int partitions) {
        if ((MAX_PARTITIONS % partitions) > 0)
            throw new IllegalArgumentException("partitions can only split the keyspace evenly by 16");

        contexts = new GnuPGContext[partitions];
        for (int i = 0; i < partitions; i++) {
            contexts[i] = new GnuPGContext();
            contexts[i].setEngineInfo(contexts[i].getProtocol(),
                    contexts[i].getFilename(),
                    homeDir + File.separator + makePartitionName(i));
            contexts[i].setArmor(false);
        }
    }

    private String makePartitionName(int partition) {
        return String.format(PARTITION_FILENAME_FORMAT, partition);
    }

    /** Returns the context to use for the given fingerprint according to keyspace partitioning. */
    private GnuPGContext getContext(String fingerprint) {
        return contexts != null ? contexts[getPartition(fingerprint)] : globalContext;
    }

    int getPartition(String fingerprint) {
        // this can't fail otherwise it's a bug
        // here we use 16 to indicate hexadecimal base
        int digit = Integer.parseInt(fingerprint.substring(0, 1), 16);
        return (int) Math.floor(digit / (MAX_PARTITIONS/contexts.length));
    }

    /**
     * Authenticates the given public key in Kontalk.
     * @param keyData public key data to check
     * @return a user instance with JID and public key fingerprint.
     */
    public KontalkUser authenticate(byte[] keyData) throws IOException, PGPException {
        String fpr = importKey(keyData);

        GnuPGKey key;
        final GnuPGContext ctx = getContext(fpr);
        synchronized (ctx) {
            key = ctx.getKeyByFingerprint(fpr);
        }

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
        GnuPGKey oldKey;
        GnuPGContext ctx = getContext(oldFingerprint);
        synchronized (ctx) {
            oldKey = ctx.getKeyByFingerprint(oldFingerprint);
        }
        if (oldKey != null && validate(oldKey) != null) {
            // old key is still valid, check for timestamp

            GnuPGKey newKey;
            ctx = getContext(user.getFingerprint());
            synchronized (ctx) {
                newKey = ctx.getKeyByFingerprint(user.getFingerprint());
            }
            if (newKey != null && newKey.getTimestamp().getTime() >= oldKey.getTimestamp().getTime()) {
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
        String fpr = importKey(keyData);
        GnuPGKey key;
        GnuPGContext ctx = getContext(fpr);
        synchronized (ctx) {
            key = ctx.getKeyByFingerprint(fpr);
        }
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

                GnuPGKey skey;
                synchronized (globalContext) {
                    try {
                        skey = globalContext.getKeyByFingerprint(sig.getKeyID());
                    }
                    catch (GnuPGException e) {
                        // ignoring - probably due to self signature
                        skey = null;
                    }
                }
                if (skey != null && skey.getFingerprint().equalsIgnoreCase(fingerprint))
                    return jid;
            }

        }

        return null;
    }

    private byte[] exportKeyGlobal(String fingerprint) throws IOException {
        return exportKeyInternal(fingerprint, globalContext);
    }

    public byte[] exportKey(String fingerprint) throws IOException {
        GnuPGContext ctx = getContext(fingerprint);
        return exportKeyInternal(fingerprint, ctx);
    }

    public byte[] exportKeyInternal(String fingerprint, GnuPGContext ctx) throws IOException {
        GnuPGData data;
        synchronized (ctx) {
            data = ctx.createDataObject();
            ctx.export(fingerprint, 0, data);
        }

        ByteArrayOutputStream baos = null;

        try {
            synchronized (ctx) {
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
                        .append(fingerprint)
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

                byte[] signedKey = exportKeyGlobal(fpr);
                // put key into its partition
                if (importKey(signedKey) != null)
                    return signedKey;
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

    String importKeyGlobal(byte[] keyData) {
        return importKeyInternal(keyData, globalContext);
    }

    String importKey(byte[] keyData) throws IOException, PGPException {
        PGPPublicKey pk = PGPUtils.getMasterKey(keyData);
        String fingerprint = Hex.toHexString(pk.getFingerprint());
        GnuPGContext ctx = getContext(fingerprint);

        return importKeyInternal(keyData, ctx);
    }

    private String importKeyInternal(byte[] keyData, GnuPGContext ctx) {
        String fpr;
        synchronized (ctx) {
            GnuPGData data = ctx.createDataObject(keyData);
            fpr = ctx.importKey(data);
            data.destroy();
        }
        return fpr;
    }

    /**
     * Verifies Kontalk legacy authentication tokens.
     * @param token the authentication token
     * @param fingerprint fingerprint of the legacy server
     * @return a Kontalk user instance if successful.
     */
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

    private static String getConfiguredHomeDir() {
        return System.getProperty("gnupg.home");
    }

    private static int getConfiguredPartitions() {
        try {
            return Integer.parseInt(System.getProperty("gnupg.partitions"));
        }
        catch (Exception e) {
            return 1;
        }
    }

    /** Initializes the keyring. */
    public static KontalkKeyring getInstance(String domain, String fingerprint) {
        KontalkKeyring instance = instances.get(domain);
        if (instances.get(domain) == null) {
            instance = new KontalkKeyring(domain, fingerprint, getConfiguredHomeDir(), getConfiguredPartitions());
            instances.put(domain, instance);
        }
        return instance;
    }

    /** Returns the singleton keyring instance. Need to call {@link #getInstance(String, String)} first! */
    public static KontalkKeyring getInstance(String domain) {
        return instances.get(domain);
    }
}
