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
