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

package org.kontalk.xmppserver.presence;

import tigase.db.NonAuthUserRepository;
import tigase.db.TigaseDBException;
import tigase.server.Iq;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.*;
import tigase.xmpp.impl.roster.RosterAbstract;
import tigase.xmpp.impl.roster.RosterFactory;
import tigase.xmpp.impl.roster.RosterFlat;

import java.lang.management.ManagementFactory;
import java.util.Date;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * XEP-0012: Last Activity implementation backed by tig_users.last_logout.
 * Inspired by the original Tigase implementation.
 * @author Daniele Ricci
 */
public class LastActivity extends XMPPProcessorAbstract {
    private static final Logger log = Logger.getLogger(LastActivity.class.getName());
    private static final String XMLNS = "jabber:iq:last";
    private final static String[] XMLNSS = new String[] { XMLNS };
    private static final Element[] DISCO_FEATURES = { new Element("feature", new String[] { "var" }, new String[] { XMLNS }) };
    private static final String[][] ELEMENTS = { Iq.IQ_QUERY_PATH };
    private final static String ID = "kontalk:" + XMLNS;

    private final RosterAbstract roster_util = getRosterUtil();

    private final JDBCPresenceRepository data_repo = new JDBCPresenceRepository();

    @Override
    public void init(Map<String, Object> settings) throws TigaseDBException {
        super.init(settings);

        String uri = (String) settings.get("db-uri");
        try {
            data_repo.initRepository(uri, null);
        }
        catch (Exception e) {
            throw new TigaseDBException("error initializing presence repository", e);
        }
    }

    private long getUptime() {
        return ManagementFactory.getRuntimeMXBean().getUptime();
    }

    private long getTime(BareJID user) {
        Date stamp = null;
        try {
            stamp = data_repo.getLastLogout(user);
        }
        catch (TigaseDBException e) {
            log.log(Level.WARNING, "error retrieving last logout time for " + user, e);
        }
        return stamp != null ? stamp.getTime() : -1;
    }

    @Override
    public String id() {
        return ID;
    }

    /** Request sent to server. Returns server uptime. */
    @Override
    public void processFromUserToServerPacket(JID connectionId, Packet packet, XMPPResourceConnection session,
            NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings) throws PacketErrorTypeException {

        if (packet.getType() == StanzaType.get) {
            results.offer(buildResult(packet, getUptime()));
        }
        else {
            results.offer(Authorization.BAD_REQUEST.getResponseMessage(packet, "Message type is incorrect", true));
        }
    }

    /** Request going to some other entity. Forwards the packet if user is subscribed to the requested entity. */
    @Override
    public void processFromUserOutPacket(JID connectionId, Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings) throws PacketErrorTypeException {
        try {
            if (packet.getType() == StanzaType.get) {
                JID user = packet.getStanzaTo();
                if (session.isUserId(user.getBareJID()) || roster_util.isSubscribedTo(session, user)) {
                    super.processFromUserOutPacket(connectionId, packet, session, repo, results, settings);
                }
                else {
                    results.offer(Authorization.ITEM_NOT_FOUND.getResponseMessage(packet, "Unknown last activity time", true));
                }
            }
        }
        catch (NotAuthorizedException e) {
            log.log(Level.WARNING, "Not authorized to ask for subscription status for session {0}", session);
        }
        catch (TigaseDBException e) {
            log.log(Level.WARNING, "Error requesting subscription status", e);
        }
    }

    @Override
    public void processServerSessionPacket(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo,
        Queue<Packet> results, Map<String, Object> settings) throws PacketErrorTypeException {
    }

    /** Request arriving to an offline user. This method probably leaks from an external server, but what can we do? */
    // FIXME WARNING this method probably leaks data if request is from an external server
    @Override
    public void processNullSessionPacket(Packet packet, NonAuthUserRepository repo, Queue<Packet> results,
                                         Map<String, Object> settings) throws PacketErrorTypeException {

        if (packet.getType() == StanzaType.get) {
            BareJID requestedJid = packet.getStanzaTo().getBareJID();
            final long last = getTime(requestedJid);

            if (log.isLoggable(Level.FINEST)) {
                log.finest("Get last:activity of offline user " + requestedJid + ". value=" + last);
            }
            if (last > 0) {
                results.offer(buildResult(packet, System.currentTimeMillis() - last));
            }
            else {
                // workaround infinite loop Tigase bug
                Packet result = Authorization.ITEM_NOT_FOUND.getResponseMessage(packet, "Unknown last activity time", true);
                result.setPacketTo(packet.getStanzaTo());
                results.offer(result);
            }
        }
        else if (packet.getType() == StanzaType.set) {
            results.offer(Authorization.BAD_REQUEST.getResponseMessage(packet, "Message type is incorrect", true));
        }
        else {
            super.processNullSessionPacket(packet, repo, results, settings);
        }
    }

    /** Request arriving to a local user. Returns last activity if subscribed from the sender. */
    @Override
    public void processToUserPacket(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo,
            Queue<Packet> results, Map<String, Object> settings) throws PacketErrorTypeException {
        try {
            if (packet.getType() == StanzaType.get) {
                JID requester = packet.getStanzaFrom();
                if (requester != null && (session.isUserId(requester.getBareJID()) || roster_util.isSubscribedFrom(session, requester))) {
                    BareJID user = packet.getStanzaTo().getBareJID();
                    long last = getTime(user);

                    if (last > 0) {
                        results.offer(buildResult(packet, System.currentTimeMillis() - last));
                        return;
                    }
                }

                // something went wrong
                results.offer(Authorization.ITEM_NOT_FOUND.getResponseMessage(packet, "Unknown last activity time", true));
            }
            else if (packet.getType() == StanzaType.set) {
                results.offer(Authorization.BAD_REQUEST.getResponseMessage(packet, "Message type is incorrect", true));
            }
            else {
                super.processToUserPacket(packet, session, repo, results, settings);
            }
        }
        catch (NotAuthorizedException e) {
            log.log(Level.WARNING, "Not authorized to ask for subscription status for session {0}", session);
        }
        catch (TigaseDBException e) {
            log.log(Level.WARNING, "Error requesting subscription status", e);
        }
    }

    private Packet buildResult(Packet packet, long millis) {
        Packet resp = packet.okResult((Element) null, 0);
        long result = millis / 1000;
        Element q = new Element("query", new String[] { "xmlns", "seconds" }, new String[] { "jabber:iq:last",
                String.valueOf(result) });

        resp.getElement().addChild(q);
        return resp;
    }

    @Override
    public Element[] supDiscoFeatures(XMPPResourceConnection session) {
        return DISCO_FEATURES;
    }

    @Override
    public String[][] supElementNamePaths() {
        return ELEMENTS;
    }

    @Override
    public String[] supNamespaces() {
        return XMLNSS;
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
