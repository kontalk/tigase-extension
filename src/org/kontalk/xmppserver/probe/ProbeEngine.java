package org.kontalk.xmppserver.probe;

import java.util.*;

import org.kontalk.xmppserver.KontalkRoster;

import tigase.db.TigaseDBException;
import tigase.server.Iq;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;
import tigase.xmpp.XMPPResourceConnection;


public class ProbeEngine {

    // request ID : probe info
    private final Map<String, ProbeInfo> probes;
    // repository
    private final ServerlistRepository repo;

    public ProbeEngine(ServerlistRepository repository) throws TigaseDBException {
        probes = new HashMap<String, ProbeInfo>();
        repo = repository;
        repo.reload();
    }

    /**
     * Broadcasts a lookup to the network.
     * @param user JID of local user (used in the from attribute)
     * @param jidList list of JIDs to lookup
     * @param results the packet queue
     * @param storage will be used to store matched JIDs
     */
    public void broadcastLookup(JID user, Collection<BareJID> jidList, String requestId, Queue<Packet> results, Set<BareJID> storage) {
        List<ServerlistRepository.ServerInfo> serverlist = repo.getList();
        for (ServerlistRepository.ServerInfo server : serverlist) {
            if (server.isEnabled()) {
                String serverName = server.getHost();
                JID serverJid = JID.jidInstanceNS(serverName);

                // build roster match packet
                Packet roster = Packet.packetInstance(buildRosterMatch(jidList, requestId, serverName),
                        user, serverJid);
                // send it to remote server
                results.offer(roster);

                ProbeInfo info = new ProbeInfo();
                info.id = requestId;
                info.sender = user;
                info.storage = storage;
                info.maxReplies = 1;
                probes.put(requestId, info);
            }
        }
    }

    /**
     * Handles the result of a remote lookup (i.e. a roster match iq result).
     * @param packet the packet
     * @return true if the packet was handled
     */
    public boolean handleResult(Packet packet, XMPPResourceConnection session, Queue<Packet> results) {
        String id = packet.getStanzaId();
        if (id != null) {
            // are we waiting for this?
            ProbeInfo info = probes.get(id);
            if (info != null) {
                info.numReplies++;

                List<Element> items = packet.getElemChildrenStaticStr(Iq.IQ_QUERY_PATH);
                if (items != null) {
                    // add all matched items to the storage
                    for (Element item : items) {
                        BareJID jid = BareJID.bareJIDInstanceNS(item.getAttributeStaticStr("jid"));
                        info.storage.add(jid);
                    }
                }

                // we received all the replies
                if (info.numReplies >= info.maxReplies) {
                    // remove the storage
                    probes.remove(id);
                    // send the final iq result
                    sendResult(info, results);
                }

                return true;
            }
        }

        return false;
    }

    /**
     * Handles an error of a remote lookup (i.e. a roster match iq error).
     * @param packet the packet
     * @return true if the packet was handled
     */
    public boolean handleError(Packet packet, XMPPResourceConnection session, Queue<Packet> results) {
        return handleResult(packet, session, results);
    }

    private void sendResult(ProbeInfo info, Queue<Packet> results) {
        Element iq = new Element(Iq.ELEM_NAME);
        iq.setAttribute(Iq.ID_ATT, info.id);
        iq.setAttribute(Iq.TYPE_ATT, StanzaType.result.toString());

        Element query = new Element("query");
        query.setXMLNS(KontalkRoster.XMLNS);

        for (BareJID jid : info.storage) {
            Element item = new Element("item");
            item.setAttribute("jid", jid.toString());
            query.addChild(item);
        }

        iq.addChild(query);

        results.offer(Packet.packetInstance(iq, null, info.sender));
    }

    private Element buildRosterMatch(Collection<BareJID> jidList, String id, String serverName) {
        Element iq = new Element(Iq.ELEM_NAME);
        iq.setAttribute(Iq.ID_ATT, id);
        iq.setAttribute(Iq.TYPE_ATT, StanzaType.get.toString());

        Element query = new Element("query");
        query.setXMLNS(KontalkRoster.XMLNS);

        for (BareJID jid : jidList) {
            Element item = new Element("item");
            item.setAttribute("jid", BareJID.bareJIDInstanceNS(jid.getLocalpart(), serverName).toString());
            query.addChild(item);
        }

        iq.addChild(query);
        return iq;
    }

    private static final class ProbeInfo {
        /** The final destination user. */
        private JID sender;
        /** Request ID. */
        private String id;
        /** Storage for matched JIDs. */
        private Set<BareJID> storage;
        /** Number of replies expected. */
        private int maxReplies;
        /** Number of replies received. */
        private int numReplies;
    }


}
