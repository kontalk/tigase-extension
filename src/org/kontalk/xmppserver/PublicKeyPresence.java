package org.kontalk.xmppserver;

import tigase.db.TigaseDBException;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.NotAuthorizedException;
import tigase.xmpp.XMPPResourceConnection;
import tigase.xmpp.impl.Presence;

import java.io.IOException;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.kontalk.xmppserver.PresenceSubscribePublicKey.ELEM_NAME;
import static org.kontalk.xmppserver.PresenceSubscribePublicKey.XMLNS;


/**
 * A presence extender for public key fingerprint element.
 * @author Daniele Ricci
 */
public class PublicKeyPresence implements Presence.ExtendedPresenceProcessorIfc {

    private static final Logger log = Logger.getLogger(PublicKeyPresence.class.getName());

    @Override
    public Element extend(XMPPResourceConnection session, Queue<Packet> results) {
        if (session != null) {
            try {
                String fingerprint = KontalkAuth.getUserFingerprint(session);
                if (fingerprint != null) {
                    Element pubkey = new Element(ELEM_NAME, new String[] { Packet.XMLNS_ATT }, new String[] { XMLNS });
                    pubkey.addChild(new Element("print", fingerprint));
                    return pubkey;
                }
            }
            catch (NotAuthorizedException e) {
                log.log(Level.WARNING, "not authorized!?", e);
            }
            catch (TigaseDBException e) {
                log.log(Level.SEVERE, "unable to access database", e);
            }
        }
        return null;
    }

}
