package org.kontalk.xmppserver.probe;

import java.util.Collection;
import java.util.HashMap;
import java.util.Queue;
import java.util.Set;

import org.kontalk.xmppserver.KontalkRoster;

import tigase.server.Iq;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;


public class ProbeEngine {

    // request ID : probe info
    private final HashMap<String, ProbeInfo> probes = new HashMap<String, ProbeInfo>();

    public ProbeEngine() {
    }

    /**
     * Broadcasts a lookup to the network.
     * @param user JID of local user (used in the from attribute)
     * @param jidList list of JIDs to lookup
     * @param results the packet queue
     * @param storage will be used to store matched JIDs
     */
    public void broadcastLookup(JID user, Collection<BareJID> jidList, Queue<Packet> results, Set<BareJID> storage) {
        // TODO send to all servers
        {
            String server = "beta.kontalk.net";
            JID serverJid = JID.jidInstanceNS(server);

            // build roster match packet
            String reqId = "test-random";
            Packet roster = Packet.packetInstance(buildRosterMatch(jidList, reqId, server),
                user, serverJid);
            // send it to remote server
            results.offer(roster);

            ProbeInfo info = new ProbeInfo();
            info.storage = storage;
            probes.put(reqId, info);
        }
    }

    /**
     * Handles the result of a remote lookup (i.e. a roster match iq result).
     * @param packet the packet
     */
    public void handleResult(Packet packet) {
        // TODO
    }

    private Element buildRosterMatch(Collection<BareJID> jidList, String id, String serverName) {
        Element iq = new Element(Iq.ELEM_NAME);
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
        private Set<BareJID> storage;
    }


}
