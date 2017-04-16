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

package org.kontalk.xmppserver.x509;

import org.bouncycastle.asn1.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;


/**
 * A custom X.509 extension for a PGP public key.
 * @author Daniele Ricci
 */
public class SubjectPGPPublicKeyInfo extends ASN1Object {

    // based on UUID 24e844a0-6cbc-11e3-8997-0002a5d5c51b
    public static final ASN1ObjectIdentifier OID = new ASN1ObjectIdentifier("2.25.49058212633447845622587297037800555803");

    private DERBitString keyData;

    public SubjectPGPPublicKeyInfo(ASN1Encodable publicKey) throws IOException {
        keyData = new DERBitString(publicKey);
    }

    public SubjectPGPPublicKeyInfo(byte[] publicKey) {
        keyData = new DERBitString(publicKey);
    }

    private SubjectPGPPublicKeyInfo(DERBitString bitstring) {
        keyData = bitstring;
    }

    public DERBitString getPublicKeyData()
    {
        return keyData;
    }

    @Override
    public ASN1Primitive toASN1Primitive() {
        return keyData;
    }

    public static SubjectPGPPublicKeyInfo getInstance(byte[] bytes) throws IOException {
        ASN1Primitive obj = toDERObject(bytes);
        if (obj instanceof DEROctetString) {
            obj = toDERObject(((DEROctetString) obj).getOctets());
            if (obj instanceof DERBitString) {
                return getInstance((DERBitString) obj);
            }
        }

        return null;
    }

    public static SubjectPGPPublicKeyInfo getInstance(DERBitString bitstring) throws IOException {
        return new SubjectPGPPublicKeyInfo(bitstring);
    }

    private static ASN1Primitive toDERObject(byte[] data) throws IOException
    {
        ASN1InputStream asnInputStream = null;
        try {
            ByteArrayInputStream inStream = new ByteArrayInputStream(data);
            asnInputStream = new ASN1InputStream(inStream);

            return asnInputStream.readObject();
        }
        finally {
            try {
                asnInputStream.close();
            }
            catch (Exception e) {
            }
        }
    }

}
