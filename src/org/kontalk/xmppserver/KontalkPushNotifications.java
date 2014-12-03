package org.kontalk.xmppserver;

import java.util.Map;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.db.NonAuthUserRepository;
import tigase.db.TigaseDBException;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;
import tigase.xmpp.XMPPPostprocessorIfc;
import tigase.xmpp.XMPPProcessor;
import tigase.xmpp.XMPPResourceConnection;


/**
 * Kontalk legacy push notifications implementation.
 * Support for GCM only. The hard-coded stuff.
 * @author Daniele Ricci
 */
public class KontalkPushNotifications extends XMPPProcessor implements XMPPPostprocessorIfc {

    private static Logger log = Logger.getLogger(KontalkPushNotifications.class.getName());
    public static final String XMLNS = "http://kontalk.org/extensions/presence#push";
    public static final String ID = "kontalk:push:legacy";

    private static final String[][] ELEMENTS = { { Message.ELEM_NAME } };
    private static final String[] XMLNSS = {XMLNS, Message.CLIENT_XMLNS};

    private String componentName;

    @Override
    public void init(Map<String, Object> settings) throws TigaseDBException {
        componentName = (String) settings.get("component");
        if (componentName == null)
            componentName = "push";
    }

    @Override
    public void postProcess(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings) {
        if (log.isLoggable(Level.FINEST)) {
            log.finest("Processing packet: " + packet.toString());
        }

        if (session == null && packet.getElemName().equals(Message.ELEM_NAME) &&
            packet.getType() == StanzaType.chat && packet.getElement().getChild("body") != null) {

            // create registration request
            Element request = new Element("message");
            request.setAttribute("type", "push");

            request.addChild(new Element("push",
                    new String[]{"xmlns", "jid"},
                    new String[]{XMLNS, packet.getStanzaTo().getBareJID().toString()}));

            // send regId to push component
            JID compJid = JID.jidInstanceNS(componentName, packet.getStanzaTo().getDomain(), null);
            JID fromJid = JID.jidInstanceNS(packet.getStanzaTo().getDomain());
            Packet p = Packet.packetInstance(request, fromJid, compJid);
            results.offer(p);
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

}
