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

import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import tigase.conf.ConfigurationException;
import tigase.db.TigaseDBException;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import java.util.Map;

/**
 * Security provider for prefix-based throttling.
 * @author Daniele Ricci
 */
public class PhonePrefixThrottlingSecurityProvider extends AbstractThrottlingSecurityProvider {

    private static final int DEFAULT_PREFIX_LENGTH = 6;

    private int prefixLength;

    @Override
    public void init(Map<String, Object> settings) throws TigaseDBException, ConfigurationException {
        super.init(settings);
        prefixLength = (int) settings.getOrDefault("length", DEFAULT_PREFIX_LENGTH);
    }

    @Override
    protected String getIdentifier(JID connectionId, BareJID jid, String phone) {
        // remove plus
        if (phone.charAt(0) == '+') {
            phone = phone.substring(1);
        }

        return phone.substring(0, prefixLength);
    }
}
