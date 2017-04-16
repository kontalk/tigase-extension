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

package org.kontalk.xmppserver.auth;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.util.encoders.Hex;
import org.kontalk.xmppserver.KontalkKeyring;
import tigase.db.DBInitException;
import tigase.db.RepositoryFactory;
import tigase.db.TigaseDBException;
import tigase.db.UserRepository;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.NotAuthorizedException;
import tigase.xmpp.XMPPResourceConnection;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Kontalk authentication stuff.
 * @author Daniele Ricci
 */
public class KontalkAuth {
    private static final String NODE_AUTH = "kontalk/auth";
    private static final String KEY_FINGERPRINT = "fingerprint";

    private static UserRepository userRepository = null;

    private KontalkAuth() {}

    public static UserRepository getUserRepository() throws TigaseDBException {
        if (userRepository == null) {
            try {
                userRepository = RepositoryFactory.getUserRepository(null, null, null);
            }
            catch (Exception e) {
                throw new TigaseDBException("Unable to get user repository instance", e);
            }
        }
        return userRepository;
    }

    public static String getUserFingerprint(XMPPResourceConnection session)
            throws TigaseDBException, NotAuthorizedException {
        return session.getData(NODE_AUTH, KEY_FINGERPRINT, null);
    }

    public static String getUserFingerprint(XMPPResourceConnection session, BareJID jid)
            throws TigaseDBException {
        return getUserRepository().getData(jid, NODE_AUTH, KEY_FINGERPRINT, null);
    }

    public static void setUserFingerprint(XMPPResourceConnection session, BareJID jid, String fingerprint)
            throws TigaseDBException {
        getUserRepository().setData(jid, NODE_AUTH, KEY_FINGERPRINT, fingerprint);
    }

    public static KontalkKeyring getKeyring(XMPPResourceConnection session) throws IOException, PGPException {
        return KontalkKeyring.getInstance(session.getDomainAsJID().toString());
    }

    public static String toUserId(String phone) {
        return sha1(phone);
    }

    public static JID toJID(String phone, String domain) {
        return JID.jidInstanceNS(toUserId(phone), domain, null);
    }

    public static BareJID toBareJID(String phone, String domain) {
        return BareJID.bareJIDInstanceNS(toUserId(phone), domain);
    }

    private static String sha1(String text) {
        try {
            MessageDigest md;
            md = MessageDigest.getInstance("SHA-1");
            md.update(text.getBytes(), 0, text.length());

            byte[] digest = md.digest();
            return Hex.toHexString(digest);
        }
        catch (NoSuchAlgorithmException e) {
            // no SHA-1?? WWWHHHHAAAAAATTTT???!?!?!?!?!
            throw new RuntimeException("no SHA-1 available. What the crap of a runtime do you have?");
        }
    }



}
