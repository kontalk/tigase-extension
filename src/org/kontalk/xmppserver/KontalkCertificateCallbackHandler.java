package org.kontalk.xmppserver;

import java.io.IOException;
import java.security.cert.Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;

import tigase.auth.callbacks.ValidateCertificateData;
import tigase.auth.impl.CertBasedCallbackHandler;
import tigase.auth.mechanisms.SaslEXTERNAL;
import tigase.cert.CertificateEntry;
import tigase.util.TigaseStringprepException;
import tigase.xmpp.XMPPResourceConnection;


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

                            String jid = verifyCertificate(peerCert);
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

    private String verifyCertificate(Certificate peerCert) throws TigaseStringprepException {
        // TODO
        return null;
    }

    @Override
    public void setSession(XMPPResourceConnection session) {
        this.session = session;
    }

}
