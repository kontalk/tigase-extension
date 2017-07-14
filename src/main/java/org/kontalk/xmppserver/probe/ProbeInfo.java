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

import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import java.util.Set;

/**
 * Probe information class.
 * @author Daniele Ricci
 */
public final class ProbeInfo {

    /**
     * Timestamp of request.
     */
    long timestamp;

    /**
     * The final destination user.
     */
    JID sender;

    /**
     * Stanza ID.
     */
    String stanzaId;

    /**
     * Request ID.
     */
    String id;

    /**
     * Storage for matched JIDs.
     */
    Set<BareJID> storage;

    /**
     * Number of replies expected.
     */
    int maxReplies;

    /**
     * Number of replies received.
     */
    int numReplies;

    public long getTimestamp() {
        return timestamp;
    }

    public JID getSender() {
        return sender;
    }

    public String getStanzaId() {
        return stanzaId;
    }

    public String getId() {
        return id;
    }

    public Set<BareJID> getStorage() {
        return storage;
    }

    public int getMaxReplies() {
        return maxReplies;
    }

    public int getNumReplies() {
        return numReplies;
    }

    @Override
    public String toString() {
        return "ProbeInfo [id="+ id + ", stanzaId="+ stanzaId + ", replies=" + numReplies + ", storage=" + storage + "]";
    }
}
