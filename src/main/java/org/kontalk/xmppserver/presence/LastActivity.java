/*
 * Kontalk XMPP Tigase extension
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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
import tigase.server.XMPPServer;
import tigase.server.xmppsession.SessionManager;
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
public class LastActivity extends XMPPProcessor implements XMPPProcessorIfc {
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

    @Override
    public void process(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings) throws XMPPException {
        if (log.isLoggable(Level.FINEST)) {
            log.finest("Processing packet: " + packet.toString());
        }
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
            if ((packet.getStanzaFrom() != null ) && !session.isUserId(packet.getStanzaFrom().getBareJID())) {
                // RFC says: ignore such request
                log.log( Level.WARNING, "Last activity request ''from'' attribute doesn't match "
                        + "session: {0}, request: {1}", new Object[] { session, packet } );
                return;
            }

            StanzaType type = packet.getType();
            if (type == StanzaType.get) {
                JID to = packet.getStanzaTo();

                // request for the server - return uptime
                if (to == null) {
                    long last = getUptime();
                    results.offer(buildResult(packet, last));
                }

                // request for a user
                else if (roster_util.isSubscribedTo(session, to) || session.isUserId(to.getBareJID())) {
                    BareJID user = to.getBareJID();
                    long millis = 0;

                    SessionManager sessMan = (SessionManager) XMPPServer.getComponent("sess-man");
                    if (!sessMan.containsJid(user)) {
                        long time = getTime(user);
                        if (time > 0)
                            millis = System.currentTimeMillis() - time;
                    }

                    results.offer(buildResult(packet, millis));
                }

                else {
                    try {
                        results.offer(Authorization.ITEM_NOT_FOUND.getResponseMessage(packet, "Unknown last activity time", true));
                    }
                    catch (PacketErrorTypeException pe) {
                        // ignored
                    }
                }

                packet.processedBy(ID);
            }

        }
        catch ( NotAuthorizedException e ) {
            log.log( Level.WARNING, "Received last activity request but user session is not authorized yet: {0}", packet );
            try {
                results.offer( Authorization.NOT_AUTHORIZED.getResponseMessage( packet,
                        "You must authorize session first.", true ) );
            }
            catch (PacketErrorTypeException pe) {
                // ignored
            }
        }
        catch ( TigaseDBException e ) {
            log.log( Level.WARNING, "Database problem, please contact admin:", e );
            try {
                results.offer( Authorization.INTERNAL_SERVER_ERROR.getResponseMessage( packet,
                        "Database access problem, please contact administrator.", true ) );
            }
            catch (PacketErrorTypeException pe) {
                // ignored
            }
        }

    }

    private Packet buildResult(Packet packet, long millis) {
        Packet resp = packet.okResult((Element) null, 0);
        long result = millis / 1000;
        Element q = new Element("query", new String[] { "xmlns", "seconds" }, new String[] { "jabber:iq:last",
                "" + result });

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
