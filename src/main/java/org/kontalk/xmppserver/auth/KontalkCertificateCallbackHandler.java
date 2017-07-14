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

package org.kontalk.xmppserver.auth;

import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.bouncycastle.openpgp.PGPException;
import org.kontalk.xmppserver.KontalkKeyring;
import org.kontalk.xmppserver.KontalkUser;
import org.kontalk.xmppserver.Security;
import org.kontalk.xmppserver.pgp.PGPUtils;

import org.kontalk.xmppserver.x509.X509Utils;
import tigase.auth.DomainAware;
import tigase.auth.PluginSettingsAware;
import tigase.auth.callbacks.ValidateCertificateData;
import tigase.auth.impl.CertBasedCallbackHandler;
import tigase.auth.mechanisms.SaslEXTERNAL;
import tigase.cert.CertificateEntry;
import tigase.cert.CertificateUtil;
import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.util.TigaseStringprepException;
import tigase.xmpp.XMPPResourceConnection;


/**
 * A callback handler for Kontalk X.509 bridge certificates.
 * @author Daniele Ricci
 */
public class KontalkCertificateCallbackHandler extends CertBasedCallbackHandler implements DomainAware, PluginSettingsAware {

    static {
        try {
            Class.forName(PGPUtils.class.getName());
        }
        catch (ClassNotFoundException ignored) {
        }
    }

    protected Logger log = Logger.getLogger(this.getClass().getName());

    private XMPPResourceConnection session;
    private String domain;
    private Map<String, Object> settings;

    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        try {
            for (int i = 0; i < callbacks.length; i++) {
                if (log.isLoggable(Level.FINEST)) {
                    log.log(Level.FINEST, "Callback: {0}", callbacks[i].getClass().getSimpleName());
                }

                if (callbacks[i] instanceof ValidateCertificateData) {
                    ValidateCertificateData authCallback = ((ValidateCertificateData) callbacks[i]);

                    Certificate peerCert = (Certificate) session.getSessionData(SaslEXTERNAL.PEER_CERTIFICATE_KEY);
                    if (peerCert != null) {
                        KontalkUser user = null;
                        try {
                            user = verifyCertificate(peerCert);
                        }
                        catch (PGPException e) {
                            log.log(Level.WARNING, "Error verifying certificate", e);
                        }
                        catch (CertificateEncodingException e) {
                            log.log(Level.WARNING, "Error parsing certificate", e);
                        }

                        if (user != null) {
                            authCallback.setAuthorized(true);
                            authCallback.setAuthorizedID(user.getJID().toString());
                            return;
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

    private KontalkUser verifyCertificate(Certificate peerCert) throws TigaseStringprepException, PGPException, IOException, CertificateEncodingException {

        if (peerCert instanceof X509Certificate) {
            X509Certificate cert = (X509Certificate) peerCert;
            byte[] publicKeyData = X509Utils.getPublicKeyBlock(cert);

            if (publicKeyData != null) {
                // verify that the certificate public key matches the public key extension
                byte[] keyDataFromExtension = PGPUtils.convertPublicKey(publicKeyData);
                byte[] keyDataFromCertificate = cert.getPublicKey().getEncoded();

                if (Arrays.equals(keyDataFromCertificate, keyDataFromExtension)) {
                    return verifyPublicKey(publicKeyData);
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

    private KontalkUser verifyPublicKey(byte[] publicKeyData) throws IOException, PGPException {
        KontalkKeyring keyring = getKeyring();
        KontalkUser user = keyring.authenticate(publicKeyData);
        if (user == null) {
            // authentication failed
            return null;
        }

        // retrive any old key fingerprint from storage
        String oldFingerprint = null;
        boolean userExists;
        try {
            oldFingerprint = KontalkAuth.getUserFingerprint(session, user.getJID());
            userExists = true;
        }
        catch (UserNotFoundException e) {
            // user not found - that's ok, we will create it later
            userExists = false;
        }
        catch (TigaseDBException e) {
            log.log(Level.WARNING, "no access to storage for old fingerpint", e);
            return null;
        }

        if (keyring.postAuthenticate(user, oldFingerprint)) {
            if (!userExists) {
                try {
                    KontalkAuth.getUserRepository().addUser(user.getJID());
                }
                catch (TigaseDBException dbe) {
                    log.log(Level.WARNING, "unable to create user " + user.getJID(), dbe);
                    return null;
                }
            }

            // store latest fingerprint
            try {
                KontalkAuth.setUserFingerprint(session, user.getJID(), user.getFingerprint());
            }
            catch (TigaseDBException e) {
                log.log(Level.WARNING, "no access to storage for storing fingerprint", e);
                return null;
            }
            return user;
        }

        return null;
    }

    @Override
    public void setSession(XMPPResourceConnection session) {
        super.setSession(session);
        this.session = session;
    }

    @Override
    public void setDomain(String domain) {
        this.domain = domain;
    }

    @Override
    public void setPluginSettings(Map<String, Object> settings) {
        this.settings = settings;
    }

    private KontalkKeyring getKeyring() throws IOException, PGPException {
        return KontalkKeyring.getInstance(domain);
    }

    // TEST
    public static void main(String[] args) throws Exception {
        System.out.println("Using provider " + Security.PROVIDER);

        String filename = args[0];
        CertificateEntry entry = CertificateUtil.loadCertificate(filename);
        Certificate cert = entry.getCertChain()[0];

        KontalkCertificateCallbackHandler c = new KontalkCertificateCallbackHandler();
        c.setDomain("prime.kontalk.net");
        Map<String, Object> settings = new HashMap<String, Object>();
        settings.put("fingerprint", "37D0E678CDD19FB9B182B3804C9539B401F8229C");
        c.setSession(new XMPPResourceConnection(null, null, null, null));
        c.setPluginSettings(settings);
        c.verifyCertificate(cert);
    }

}
