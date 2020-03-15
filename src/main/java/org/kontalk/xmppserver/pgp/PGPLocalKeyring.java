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

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKeyRing;

import java.io.IOException;
import java.io.InputStream;


public interface PGPLocalKeyring {

    /** Returns the public key represented by the given fingerprint. */
    PGPPublicKeyRing getKey(String fingerprint) throws IOException, PGPException;

    /** Imports the given key. */
    PGPPublicKeyRing importKey(InputStream in) throws IOException, PGPException;

    /** Imports the given key. */
    PGPPublicKeyRing importKey(byte[] data) throws IOException, PGPException;

    void close() throws IOException;

}
