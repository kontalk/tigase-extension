package org.kontalk.xmppserver;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kontalk.xmppserver.pgp.KontalkKeyring;

import tigase.db.NonAuthUserRepository;
import tigase.db.TigaseDBException;
import tigase.server.Packet;
import tigase.server.Presence;
import tigase.util.Base64;
import tigase.xml.Element;
import tigase.xmpp.NotAuthorizedException;
import tigase.xmpp.XMPPPacketFilterIfc;
import tigase.xmpp.XMPPProcessor;
import tigase.xmpp.XMPPResourceConnection;
import tigase.xmpp.impl.roster.RosterAbstract;
import tigase.xmpp.impl.roster.RosterAbstract.PresenceType;
import tigase.xmpp.impl.roster.RosterFactory;
import tigase.xmpp.impl.roster.RosterFlat;


/**
 * Presence plugin to inject the subscriber public key when requesting
 * subscription the first time.
 * @author Daniele Ricci
 */
public class PresenceSubscribePublicKey extends XMPPProcessor implements
        XMPPPacketFilterIfc {

    private static final String ID = "presence/urn:xmpp:pubkey:2";

    private static final String ELEM_NAME = "pubkey";
    private static final String XMLNS = "urn:xmpp:pubkey:2";

    private static final Logger     log = Logger.getLogger(Presence.class.getName());

    private RosterAbstract roster_util = getRosterUtil();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void filter(Packet packet, XMPPResourceConnection session,
        NonAuthUserRepository repo, Queue<Packet> results) {

        if (session != null && session.isAuthorized() && packet.getElemName() == Presence.ELEM_NAME && results.size() > 0) {

            try {
                PresenceType presenceType = roster_util.getPresenceType(session, packet);
                if (presenceType == PresenceType.out_subscribe &&
                        !roster_util.isSubscribedFrom(session, packet.getStanzaTo())) {

                    //List<Packet>

                    for (Iterator<Packet> it = results.iterator(); it.hasNext(); ) {
                        Packet res = it.next();

                        // check if pubkey element was already added
                        if (!hasPublicKey(res)) {
                            if (addPublicKey(session, res)) {
                                res.processedBy(ID);
                                results.offer(res);
                            }
                        }
                    }
                }
            }
            catch (NotAuthorizedException e) {
                log.log(Level.WARNING, "not authorized!?", e);

            }
            catch (TigaseDBException e) {
                log.log(Level.SEVERE, "unable to access database", e);
            }

        }

    }

    private boolean hasPublicKey(Packet packet) {
        return packet.getElement().getChild(ELEM_NAME, XMLNS) != null;
    }

    private boolean addPublicKey(XMPPResourceConnection session, Packet packet) throws NotAuthorizedException, TigaseDBException {
        String fingerprint = session.getData(KontalkCertificateCallbackHandler.DATA_NODE, "fingerprint", null);

        if (fingerprint != null) {
            try {
                byte[] keyData = KontalkKeyring.getInstance().exportKey(fingerprint);
                if (keyData != null && keyData.length > 0) {
                    Element pubkey = new Element(ELEM_NAME, new String[] { Packet.XMLNS_ATT }, new String[] { XMLNS });
                    pubkey.addChild(new Element("key", Base64.encode(keyData)));
                    pubkey.addChild(new Element("print", fingerprint));

                    packet.getElement().addChild(pubkey);
                    packet.initVars(packet.getStanzaFrom(), packet.getStanzaTo());
                    return true;
                }
            }
            catch (IOException e) {
                log.log(Level.WARNING, "unable to load key for user " +
                    session.getBareJID(), e);
            }
        }

        return false;
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
