package org.kontalk.xmppserver.probe;


import tigase.server.AbstractMessageReceiver;
import tigase.server.Packet;
import tigase.server.XMPPServer;
import tigase.server.xmppsession.SessionManager;
import tigase.xmpp.BareJID;

/**
 * The network probe component.
 * This components is used to lookup users and other information in the network.
 * @author Daniele Ricci
 */
public class ProbeComponent extends AbstractMessageReceiver {

    @Override
    public void processPacket(Packet packet) {
        // TODO SessionManager sessMan = (SessionManager) XMPPServer.getComponent("sess-man");
        // TODO sessMan.containsJidLocally((BareJID) null);
    }

}
