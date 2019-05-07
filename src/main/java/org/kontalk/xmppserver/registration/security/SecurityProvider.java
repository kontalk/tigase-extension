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

import org.kontalk.xmppserver.registration.PhoneNumberVerificationProvider;
import tigase.conf.ConfigurationException;
import tigase.db.TigaseDBException;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import java.util.Map;


/**
 * Security provider interface.
 * Currently somewhat tied to the concept of throttling, but once we have more security features we'll adapt it.
 * @author Daniele Ricci
 */
public interface SecurityProvider {

    void init(Map<String, Object> settings) throws TigaseDBException, ConfigurationException;

    /**
     * Check if the registration may pass.
     * @return false to block the registration attempt as unsafe.
     */
    boolean pass(JID connectionId, BareJID jid, String phone, PhoneNumberVerificationProvider provider);

}
