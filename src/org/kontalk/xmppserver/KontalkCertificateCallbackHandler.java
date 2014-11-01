package org.kontalk.xmppserver;

import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.kontalk.xmppserver.pgp.PGPUtils;
import org.kontalk.xmppserver.x509.SubjectPGPPublicKeyInfo;
import tigase.auth.callbacks.ValidateCertificateData;
import tigase.auth.impl.CertBasedCallbackHandler;
import tigase.auth.mechanisms.SaslEXTERNAL;
import tigase.cert.CertificateEntry;
import tigase.cert.CertificateUtil;
import tigase.util.TigaseStringprepException;
import tigase.xmpp.XMPPResourceConnection;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * A callback handler for Kontalk X.509 bridge certificates.
 * @author Daniele Ricci
 */
public class KontalkCertificateCallbackHandler extends CertBasedCallbackHandler {

    protected Logger log = Logger.getLogger(this.getClass().getName());

    private XMPPResourceConnection session;

    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        try {
            for (int i = 0; i < callbacks.length; i++) {
                if (log.isLoggable(Level.FINEST)) {
                    log.log(Level.FINEST, "Callback: {0}", callbacks[i].getClass().getSimpleName());
                }

                if (callbacks[i] instanceof ValidateCertificateData) {
                    ValidateCertificateData authCallback = ((ValidateCertificateData) callbacks[i]);

                    CertificateEntry certEntry = (CertificateEntry) session.getSessionData(SaslEXTERNAL.SESSION_AUTH_PEER_CERT);
                    if (certEntry != null) {
                        Certificate[] chain = certEntry.getCertChain();
                        if (chain != null && chain.length > 0) {
                            // take the last certificate in the chain
                            // it shouldn't matter since the peer certificate should be just one
                            Certificate peerCert = chain[chain.length - 1];

                            String jid = null;
                            try {
                                jid = verifyCertificate(peerCert);
                            }
                            catch (PGPException e) {
                                log.log(Level.WARNING, "Error verifying certificate", e);
                            }
                            catch (CertificateEncodingException e) {
                                log.log(Level.WARNING, "Error parsing certificate", e);
                            }

                            if (jid != null) {
                                authCallback.setAuthorized(true);
                                authCallback.setAuthorizedID(jid);
                                return;
                            }
                        }
                    }
                }
                else {
                    throw new UnsupportedCallbackException(callbacks[i], "Unrecognized Callback");
                }
            }

            // try standard certificate verification
            super.handle(callbacks);
        }
        catch (TigaseStringprepException e) {
            throw new RuntimeException(e);
        }
    }

    private String verifyCertificate(Certificate peerCert) throws TigaseStringprepException, PGPException, IOException, CertificateEncodingException {

        if (peerCert instanceof X509Certificate) {
            X509Certificate cert = (X509Certificate) peerCert;
            byte[] publicKeyExtData = cert.getExtensionValue(SubjectPGPPublicKeyInfo.OID.getId());

            if (publicKeyExtData != null) {
                SubjectPGPPublicKeyInfo keyInfo = SubjectPGPPublicKeyInfo.getInstance(publicKeyExtData);

                byte[] publicKeyData = keyInfo.getPublicKeyData().getBytes();

                // verify that the certificate public key matches the public key extension
                byte[] keyDataFromExtension = convertPublicKey(publicKeyData);
                byte[] keyDataFromCertificate = cert.getPublicKey().getEncoded();

                if (Arrays.equals(keyDataFromCertificate, keyDataFromExtension)) {
                    // TODO
                    String fingerprint = verifyPublicKey(publicKeyData);
                }
                else {
                    log.log(Level.WARNING, "Public key in extension does not match certificate public key");
                }
            }
            else {
                log.log(Level.WARNING, "No public key extension found in certificate");
            }
        }
        else {
            log.log(Level.WARNING, "Not an X.509 certificate: {0}", peerCert);
        }
        return null;
    }

    /** Converts a PGP public key into a public key. */
    private byte[] convertPublicKey(byte[] publicKeyData) throws PGPException, IOException {
        PGPPublicKey pk = PGPUtils.getMasterKey(publicKeyData);
        return PGPUtils.convertPublicKey(pk).getEncoded();
    }

    private String verifyPublicKey(byte[] publicKeyData) {
        // TODO
        return null;
    }

    @Override
    public void setSession(XMPPResourceConnection session) {
        super.setSession(session);
        this.session = session;
    }

    // TEST
    public static void main(String[] args) throws Exception {
        java.security.Security.insertProviderAt(new BouncyCastleProvider(), 1);

        String filename = args[0];
        CertificateEntry entry = CertificateUtil.loadCertificate(filename);
        Certificate cert = entry.getCertChain()[0];

        KontalkCertificateCallbackHandler c = new KontalkCertificateCallbackHandler();
        c.verifyCertificate(cert);
    }

}
