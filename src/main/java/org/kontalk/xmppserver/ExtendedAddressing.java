package org.kontalk.xmppserver;

import tigase.annotations.TODO;
import tigase.db.NonAuthUserRepository;
import tigase.server.Iq;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.server.Presence;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.*;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * XEP-0033: Extended Stanza Addressing
 * http://xmpp.org/extensions/xep-0033.html
 *
 * @author Daniele Ricci
 */
@TODO(note = "support for cc and bcc")
public class ExtendedAddressing extends XMPPProcessorAbstract {
    private static Logger log = Logger.getLogger(ExtendedAddressing.class.getName());

    private static final String XMLNS = "http://jabber.org/protocol/address";
    public static final String ID = XMLNS;

    private static final String[] XMLNSS = {XMLNS, XMLNS, XMLNS};

    private static final String ELEM_NAME = "addresses";
    private static final String CHILD_ELEM_NAME = "address";

    private static final String[][] ELEMENTS = {
            { Message.ELEM_NAME, ELEM_NAME },
            { Presence.ELEM_NAME, ELEM_NAME },
            { Iq.ELEM_NAME, ELEM_NAME },
    };

    private static final Element[] DISCO_FEATURES = new Element[] {
        new Element("feature", new String[] { "var" },  new String[] { XMLNS })
    };

    @Override
    public void processFromUserToServerPacket(JID connectionId, Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings) throws PacketErrorTypeException {
        if (session == null) {
            return;
        }

        Element child = packet.getElement().getChild(ELEM_NAME, XMLNS);
        if (child != null) {
            List<Element> addresses = child.getChildren();
            if (addresses != null && addresses.size() > 0) {
                addresses.stream().filter(address -> address.getName() == CHILD_ELEM_NAME).forEach(address -> {
                    // TODO only "to" is supported for now
                    String type = address.getAttributeStaticStr("type");
                    if ("to".equals(type)) {
                        String jid = address.getAttributeStaticStr("jid");
                        if (jid != null) {
                            try {
                                JID to = JID.jidInstance(jid);
                                Packet fwd = packet.copyElementOnly();
                                fwd.initVars(packet.getStanzaFrom(), to);
                                stripAddresses(fwd);

                                packet.processedBy(ID);
                                results.offer(fwd);
                            }
                            catch (TigaseStringprepException e) {
                                log.log(Level.WARNING, "invalid JID: " + jid);
                                // TODO how to handle errors?
                            }
                        }
                    }
                });
            }
        }
    }

    @Override
    public void processServerSessionPacket(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings) throws PacketErrorTypeException {
        // handle server packet in server session - possible use for relay?
    }

    private void stripAddresses(Packet packet) {
        Element e = packet.getElement();
        Element child = e.getChild(ELEM_NAME, XMLNS);
        if (child != null) {
            e.removeChild(child);
        }
    }

    @Override
    public String id() {
        return ID;
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
    public Element[] supDiscoFeatures(XMPPResourceConnection session) {
        return DISCO_FEATURES;
    }

}
