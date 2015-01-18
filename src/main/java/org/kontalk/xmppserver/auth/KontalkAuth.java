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

package org.kontalk.xmppserver.auth;

import org.kontalk.xmppserver.KontalkKeyring;
import tigase.db.TigaseDBException;
import tigase.xmpp.BareJID;
import tigase.xmpp.NotAuthorizedException;
import tigase.xmpp.XMPPResourceConnection;

/**
 * Kontalk authentication stuff.
 * @author Daniele Ricci
 */
public class KontalkAuth {
    private static final String NODE_AUTH = "kontalk/auth";
    private static final String KEY_FINGERPRINT = "fingerprint";

    private KontalkAuth() {}

    public static String getUserFingerprint(XMPPResourceConnection session)
            throws TigaseDBException, NotAuthorizedException {
        return session.getData(NODE_AUTH, KEY_FINGERPRINT, null);
    }

    public static String getUserFingerprint(XMPPResourceConnection session, BareJID jid)
            throws TigaseDBException {
        return session.getUserRepository().getData(jid, NODE_AUTH, KEY_FINGERPRINT, null);
    }

    public static void setUserFingerprint(XMPPResourceConnection session, BareJID jid, String fingerprint)
            throws TigaseDBException {
        session.getUserRepository().setData(jid, NODE_AUTH, KEY_FINGERPRINT, fingerprint);
    }

    public static KontalkKeyring getKeyring(XMPPResourceConnection session, String serverFingerprint) {
        return KontalkKeyring.getInstance(session.getDomainAsJID().toString(), serverFingerprint);
    }

}
