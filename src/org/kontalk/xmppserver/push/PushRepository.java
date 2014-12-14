package org.kontalk.xmppserver.push;

import tigase.db.DBInitException;
import tigase.db.TigaseDBException;
import tigase.xmpp.BareJID;

import java.util.List;
import java.util.Map;


/**
 * Interface for push registration repository.
 * @author Daniele Ricci
 */
public interface PushRepository {

    public void init(Map<String, Object> props) throws DBInitException;

    /** Registers a user. */
    public void register(BareJID jid, String provider, String registrationId) throws TigaseDBException;

    /** Unregisters a user. */
    public void unregister(BareJID jid, String provider) throws TigaseDBException;

    /** Retrieves registration info with all providers for a user. */
    public List<PushRegistrationInfo> getRegistrationInfo(BareJID jid) throws TigaseDBException;

}
