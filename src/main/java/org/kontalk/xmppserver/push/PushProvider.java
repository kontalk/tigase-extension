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

    void init(Map<String, Object> props) throws ConfigurationException;

    String getName();

    // for service discovery
    String getNode();
    String getJidPrefix();
    String getDescription();

    void register(BareJID jid, String registrationId);
    void unregister(BareJID jid);

    void sendPushNotification(BareJID jid, PushRegistrationInfo info) throws IOException;

}
