package org.kontalk.xmppserver.x509;


import org.bouncycastle.openpgp.PGPException;
import org.kontalk.xmppserver.Security;
import org.kontalk.xmppserver.pgp.PGPUtils;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

/**
 * X.509 bridge certificate utilities.
 * @author Daniele Ricci
 */
public class X509Utils {

    static {
        Security.init();
    }

    private X509Utils() {
    }

    public static byte[] getPublicKeyBlock(X509Certificate certificate) throws IOException {
        byte[] publicKeyExtData = certificate.getExtensionValue(SubjectPGPPublicKeyInfo.OID.getId());

        if (publicKeyExtData != null) {
            SubjectPGPPublicKeyInfo keyInfo = SubjectPGPPublicKeyInfo.getInstance(publicKeyExtData);

            return keyInfo.getPublicKeyData().getBytes();
        }

        return null;
    }

    public static byte[] getMatchingPublicKey(X509Certificate certificate) throws PGPException, IOException {
        byte[] publicKeyData = X509Utils.getPublicKeyBlock(certificate);
        if (publicKeyData != null) {
            byte[] keyDataFromExtension = PGPUtils.convertPublicKey(publicKeyData);
            byte[] keyDataFromCertificate = certificate.getPublicKey().getEncoded();

            if (Arrays.equals(keyDataFromCertificate, keyDataFromExtension)) {
                return publicKeyData;
            }
        }
        return null;
    }

}
