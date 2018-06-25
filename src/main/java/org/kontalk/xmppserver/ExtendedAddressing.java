package org.kontalk.xmppserver;

import tigase.annotations.TODO;
import tigase.conf.ConfigurationException;
import tigase.server.*;
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
public class ExtendedAddressing extends AbstractMessageReceiver {
    private static Logger log = Logger.getLogger(ExtendedAddressing.class.getName());

    private static final String NODE = "http://jabber.org/protocol/address";
    private static final String DISCO_DESCRIPTION = "Multicast";

    private static final String XMLNS = NODE;
    private static final String ELEM_NAME = "addresses";
    private static final String CHILD_ELEM_NAME = "address";

    private static final String[] ELEMENTS = {
            Message.ELEM_NAME,
            Presence.ELEM_NAME,
            Iq.ELEM_NAME,
    };

    @Override
    public void processPacket(Packet packet) {
        packet.processedBy(getName());

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
                                if (!addOutPacket(fwd)) {
                                    try {
                                        addOutPacket(Authorization.RESOURCE_CONSTRAINT
                                                .getResponseMessage(packet, "Queue overflow", false));
                                    }
                                    catch (PacketErrorTypeException ignored) {
                                    }
                                }
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

    private void stripAddresses(Packet packet) {
        Element e = packet.getElement();
        Element child = e.getChild(ELEM_NAME, XMLNS);
        if (child != null) {
            e.removeChild(child);
        }
    }

    @Override
    public void setProperties(Map<String, Object> props) throws ConfigurationException {
        super.setProperties(props);
        updateServiceDiscoveryItem(getName(), null, getDiscoDescription(), false, NODE);
    }

    @Override
    public Map<String, Object> getDefaults(Map<String, Object> params) {
        Map<String, Object> defs = super.getDefaults(params);
        defs.put("packet-types", ELEMENTS);
        return defs;
    }

    @Override
    public String getDiscoDescription() {
        return DISCO_DESCRIPTION;
    }

    @Override
    public String getDiscoCategoryType() {
        return "generic";
    }

}
