package org.kontalk.xmppserver;


import tigase.db.NonAuthUserRepository;
import tigase.server.Packet;
import tigase.xmpp.XMPPPreprocessorIfc;
import tigase.xmpp.XMPPProcessor;
import tigase.xmpp.XMPPResourceConnection;
import tigase.xmpp.impl.roster.RosterAbstract;

import java.util.Map;
import java.util.Queue;

/**
 * Kontalk roster implementation.
 * This plugin will lookup users in the Kontalk network by probing every server or by polling the local cache.
 * If a roster request contains items, the packet will be processed by this plugin and then filtered.
 * @author Daniele Ricci
 */
public class KontalkRoster extends XMPPProcessor implements XMPPPreprocessorIfc {

    public static final String ID = "kontalk/" + RosterAbstract.XMLNS;

    @Override
    public boolean preProcess(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings) {
        // TODO
        return false;
    }

    @Override
    public String id() {
        return ID;
    }

    // TODO "sup" methods

}
