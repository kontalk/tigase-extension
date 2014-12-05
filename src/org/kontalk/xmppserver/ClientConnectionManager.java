package org.kontalk.xmppserver;

import tigase.server.Message;
import tigase.server.Packet;


/**
 * Custom client connection manager that requeues undelivered message stanzas.
 * @author Daniele Ricci
 *
 */
public class ClientConnectionManager extends tigase.server.xmppclient.ClientConnectionManager {

    @Override
    public boolean processUndeliveredPacket(Packet packet, String errorMessage) {
        if (packet.getElemName() == Message.ELEM_NAME) {
            Packet result = packet.copyElementOnly();
            result.initVars(packet.getStanzaFrom(), packet.getStanzaTo());
            addOutPacket(result);
            return true;
        }
        else {
            return super.processUndeliveredPacket(packet, errorMessage);
        }
    }

}
