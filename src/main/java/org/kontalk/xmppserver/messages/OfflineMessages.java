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

import tigase.db.MsgRepositoryIfc;
import tigase.db.NonAuthUserRepository;
import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.server.Packet;
import tigase.util.DNSResolver;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.*;
import tigase.xmpp.impl.C2SDeliveryErrorProcessor;
import tigase.xmpp.impl.FlexibleOfflineMessageRetrieval;
import tigase.xmpp.impl.Message;
import tigase.xmpp.impl.PresenceState;
import tigase.xmpp.impl.annotation.*;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.kontalk.xmppserver.messages.OfflineMessages.*;


/**
 * A more advanced offline messages implementation.
 * It uses an ad-hoc table and it is capable of expiring messages.
 * Inspired by the original OfflineMessages by Tigase.
 * @author Daniele Ricci
 */
@Id(ID)
@Handles({
    @Handle(path={PresenceState.PRESENCE_ELEMENT_NAME},xmlns=XMLNS)
})
@DiscoFeatures({
    "msgoffline"
})
public class OfflineMessages extends AnnotatedXMPPProcessor
        implements XMPPPostprocessorIfc, XMPPProcessorIfc {

    protected static final String XMLNS = "jabber:client";
    /**
     * This can be considered a second version of the original <em>msgoffline</em>.
     */
    protected static final String ID = "msgoffline2";

    /** Field holds the default hostname of the machine. */
    private static final String defHost = DNSResolver.getDefaultHostname();

    private static final Logger log = Logger.getLogger(OfflineMessages.class.getName());

    private static final int DEF_EXPIRE_SECONDS = 604800;

    private int messageExpire;
    private int presenceExpire;

    private Timer taskTimer;

    private MsgRepository msgRepo = new JDBCMsgRepository();
    private Message message = new Message();
    private final DateFormat formatter;

    {
        this.formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        this.formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    @Override
    public int concurrentQueuesNo() {
        return Runtime.getRuntime().availableProcessors();
    }

    @Override
    public void init(Map<String, Object> settings) throws TigaseDBException {
        super.init(settings);
        String uri = (String) settings.get("db-uri");
        msgRepo.initRepository(uri, null);

        try {
            messageExpire = (int) settings.get("message-expire");
        }
        catch (Exception e) {
            messageExpire = DEF_EXPIRE_SECONDS;
        }
        try {
            presenceExpire = (int) settings.get("presence-expire");
        }
        catch (Exception e) {
            presenceExpire = DEF_EXPIRE_SECONDS;
        }

        long hour = TimeUnit.HOURS.toMillis(1);
        taskTimer = new Timer(ID + " tasks", true);
        taskTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    if (log.isLoggable(Level.FINEST)) {
                        log.finest("Purging expired messages.");
                    }
                    msgRepo.expireMessages();
                }
                catch (TigaseDBException e) {
                    log.log(Level.WARNING, "error purging expired messages", e);
                }
            }
        }, hour, hour);

    }

    /**
     * Returns expiration time for the given packet.
     * @return expiration UTC time, or null for no expiration
     */
    private Date getExpiration(Packet packet) {
        int seconds;
        if (packet.getElemName() == tigase.server.Presence.ELEM_NAME) {
            seconds = presenceExpire;
        }
        else {
            seconds = messageExpire;
        }

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.SECOND, seconds);
        return cal.getTime();
    }

    @Override
    public void process(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo,
                        Queue<Packet> results, Map<String, Object> settings) throws XMPPException {
        if ( loadOfflineMessages( packet, session ) ){
            try {
                Queue<Packet> packets = restorePacketForOffLineUser(session, msgRepo);

                if ( packets != null ){
                    if ( log.isLoggable( Level.FINER ) ){
                        log.finer( "Sending offline messages: " + packets.size() );
                    }
                    waitForPresence(session, 250);
                    results.addAll( packets );
                }
            } catch ( TigaseDBException e ) {
                log.info( "Something wrong, DB problem, cannot load offline messages. " + e );
            }
        }
    }

    private void waitForPresence(XMPPResourceConnection session, int millis) {
        if (session.getPresence() == null || session.getPriority() <= 0) {
            try {
                Thread.sleep(millis);
            }
            catch (InterruptedException ignored) {
            }
        }
    }

    @Override
    public void postProcess(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo,
                            Queue<Packet> results, Map<String, Object> settings) {
        if (session == null || !message.hasConnectionForMessageDelivery(session)) {
            try {
                if (session != null && packet.getStanzaTo() != null && !session.isUserId(packet.getStanzaTo().getBareJID()))
                    return;

                savePacketForOffLineUser(packet, msgRepo);
            }
            catch (UserNotFoundException e) {
                if (log.isLoggable(Level.FINEST)) {
                    log.log(Level.FINEST, "unable to store offline packet: user not found ({0})", packet);
                }
            }
            catch (TigaseDBException e) {
                log.log(Level.WARNING, "TigaseDBException at trying to save packet for off-line user." + packet, e);
            }
            catch (NotAuthorizedException e) {
                if (log.isLoggable(Level.FINEST)) {
                    log.log(Level.FINEST, "unable to store offline packet: not authorized ({0})", packet);
                }
            }
        }
    }

    /**
     * Method determines whether offline messages should be loaded - the process
     * should be run only once per user session and only for available/null
     * presence with priority greater than 0.
     *
     *
     * @param packet a {@link Packet} object containing packet that should be
     *               verified and saved
     * @param conn   user session which keeps all the user session data and also
     *               gives an access to the user's repository data.
     *
     * @return {@code true} if the messages should be loaded, {@code false}
     *         otherwise.
     */
    private boolean loadOfflineMessages( Packet packet, XMPPResourceConnection conn ) {

        // If the user session is null or the user is anonymous just
        // ignore it.
        if ( ( conn == null ) || conn.isAnonymous() ){
            return false;
        }    // end of if (session == null)

        // Try to restore the offline messages only once for the user session
        if ( conn.getSessionData( ID ) != null ){
            return false;
        }

        // make sure this is broadcast presence as only in this case we should sent offline messages
        if (packet.getStanzaTo() != null)
            return false;

        // if we are using XEP-0013: Flexible offline messages retrieval then we skip loading
        if ( conn.getCommonSessionData(FlexibleOfflineMessageRetrieval.FLEXIBLE_OFFLINE_XMLNS) != null ){
            return false;
        }

        StanzaType type = packet.getType();

        if ( ( type == null ) || ( type == StanzaType.available ) ){

            // Should we send off-line messages now?
            // Let's try to do it here and maybe later I find better place.
            String priority_str = packet.getElemCDataStaticStr( tigase.server.Presence.PRESENCE_PRIORITY_PATH );
            int priority = 0;

            if ( priority_str != null ){
                try {
                    priority = Integer.decode( priority_str );
                } catch ( NumberFormatException e ) {
                    priority = 0;
                }    // end of try-catch
            }      // end of if (priority != null)
            if ( priority >= 0 ){
                conn.putSessionData( ID, ID );

                return true;
            }      // end of if (priority >= 0)
        }        // end of if (type == null || type == StanzaType.available)

        return false;
    }

    /**
     * Method restores all messages from repository for the JID of the current
     * session. All retrieved elements are then instantiated as {@code Packet}
     * objects added to {@code LinkedList} collection and, if possible, sorted by
     * timestamp.
     *
     * @param session user session which keeps all the user session data and also
     *             gives an access to the user's repository data.
     * @param repo an implementation of {@link MsgRepositoryIfc} interface
     *
     *
     * @return a {@link Queue} of {@link Packet} objects based on all stored
     *         payloads for the JID of the current session.
     *
     * @throws UserNotFoundException
     * @throws NotAuthorizedException
     */
    public Queue<Packet> restorePacketForOffLineUser( XMPPResourceConnection session,
                                                      MsgRepository repo )
            throws TigaseDBException, NotAuthorizedException {
        Queue<Element> elems = repo.loadMessagesToJID(session.getBareJID(), true);

        if ( elems != null ){
            LinkedList<Packet> pacs = new LinkedList<Packet>();
            Element elem;

            while ( ( elem = elems.poll() ) != null ) {
                try {
                    pacs.offer( Packet.packetInstance( elem ) );
                } catch ( TigaseStringprepException ex ) {
                    log.warning( "Packet addressing problem, stringprep failed: " + elem );
                }
            }    // end of while (elem = elems.poll() != null)
            try {
                if (pacs.size() > 1)
                    pacs.sort(new StampComparator());
            } catch ( NullPointerException e ) {
                try {
                    log.warning( "Can not sort off line messages: " + pacs + ",\n" + e );
                } catch ( Exception exc ) {
                    log.log( Level.WARNING, "Can not print log message.", exc );
                }
            }

            return pacs;
        }

        return null;
    }

    /**
     * Method stores messages to offline repository with the following rules
     * applied, i.e. saves only:
     * <ul>
     * <li> message stanza with either nonempty {@code <body>}, {@code <event>} or
     * {@code <header>} child element and only messages of type normal, chat.</li>
     * <li> presence stanza of type subscribe, subscribed, unsubscribe and
     * unsubscribed.</li>
     * </ul>
     * <br>
     * Processed messages are stamped with the {@code delay} element and
     * appropriate timestamp.
     * <br>
     *
     *
     * @param pac  a {@link Packet} object containing packet that should be
     *             verified and saved
     * @param repo a {@link MsgRepository} repository handler responsible for
     *             storing messages
     *
     * @return {@code true} if the packet was correctly saved to repository,
     *         {@code false} otherwise.
     *
     * @throws UserNotFoundException
     */
    public boolean savePacketForOffLineUser(Packet pac, MsgRepository repo)
            throws TigaseDBException {
        StanzaType type = pac.getType();

        // save only:
        // message stanza with either {@code <body>} or {@code <event>} child element and only of type normal, chat
        // presence stanza of type subscribe, subscribed, unsubscribe and unsubscribed
        if ( ( pac.getElemName().equals( "message" )
                && ( ( pac.getElemCDataStaticStr( tigase.server.Message.MESSAGE_BODY_PATH ) != null )
                || ( pac.getElement().getChild("request", "urn:xmpp:receipts") != null )
                || ( pac.getElement().getChild("received", "urn:xmpp:receipts") != null ) )
                && ( ( type == null ) || ( type == StanzaType.normal ) || ( type == StanzaType.chat ) ) )
                || ( pac.getElemName().equals( "presence" )
                && ( ( type == StanzaType.subscribe ) || ( type == StanzaType.subscribed )
                || ( type == StanzaType.unsubscribe ) || ( type == StanzaType.unsubscribed ) ) ) ){
            if ( log.isLoggable( Level.FINEST ) ){
                log.log( Level.FINEST, "Storing packet for offline user: {0}", pac );
            }

            Element elem = pac.getElement().clone();

            C2SDeliveryErrorProcessor.filterErrorElement(elem);

            String stamp;

            synchronized ( formatter ) {
                stamp = formatter.format( new Date() );
            }

            // remove any previous delay element
            Element delay = elem.getChild("delay", "urn:xmpp:delay");
            if (delay == null) {
                String from = pac.getStanzaTo().getDomain();
                Element x = new Element( "delay", "Offline Storage - " + defHost, new String[] {
                        "from",
                        "stamp", "xmlns" }, new String[] { from, stamp, "urn:xmpp:delay" } );

                elem.addChild(x);
            }

            repo.storeMessage(pac.getStanzaTo().getBareJID(), elem, getExpiration(pac));
            pac.processedBy(ID);

            return true;
        }

        return false;
    }

    /**
     * {@link Comparator} interface implementation for the purpose of sorting
     * Elements retrieved from the repository by the timestamp stored in
     * {@code delay} element.
     */
    private static class StampComparator
            implements Comparator<Packet> {

        @Override
        public int compare( Packet p1, Packet p2 ) {
            String stamp1 = null;
            String stamp2 = null;

            // Try XEP-0203 - the new XEP...
            Element stamp_el1 = p1.getElement().getChild( "delay", "urn:xmpp:delay" );

            if ( stamp_el1 == null ){
                // XEP-0091 support - the old one...
                stamp_el1 = p1.getElement().getChild( "x", "jabber:x:delay" );
            }
            if ( stamp_el1 != null ){
                stamp1 = stamp_el1.getAttributeStaticStr( "stamp" );
            }
            if (stamp1 == null) {
                stamp1 = "";
            }

            // Try XEP-0203 - the new XEP...
            Element stamp_el2 = p2.getElement().getChild( "delay", "urn:xmpp:delay" );

            if ( stamp_el2 == null ){
                // XEP-0091 support - the old one...
                stamp_el2 = p2.getElement().getChild( "x", "jabber:x:delay" );
            }
            if ( stamp_el2 != null ){
                stamp2 = stamp_el2.getAttributeStaticStr( "stamp" );
            }
            if (stamp2 == null) {
                stamp2 = "";
            }

            return stamp1.compareTo( stamp2 );
        }
    }

}
