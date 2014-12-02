package org.kontalk.xmppserver.push;

import java.io.IOException;
import java.util.Map;

import tigase.conf.ConfigurationException;
import tigase.xmpp.BareJID;


/**
 * Interface for a push notifications provider.
 * @author Daniele Ricci
 */
public interface PushProvider {

    public void init(Map<String, Object> props) throws ConfigurationException;

    public String getName();

    // for service discovery
    public String getNode();
    public String getJidPrefix();
    public String getDescription();

    public void register(BareJID jid, String registrationId);

    public void sendPushNotification(BareJID jid) throws IOException;

}
