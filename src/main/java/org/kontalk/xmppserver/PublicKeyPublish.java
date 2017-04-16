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
import tigase.db.UserNotFoundException;
import tigase.server.Iq;
import tigase.server.Packet;
import tigase.util.Base64;
import tigase.xml.Element;
import tigase.xmpp.*;
import tigase.xmpp.impl.roster.RosterAbstract;
import tigase.xmpp.impl.roster.RosterFactory;
import tigase.xmpp.impl.roster.RosterFlat;

import java.io.IOException;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

// FIXME this class needs to be redesigned using XMPPProcessorAbstract methods
public class PublicKeyPublish extends XMPPProcessor implements XMPPProcessorIfc {

    private static Logger log = Logger.getLogger(PublicKeyPublish.class.getName());
    private static final String XMLNS = "urn:xmpp:pubkey:2";
    private static final String ELEM_NAME = "pubkey";
    public static final String ID = XMLNS;

    private static final String[] IQ_PUBKEY_PATH = {Iq.ELEM_NAME, ELEM_NAME};
    private static final String[][] ELEMENTS = { IQ_PUBKEY_PATH };
    private static final String[] XMLNSS = {XMLNS};

    private final RosterAbstract roster_util = getRosterUtil();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void process(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings) throws XMPPException {
        if (log.isLoggable(Level.FINEST)) {
            log.finest("Processing packet: " + packet.toString());
        }
        if (session == null) {
            if (log.isLoggable( Level.FINE)) {
                log.log( Level.FINE, "Session is null, ignoring packet: {0}", packet );
            }
            return;
        }
        if (!session.isAuthorized()) {
            if ( log.isLoggable( Level.FINE ) ){
                log.log( Level.FINE, "Session is not authorized, ignoring packet: {0}", packet );
            }
            return;
        }

        try {
            if ((packet.getStanzaFrom() != null ) && !session.isUserId(packet.getStanzaFrom().getBareJID())) {
                // RFC says: ignore such request
                log.log( Level.WARNING, "Public key request ''from'' attribute doesn't match "
                    + "session: {0}, request: {1}", new Object[] { session, packet } );
                return;
            }

            StanzaType type = packet.getType();
            String xmlns = packet.getElement().getXMLNSStaticStr(IQ_PUBKEY_PATH);

            if (xmlns == XMLNS && type == StanzaType.get) {
                JID to = packet.getStanzaTo();

                // our server's key was requested
                JID domain = session.getDomainAsJID();
                if (domain.equals(to)) {
                    try {
                        byte[] publicKeyData = KontalkKeyring.getInstance(domain.toString())
                                .getSecretPublicKey().getEncoded();
                        Element pubkey = new Element("pubkey");
                        pubkey.setXMLNS(XMLNS);
                        pubkey.setCData(Base64.encode(publicKeyData));
                        results.offer(packet.okResult(pubkey, 0));
                    }
                    catch (IOException | PGPException e) {
                        log.log(Level.WARNING, "Unable retrieve server public keyring");
                        results.offer((Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
                                "Server public key not available.", true)));
                    }
                }

                else if (roster_util.isSubscribedTo(session, to) || session.isUserId(to.getBareJID())) {
                    Element pubkey = null;

                    // retrieve fingerprint from repository and send key data
                    String fingerprint = KontalkAuth.getUserFingerprint(session, to.getBareJID());
                    if (fingerprint != null) {
                        try {
                            byte[] publicKeyData = KontalkKeyring.
                                    getInstance(session.getDomainAsJID().toString()).exportKey(fingerprint);
                            if (publicKeyData != null) {
                                pubkey = new Element("pubkey");
                                pubkey.setXMLNS(XMLNS);
                                pubkey.setCData(Base64.encode(publicKeyData));
                            }
                        }
                        catch (IOException | PGPException e) {
                            log.log(Level.WARNING, "Unable to export key for user {0} (fingerprint: {1})",
                                    new Object[]{to, fingerprint});
                        }
                    }

                    results.offer(packet.okResult(pubkey, 0));
                }

                else {
                    try {
                        results.offer( Authorization.NOT_AUTHORIZED.getResponseMessage( packet,
                                "Not authorized.", true ) );
                    }
                    catch (PacketErrorTypeException pe) {
                        // ignored
                    }
                }

                packet.processedBy(ID);
            }

        }
        catch ( NotAuthorizedException e ) {
            log.log( Level.WARNING, "Received roster request but user session is not authorized yet: {0}", packet );
            try {
                results.offer( Authorization.NOT_AUTHORIZED.getResponseMessage( packet,
                    "You must authorize session first.", true ) );
            }
            catch (PacketErrorTypeException pe) {
                // ignored
            }
        }
        catch (UserNotFoundException e) {
            log.log(Level.WARNING, "user not found: {0}", session);
        }
        catch ( TigaseDBException e ) {
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

    /**
     * Returns shared instance of class implementing {@link RosterAbstract} -
     * either default one ({@link RosterFlat}) or the one configured with
     * <em>"roster-implementation"</em> property.
     *
     * @return shared instance of class implementing {@link RosterAbstract}
     */
    protected RosterAbstract getRosterUtil() {
        return RosterFactory.getRosterImplementation(true);
    }

}
