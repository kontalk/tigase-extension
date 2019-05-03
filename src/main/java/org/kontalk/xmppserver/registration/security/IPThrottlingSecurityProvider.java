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

package org.kontalk.xmppserver.registration.security;

import org.apache.commons.lang3.StringUtils;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Security provider for IP-based throttling.
 * @author Daniele Ricci
 */
public class IPThrottlingSecurityProvider extends AbstractThrottlingSecurityProvider {

    /** Pattern for connectionId resource: 127.0.0.1_5222_127.0.0.1_38492 */
    // FIXME this is valid only for IPv4
    private static final Pattern PATTERN_CLIENT_ADDRESS = Pattern.compile("^.*_.*_(.*)_.*$");

    @Override
    protected String getIdentifier(JID connectionId, BareJID jid, String phone) {
        String host = extractHostFromConnectionId(connectionId);
        if (StringUtils.isNotEmpty(host)) {
            return host;
        }
        return null;
    }

    private String extractHostFromConnectionId(JID connectionId) {
        String hostInfo = connectionId.getResource();
        if (hostInfo != null) {
            Matcher match = PATTERN_CLIENT_ADDRESS.matcher(hostInfo);
            if (match.matches() && match.groupCount() > 0) {
                return match.group(1);
            }
        }
        return null;
    }

}
