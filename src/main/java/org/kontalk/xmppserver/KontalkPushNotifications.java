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

import tigase.db.NonAuthUserRepository;
import tigase.db.TigaseDBException;
import tigase.server.Message;
import tigase.server.Packet;
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

    private String componentName;

    @Override
    public void init(Map<String, Object> settings) throws TigaseDBException {
        componentName = (String) settings.get("component");
        if (componentName == null)
            componentName = "push";
    }

    @Override
    public void postProcess(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings) {
        if (session == null && packet.getElemName().equals(Message.ELEM_NAME) &&
            packet.getType() == StanzaType.chat && (packet.getElement().getChild("body") != null ||
                packet.getElement().getChild("request", "urn:xmpp:receipts") != null)) {

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
