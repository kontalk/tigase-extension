/*
 * Kontalk XMPP Tigase extension
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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


/**
 * Custom IO processor for Kontalk.
 * @author Daniele Ricci
 */
public class KontalkIOProcessor extends StreamManagementIOProcessor {

    @Override
    protected boolean shouldRequestAck(XMPPIOService service, OutQueue outQueue) {
        return super.shouldRequestAck(service, outQueue) ||
                (outQueue instanceof MyOutQueue &&
                        ((MyOutQueue) outQueue).messageWaitingForAck() > 0);
    }

    @Override
    protected OutQueue newOutQueue(XMPPIOService service) {
        return new MyOutQueue();
    }

    private static class MyOutQueue extends OutQueue {
        private int messageWaiting;

        @Override
        public void append(Packet packet) {
            if (!packet.wasProcessedBy(XMLNS)) {
                if (shouldRequestAck(packet)) {
                    messageWaiting++;
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

            ArrayDeque<Packet> queue = getQueue();
            while (count < queue.size()) {
                Packet packet = queue.poll();
                if (shouldRequestAck(packet))
                    messageWaiting--;
            }
        }

        private boolean shouldRequestAck(Packet packet) {
            if (packet.getElemName() == Message.ELEM_NAME) {
                Element element = packet.getElement();

                // check for message body
                if (element.getChild("body") != null)
                    return true;

                // check for delivery receipt
                if (element.getChild("received", "urn:xmpp:receipts") != null)
                    return true;
            }

            return false;
        }

        public int messageWaitingForAck() {
            return messageWaiting;
        }
    }

}
