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

package org.kontalk.xmppserver.pgp;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKeyRing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;


/**
 * Since the whole gpgme-gnupg stuff is often source of bugs
 * and incompatibility issues, here is some gpg2 invoking methods
 * for just the basic stuff we need.
 * @author Daniele Ricci
 */
public class GnuPGInterface {
    private static GnuPGInterface instance;

    /** GnuPG executable (must be in PATH). */
    private static final String GPG_EXEC = "gpg2";

    public static GnuPGInterface getInstance() {
        if (instance == null)
            instance = new GnuPGInterface();
        return instance;
    }

    private GnuPGInterface() {
    }

    private int invoke(String... args) throws IOException {
        return invoke(null, null, args);
    }

    private int invoke(byte[] standardInput, String... args) throws IOException {
        return invoke(standardInput, null, args);
    }

    private int invoke(byte[] standardInput, OutputStream output, String... args) throws IOException {
        try {
            String[] procArgs = new String[args.length + 1];
            procArgs[0] = GPG_EXEC;
            System.arraycopy(args, 0, procArgs, 1, args.length);
            Process p = new ProcessBuilder(procArgs).start();
            if (standardInput != null) {
                p.getOutputStream().write(standardInput);
                p.getOutputStream().close();
            }
            if (output != null)
                IOUtils.copy(p.getInputStream(), output);

            return p.waitFor();
        }
        catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    public void importKey(String filename) throws IOException, PGPException {
        synchronized (this) {
            if (invoke("-q", "--yes", "--batch", "--import", filename) != 0)
                throw new PGPException("error importing key");
        }
    }

    public void importKey(byte[] keyData) throws IOException, PGPException {
        synchronized (this) {
            if (invoke(keyData, "-q", "--yes", "--batch", "--ignore-time-conflict", "--import") != 0)
                throw new PGPException("error importing key");
        }
    }

    private byte[] exportKey(String keyId) throws IOException, PGPException {
        synchronized (this) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (invoke(null, output, "--export", keyId) != 0)
                throw new PGPException("error exporting key");
            return output.toByteArray();
        }
    }

    private void deleteKey(String keyId) throws IOException, PGPException {
        synchronized (this) {
            if (invoke("--yes", "--batch", "--delete-key", keyId) != 0)
                throw new PGPException("error deleting key");
        }
    }

    public byte[] signKey(byte[] keyData, String signKeyId) throws IOException, PGPException {
        synchronized (this) {
            importKey(keyData);
            // discover key fingerprint
            PGPPublicKeyRing masterKey = PGPUtils.readPublicKeyring(keyData);
            if (masterKey == null)
                throw new PGPException("invalid key data");

            String fingerprint = PGPUtils.getFingerprint(masterKey);
            if (invoke("--yes", "--batch", "-u", signKeyId, "--sign-key", fingerprint) != 0)
                throw new PGPException("error signing key");

            byte[] signedKey = exportKey(fingerprint);

            // delete the imported key
            deleteKey(fingerprint);

            return signedKey;
        }
    }

    public byte[] signData(byte[] data, String signKeyId) throws IOException, PGPException {
        synchronized (this) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (invoke(data, output, "--yes", "--batch", "--sign", "-u", signKeyId) != 0)
                throw new PGPException("error signing data");
            return output.toByteArray();
        }
    }

}
