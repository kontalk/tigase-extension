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

import tigase.db.TigaseDBException;
import tigase.db.UserRepository;
import tigase.server.Packet;
import tigase.xmpp.BareJID;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Handles listeners and probe requests from plugins.
 * @author Daniele Ricci
 */
public class ProbeManager {
    private static Logger log = Logger.getLogger(ProbeManager.class.getName());

    private static ProbeManager instance;

    private static final class ProbeListenerInfo {
        ProbeListener listener;
        Object userData;

        ProbeListenerInfo(ProbeListener listener, Object userData) {
            this.listener = listener;
            this.userData = userData;
        }
    }

    // key: ProbeInfo.stanzaId
    private final Map<String, ProbeListenerInfo> listeners;

    private final UserRepository userRepository;

    private ProbeManager(UserRepository repo) {
        listeners = new HashMap<>();
        userRepository = repo;
    }

    static ProbeManager init(UserRepository repo) {
        instance = new ProbeManager(repo);
        return instance;
    }

    public static ProbeManager getInstance() {
        return instance;
    }

    /**
     * Notifies the probe listener linked to the given request.
     * @return true to block the result packet for the original requestor
     */
    boolean notifyProbeResult(ProbeInfo info, Queue<Packet> results) {
        ProbeListenerInfo l = listeners.get(info.stanzaId);
        if (l != null) {
            try {
                return l.listener.onProbeResult(info, l.userData, results);
            }
            finally {
                listeners.remove(info.stanzaId);
            }
        }
        return false;
    }

    /**
     * Checks for a registered user (only local part is considered).
     * @param user the user to check for
     * @param listener the listener to be called after lookup is completed
     * @return null if the user was found locally, a request ID otherwise. Expect the listener to be called then.
     */
    public String probe(BareJID user, ProbeListener listener, Object userData, Queue<Packet> results) {
        boolean foundLocally = false;
        // shortcut to check locally immediately
        try {
            if (userRepository.getUserUID(user) > 0) {
                foundLocally = true;
            }
        }
        catch (TigaseDBException e) {
            if (log.isLoggable(Level.WARNING)) {
                log.log(Level.WARNING, "unable to lookup user {0} locally", user);
            }
        }

        if (!foundLocally) {
            // request a probe
            String requestId = UUID.randomUUID().toString();
            Packet probe = ProbeComponent.createProbeRequest(requestId, user);
            // store the listener
            listeners.put(requestId, new ProbeListenerInfo(listener, userData));
            // send the packet out
            results.offer(probe);

            return requestId;
        }

        // user found locally
        return null;
    }

}
