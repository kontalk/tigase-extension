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

import tigase.db.NonAuthUserRepository;
import tigase.db.TigaseDBException;
import tigase.server.Iq;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.DomBuilderHandler;
import tigase.xml.Element;
import tigase.xml.SimpleParser;
import tigase.xml.SingletonFactory;
import tigase.xmpp.*;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * XEP-0191: Blocking Command.
 * Based on privacy lists.
 * @author Daniele Ricci
 */
public class BlockingCommand extends XMPPProcessorAbstract implements
        XMPPProcessorIfc, XMPPPreprocessorIfc, XMPPPacketFilterIfc {
    private static Logger log = Logger.getLogger(BlockingCommand.class.getName());

    private static final String XMLNS = "urn:xmpp:blocking";
    public static final String ID = "kontalk:" + XMLNS;

    private static final String[] XMLNSS = {XMLNS, XMLNS, XMLNS};

    private static final String[] IQ_BLOCKLIST_PATH = { Iq.ELEM_NAME, "blocklist" };
    private static final String[] IQ_BLOCK_PATH = { Iq.ELEM_NAME, "block" };
    private static final String[] IQ_UNBLOCK_PATH = { Iq.ELEM_NAME, "unblock" };
    private static final String[][] ELEMENTS = {
        IQ_BLOCKLIST_PATH,
        IQ_BLOCK_PATH,
        IQ_UNBLOCK_PATH,
    };

    private static final Element[] DISCO_FEATURES = {new Element("feature", new String[]{"var"},
            new String[]{XMLNS})};

    private static final String BLOCKLIST = "blocklist";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean preProcess(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings) {
        if ((session == null) ||!session.isAuthorized() ||
                packet.isXMLNSStaticStr(IQ_BLOCKLIST_PATH, XMLNS) ||
                packet.isXMLNSStaticStr(IQ_BLOCK_PATH, XMLNS) ||
                packet.isXMLNSStaticStr(IQ_UNBLOCK_PATH, XMLNS)) {
            return false;
        }

        return !allowed(packet, session);
    }

    @Override
    public void filter(Packet packet, XMPPResourceConnection session,
                       NonAuthUserRepository repo, Queue<Packet> results) {
        if ((session == null) ||!session.isAuthorized() ||
                (results == null) || (results.size() == 0)) {
            return;
        }
        for (Iterator<Packet> it = results.iterator(); it.hasNext(); ) {
            Packet res = it.next();

            if (log.isLoggable(Level.FINEST)) {
                log.log(Level.FINEST, "Checking outbound packet: {0}", res);
            }

            // Always allow presence unavailable to go, blocking command packets and
            // all other which are allowed by block list rules
            if ((res.getType() == StanzaType.unavailable) ||
                    res.isXMLNSStaticStr(IQ_BLOCKLIST_PATH, XMLNS) ||
                    res.isXMLNSStaticStr(IQ_BLOCK_PATH, XMLNS) ||
                    res.isXMLNSStaticStr(IQ_UNBLOCK_PATH, XMLNS) ||
                    allowed(res, session)) {
                continue;
            }
            if (log.isLoggable(Level.FINEST)) {
                log.log(Level.FINEST, "Packet not allowed to go, removing: {0}", res);
            }
            it.remove();
        }
    }


    private boolean allowed(Packet packet, XMPPResourceConnection session) {
        try {
            // If this is a preprocessing phase, always allow all packets to
            // make it possible for the client to communicate with the server.
            /*
            if (session.getConnectionId().equals(packet.getPacketFrom())) {
                return true;
            }
            */

            // allow packets for clients originated by the server
            if (packet.getStanzaFrom() == null && session.getJID().equals(packet.getStanzaTo())) {
                return true;
            }

            // allow packets without from attribute and packets with from attribute same as domain name
            if (/*(packet.getStanzaFrom() == null) || */(packet.getStanzaFrom() != null &&
                    (packet.getStanzaFrom().getLocalpart() == null) && session.getBareJID().getDomain().equals(packet.getStanzaFrom()
                    .getDomain()))) {
                return true;
            }

            // allow packets without to attribute and packets with to attribute same as domain name
            if ((packet.getStanzaTo() == null) || ((packet.getStanzaTo().getLocalpart() ==
                    null) && session.getBareJID().getDomain().equals(packet.getStanzaTo()
                    .getDomain()))) {
                return true;
            }

            BareJID sessionUserId = session.getBareJID();
            BareJID jid = packet.getStanzaFrom() != null ? packet.getStanzaFrom().getBareJID() : null;

            if ((jid == null) || sessionUserId.equals(jid)) {
                jid = packet.getStanzaTo().getBareJID();
            }

            Set<BareJID> list = getBlocklist(session);
            if (list != null && list.contains(jid))
                return false;
        }
        /*
        catch (NoConnectionIdException e) {
            // always allow, this is a server dummy session
        }
        */
        catch (NotAuthorizedException e) {
            // ignored
        }
        catch (TigaseDBException e) {
            log.log(Level.WARNING, "Database problem: " + e, e);
        }

        return true;
    }

    @Override
    public void processFromUserToServerPacket(JID connectionId, Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings) throws PacketErrorTypeException {
        if (log.isLoggable(Level.FINEST)) {
            log.finest("Processing packet: " + packet.toString());
        }

        try {
            switch (packet.getType()) {
                case get:
                    if (packet.getElement().getChild("blocklist", XMLNS) != null) {
                        handleBlocklistRequest(packet, session, results);
                    }
                    else {
                        throw new IllegalArgumentException();
                    }

                    break;

                case set:
                    Element block = packet.getElement().getChild("block", XMLNS);
                    if (block != null) {
                        handleBlockRequest(packet, session, block, results);
                    }

                    Element unblock = packet.getElement().getChild("unblock", XMLNS);
                    if (unblock != null) {
                        handleUnblockRequest(packet, session, unblock, results);
                    }

                    if (!packet.wasProcessedBy(ID)) {
                        throw new IllegalArgumentException();
                    }

                    break;

                default:
                    throw new IllegalArgumentException();
            }

        }

        catch (IllegalArgumentException e) {
            results.offer(Authorization.BAD_REQUEST.getResponseMessage(packet,
                    "Bad request.", true));
        }

        catch (NotAuthorizedException e) {
            results.offer(Authorization.NOT_AUTHORIZED.getResponseMessage(packet,
                    "You are not authorized to access block lists [" + e.getMessage() + "]", true));
        }
        catch (TigaseDBException e) {
            log.warning("Database problem: " + e);
            results.offer(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
                    "Database access problem, please contact administrator.", true));
        }
        catch (TigaseStringprepException e) {
            log.warning("Malformed JID: " + e);
            results.offer(Authorization.BAD_REQUEST.getResponseMessage(packet,
                    "Malformed JID.", true));
        }
    }

    private boolean handleBlocklistRequest(Packet packet, XMPPResourceConnection session, Queue<Packet> results)
            throws NotAuthorizedException, TigaseDBException, TigaseStringprepException {
        Element blocklist = new Element(BLOCKLIST);
        blocklist.setXMLNS(XMLNS);

        Set<BareJID> list = getBlocklist(session);
        if (list != null) {
            for (BareJID jid : list) {
                blocklist.addChild(new Element("item",
                        new String[] { "jid" },
                        new String[] { jid.toString() }
                ));
            }
        }

        results.offer(packet.okResult(blocklist, 0));
        packet.processedBy(ID);
        return true;
    }

    private void handleBlockRequest(Packet packet, XMPPResourceConnection session, Element block, Queue<Packet> results)
            throws NotAuthorizedException, TigaseDBException, TigaseStringprepException {

        int count = 0;
        List<Element> items = block.getChildren();
        Set<BareJID> list = null;
        if (items != null) {
            list = getBlocklist(session);

            for (Element item : items) {
                if ("item".equalsIgnoreCase(item.getName())) {
                    String jidString = item.getAttributeStaticStr("jid");
                    BareJID jid;
                    if (jidString != null) {
                        // if jid is not valid it will throw exception
                        try {
                            jid = BareJID.bareJIDInstance(jidString);
                        }
                        catch (TigaseStringprepException e) {
                            // invalid jid
                            throw new IllegalArgumentException("invalid jid: " + jidString);
                        }
                        count++;
                        list = addItem(list, jid);
                    }
                }
            }
        }

        packet.processedBy(ID);
        if (count > 0 && list != null) {
            setBlocklist(session, list);
            results.offer(packet.okResult((Element) null, 0));
        }
        else {
            // bad request
            throw new IllegalArgumentException();
        }
    }

    private void handleUnblockRequest(Packet packet, XMPPResourceConnection session, Element unblock, Queue<Packet> results)
            throws NotAuthorizedException, TigaseDBException, TigaseStringprepException {

        int count = 0;
        List<Element> items = unblock.getChildren();
        Set<BareJID> list = null;
        if (items != null) {
            list = getBlocklist(session);

            // no point in proceeding if block list doesn't exist
            if (list != null) {
                for (Element item : items) {
                    if ("item".equalsIgnoreCase(item.getName())) {
                        BareJID jid;
                        String jidString = item.getAttributeStaticStr("jid");
                        if (jidString != null) {
                            // if jid is not valid it will throw exception
                            try {
                                jid = BareJID.bareJIDInstance(jidString);
                            }
                            catch (TigaseStringprepException e) {
                                // invalid jid
                                throw new IllegalArgumentException("invalid jid: " + jidString);
                            }
                            count++;
                            removeItem(list, jid);
                        }
                    }
                }
            }
        }

        packet.processedBy(ID);
        if (count > 0 || list == null) {
            if (list != null) {
                setBlocklist(session, list);
            }
            results.offer(packet.okResult((Element) null, 0));
        }
        else {
            // bad request
            throw new IllegalArgumentException();
        }
    }

    private Set<BareJID> addItem(Set<BareJID> list, BareJID jid)
            throws NotAuthorizedException, TigaseDBException {
        if (list == null)
            list = new HashSet<>();

        list.add(jid);
        return list;
    }

    private void removeItem(Set<BareJID> list, BareJID jid)
            throws NotAuthorizedException, TigaseDBException {
        list.remove(jid);
    }

    /** Loads the user's block list. */
    private Set<BareJID> getBlocklist(XMPPResourceConnection session)
            throws NotAuthorizedException, TigaseDBException {

        @SuppressWarnings("unchecked")
        Set<BareJID> list = (Set<BareJID>) session.getCommonSessionData(BLOCKLIST);

        if (list == null) {
            String list_str = session.getData(BLOCKLIST, BLOCKLIST, null);

            if ((list_str != null) && !list_str.isEmpty()) {
                SimpleParser parser = SingletonFactory.getParserInstance();
                DomBuilderHandler domHandler = new DomBuilderHandler();

                parser.parse(domHandler, list_str.toCharArray(), 0, list_str.length());

                Queue<Element> elems = domHandler.getParsedElements();
                Element result = elems.poll();

                if (log.isLoggable(Level.FINEST)) {
                    log.log(Level.FINEST, "Loaded block list: {0}", result);
                }

                List<Element> children = result.getChildren();
                if (children != null) {
                    list = new HashSet<>(children.size());
                    for (Element item : children) {
                        list.add(BareJID.bareJIDInstanceNS(item.getAttributeStaticStr("jid")));
                    }
                }
            }
        }

        return list;
    }

    /** Saves the user's block list. */
    private void setBlocklist(XMPPResourceConnection session, Set<BareJID> list)
            throws NotAuthorizedException, TigaseDBException {
        session.setData(BLOCKLIST, BLOCKLIST, dumpBlocklist(list).toString());
        session.putCommonSessionData(BLOCKLIST, list);
    }

    private Element dumpBlocklist(Set<BareJID> list) {
        Element xml = new Element(BLOCKLIST);
        xml.setXMLNS(XMLNS);
        for (BareJID jid : list) {
            xml.addChild(new Element("item",
                    new String[] { "jid" },
                    new String[] { jid.toString() }
            ));
        }
        return xml;
    }

    @Override
    public void processServerSessionPacket(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings) throws PacketErrorTypeException {
        // not used
    }

    @Override
    public Element[] supDiscoFeatures(XMPPResourceConnection session) {
        if (log.isLoggable(Level.FINEST) && (session != null)) {
            log.finest("VHostItem: " + session.getDomain());
        }
        if ((session != null) && session.getDomain().isRegisterEnabled()) {
            return DISCO_FEATURES;
        }
        else {
            return null;
        }
    }

    @Override
    public String[][] supElementNamePaths() {
        return ELEMENTS;
    }

    @Override
    public String[] supNamespaces() {
        return XMLNSS;
    }

}
