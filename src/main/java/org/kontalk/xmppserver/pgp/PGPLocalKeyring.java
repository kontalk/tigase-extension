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

import fm.last.commons.kyoto.DbType;
import fm.last.commons.kyoto.KyotoDb;
import fm.last.commons.kyoto.factory.KyotoDbBuilder;
import fm.last.commons.kyoto.factory.Mode;
import fm.last.commons.lang.units.JedecByteUnit;
import org.apache.log4j.BasicConfigurator;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKeyRing;

import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;


public class PGPLocalKeyring {
    static {
        BasicConfigurator.configure();
    }

    private final KyotoDb db;

    public PGPLocalKeyring(String filename) throws IOException {
        File dbFile = DbType.FILE_HASH.createFile(filename);
        db = new KyotoDbBuilder(dbFile)
                .modes(Mode.CREATE, Mode.READ_WRITE)
                .buckets(100000)
                .memoryMapSize(5, JedecByteUnit.MEGABYTES)
                .buildAndOpen();
        Runtime.getRuntime().addShutdownHook(new ShutdownThread());
    }

    /** Returns the public key represented by the given fingerprint. */
    public PGPPublicKeyRing getKey(String fingerprint) throws IOException, PGPException {
        return getKey(fingerprintKey(fingerprint));
    }

    /** Imports the given key. */
    public PGPPublicKeyRing importKey(InputStream in) throws IOException, PGPException {
        return importKey(PGPUtils.readPublicKeyring(in));
    }

    /** Imports the given key. */
    public PGPPublicKeyRing importKey(byte[] data) throws IOException, PGPException {
        return importKey(PGPUtils.readPublicKeyring(data));
    }

    private PGPPublicKeyRing importKey(PGPPublicKeyRing keyring) throws IOException, PGPException {
        String fpr = PGPUtils.getFingerprint(keyring);
        PGPPublicKeyRing newring;
        PGPPublicKeyRing oldring = getKey(fpr);
        if (oldring != null) {
            newring = PGPUtils.merge(oldring, keyring);
        }
        else {
            newring = keyring;
        }

        db.set(fingerprintKey(fpr), newring.getEncoded());
        return newring;
    }

    public void close() throws IOException {
        db.close();
    }

    // TODO signKey method?

    private PGPPublicKeyRing getKey(byte[] fingerprint) throws IOException, PGPException {
        byte[] data = db.get(fingerprint);
        if (data != null)
            return PGPUtils.readPublicKeyring(data);

        return null;
    }

    private byte[] fingerprintKey(String s) {
        return DatatypeConverter.parseHexBinary(s);
    }

    private class ShutdownThread extends Thread {

        ShutdownThread() {
            super();
            setName("PGPLocalKeyRingShutdownThread");
        }

        @Override
        public void run() {
            try {
                db.close();
            }
            catch (Exception ignored) {
            }
        }
    }

}
