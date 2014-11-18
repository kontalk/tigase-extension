package org.kontalk.xmppserver;


import static tigase.xmpp.impl.roster.RosterAbstract.SUB_BOTH;
import static tigase.xmpp.impl.roster.RosterAbstract.SUB_FROM;
import static tigase.xmpp.impl.roster.RosterAbstract.SUB_TO;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.db.NonAuthUserRepository;
import tigase.db.TigaseDBException;
import tigase.server.Iq;
import tigase.server.Packet;
import tigase.server.Presence;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.NotAuthorizedException;
import tigase.xmpp.PacketErrorTypeException;
import tigase.xmpp.StanzaType;
import tigase.xmpp.XMPPException;
import tigase.xmpp.XMPPProcessor;
import tigase.xmpp.XMPPProcessorIfc;
import tigase.xmpp.XMPPResourceConnection;
import tigase.xmpp.impl.roster.DynamicRoster;
import tigase.xmpp.impl.roster.RepositoryAccessException;
import tigase.xmpp.impl.roster.RosterAbstract;
import tigase.xmpp.impl.roster.RosterFactory;
import tigase.xmpp.impl.roster.RosterFlat;
import tigase.xmpp.impl.roster.RosterRetrievingException;


/**
 * Kontalk roster implementation.
 * This plugin will lookup users in the Kontalk network by probing every server or by polling the local cache.
 * If a roster request contains items, the packet will be processed by this plugin and then filtered.
 * @author Daniele Ricci
 */
public class KontalkRoster extends XMPPProcessor implements XMPPProcessorIfc {

    private static Logger log = Logger.getLogger(KontalkRoster.class.getName());
    public static final String XMLNS = "http://kontalk.org/extensions/roster";
    public static final String ID = "kontalk/" + RosterAbstract.XMLNS;

    private static final String[][] ELEMENTS = {Iq.IQ_QUERY_PATH};
    private static final String[] XMLNSS = {XMLNS};

    private static final Element[] FEATURES = { new Element("roster", new String[] { "xmlns" }, new String[] { XMLNS }) };

    private final RosterAbstract roster_util = getRosterUtil();

    private String networkDomain;

    @Override
    public void init(Map<String, Object> settings) throws TigaseDBException {
        networkDomain = (String) settings.get("network-domain");
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
                    for (Element item : items) {

                        BareJID jid = BareJID.bareJIDInstance(item.getAttributeStaticStr("jid"));
                        BareJID localJid = BareJID.bareJIDInstance(jid.getLocalpart(), serverDomain);
                        String domain = jid.getDomain();

                        // TODO check for block status (XEP-0191)
                        // blocked contacts must not be found as existing

                        boolean isNetworkJid = domain.equalsIgnoreCase(networkDomain);
                        boolean isLocalJid = domain.equalsIgnoreCase(serverDomain);

                        if (isLocalJid && session.getUserRepository().getUserUID(localJid) > 0) {
                            // local user
                            found.add(jid);
                        }
                        else if (isNetworkJid) {
                            // TODO remote lookup
                            // remoteLookup(jid);
                        }

                        else {
                            // TODO neither local nor network JID

                        }

                    }

                    Element query = new Element("query");
                    query.setXMLNS(XMLNS);

                    for (BareJID jid : found) {
                        Element item = new Element("item");
                        item.setAttribute("jid", jid.toString());
                        query.addChild(item);
                    }

                    packet.processedBy(ID);
                    results.offer(packet.okResult(query, 0));

                    // send presence probes and public key requests
                    broadcastProbe(session, results);
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

    /*
    private Future<Boolean> remoteLookup(BareJID jid) {
        // TODO
        return null;
    }
    */

    public void broadcastProbe(XMPPResourceConnection session, Queue<Packet> results)
                    throws NotAuthorizedException, TigaseDBException {
        if (log.isLoggable(Level.FINEST)) {
            log.log(Level.FINEST, "Broadcasting probes for: {0}", session);
        }

        // Probe is always broadcasted with initial presence
        Element presInit  = session.getPresence();
        Element presProbe = new Element(Presence.ELEM_NAME);

        presProbe.setXMLNS(CLIENT_XMLNS);
        presProbe.setAttribute("type", StanzaType.probe.toString());
        presProbe.setAttribute("from", session.getBareJID().toString());

        JID[] buddies = roster_util.getBuddies(session, SUB_BOTH);

        if (buddies != null) {
            for (JID buddy : buddies) {
                if (log.isLoggable(Level.FINEST)) {
                    log.log(Level.FINEST, session.getBareJID() +
                            " | Sending presence probe to: " + buddy);
                }
                tigase.xmpp.impl.Presence.sendPresence(null, null, buddy, results, presProbe);
                if (log.isLoggable(Level.FINEST)) {
                    log.log(Level.FINEST, session.getBareJID() +
                            " | Sending intial presence to: " + buddy);
                }
                tigase.xmpp.impl.Presence.sendPresence(null, null, buddy, results, presInit);
                roster_util.setPresenceSent(session, buddy, true);
            }    // end of for (String buddy: buddies)
        }      // end of if (buddies == null)

        JID[] buddies_to = roster_util.getBuddies(session, SUB_TO);

        if (buddies_to != null) {
            for (JID buddy : buddies_to) {
                if (log.isLoggable(Level.FINEST)) {
                    log.log(Level.FINEST, session.getBareJID() + " | Sending probe to: " + buddy);
                }
                tigase.xmpp.impl.Presence.sendPresence(null, null, buddy, results, presProbe);
            }    // end of for (String buddy: buddies)
        }      // end of if (buddies == null)
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
