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

package org.kontalk.xmppserver.push;

import tigase.conf.ConfigurationException;
import tigase.xmpp.BareJID;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Dummy push provider that just logs at INFO level all push notifications.
 * @author Daniele Ricci
 */
public class DummyProvider implements PushProvider {

    private static Logger log = Logger.getLogger(DummyProvider.class.getName());

    public static final String PROVIDER_NAME = "dummy";
    private static final String DUMMY_JID_PREFIX = "dummy.push.";
    private static final String DUMMY_DESCRIPTION = "Dummy push notifications";

    public void init(Map<String, Object> props) throws ConfigurationException {
    }

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    public String getNode() {
        return "dummy";
    }

    public String getJidPrefix() {
        return DUMMY_JID_PREFIX;
    }

    public String getDescription() {
        return DUMMY_DESCRIPTION;
    }

    @Override
    public void register(BareJID jid, String registrationId) {
        // nothing to do
    }

    @Override
    public void unregister(BareJID jid) {
        // nothing to do
    }

    @Override
    public void sendPushNotification(BareJID jid, PushRegistrationInfo info) throws IOException {
        String regId = info.getRegistrationId();
        if (regId != null) {
            log.log(Level.INFO, "Push notification sent to {0}", jid);
        }
        else {
            log.log(Level.INFO, "No registration ID found for {0}", jid);
        }
    }

}
