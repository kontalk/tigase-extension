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

import org.kontalk.xmppserver.auth.KontalkAuth;
import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.NotAuthorizedException;
import tigase.xmpp.XMPPResourceConnection;
import tigase.xmpp.impl.PresenceState;

import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.kontalk.xmppserver.presence.PresenceSubscribePublicKey.ELEM_NAME;
import static org.kontalk.xmppserver.presence.PresenceSubscribePublicKey.XMLNS;


/**
 * A presence extender for public key fingerprint element.
 * @author Daniele Ricci
 */
public class PublicKeyPresence implements PresenceState.ExtendedPresenceProcessorIfc {

    private static final Logger log = Logger.getLogger(PublicKeyPresence.class.getName());

    @Override
    public Element extend(XMPPResourceConnection session, Queue<Packet> results) {
        if (session != null) {
            try {
                String fingerprint = KontalkAuth.getUserFingerprint(session);
                if (fingerprint != null) {
                    Element pubkey = new Element(ELEM_NAME, new String[] { Packet.XMLNS_ATT }, new String[] { XMLNS });
                    pubkey.addChild(new Element("print", fingerprint));
                    return pubkey;
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
        return null;
    }

}
