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

package org.kontalk.xmppserver.presence;

import org.bouncycastle.openpgp.PGPException;
import org.kontalk.xmppserver.KontalkKeyring;
import org.kontalk.xmppserver.auth.KontalkAuth;
import tigase.db.NonAuthUserRepository;
import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.server.Packet;
import tigase.server.Presence;
import tigase.util.Base64;
import tigase.xml.Element;
import tigase.xmpp.*;
import tigase.xmpp.impl.roster.RosterAbstract;
import tigase.xmpp.impl.roster.RosterAbstract.PresenceType;
import tigase.xmpp.impl.roster.RosterFactory;
import tigase.xmpp.impl.roster.RosterFlat;

import java.io.IOException;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Presence plugin to inject the subscriber public key when requesting
 * subscription the first time.
 * @author Daniele Ricci
 */
public class PresenceSubscribePublicKey extends XMPPProcessor implements
        XMPPPreprocessorIfc {

    private static final String ID = "presence:urn:xmpp:pubkey:2";

    public static final String ELEM_NAME = "pubkey";
    public static final String XMLNS = "urn:xmpp:pubkey:2";

    private static final Logger log = Logger.getLogger(PresenceSubscribePublicKey.class.getName());

    private final RosterAbstract roster_util = getRosterUtil();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean preProcess(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings) {
        if (session != null && session.isAuthorized() && packet.getElemName().equals(Presence.ELEM_NAME)) {

            try {
                PresenceType presenceType = roster_util.getPresenceType(session, packet);
                if (presenceType == PresenceType.in_subscribe &&
                        !roster_util.isSubscribedFrom(session, packet.getStanzaFrom())) {

                    // check if pubkey element was already added
                    if (!hasPublicKey(packet)) {
                        Packet res = addPublicKey(session, packet);
                        if (res != null) {
                            packet.processedBy(ID);
                            results.offer(res);
                            return true;
                        }
                    }
                }
            }
            catch (NotAuthorizedException e) {
                log.log(Level.WARNING, "not authorized!?", e);

            }
            catch (UserNotFoundException e) {
                if (log.isLoggable(Level.FINE)) {
                    log.log(Level.FINE, "user not found: {0}", session);
                }
            }
            catch (TigaseDBException e) {
                log.log(Level.SEVERE, "unable to access database", e);
            }

        }

        return false;
    }

    private boolean hasPublicKey(Packet packet) {
        return packet.getElement().getChild(ELEM_NAME, XMLNS) != null;
    }

    private Packet addPublicKey(XMPPResourceConnection session, Packet packet) throws NotAuthorizedException, TigaseDBException {
        BareJID user = packet.getStanzaFrom().getBareJID();
        String fingerprint = KontalkAuth.getUserFingerprint(session, user);

        if (fingerprint != null) {
            try {
                String domain = session.getDomainAsJID().toString();
                byte[] keyData = KontalkKeyring.getInstance(domain).exportKey(fingerprint);
                if (keyData != null && keyData.length > 0) {
                    Element pubkey = new Element(ELEM_NAME, new String[] { Packet.XMLNS_ATT }, new String[] { XMLNS });
                    pubkey.addChild(new Element("key", Base64.encode(keyData)));
                    pubkey.addChild(new Element("print", fingerprint));

                    Packet result = packet.copyElementOnly();
                    result.getElement().addChild(pubkey);
                    result.initVars(packet.getStanzaFrom(), packet.getStanzaTo());
                    return result;
                }
            }
            catch (IOException | PGPException e) {
                log.log(Level.WARNING, "unable to load key for user " + user, e);
            }
        }

        return null;
    }

    /**
     * Returns shared instance of class implementing {@link RosterAbstract} -
     * either default one ({@link RosterFlat}) or the one configured with
     * <em>"roster-implementation"</em> property.
     *
     * @return shared instance of class implementing {@link RosterAbstract}
     */
    protected RosterAbstract getRosterUtil() {
        return RosterFactory.getRosterImplementation(true);
    }

    // TODO sup methods

}
