package org.kontalk.xmppserver;

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

public class PublicKeyPublish extends XMPPProcessor implements XMPPProcessorIfc {

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
                Element pubkey = null;

                // retrieve fingerprint from repository and send key data
                String fingerprint = KontalkAuth.getUserFingerprint(session, packet.getStanzaTo().getBareJID());
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
                    catch (IOException e) {
                        log.log(Level.WARNING, "Unable to export key for user {0} (fingerprint: {1})",
                                new Object[] { packet.getStanzaTo(), fingerprint });
                    }
                }

                packet.processedBy(ID);
                results.offer(packet.okResult(pubkey, 0));
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
