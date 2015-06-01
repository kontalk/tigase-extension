/*
 * Kontalk XMPP Tigase extension
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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

import org.junit.Before;
import org.junit.Test;
import tigase.conf.ConfigurationException;
import tigase.server.Iq;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;

import java.util.*;

import static org.junit.Assert.*;


public class ProbeComponentTest {

    private ProbeComponentMock compPrime;
    private ProbeComponentMock compBeta;

    private static final class ProbeComponentMock extends ProbeComponent {
        private final String localPart;
        protected Queue<Packet> queue = new ArrayDeque<>();
        private final String domain;

        public ProbeComponentMock(String domain, String localPart) {
            this.domain = domain;
            this.localPart = localPart;
        }

        @Override
        public String getName() {
            return "probe";
        }

        @Override
        public BareJID getDefVHostItem() {
            return BareJID.bareJIDInstanceNS(domain);
        }

        @Override
        protected boolean addOutPacket(Packet packet) {
            queue.offer(packet);
            return true;
        }

        @Override
        protected boolean isLocalJID(BareJID jid) {
            return jid.compareTo(BareJID.bareJIDInstanceNS(localPart, domain)) == 0;
        }
    }

    //@Before
    public void setUp() throws ConfigurationException {
        Map<String, Object> cfg = new HashMap<>();
        // FIXME non-replayable
        cfg.put("db-uri", "jdbc:mysql://localhost:3306/tigase?user=root&password=ciao&useUnicode=true&characterEncoding=UTF-8&autoCreateUser=true");
        compPrime = new ProbeComponentMock("prime.kontalk.net", "admin");
        compPrime.setProperties(cfg);
        compBeta = new ProbeComponentMock("beta.kontalk.net", "pippo");
        compBeta.setProperties(cfg);
    }

    //@Test
    public void testSendRequest() throws TigaseStringprepException {
        String id = UUID.randomUUID().toString();

        Element iq = new Element(Iq.ELEM_NAME);
        iq.setAttribute(Iq.ID_ATT, id);
        iq.setAttribute(Iq.TYPE_ATT, StanzaType.get.toString());

        Element query = new Element("query");
        query.setXMLNS(ProbeComponent.XMLNS);

        List<BareJID> jidList = new ArrayList<>();
        jidList.add(BareJID.bareJIDInstanceNS("admin@prime.kontalk.net"));
        jidList.add(BareJID.bareJIDInstanceNS("pippo@prime.kontalk.net"));
        jidList.add(BareJID.bareJIDInstanceNS("12345678@prime.kontalk.net"));
        jidList.add(BareJID.bareJIDInstanceNS("pluto@beta.kontalk.net"));

        for (BareJID jid : jidList) {
            Element item = new Element("item");
            item.setAttribute("jid", jid.toString());
            query.addChild(item);
        }

        iq.addChild(query);

        JID stanzaFrom = JID.jidInstance("admin@prime.kontalk.net");
        compPrime.processPacket(Packet.packetInstance(iq, stanzaFrom, null));

        System.out.println("prime: " + compPrime.queue);
        assertEquals(1, compPrime.queue.size());

        Packet request = compPrime.queue.poll();
        if (request != null) {
            compBeta.processPacket(request);

            System.out.println("beta: " + compBeta.queue);
            assertEquals(1, compBeta.queue.size());

            Packet response = compBeta.queue.poll();
            if (response != null) {
                compPrime.processPacket(response);

                System.out.println("prime: " + compPrime.queue);
                assertEquals(1, compPrime.queue.size());
                assertEquals(id, compPrime.queue.poll().getStanzaId());
            }
        }
    }

}
