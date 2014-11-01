package org.kontalk.xmppserver.x509;

import org.kontalk.xmppserver.Security;

import java.io.ByteArrayInputStream;
import java.security.NoSuchProviderException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;

/**
 * X.509 related utilities.
 * @author Daniele Ricci
 */
public class X509Utils {

    private X509Utils() {
    }

    public static Certificate loadCertificate(byte[] bytes) throws CertificateException, NoSuchProviderException {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        CertificateFactory cf = CertificateFactory.getInstance("X.509", Security.PROVIDER);
        return cf.generateCertificate(bais);
    }
}
