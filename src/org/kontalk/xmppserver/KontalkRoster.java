package org.kontalk.xmppserver;


import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.db.NonAuthUserRepository;
import tigase.db.TigaseDBException;
import tigase.server.Iq;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.NotAuthorizedException;
import tigase.xmpp.PacketErrorTypeException;
import tigase.xmpp.StanzaType;
import tigase.xmpp.XMPPPreprocessorIfc;
import tigase.xmpp.XMPPProcessor;
import tigase.xmpp.XMPPResourceConnection;
import tigase.xmpp.impl.roster.RosterAbstract;
import tigase.xmpp.impl.roster.RosterFactory;
import tigase.xmpp.impl.roster.RosterFlat;


/**
 * Kontalk roster implementation.
 * This plugin will lookup users in the Kontalk network by probing every server or by polling the local cache.
 * If a roster request contains items, the packet will be processed by this plugin and then filtered.
 * @author Daniele Ricci
 */
public class KontalkRoster extends XMPPProcessor implements XMPPPreprocessorIfc {

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
    public boolean preProcess(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings) {
        if (log.isLoggable(Level.FINEST)) {
            log.finest("Processing packet: " + packet.toString());
        }
        if (session == null) {
            if (log.isLoggable( Level.FINE)) {
                log.log( Level.FINE, "Session is null, ignoring packet: {0}", packet );
            }
            return false;
        }
        if (!session.isAuthorized()) {
            if ( log.isLoggable( Level.FINE ) ){
                log.log( Level.FINE, "Session is not authorized, ignoring packet: {0}", packet );
            }
            return false;
        }


        try {
            if ((packet.getStanzaFrom() != null ) && !session.isUserId(packet.getStanzaFrom().getBareJID())) {
                // RFC says: ignore such request
                log.log( Level.WARNING, "Roster request ''from'' attribute doesn't match "
                    + "session: {0}, request: {1}", new Object[] { session, packet } );
                return false;
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
                    results.offer(packet.okResult(query, 1));
                    return true;
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

        return false;
    }

    private Future<Boolean> remoteLookup(BareJID jid) {
        // TODO
        return null;
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
