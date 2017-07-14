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

import org.kontalk.xmppserver.auth.KontalkAuth;
import org.kontalk.xmppserver.probe.DataServerlistRepository;
import org.kontalk.xmppserver.probe.ProbeComponent;
import org.kontalk.xmppserver.probe.ServerlistRepository;
import tigase.db.DBInitException;
import tigase.db.NonAuthUserRepository;
import tigase.db.TigaseDBException;
import tigase.server.Iq;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.*;
import tigase.xmpp.impl.roster.RosterAbstract;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Kontalk roster implementation.
 * This plugin will lookup users in the Kontalk network by probing every server or by polling the local cache.
 * If a roster request contains items, the packet will be processed by this plugin and then filtered.
 * @author Daniele Ricci
 * @deprecated Replaced by {@link ProbeComponent}.
 */
@Deprecated
public class KontalkRoster extends XMPPProcessor implements XMPPProcessorIfc {

    private static Logger log = Logger.getLogger(KontalkRoster.class.getName());
    public static final String XMLNS = "http://kontalk.org/extensions/roster";
    public static final String ID = "kontalk:" + RosterAbstract.XMLNS;

    private static final String[][] ELEMENTS = {Iq.IQ_QUERY_PATH};
    private static final String[] XMLNSS = {XMLNS};

    private static final Element[] FEATURES = { new Element("roster", new String[] { "xmlns" }, new String[] { XMLNS }) };

    @Override
    public void init(Map<String, Object> settings) throws TigaseDBException {
        // database parameters for probe engine
        String dbUri = (String) settings.get("db-uri");
        try {
            ServerlistRepository repo = new DataServerlistRepository();
            repo.init(settings);
        }
        catch (DBInitException e) {
            throw new TigaseDBException("Unable to create instance for repository (uri=" + dbUri + ")", e);
        }
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
        if ((packet.getStanzaFrom() != null && session.isUserId(packet.getStanzaFrom().getBareJID())) && !session.isAuthorized()) {
            if ( log.isLoggable( Level.FINE ) ){
                log.log( Level.FINE, "Session is not authorized, ignoring packet: {0}", packet );
            }
            return;
        }

        // ignore packet if directed to a component
        if (packet.getStanzaTo() != null) {
            if ( log.isLoggable( Level.FINE ) ){
                log.log( Level.FINE, "Packet directed to component: {0}", packet );
            }
            Packet fwd = packet.copyElementOnly();
            fwd.setPacketTo(JID.jidInstanceNS("probe", session.getDomainAsJID().getDomain()));
            results.offer(fwd);
            return;
        }

        try {
            if (!session.isServerSession() && (packet.getStanzaFrom() != null ) && !session.isUserId(packet.getStanzaFrom().getBareJID())) {
                // RFC says: ignore such request
                log.log( Level.WARNING, "Roster request ''from'' attribute doesn't match "
                    + "session: {0}, request: {1}", new Object[] { session, packet } );
                return;
            }

            StanzaType type = packet.getType();
            String xmlns = packet.getElement().getXMLNSStaticStr( Iq.IQ_QUERY_PATH );

            if (xmlns == XMLNS && type == StanzaType.get) {

                List<Element> items = packet.getElemChildrenStaticStr(Iq.IQ_QUERY_PATH);
                if (items != null) {
                    String serverDomain = session.getDomainAsJID().getDomain();

                    Set<BareJID> found = new HashSet<BareJID>();
                    Set<BareJID> remote = new HashSet<BareJID>();
                    for (Element item : items) {
                        if (!item.getName().equals("item")) {
                            // not a roster item
                            continue;
                        }

                        BareJID jid = BareJID.bareJIDInstance(item.getAttributeStaticStr("jid"));
                        BareJID localJid = BareJID.bareJIDInstance(jid.getLocalpart(), serverDomain);
                        String domain = jid.getDomain();

                        // TODO check for block status (XEP-0191)
                        // blocked contacts must not be found as existing

                        boolean isLocalJid = domain.equalsIgnoreCase(serverDomain);

                        if (isLocalJid) {
                            if (KontalkAuth.getUserRepository().getUserUID(localJid) > 0) {
                                // local user
                                found.add(jid);
                            }
                            else {
                                // queue for remote lookup
                                remote.add(jid);
                            }
                        }
                    }

                    boolean remotePending = (remote.size() > 0);
                    if (remotePending) {
                        // process remote entries
                        remotePending = (remoteLookup(session, remote, packet.getStanzaId(), results, found) > 0);
                    }

                    if (!remotePending) {
                        // local results only
                        // return result immediately
                        Element query = new Element("query");
                        query.setXMLNS(XMLNS);

                        for (BareJID jid : found) {
                            Element item = new Element("item");
                            item.setAttribute("jid", jid.toString());
                            query.addChild(item);
                        }

                        results.offer(packet.okResult(query, 0));
                    }

                    // packet was processed successfully
                    packet.processedBy(ID);
                }

            }

        }
        catch ( NotAuthorizedException e ) {
            log.log( Level.WARNING, "Received roster request but user session is not authorized yet: {0}", packet );
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
        catch (TigaseStringprepException e) {
            log.log(Level.WARNING, "Invalid JID string", e);
            try {
                results.offer(Authorization.BAD_REQUEST.getResponseMessage(packet, "Invalid JID string", false));
            }
            catch (PacketErrorTypeException pe) {
                // ignored
            }
        }
    }

    /**
     * Requests a remote lookup to the network.
     * @param session the current session
     * @param jidList list of JIDs to lookup
     * @param results the packet queue
     * @param localJidList list of already found local JIDs
     */
    private int remoteLookup(XMPPResourceConnection session, Collection<BareJID> jidList, String requestId, Queue<Packet> results, Set<BareJID> localJidList) throws NotAuthorizedException {
        return 0;
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
    public Set<StanzaType> supTypes() {
        return new HashSet<StanzaType>(Arrays.asList(StanzaType.get));
    }

    @Override
    public Element[] supStreamFeatures(XMPPResourceConnection session) {
        if (log.isLoggable(Level.FINEST) && (session != null)) {
            log.finest("VHostItem: " + session.getDomain());
        }
        if (session != null) {
            return FEATURES;
        }
        else {
            return null;
        }
    }

}
