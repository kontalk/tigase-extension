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

import tigase.server.Packet;

import java.util.Queue;

/**
 * A listener to be notified for probe responses.
 * @author Daniele Ricci
 */
public interface ProbeListener {

    /**
     * Called when a probe result is available.
     * Use the provided queue to push out packets.
     * @param info the probe result
     * @param userData user data provided before
     * @param results a queue to push results back to the server
     * @return true to block the result packet for the original requestor
     */
    boolean onProbeResult(ProbeInfo info, Object userData, Queue<Packet> results);

}
