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

import com.sleepycat.je.*;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.kontalk.xmppserver.util.Utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;


public class BerkeleyPGPLocalKeyring implements PGPLocalKeyring {

    private final Environment env;
    private final Database db;

    public BerkeleyPGPLocalKeyring(String filename) throws IOException {
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);

        File envHome = new File(filename);
        if (!envHome.isDirectory() && !envHome.mkdirs()) {
            throw new IOException("Unable to create environment home");
        }
        env = new Environment(envHome, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setTransactional(false);
        dbConfig.setSortedDuplicates(false);
        db = env.openDatabase(null, "keyring", dbConfig);

        Runtime.getRuntime().addShutdownHook(new ShutdownThread());
    }

    @Override
    public PGPPublicKeyRing getKey(String fingerprint) throws IOException, PGPException {
        return getKey(fingerprintKey(fingerprint));
    }

    @Override
    public PGPPublicKeyRing importKey(InputStream in) throws IOException, PGPException {
        return importKey(PGPUtils.readPublicKeyring(in));
    }

    @Override
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

        try {
            db.put(null, new DatabaseEntry(fingerprintKey(fpr)), new DatabaseEntry(newring.getEncoded()));
        }
        catch (DatabaseException e) {
            throw new IOException("Database error", e);
        }
        return newring;
    }

    @Override
    public void close() throws IOException {
        db.close();
    }

    // TODO signKey method?

    private PGPPublicKeyRing getKey(byte[] fingerprint) throws IOException, PGPException {
        DatabaseEntry data = new DatabaseEntry();
        try {
            if (db.get(null, new DatabaseEntry(fingerprint), data, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
                return PGPUtils.readPublicKeyring(data.getData());
            }
        }
        catch (DatabaseException e) {
            throw new IOException("Database error", e);
        }

        return null;
    }

    private byte[] fingerprintKey(String s) {
        return Utils.parseHexBinary(s);
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
                env.close();
            }
            catch (Exception ignored) {
            }
        }
    }

}
