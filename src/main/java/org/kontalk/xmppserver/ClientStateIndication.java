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
import tigase.server.Iq;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.server.Presence;
import tigase.xml.Element;
import tigase.xmpp.*;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * XEP-0352: Client State Indication
 * @author Daniele Ricci
 */
public class ClientStateIndication extends XMPPProcessorAbstract implements XMPPPacketFilterIfc, XMPPStopListenerIfc {
    private static Logger log = Logger.getLogger(ClientStateIndication.class.getName());

    static final String XMLNS = "urn:xmpp:csi:0";
    public static final String ID = "kontalk:" + XMLNS;

    /** Max size of the packet queue. After reaching this point, data will be sent out to the client. */
    // TODO make this a parameter
    private static final int MAX_QUEUE_SIZE = 50;

    private static final String[] XMLNSS = {XMLNS, XMLNS};

    static final String ELEM_ACTIVE = "active";
    static final String ELEM_INACTIVE = "inactive";

    private static final String[][] ELEMENTS = {
            { ELEM_ACTIVE },
            { ELEM_INACTIVE },
    };

    private static final Element[] FEATURES = { new Element("csi", new String[] { "xmlns" }, new String[] { XMLNS }) };

    private static final String CHATSTATE_XMLNS = "http://jabber.org/protocol/chatstates";

    static final String SESSION_QUEUE = ID + ":queue";

    @Override
    public void processFromUserToServerPacket(JID connectionId, Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings) throws PacketErrorTypeException {
        if (packet.getElemName() == ELEM_ACTIVE) {
            if (log.isLoggable(Level.FINEST)) {
                log.log(Level.FINEST, "Activating session {0}", session);
            }
            setActive(session, results);
            packet.processedBy(ID);
        }
        else if (packet.getElemName() == ELEM_INACTIVE) {
            if (log.isLoggable(Level.FINEST)) {
                log.log(Level.FINEST, "Deactivating session {0}", session);
            }
            setInactive(session);
            packet.processedBy(ID);
        }
    }

    @Override
    public void stopped(XMPPResourceConnection session, Queue<Packet> results, Map<String, Object> settings) {
        if (session != null && results != null) {
            if (log.isLoggable(Level.FINEST)) {
                log.log(Level.FINEST, "User disconnected, activating session {0}", session);
            }
            flush(session, results, false, true, true);
        }
        else {
            if (log.isLoggable(Level.FINEST)) {
                log.log(Level.FINEST, "Session disappeared!");
            }
        }
    }

    /** Activates client state indication (that is, client going to inactive state). */
    private void setInactive(XMPPResourceConnection session) {
        // check if there is already a queue
        final InternalQueue queue = (InternalQueue) session.getSessionData(SESSION_QUEUE);
        if (queue == null)
            session.putSessionData(SESSION_QUEUE, new InternalQueue(MAX_QUEUE_SIZE));
    }

    /** Deactivates client state indication (that is, client going to active state). */
    private void setActive(XMPPResourceConnection session, Queue<Packet> results) {
        flush(session, results, true, false, true);
    }

    private void flush(XMPPResourceConnection session, Queue<Packet> results, boolean flushPresence, boolean stopped, boolean remove) {
        final InternalQueue queue = (InternalQueue) session.getSessionData(SESSION_QUEUE);
        if (queue == null)
            return;

        synchronized (queue) {
            if (flushPresence && queue.size() > 0) {
                // send all pending presence data
                try {
                    JID connId = session.getConnectionId();
                    Collection<Presence> values = queue.values();
                    for (Presence p : values) {
                        p.setPacketTo(connId);
                        results.offer(p);
                    }
                }
                catch (NoConnectionIdException e) {
                    log.log(Level.SEVERE, "this should not happen", e);
                }
            }
            // send all pending messages
            List<Message> msgs = queue.getMessages();
            if (msgs != null && msgs.size() > 0) {
                if (stopped) {
                    // we are stopping, redeliver all stanzas
                    for (Message p : msgs) {
                        // we are redelivering, no connection id
                        p.setPacketTo(null);
                        results.offer(p);
                    }
                }
                else {
                    try {
                        JID connId = session.getConnectionId();
                        for (Message p : msgs) {
                            // create a copy so we don't alter the original stanza
                            Packet p2 = p.copyElementOnly();
                            p2.setPacketFrom(p.getPacketFrom());
                            p2.setPacketTo(connId);
                            results.offer(p2);
                        }
                    }
                    catch (NoConnectionIdException e) {
                        log.log(Level.WARNING, "connection has vanished, sending messages to JID", e);
                        for (Message p : msgs) {
                            p.setPacketTo(null);
                            results.offer(p);
                        }
                    }
                }
            }
            // destroy and remove stanza store
            queue.clear();
            if (remove)
                session.removeSessionData(SESSION_QUEUE);
        }
    }

    @Override
    public void filter(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo, Queue<Packet> results) {
        if ((session == null) || (!session.isAuthorized()) || (results == null) || (results.size() == 0)) {
            return;
        }

        final InternalQueue queue = (InternalQueue) session.getSessionData(SESSION_QUEUE);
        if (queue == null) {
            return;
        }

        // it will be true if at least a packet is going through
        boolean needsFlush = false;

        for (Iterator<Packet> it = results.iterator(); it.hasNext(); ) {
            Packet res = it.next();
            try {
                if (res.getPacketTo() != null && res.getPacketTo().equals(session.getConnectionId())) {
                    synchronized (queue) {
                        if (log.isLoggable(Level.FINEST)) {
                            log.log(Level.FINEST, "Checking packet {0} for session {1}",
                                    new Object[]{packet, session});
                        }
                        if (filterPacket(res, queue)) {
                            it.remove();
                            // queue is getting big, flush them all!
                            if (queue.needsFlush()) {
                                needsFlush = true;
                                // since we are going to flush anyway, no need to continue
                                // (fix for ConcurrentModificationException)
                                break;
                            }
                        }
                        else if (!isSilent(res)) {
                            // this packet will go through
                            // do a flush later since we are transmitting
                            needsFlush = true;
                        }
                    }
                }
            }
            catch (NoConnectionIdException e) {
                // ignore
            }
        }

        if (needsFlush) {
            flush(session, results, true, false, false);
        }
    }

    private boolean filterPacket(Packet packet, InternalQueue queue) {
        if (isPresence(packet)) {
            if (log.isLoggable(Level.FINEST)) {
                log.log(Level.FINEST, "Delaying presence {0}", packet);
            }
            queue.put(packet.getStanzaFrom(), (Presence) packet);
            return true;
        }

        if (isDeliveryReceipt(packet)) {
            if (log.isLoggable(Level.FINEST)) {
                log.log(Level.FINEST, "Delaying delivery receipt {0}", packet);
            }
            queue.putMessage((Message) packet);
            return true;
        }

        if (isChatState(packet)) {
            if (log.isLoggable(Level.FINEST)) {
                log.log(Level.FINEST, "Filtering packet {0}", packet);
            }
            return true;
        }

        return false;
    }

    /** Returns true if the given packet is silent (e.g. ping). */
    private boolean isSilent(Packet packet) {
        return packet.getElemName() == Iq.ELEM_NAME &&
                packet.getElement().getChild("ping", "urn:xmpp:ping") != null;
    }

    private boolean isPresence(Packet packet) {
        return packet.getElemName() == Presence.ELEM_NAME &&
                (packet.getType() == null ||
                packet.getType() == StanzaType.available ||
                packet.getType() == StanzaType.unavailable);
    }

    private boolean isDeliveryReceipt(Packet packet) {
        return packet.getElemName() == Message.ELEM_NAME &&
                packet.getElement().getChild("received", "urn:xmpp:receipts") != null;
    }

    private boolean isChatState(Packet packet) {
        return packet.getElemName() == Message.ELEM_NAME &&
                packet.getElement().getChild("body") == null &&
                (packet.getElement().getChild("inactive", CHATSTATE_XMLNS) != null ||
                packet.getElement().getChild("gone", CHATSTATE_XMLNS) != null ||
                packet.getElement().getChild("composing", CHATSTATE_XMLNS) != null ||
                packet.getElement().getChild("paused", CHATSTATE_XMLNS) != null);
    }

    @Override
    public void processServerSessionPacket(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings) throws PacketErrorTypeException {
        // not used.
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
    public Element[] supStreamFeatures(XMPPResourceConnection session) {
        return (session != null) ? FEATURES : null;
    }

    static boolean isElement(Packet packet) {
        return packet.getElemName() == ELEM_ACTIVE || packet.getElemName() == ELEM_INACTIVE;
    }

    /** A typedef for the internal stanza queue for CSI. */
    static final class InternalQueue extends LinkedHashMap<JID, Presence> {
        private static final DateFormat formatter;
        static {
            formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        }

        private List<Message> messages;
        private int maxSize;

        public InternalQueue(int max) {
            super();
            maxSize = max;
        }

        @Override
        public Presence put(JID key, Presence value) {
            return super.put(key, addDelay(value));
        }

        public void putMessage(Message packet) {
            if (messages == null) {
                messages = new LinkedList<>();
            }
            messages.add(addDelay(packet));
        }

        public List<Message> getMessages() {
            return messages;
        }

        private <T extends Packet> T addDelay(T packet) {
            Element elem = packet.getElement();
            // do not overwrite old delay element
            if (elem.getChild("delay", "urn:xmpp:delay") == null) {
                String stamp;

                synchronized (formatter) {
                    stamp = formatter.format(new Date());
                }

                Element x = new Element("delay", (String) null,
                        new String[] { "stamp", "xmlns" },
                        new String[] {  stamp, "urn:xmpp:delay" }
                );
                elem.addChild(x);
            }
            return packet;
        }

        @Override
        public void clear() {
            super.clear();
            if (messages != null) {
                messages.clear();
            }
        }

        public boolean needsFlush() {
            return ((messages != null ? messages.size() : 0) + size()) >= maxSize;
        }
    }

}
