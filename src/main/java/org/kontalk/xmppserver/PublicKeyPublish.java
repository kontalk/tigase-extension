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

package org.kontalk.xmppserver;

import org.bouncycastle.openpgp.PGPException;
import org.kontalk.xmppserver.auth.KontalkAuth;
import tigase.db.NonAuthUserRepository;
import tigase.db.TigaseDBException;
import tigase.server.Iq;
import tigase.server.Packet;
import tigase.util.Base64;
import tigase.xml.Element;
import tigase.xmpp.*;

import java.io.IOException;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;


public class PublicKeyPublish extends XMPPProcessorAbstract {

    private static Logger log = Logger.getLogger(PublicKeyPublish.class.getName());
    private static final String XMLNS = "urn:xmpp:pubkey:2";
    private static final String ELEM_NAME = "pubkey";
    public static final String ID = XMLNS;

    private static final String[] IQ_PUBKEY_PATH = {Iq.ELEM_NAME, ELEM_NAME};
    private static final String[][] ELEMENTS = { IQ_PUBKEY_PATH };
    private static final String[] XMLNSS = {XMLNS};

    @Override
    public String id() {
        return ID;
    }

    /** Request sent to server. Returns the server public key. */
    @Override
    public void processFromUserToServerPacket(JID connectionId, Packet packet, XMPPResourceConnection session,
                                              NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings) throws PacketErrorTypeException {

        if (packet.getType() == StanzaType.get) {
            try {
                JID domain = session.getDomainAsJID();
                byte[] publicKeyData = KontalkKeyring.getInstance(domain.toString())
                        .getSecretPublicKey().getEncoded();
                Element pubkey = new Element("pubkey");
                pubkey.setXMLNS(XMLNS);
                pubkey.setCData(Base64.encode(publicKeyData));
                results.offer(packet.okResult(pubkey, 0));
                packet.processedBy(ID);
            }
            catch (IOException | PGPException e) {
                log.log(Level.WARNING, "Unable retrieve server public keyring");
                results.offer((Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
                        "Server public key not available.", true)));
            }
        }
        else {
            results.offer(Authorization.BAD_REQUEST.getResponseMessage(packet, "Message type is incorrect", true));
        }
    }

    /** Request to some entity. Return the public key. */
    @Override
    public void processFromUserOutPacket(JID connectionId, Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings) throws PacketErrorTypeException {
        try {
            if (packet.getType() == StanzaType.get) {
                JID to = packet.getStanzaTo();
                if (to != null && session.isLocalDomain(to.getDomain(), false)) {
                    sendPublicKey(session, packet, results);
                }
                else {
                    // not for us
                    super.processFromUserOutPacket(connectionId, packet, session, repo, results, settings);
                }
            }
            else {
                results.offer(Authorization.BAD_REQUEST.getResponseMessage(packet, "Message type is incorrect", true));
            }
        }
        catch (TigaseDBException e) {
            log.log( Level.WARNING, "Database problem, please contact admin:", e );
            try {
                results.offer( Authorization.INTERNAL_SERVER_ERROR.getResponseMessage( packet,
                        "Database access problem, please contact administrator.", true ) );
            }
            catch (PacketErrorTypeException pe) {
                // ignored
            }
        }
    }

    @Override
    public void processServerSessionPacket(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo,
                                           Queue<Packet> results, Map<String, Object> settings) throws PacketErrorTypeException {
    }

    /** Destination local user not connected. Do nothing. */
    @Override
    public void processNullSessionPacket(Packet packet, NonAuthUserRepository repo, Queue<Packet> results,
                                         Map<String, Object> settings) throws PacketErrorTypeException {
    }

    private void sendPublicKey(XMPPResourceConnection session, Packet packet, Queue<Packet> results)
            throws PacketErrorTypeException, TigaseDBException {
        Element pubkey = null;

        // retrieve fingerprint from repository and send key data
        BareJID user = packet.getStanzaTo().getBareJID();
        String fingerprint = KontalkAuth.getUserFingerprint(session, user);
        if (fingerprint != null) {
            try {
                byte[] publicKeyData = KontalkKeyring.
                        getInstance(session.getDomainAsJID().toString()).exportKey(fingerprint);
                if (publicKeyData != null) {
                    pubkey = new Element("pubkey");
                    pubkey.setXMLNS(XMLNS);
                    pubkey.setCData(Base64.encode(publicKeyData));
                    results.offer(packet.okResult(pubkey, 0));
                }
                else {
                    throw new IOException("Public key not found");
                }
            }
            catch (IOException | PGPException e) {
                log.log(Level.WARNING, "Unable to export key for user {0} (fingerprint: {1})",
                        new Object[]{user, fingerprint});
                results.offer( Authorization.INTERNAL_SERVER_ERROR.getResponseMessage( packet,
                        "Unable to process public key.", true) );
            }
        }
        else {
            results.offer( Authorization.ITEM_NOT_FOUND.getResponseMessage( packet,
                    "Public key not found.", true) );
        }
    }

    public static Packet requestPublicKey(JID from, JID to, Queue<Packet> results) {
        Element req = new Element(Iq.ELEM_NAME);
        req.setAttribute(Packet.ID_ATT, String.valueOf(System.currentTimeMillis()));
        req.setAttribute(Packet.TYPE_ATT, StanzaType.get.toString());
        req.setXMLNS(CLIENT_XMLNS);

        Element pubkey = new Element(ELEM_NAME);
        pubkey.setXMLNS(XMLNS);
        req.addChild(pubkey);

        Packet packet = Packet.packetInstance(req, from, to);
        results.offer(packet);
        return packet;
    }

    @Override
    public String[][] supElementNamePaths() {
        return ELEMENTS;
    }

    @Override
    public String[] supNamespaces() {
        return XMLNSS;
    }

}
