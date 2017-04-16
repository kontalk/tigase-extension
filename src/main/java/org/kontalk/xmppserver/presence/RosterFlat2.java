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

import tigase.db.TigaseDBException;
import tigase.db.UserRepository;
import tigase.xml.Element;
import tigase.xmpp.JID;
import tigase.xmpp.NotAuthorizedException;
import tigase.xmpp.XMPPResourceConnection;
import tigase.xmpp.impl.roster.RosterFlat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;


/**
 * A custom roster flat implementation.
 * @author Daniele Ricci
 */
public class RosterFlat2 extends RosterFlat {

    private final SimpleDateFormat formatter;
    {
        this.formatter = new SimpleDateFormat( "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" );
        this.formatter.setTimeZone( TimeZone.getTimeZone("UTC") );
    }

    private final JDBCPresenceRepository data_repo = new JDBCPresenceRepository();

    @Override
    public void init(UserRepository repo) throws TigaseDBException {
        super.init(repo);

        String uri = repo.getResourceUri();
        try {
            data_repo.initRepository(uri, null);
        }
        catch (Exception e) {
            throw new TigaseDBException("error initializing presence repository", e);
        }
    }

    @Override
    public Element getCustomChild(XMPPResourceConnection session, JID buddy) throws NotAuthorizedException, TigaseDBException {
        Date lastSeen = data_repo.getLastLogout(buddy.getBareJID());
        if (lastSeen != null) {
            String stamp;
            synchronized (formatter) {
                stamp = formatter.format(lastSeen);
            }

            return new Element("delay", new String[]{
                    "stamp", "xmlns"}, new String[]{stamp, "urn:xmpp:delay"});
        }

        // fallback to standard behaviour: use last-seen from roster element
        return super.getCustomChild(session, buddy);
    }

}
