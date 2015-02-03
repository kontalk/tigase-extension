/*
 * Kontalk XMPP Tigase extension
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.xmppserver;

import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.util.encoders.Hex;
import org.kontalk.xmppserver.auth.KontalkAuth;
import org.kontalk.xmppserver.pgp.PGPUserID;
import org.kontalk.xmppserver.pgp.PGPUtils;
import org.kontalk.xmppserver.registration.PhoneNumberVerificationProvider;

import org.kontalk.xmppserver.registration.VerificationRepository;
import org.kontalk.xmppserver.x509.X509Utils;
import tigase.annotations.TODO;
import tigase.auth.mechanisms.SaslEXTERNAL;
import tigase.cert.CertificateEntry;
import tigase.db.NonAuthUserRepository;
import tigase.db.TigaseDBException;
import tigase.form.Field;
import tigase.form.Form;
import tigase.server.Iq;
import tigase.server.Packet;
import tigase.stats.StatisticsList;
import tigase.util.Base64;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.NotAuthorizedException;
import tigase.xmpp.PacketErrorTypeException;
import tigase.xmpp.StanzaType;
import tigase.xmpp.XMPPException;
import tigase.xmpp.XMPPProcessor;
import tigase.xmpp.XMPPProcessorIfc;
import tigase.xmpp.XMPPResourceConnection;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;


/**
 * jabber:iq:register plugin for Kontalk.
 * Inspired by the jabber:iq:register Tigase plugin.
 * @author Daniele Ricci
 */
@TODO(note = "Support for multiple virtual hosts")
public class KontalkIqRegister extends XMPPProcessor implements XMPPProcessorIfc {

    private static final String[][] ELEMENTS = {Iq.IQ_QUERY_PATH};
    public static final String ID = "kontalk:jabber:iq:register";

    private static Logger log = Logger.getLogger(KontalkIqRegister.class.getName());
    private static final String[] XMLNSS = {"jabber:iq:register"};

    // form XPath and xmlns
    private static final String IQ_FORM_ELEM_NAME = "x" ;
    private static final String IQ_FORM_XMLNS = "jabber:x:data";
    private static final String IQ_FORM_KONTALK_CODE_XMLNS = "http://kontalk.org/protocol/register#code";

    // form fields
    private static final String FORM_FIELD_PHONE = "phone";
    private static final String FORM_FIELD_CODE = "code";
    private static final String FORM_FIELD_PUBLICKEY = "publickey";
    private static final String FORM_FIELD_REVOKED = "revoked";

    private static final Element[] FEATURES = {new Element("register", new String[]{"xmlns"},
            new String[]{"http://jabber.org/features/iq-register"})};
    private static final Element[] DISCO_FEATURES = {new Element("feature", new String[]{"var"},
            new String[]{"jabber:iq:register"})};

    private static final String ERROR_INVALID_CODE = "Invalid verification code.";
    private static final String ERROR_MALFORMED_REQUEST = "Please provide either a phone number or a public key and a verification code.";
    private static final String ERROR_INVALID_REVOKED = "Invalid revocation key.";
    private static final String ERROR_INVALID_PUBKEY = "Invalid public key.";

    private String serverFingerprint;
    private PhoneNumberVerificationProvider provider;

    private long statsRegistrationAttempts;
    private long statsRegisteredUsers;
    private long statsInvalidRegistrations;
    private Map<BareJID, String> requests;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void init(Map<String, Object> settings) throws TigaseDBException {
        requests = new HashMap<>();
        serverFingerprint = (String) settings.get("fingerprint");

        // registration provider
        String providerClassName = (String) settings.get("provider");
        try {
            @SuppressWarnings("unchecked")
            Class<? extends PhoneNumberVerificationProvider> providerClass =
                    (Class<? extends PhoneNumberVerificationProvider>) Class.forName(providerClassName);
            provider = providerClass.newInstance();
            provider.init(settings);
        }
        catch (ClassNotFoundException e) {
            throw new TigaseDBException("Provider class not found: " + providerClassName);
        }
        catch (InstantiationException e) {
            throw new TigaseDBException("Unable to create provider instance for " + providerClassName);
        }
        catch (IllegalAccessException e) {
            throw new TigaseDBException("Unable to create provider instance for " + providerClassName);
        }
    }

    @Override
    public void process(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings) throws XMPPException {
        if (log.isLoggable(Level.FINEST)) {
            log.finest("Processing packet: " + packet.toString());
        }
        if (session == null) {
            if (log.isLoggable(Level.FINEST)) {
                log.finest("Session is null, ignoring");
            }

            return;
        }

        BareJID id = session.getDomainAsJID().getBareJID();

        if (packet.getStanzaTo() != null) {
            id = packet.getStanzaTo().getBareJID();
        }
        try {

            if ((packet.getPacketFrom() != null) && packet.getPacketFrom().equals(session.getConnectionId())
                    && (!session.isAuthorized() || (session.isUserId(id) || session.isLocalDomain(id.toString(), false)))) {

                Element request = packet.getElement();

                if (!session.isAuthorized()) {
                    if (!session.getDomain().isRegisterEnabled()) {
                        results.offer(Authorization.NOT_ALLOWED.getResponseMessage(packet,
                                "Registration is not allowed for this domain.", true));
                        ++statsInvalidRegistrations;
                        return;
                    }
                }

                StanzaType type = packet.getType();

                switch (type) {
                    case set:

                        Element query = request.getChild(Iq.QUERY_NAME, XMLNSS[0]);
                        Element formElement = (query != null) ? query.getChild(IQ_FORM_ELEM_NAME, IQ_FORM_XMLNS) : null;
                        if (formElement != null) {
                            Form form = new Form(formElement);

                            // phone number: verification code
                            String phone = form.getAsString(FORM_FIELD_PHONE);
                            if (!session.isAuthorized() && phone != null) {
                                Packet response = registerPhone(session, packet, phone);
                                statsRegistrationAttempts++;
                                packet.processedBy(ID);
                                results.offer(response);
                                break;
                            }

                            // verification code: key submission
                            String code = form.getAsString(FORM_FIELD_CODE);
                            // get public key block from client certificate
                            byte[] publicKeyData = getPublicKey(session);

                            if (!session.isAuthorized() && code != null) {

                                // load public key
                                PGPPublicKey key = loadPublicKey(publicKeyData);
                                // verify user id
                                BareJID jid = verifyPublicKey(session, key);

                                if (verifyCode(session, jid, code)) {
                                    byte[] signedKey = signPublicKey(session, publicKeyData);

                                    Packet response = register(session, packet, jid, key.getFingerprint(), signedKey);
                                    statsRegisteredUsers++;
                                    packet.processedBy(ID);
                                    results.offer(response);
                                }
                                else {
                                    // invalid verification code
                                    results.offer(Authorization.BAD_REQUEST.getResponseMessage(packet, ERROR_INVALID_CODE, true));
                                }

                                break;
                            }

                            // public key + revoked key: key rollover or upgrade from legacy
                            if (session.isAuthorized()) {
                                String oldFingerprint = KontalkAuth.getUserFingerprint(session);
                                if (oldFingerprint != null) {
                                    // do not use public key from certificate
                                    publicKeyData = null;

                                    // user already has a key, check if revoked key fingerprint matches
                                    String publicKey = form.getAsString(FORM_FIELD_PUBLICKEY);
                                    String revoked = form.getAsString(FORM_FIELD_REVOKED);
                                    if (publicKey != null && revoked != null) {
                                        publicKeyData = Base64.decode(publicKey);
                                        byte[] revokedData = Base64.decode(revoked);
                                        KontalkKeyring keyring = getKeyring(session);
                                        if (!keyring.revoked(revokedData, oldFingerprint)) {
                                            // invalid revocation key
                                            log.log(Level.INFO, "Invalid revocation key for user {0}", session.getBareJID());
                                            results.offer(Authorization.FORBIDDEN.getResponseMessage(packet, ERROR_INVALID_REVOKED, false));
                                            break;
                                        }
                                    }
                                }

                                // user has no key or revocation key was fine, accept the new key
                                if (publicKeyData != null) {
                                    rolloverContinue(session, publicKeyData, packet, results);
                                    break;
                                }
                            }
                        }

                        // bad request
                        results.offer(Authorization.BAD_REQUEST.getResponseMessage(packet, ERROR_MALFORMED_REQUEST, true));
                        break;

                    case get: {
                        // TODO instructions form
                        break;
                    }
                    default:
                        results.offer(Authorization.BAD_REQUEST.getResponseMessage(packet, "Message type is incorrect", true));

                        break;
                }
            }
        }
        catch (NotAuthorizedException e) {
            results.offer(Authorization.NOT_AUTHORIZED.getResponseMessage(packet,
                    "You are not authorized to change registration settings.\n" + e.getMessage(), true));
        }
        catch (TigaseDBException e) {
            log.warning("Database problem: " + e);
            results.offer(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
                    "Database access problem, please contact administrator.", true));
        }
        // generated from PGP
        catch (IOException e) {
            log.warning("Unknown error: " + e);
            results.offer(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
                    "Internal PGP error. Please contact administrator.", true));
        }
        catch (PGPException e) {
            e.printStackTrace(System.err);
            log.warning("PGP problem: " + e);
            results.offer(Authorization.BAD_REQUEST.getResponseMessage(packet,
                    ERROR_INVALID_PUBKEY, true));
        }
    }

    private byte[] getPublicKey(XMPPResourceConnection session) throws PGPException, IOException {
        CertificateEntry certEntry = (CertificateEntry) session.getSessionData(SaslEXTERNAL.SESSION_AUTH_PEER_CERT);
        if (certEntry != null) {
            Certificate[] chain = certEntry.getCertChain();
            if (chain != null && chain.length > 0) {
                // take the last certificate in the chain
                // it shouldn't matter since the peer certificate should be just one
                Certificate peerCert = chain[chain.length - 1];

                if (peerCert instanceof X509Certificate) {
                    return X509Utils.getMatchingPublicKey((X509Certificate) peerCert);
                }
            }
        }

        throw new PGPException("client certificate not found");
    }

    private Packet registerPhone(XMPPResourceConnection session, Packet packet, String phoneInput) throws PacketErrorTypeException, TigaseDBException {
        String phone;
        try {
            phone = formatPhoneNumber(phoneInput);
        }
        catch (NumberParseException e) {
            // bad number
            statsInvalidRegistrations++;
            log.log(Level.INFO, "Invalid phone number: {0}", phoneInput);
            return Authorization.BAD_REQUEST.getResponseMessage(packet, "Bad phone number.", true);
        }

        log.log(Level.FINEST, "Registering phone number: {0}", phone);

        BareJID jid = KontalkAuth.toBareJID(phone, session.getDomainAsJID().getDomain());
        try {
            String requestId = provider.startVerification(session, phone);
            if (requestId != null) {
                requests.put(jid, requestId);
            }
            else {
                requests.remove(jid);
            }
            return packet.okResult(prepareSMSResponseForm(provider.getSenderId()), 0);
        }
        catch (IOException e) {
            // some kind of error
            statsInvalidRegistrations++;
            log.log(Level.WARNING, "Failed to send verification code for: {0}", jid);
            return Authorization.NOT_ACCEPTABLE.getResponseMessage(packet, "Unable to send SMS.", true);
        }
        catch (VerificationRepository.AlreadyRegisteredException e) {
            // throttling registrations
            statsInvalidRegistrations++;
            log.log(Level.INFO, "Throttling registration for: {0}", jid);
            return packet.errorResult("wait",
                    Authorization.SERVICE_UNAVAILABLE.getErrorCode(),
                    Authorization.SERVICE_UNAVAILABLE.getCondition(),
                    "Too many attempts.",
                    true);
        }
    }

    private Element prepareSMSResponseForm(String from) {
        Element query = new Element("query", new String[] { "xmlns" }, XMLNSS);
        query.addChild(new Element("instructions", provider.getAckInstructions()));
        Form form = new Form("form", null, null);

        form.addField(Field.fieldHidden("FORM_TYPE", XMLNSS[0]));
        form.addField(Field.fieldTextSingle("from", from, "SMS sender"));

        query.addChild(form.getElement());
        return query;
    }

    private Packet register(XMPPResourceConnection session, Packet packet, BareJID jid, byte[] fingerprint, byte[] publicKey)
            throws TigaseDBException {
        KontalkAuth.setUserFingerprint(session, jid, Hex.toHexString(fingerprint).toUpperCase());
        return packet.okResult(prepareRegisteredResponseForm(publicKey), 0);
    }

    private Element prepareRegisteredResponseForm(byte[] publicKey) {
        Element query = new Element("query", new String[] { "xmlns" }, XMLNSS);
        Form form = new Form("form", null, null);

        form.addField(Field.fieldHidden("FORM_TYPE", IQ_FORM_KONTALK_CODE_XMLNS));
        form.addField(Field.fieldTextSingle("publickey", Base64.encode(publicKey), "Signed public key"));

        query.addChild(form.getElement());
        return query;
    }

    private void rolloverContinue(XMPPResourceConnection session, byte[] publicKeyData, Packet packet, Queue<Packet> results)
            throws IOException, PGPException, TigaseDBException, PacketErrorTypeException, NotAuthorizedException {

        PGPPublicKey key = loadPublicKey(publicKeyData);
        // verify user id
        BareJID jid = verifyPublicKey(session, key);
        if (jid != null) {
            byte[] signedKey = signPublicKey(session, publicKeyData);
            Packet response = register(session, packet, jid, key.getFingerprint(), signedKey);

            // send signed key in response
            packet.processedBy(ID);
            results.offer(response);
        }
        else {
            results.offer(Authorization.FORBIDDEN.getResponseMessage(packet, ERROR_INVALID_PUBKEY, false));
        }
    }

    private String formatPhoneNumber(String phoneInput) throws NumberParseException {
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        Phonenumber.PhoneNumber phone = util.parse(phoneInput, null);
        return util.format(phone, PhoneNumberUtil.PhoneNumberFormat.E164);
    }

    private BareJID parseUserID(PGPPublicKey publicKey) throws PGPException {
        PGPUserID uid = PGPUtils.parseUserID(publicKey);
        if (uid == null)
            throw new PGPException("Invalid user id");
        return BareJID.bareJIDInstanceNS(uid.getEmail());
    }

    private PGPPublicKey loadPublicKey(byte[] publicKeyData) throws IOException, PGPException {
        return PGPUtils.getMasterKey(publicKeyData);
    }

    private BareJID verifyPublicKey(XMPPResourceConnection session, PGPPublicKey publicKey) throws PGPException, NotAuthorizedException {
        // TODO import key into gpg for advanced verification
        BareJID jid = parseUserID(publicKey);
        if (session.isAuthorized() ?
                session.getBareJID().equals(jid) :
                session.getDomainAsJID().toString().equalsIgnoreCase(jid.getDomain()))
            return jid;

        throw new PGPException("Invalid email identifier");
    }

    private boolean verifyCode(XMPPResourceConnection session, BareJID jid, String code) throws TigaseDBException, IOException {
        String requestId = requests.get(jid);
        return provider.endVerification(session, requestId, code);
    }

    private byte[] signPublicKey(XMPPResourceConnection session, byte[] publicKeyData) throws IOException, PGPException {
        KontalkKeyring keyring = getKeyring(session);
        return keyring.signKey(publicKeyData);
    }

    private KontalkKeyring getKeyring(XMPPResourceConnection session) {
        return KontalkAuth.getKeyring(session, serverFingerprint);
    }

    @Override
    public Element[] supDiscoFeatures(XMPPResourceConnection session) {
        if (log.isLoggable(Level.FINEST) && (session != null)) {
            log.finest("VHostItem: " + session.getDomain());
        }
        if ((session != null) && session.getDomain().isRegisterEnabled()) {
            return DISCO_FEATURES;
        }
        else {
            return null;
        }
    }

    @Override
    public String[][] supElementNamePaths() {
        return ELEMENTS;
    }

    @Override
    public String[] supNamespaces() {
        return XMLNSS;
    }

    @Override
    public Element[] supStreamFeatures(XMPPResourceConnection session) {
        if (log.isLoggable(Level.FINEST) && (session != null)) {
            log.finest("VHostItem: " + session.getDomain());
        }
        if ((session != null) && session.getDomain().isRegisterEnabled()) {
            return FEATURES;
        }
        else {
            return null;
        }
    }

    @Override
    public void getStatistics(StatisticsList list) {
        super.getStatistics(list);
        list.add(getComponentInfo().getName(), "Registration attempts", statsRegistrationAttempts, Level.INFO);
        list.add(getComponentInfo().getName(), "Registered users", statsRegisteredUsers, Level.INFO);
        list.add(getComponentInfo().getName(), "Invalid registrations", statsInvalidRegistrations, Level.INFO);
    }

}
