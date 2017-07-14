package org.kontalk.xmppserver;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;
import tigase.xmpp.XMPPException;
import tigase.xmpp.XMPPResourceConnection;
import tigase.xmpp.impl.ProcessorTestCase;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Queue;

import static org.junit.Assert.*;


public class ClientStateIndicationTest extends ProcessorTestCase {

    private ClientStateIndication csi;

    @Before
    public void setUp() throws Exception {
        csi = new ClientStateIndication();
        csi.init(new HashMap<>());
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        csi = null;
        super.tearDown();
    }

    @Test
    public void testEnable() throws XMPPException, TigaseStringprepException {
        String recipient = "recipient-1@localhost";
        JID recp1 = JID.jidInstanceNS(recipient + "/res1");
        JID connId1 = JID.jidInstanceNS("c2s@localhost/recipient1-res1");
        XMPPResourceConnection session1 = getSession(connId1, recp1);

        enableCSI(session1);
        assertTrue(session1.getSessionData(ClientStateIndication.SESSION_QUEUE) instanceof ClientStateIndication.InternalQueue);
    }

    @Test
    public void testEnableDisable() throws XMPPException, TigaseStringprepException {
        String recipient = "recipient-1@localhost";
        JID recp1 = JID.jidInstanceNS(recipient + "/res1");
        JID connId1 = JID.jidInstanceNS("c2s@localhost/recipient1-res1");
        XMPPResourceConnection session1 = getSession(connId1, recp1);

        enableCSI(session1);
        assertTrue(session1.getSessionData(ClientStateIndication.SESSION_QUEUE) instanceof ClientStateIndication.InternalQueue);
        disableCSI(session1);
        assertNull(session1.getSessionData(ClientStateIndication.SESSION_QUEUE));
    }

    @Test
    public void testDisable() throws XMPPException, TigaseStringprepException {
        String recipient = "recipient-1@localhost";
        JID recp1 = JID.jidInstanceNS(recipient + "/res1");
        JID connId1 = JID.jidInstanceNS("c2s@localhost/recipient1-res1");
        XMPPResourceConnection session1 = getSession(connId1, recp1);

        disableCSI(session1);
        assertNull(session1.getSessionData(ClientStateIndication.SESSION_QUEUE));
    }

    @Test
    public void testMessagesToInactive() throws XMPPException, TigaseStringprepException {
        String recipient = "recipient-1@localhost";
        JID recp1 = JID.jidInstanceNS(recipient + "/res1");
        JID connId1 = JID.jidInstanceNS("c2s@localhost/recipient1-res1");
        XMPPResourceConnection session1 = getSession(connId1, recp1);

        enableCSI(session1);

        ArrayDeque<Packet> results = new ArrayDeque<>();
        Packet p = Packet.packetInstance("message", "sender-1@localhost/res1", recp1.toString(), StanzaType.chat);
        p.setPacketTo(connId1);
        results.offer(p);
        Packet[] expected = results.toArray(new Packet[results.size()]);
        csi.filter(p, session1, null, results);
        Packet[] processed = results.toArray(new Packet[results.size()]);
        Assert.assertArrayEquals(expected, processed);
    }

    @Test
    public void testPresenceToInactive() throws XMPPException, TigaseStringprepException {
        String recipient = "recipient-1@localhost";
        JID recp1 = JID.jidInstanceNS(recipient + "/res1");
        JID connId1 = JID.jidInstanceNS("c2s@localhost/recipient1-res1");
        XMPPResourceConnection session1 = getSession(connId1, recp1);

        enableCSI(session1);

        ArrayDeque<Packet> results = new ArrayDeque<>();
        Packet p = Packet.packetInstance("presence", "sender-1@localhost/res1", recp1.toString(), StanzaType.available);
        p.setPacketTo(connId1);
        results.offer(p);
        Packet[] expected = new Packet[0];
        csi.filter(p, session1, null, results);
        Packet[] processed = results.toArray(new Packet[results.size()]);
        Assert.assertArrayEquals(expected, processed);
    }

    @Test
    public void testFlushStopping() throws XMPPException, TigaseStringprepException {
        String recipient = "recipient-1@localhost";
        JID recp1 = JID.jidInstanceNS(recipient + "/res1");
        JID connId1 = JID.jidInstanceNS("c2s@localhost/recipient1-res1");
        XMPPResourceConnection session1 = getSession(connId1, recp1);

        enableCSI(session1);

        ArrayDeque<Packet> results = new ArrayDeque<>();
        Packet p = Packet.packetInstance("presence", "sender-1@localhost/res1", recp1.toString(), StanzaType.available);
        p.setPacketTo(connId1);
        csi.filter(p, session1, null, results);

        results.clear();
        Packet m = Packet.packetInstance("message", "sender-1@localhost/res1", recp1.toString(), StanzaType.chat);
        m.getElement().addChild(new Element("received", new String[]{ "xmlns" }, new String[] { "urn:xmpp:receipts" }));
        m.setPacketTo(connId1);
        results.offer(m);
        csi.filter(m, session1, null, results);

        results.clear();
        results.offer(m);
        Packet[] expected = results.toArray(new Packet[results.size()]);
        results.clear();
        csi.stopped(session1, results, new HashMap<>());
        Packet[] processed = results.toArray(new Packet[results.size()]);
        Assert.assertArrayEquals(expected, processed);
    }

    private Queue<Packet> enableCSI(XMPPResourceConnection session) throws TigaseStringprepException, XMPPException {
        Packet p = Packet.packetInstance(new Element(ClientStateIndication.ELEM_INACTIVE,
                new String[] { "xmlns" }, new String[] { ClientStateIndication.XMLNS }));
        ArrayDeque<Packet> results = new ArrayDeque<>();
        csi.processFromUserToServerPacket(session.getConnectionId(), p, session, null, results, null);
        return results;
    }

    private Queue<Packet> disableCSI(XMPPResourceConnection session) throws TigaseStringprepException, XMPPException {
        Packet p = Packet.packetInstance(new Element(ClientStateIndication.ELEM_ACTIVE,
                new String[] { "xmlns" }, new String[] { ClientStateIndication.XMLNS }));
        ArrayDeque<Packet> results = new ArrayDeque<>();
        csi.processFromUserToServerPacket(session.getConnectionId(), p, session, null, results, null);
        return results;
    }

}
