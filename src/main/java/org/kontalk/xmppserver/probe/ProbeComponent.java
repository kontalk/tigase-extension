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

package org.kontalk.xmppserver.probe;

import tigase.conf.ConfigurationException;
import tigase.db.RepositoryFactory;
import tigase.db.TigaseDBException;
import tigase.db.UserRepository;
import tigase.server.AbstractMessageReceiver;
import tigase.server.Iq;
import tigase.server.Packet;
import tigase.server.XMPPServer;
import tigase.util.TigaseStringprepException;
import tigase.vhosts.VHostManagerIfc;
import tigase.xml.Element;
import tigase.xmpp.*;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * The network probe component.
 * This component is used to lookup users and other information in the network.
 * @author Daniele Ricci
 */
public class ProbeComponent extends AbstractMessageReceiver {
    private static Logger log = Logger.getLogger(ProbeComponent.class.getName());

    private static final String DISCO_DESCRIPTION = "Kontalk probe engine";
    static final String XMLNS = "http://kontalk.org/extensions/roster";
    private static final String NODE = XMLNS + "#probe";

    /** Timeout of remote requests in milliseconds. */
    private static final int REQUEST_TIMEOUT = 60000;

    private static final int NUM_THREADS = 20;

    // request ID : probe info
    private final Map<String, ProbeInfo> probes = new HashMap<>();
    private ProbeManager probeManager;
    private JID authorizedSender;

    private JID publicId;

    private final ServerlistRepository repository = new DataServerlistRepository();
    private UserRepository user_repository;

    @Override
    public void processPacket(Packet packet) {
        /*
         * Tasks for this component:
         * 1. receive roster match requests by clients
         *  - handle them locally when possible
         *  - send out network broadcasts for probe
         * 2. receive roster match requests by remote probe components
         *  - handle them locally only
         *
         * Just handle roster matches the same way for both clients & servers,
         * but limit server requests to local probes only.
         * REMEMBER TO NOT ALLOW REMOTE CLIENT REQUESTS.
         */

        JID stanzaFrom = packet.getStanzaFrom();

        // do not allow requests by remote clients or unknown servers
        if (stanzaFrom == null ||
                !repository.isNetworkDomain(stanzaFrom.getDomain()) ||
                isRemoteClient(stanzaFrom)) {
            log.log(Level.WARNING, "Denying request from {0}", stanzaFrom);
            return;
        }

        try {
            StanzaType type = packet.getType();
            String xmlns = packet.getElement().getXMLNSStaticStr(Iq.IQ_QUERY_PATH);

            if (xmlns == XMLNS) {
                if (type == StanzaType.get) {
                    // if sender is a remote server, limit probes to local results only
                    boolean localOnly = isRemoteProbe(stanzaFrom);

                    List<Element> items = packet.getElemChildrenStaticStr(Iq.IQ_QUERY_PATH);
                    if (items != null) {
                        String serverDomain = getDefVHostItem().getDomain();

                        Set<BareJID> found = new HashSet<>();
                        Set<BareJID> remote = localOnly ? null : new HashSet<>();
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
                                if (isLocalJID(localJid)) {
                                    if (log.isLoggable(Level.FINEST)) {
                                        log.log(Level.FINEST, "found local user {0}", jid);
                                    }

                                    // local user
                                    found.add(localJid);
                                }
                                else if (remote != null) {
                                    if (log.isLoggable(Level.FINEST)) {
                                        log.log(Level.FINEST, "remote lookup for user {0}", jid);
                                    }

                                    // queue for remote lookup
                                    remote.add(jid);
                                }
                            }
                        }

                        boolean remotePending = false;
                        if (remote != null && remote.size() > 0) {
                            // process remote entries
                            remotePending = (remoteLookup(stanzaFrom, remote, packet.getStanzaId(), found) > 0);
                        }

                        if (!remotePending) {
                            // local results only

                            // notify listener first
                            Queue<Packet> results = new LinkedList<>();
                            ProbeInfo info = new ProbeInfo();
                            info.stanzaId = packet.getStanzaId();
                            if (!probeManager.notifyProbeResult(info, results)) {
                                // return result immediately
                                Element query = new Element("query");
                                query.setXMLNS(XMLNS);

                                for (BareJID jid : found) {
                                    Element item = new Element("item");
                                    item.setAttribute("jid", jid.toString());
                                    query.addChild(item);
                                }

                                addOutPacket(packet.okResult(query, 0));
                            }

                            // add any packet queued by the listener
                            addOutPackets(results);
                        }

                        // packet was processed successfully
                        packet.processedBy(getName());
                    }
                }

                else if (type == StanzaType.result) {
                    String requestId = packet.getStanzaId();
                    synchronized (probes) {
                        ProbeInfo probe = probes.get(requestId);
                        if (probe != null) {
                            // we have a pending probe
                            probe.numReplies++;

                            List<Element> items = packet.getElemChildrenStaticStr(Iq.IQ_QUERY_PATH);
                            if (items != null) {
                                for (Element item : items) {
                                    if (!item.getName().equals("item")) {
                                        // not a roster item
                                        continue;
                                    }

                                    // add JID to storage
                                    BareJID jid = BareJID.bareJIDInstance(item.getAttributeStaticStr("jid"));
                                    probe.storage.add(jid);
                                }
                            }

                            if (probe.numReplies >= probe.maxReplies) {
                                // all replies are here, send back result to original requester
                                sendResult(probe);
                                probes.remove(requestId);
                            }
                        }
                    }
                }
            }
        }
        catch (TigaseStringprepException e) {
            log.log(Level.WARNING, "Invalid JID string", e);
            try {
                addOutPacket(Authorization.BAD_REQUEST.getResponseMessage(packet, "Invalid JID string", false));
            }
            catch (PacketErrorTypeException pe) {
                // ignored
            }
        }
    }

    /**
     * Starts a network-wide lookup.
     * @param user original client requester
     * @param jidList list of JIDs to look for
     * @param stanzaId stanza ID of the original client request (for generating the final result)
     * @param localJidList list of already found locally JIDs, used as storage for users that will be found
     * @return number of requests sent - should match number of enabled servers - 1 (ourself)
     */
    private int remoteLookup(JID user, Collection<BareJID> jidList, String stanzaId, Set<BareJID> localJidList) {
        // generate a unique internal request id
        String requestId = UUID.randomUUID().toString();
        ProbeInfo info = null;

        List<ServerlistRepository.ServerInfo> serverlist = repository.getList();
        for (ServerlistRepository.ServerInfo server : serverlist) {
            String serverName = server.getHost();
            if (server.isEnabled() && !serverName.equalsIgnoreCase(user.getDomain())) {
                JID serverJid = JID.jidInstanceNS(getName(), serverName);

                // build roster match packet
                Packet roster = Packet.packetInstance(buildRosterMatch(jidList, requestId, serverName),
                        getComponentPublicId(), serverJid);
                // send it to remote server
                addOutPacket(roster);

                synchronized (probes) {
                    info = probes.get(requestId);
                    if (info == null) {
                        info = new ProbeInfo();
                        info.timestamp = System.currentTimeMillis();
                        info.id = requestId;
                        info.sender = user;
                        info.stanzaId = stanzaId;
                        info.storage = localJidList;
                        info.maxReplies = 1;
                        probes.put(requestId, info);
                    }
                    else {
                        info.maxReplies++;
                    }
                }
            }
        }

        return (info != null) ? info.maxReplies : 0;
    }

    /**
     * Builds a roster match IQ for remote lookup requests.
     * JIDs domain will be replaced by the given server name.
     */
    private Element buildRosterMatch(Collection<BareJID> jidList, String id, String serverName) {
        Element iq = new Element(Iq.ELEM_NAME);
        iq.setAttribute(Iq.ID_ATT, id);
        iq.setAttribute(Iq.TYPE_ATT, StanzaType.get.toString());

        Element query = new Element("query");
        query.setXMLNS(XMLNS);

        for (BareJID jid : jidList) {
            Element item = new Element("item");
            item.setAttribute("jid", BareJID.bareJIDInstanceNS(jid.getLocalpart(), serverName).toString());
            query.addChild(item);
        }

        iq.addChild(query);
        return iq;
    }

    private void sendResult(ProbeInfo info) {
        // notify listener first
        Queue<Packet> results = new LinkedList<>();
        if (!probeManager.notifyProbeResult(info, results)) {
            Element iq = new Element(Iq.ELEM_NAME);
            iq.setAttribute(Iq.ID_ATT, info.stanzaId);
            iq.setAttribute(Iq.TYPE_ATT, StanzaType.result.toString());

            Element query = new Element("query");
            query.setXMLNS(XMLNS);

            for (BareJID jid : info.storage) {
                Element item = new Element("item");
                item.setAttribute("jid", jid.toString());
                query.addChild(item);
            }

            iq.addChild(query);

            addOutPacket(Packet.packetInstance(iq, null, info.sender));
        }

        // add any packet queued by the listener
        addOutPackets(results);
    }

    @Override
    public synchronized void everySecond() {
        super.everySecond();
        purgeTimedOutRequests();
    }

    public JID getComponentPublicId() {
        if (publicId == null)
            publicId = JID.jidInstanceNS(getName(), getDefVHostItem().getDomain());
        return publicId;
    }

    private void purgeTimedOutRequests() {
        synchronized (probes) {
            Iterator<ProbeInfo> entries = probes.values().iterator();
            while (entries.hasNext()) {
                ProbeInfo info = entries.next();
                long diff = System.currentTimeMillis() - info.timestamp;
                if (diff > REQUEST_TIMEOUT) {
                    sendResult(info);
                    entries.remove();
                }
            }
        }
    }

    /** Returns true if the given JID is an authorized remote probe component. */
    private boolean isRemoteProbe(JID jid) {
        return getName().equalsIgnoreCase(jid.getLocalpart()) &&
                repository.isNetworkDomain(jid.getDomain());
    }

    /** Returns true if the given JID is a remote client (domain-only lookup, no sess-man invocation). */
    private boolean isRemoteClient(JID jid) {
        return !getName().equalsIgnoreCase(jid.getLocalpart()) &&
                !jid.getDomain().equalsIgnoreCase(getDefVHostItem().getDomain());
    }

    /** Returns true if the given JID is registered locally (user repository lookup). */
    protected boolean isLocalJID(BareJID jid) {
        try {
            return user_repository.getUserUID(jid) > 0;
        }
        catch (TigaseDBException e) {
            log.log(Level.WARNING, "error reading from user repository", e);
            return false;
        }
    }

    @Override
    public int processingInThreads() {
        return NUM_THREADS;
    }

    @Override
    public Map<String, Object> getDefaults(Map<String, Object> params) {
        Map<String, Object> defs = super.getDefaults(params);
        defs.put("packet-types", new String[]{"iq"});
        return defs;
    }

    @Override
    public void setProperties(Map<String, Object> props) throws ConfigurationException {
        super.setProperties(props);
        try {
            repository.init(props);
            repository.reload();
        }
        catch (Exception e) {
            throw new ConfigurationException("unable to initialize push data repository", e);
        }

        try {
            user_repository = RepositoryFactory.getUserRepository(null, (String) props.get("db-uri"), null);
        }
        catch (Exception e) {
            throw new ConfigurationException("unable to initialize user data repository", e);
        }

        // init probe manager
        probeManager = ProbeManager.init(user_repository);

        updateServiceDiscoveryItem(getName(), null, getDiscoDescription(), false, NODE);
    }

    @Override
    public String getDiscoDescription() {
        return DISCO_DESCRIPTION;
    }

    @Override
    public String getDiscoCategoryType() {
        return "generic";
    }

    @Override
    public void setVHostManager(VHostManagerIfc manager) {
        super.setVHostManager(manager);
        authorizedSender = JID.jidInstanceNS("internal", manager.getDefVHostItem().getDomain());
    }

    public static Packet createProbeRequest(String id, BareJID... users) {
        ProbeComponent instance = (ProbeComponent) XMPPServer.getComponent("probe");
        String domain = instance.getDefVHostItem().getDomain();

        Element match = new Element("query", new String[] { "xmlns" }, new String[] { XMLNS });
        for (BareJID user : users)
            match.addChild(new Element("item", new String[] { "jid" }, new String[] { user.toString() }));

        Element iq = new Element("iq", new String[] { "id", "type" }, new String[] { id, StanzaType.get.toString() });
        iq.addChild(match);

        return Packet.packetInstance(iq, instance.authorizedSender, JID.jidInstanceNS("probe", domain));
    }

}
