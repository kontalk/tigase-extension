package org.kontalk.xmppserver;

import java.util.Map;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.db.NonAuthUserRepository;
import tigase.db.TigaseDBException;
import tigase.server.Iq;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.JID;
import tigase.xmpp.NotAuthorizedException;
import tigase.xmpp.PacketErrorTypeException;
import tigase.xmpp.StanzaType;
import tigase.xmpp.XMPPPreprocessorIfc;
import tigase.xmpp.XMPPProcessor;
import tigase.xmpp.XMPPResourceConnection;

public class PublicKeyPublish extends XMPPProcessor implements XMPPPreprocessorIfc {

    private static Logger log = Logger.getLogger(PublicKeyPublish.class.getName());
    private static final String XMLNS = "urn:xmpp:pubkey:2";
    private static final String ELEM_NAME = "pubkey";
    public static final String ID = XMLNS;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean preProcess(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings) {
        if (log.isLoggable(Level.FINEST)) {
            log.finest("Processing packet: " + packet.toString());
        }
        if (session == null) {
            if (log.isLoggable( Level.FINE)) {
                log.log( Level.FINE, "Session is null, ignoring packet: {0}", packet );
            }
            return false;
        }
        if (!session.isAuthorized()) {
            if ( log.isLoggable( Level.FINE ) ){
                log.log( Level.FINE, "Session is not authorized, ignoring packet: {0}", packet );
            }
            return false;
        }

        try {
            if ((packet.getStanzaFrom() != null ) && !session.isUserId(packet.getStanzaFrom().getBareJID())) {
                // RFC says: ignore such request
                log.log( Level.WARNING, "Public key request ''from'' attribute doesn't match "
                    + "session: {0}, request: {1}", new Object[] { session, packet } );
                return false;
            }

            StanzaType type = packet.getType();
            String xmlns = packet.getElement().getXMLNSStaticStr( Iq.IQ_QUERY_PATH );

            if (xmlns == XMLNS && type == StanzaType.get) {

                // TODO retrieve fingerprint from repository and send key data

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
        catch (TigaseStringprepException e) {
            log.log(Level.WARNING, "Invalid JID string", e);
            try {
                results.offer(Authorization.BAD_REQUEST.getResponseMessage(packet, "Invalid JID string", false));
            }
            catch (PacketErrorTypeException pe) {
                // ignored
            }
        }

        return false;
    }

    public static Packet requestPublicKey(JID from, JID to, Queue<Packet> results) {
        Element req = new Element(Iq.ELEM_NAME);
        req.setAttribute("from", from.toString());
        req.setXMLNS(CLIENT_XMLNS);

        Element pubkey = new Element(ELEM_NAME);
        pubkey.setXMLNS(XMLNS);
        req.addChild(pubkey);

        Packet packet = Packet.packetInstance(req, from, to);
        results.offer(packet);
        return packet;
    }

}
