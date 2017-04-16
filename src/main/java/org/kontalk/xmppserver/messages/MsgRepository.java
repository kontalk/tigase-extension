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

package org.kontalk.xmppserver.messages;

import tigase.db.Repository;
import tigase.db.TigaseDBException;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.XMPPResourceConnection;

import java.util.Date;
import java.util.Queue;


/**
 * Message repository interface.
 * Inspired by the original MsgRepositoryIfc by Tigase.
 * @author Daniele Ricci
 */
public interface MsgRepository extends Repository {

    /**
     * Deletes all expired messages.
     * @return number of deleted expired messages
     */
    int expireMessages() throws TigaseDBException;

    /** Loads all payloads for the given user's {@link JID} from repository. */
    Queue<Element> loadMessagesToJID(BareJID user, boolean delete) throws TigaseDBException;

    /**
     * Saves the massage to the repository.
     * @param expire date of expiration (UTC)
     */
    void storeMessage(BareJID user, Element msg, Date expire) throws TigaseDBException;

}
