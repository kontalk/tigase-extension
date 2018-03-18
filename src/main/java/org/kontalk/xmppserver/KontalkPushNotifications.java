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

import tigase.conf.Configurable;
import tigase.db.NonAuthUserRepository;
import tigase.db.TigaseDBException;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.server.XMPPServer;
import tigase.server.xmppclient.ClientConnectionManager;
import tigase.xml.Element;
import tigase.xmpp.*;

import java.util.Map;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;


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

    /**
     * How many milliseconds from the last received packet for considering a session as idle.
     * If the session is idle a push notification is sent even if it's not considered closed.
     */
    private static final int IDLE_SESSION_MS = 30000;

    private String componentName;

    @Override
    public void init(Map<String, Object> settings) throws TigaseDBException {
        componentName = (String) settings.get("component");
        if (componentName == null)
            componentName = "push";
    }

    @Override
    public void postProcess(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings) {
        if ((session == null || isIdleSession(session)) && shouldNotify(packet)) {
            if (log.isLoggable(Level.FINEST)) {
                log.log(Level.FINEST, "Processing packet: {0}", packet);
            }

            // create push notification message
            Element request = new Element("message");
            request.setAttribute("type", "push");

            request.addChild(new Element("push",
                    new String[]{"xmlns", "jid"},
                    new String[]{XMLNS, packet.getStanzaTo().getBareJID().toString()}));

            // send request to push component
            JID compJid = JID.jidInstanceNS(componentName, packet.getStanzaTo().getDomain(), null);
            JID fromJid = JID.jidInstanceNS(packet.getStanzaTo().getDomain());
            Packet p = Packet.packetInstance(request, fromJid, compJid);
            results.offer(p);

            packet.processedBy(ID);
        }
    }

    private boolean isIdleSession(XMPPResourceConnection session) {
        ClientConnectionManager c2s = (ClientConnectionManager) XMPPServer.getComponent(Configurable.DEF_C2S_NAME);
        try {
            XMPPIOService io = c2s.getXMPPIOService(session.getConnectionId().getResource());
            if (log.isLoggable(Level.FINEST)) {
                log.log(Level.FINEST, "found IO service for session {0}: {1}", new Object[] { session, io });
            }

            return (io != null && (System.currentTimeMillis() - io.getLastXmppPacketReceiveTime()) > IDLE_SESSION_MS);
        }
        catch (NoConnectionIdException e) {
            log.log(Level.WARNING, "unable to check for idle connection: {0}", session);
            return false;
        }
    }

    private boolean shouldNotify(Packet packet) {
        return packet.getElemName().equals(Message.ELEM_NAME) &&
                packet.getType() == StanzaType.chat && (packet.getElement().getChild("body") != null ||
                packet.getElement().getChild("request", "urn:xmpp:receipts") != null);
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
