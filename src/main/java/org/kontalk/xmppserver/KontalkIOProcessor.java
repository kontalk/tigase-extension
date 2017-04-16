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

package org.kontalk.xmppserver;

import tigase.server.Message;
import tigase.server.Packet;
import tigase.server.xmppclient.StreamManagementIOProcessor;
import tigase.xml.Element;
import tigase.xmpp.XMPPIOService;

import java.util.ArrayDeque;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Custom IO processor for Kontalk.
 * @author Daniele Ricci
 */
public class KontalkIOProcessor extends StreamManagementIOProcessor {

    private static final Logger log = Logger.getLogger(KontalkIOProcessor.class.getCanonicalName());

    @Override
    protected boolean shouldRequestAck(XMPPIOService service, OutQueue outQueue) {
        return super.shouldRequestAck(service, outQueue) ||
                (outQueue instanceof MyOutQueue &&
                        ((MyOutQueue) outQueue).messagesWaitingForAck() > 0);
    }

    @Override
    protected boolean shouldIncrementIncoming(XMPPIOService service, Packet packet) {
        return !ClientStateIndication.isElement(packet);
    }

    @Override
    protected OutQueue newOutQueue() {
        return new MyOutQueue();
    }

    private static class MyOutQueue extends OutQueue {
        private int messagesWaiting;

        @Override
        public void append(Packet packet) {
            if (!packet.wasProcessedBy(XMLNS)) {
                if (shouldRequestAck(packet)) {
                    messagesWaiting++;
                }
                super.append(packet);
            }
        }

        @Override
        public void ack(int value) {
            int count = get() - value;

            if (count < 0) {
                count = (Integer.MAX_VALUE - value) + get() + 1;
            }

            ArrayDeque<Entry> queue = getQueue();
            if (log.isLoggable(Level.FINEST)) {
                log.log(Level.FINEST, "acking {0} packets", new Object[] { queue.size() - count });
            }

            while (count < queue.size()) {
                Entry entry = queue.poll();
                Packet packet = entry.getPacketWithStamp();
                if (shouldRequestAck(packet)) {
                    if (log.isLoggable(Level.FINEST)) {
                        log.log(Level.FINEST, "acking message: {0}", packet.toString());
                    }
                    messagesWaiting--;
                }
            }
        }

        private boolean shouldRequestAck(Packet packet) {
            if (packet.getElemName() == Message.ELEM_NAME) {
                Element element = packet.getElement();

                // check for message body or delivery receipt
                return (element.getChild("body") != null ||
                        element.getChild("received", "urn:xmpp:receipts") != null);
            }

            return false;
        }

        public int messagesWaitingForAck() {
            return messagesWaiting;
        }
    }

}
