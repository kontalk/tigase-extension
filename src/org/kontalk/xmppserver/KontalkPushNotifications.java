package org.kontalk.xmppserver;

import java.util.Map;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.db.NonAuthUserRepository;
import tigase.db.TigaseDBException;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.server.Presence;
import tigase.xml.Element;
import tigase.xmpp.JID;
import tigase.xmpp.NotAuthorizedException;
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

    private static final String[] PRESENCE_CAPS_PATH = { Presence.ELEM_NAME, "c" };
    private static final String[][] ELEMENTS = { PRESENCE_CAPS_PATH, { Message.ELEM_NAME } };
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

        if (packet.getElemName().equals(Presence.ELEM_NAME)) {
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

                Element element = packet.getElement();
                Element cap = element.getChild("c", XMLNS);
                if (cap != null && KontalkLegacyPushComponent.GCM_PROVIDER_NAME.equals(cap.getAttributeStaticStr("provider"))) {
                    String regId = cap.getCData();
                    if (regId != null && regId.length() > 0) {
                        // create registration request
                        Element request = new Element("iq");
                        request.setAttribute("type", "set");

                        request.addChild(new Element("register",
                                regId,
                                new String[]{"xmlns", "provider"},
                                new String[]{XMLNS, KontalkLegacyPushComponent.GCM_PROVIDER_NAME}));

                        // send regId to push component
                        JID compJid = JID.jidInstanceNS(componentName, session.getDomainAsJID().getDomain(), null);
                        Packet p = Packet.packetInstance(request, session.getJID(), compJid);
                        results.offer(p);
                    }
                }
            } catch (NotAuthorizedException e) {
                // TODO
                log.log(Level.WARNING, "Not authorized.");
            }
        }

        else if (packet.getElemName().equals(Message.ELEM_NAME)) {
            if (session == null) {
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
