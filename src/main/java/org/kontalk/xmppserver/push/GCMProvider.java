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

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.android.gcm.server.Message;
import tigase.conf.ConfigurationException;
import tigase.xmpp.BareJID;

import com.google.android.gcm.server.Result;
import com.google.android.gcm.server.Sender;


/**
 * Push provider for Google Cloud Messaging.
 * @author Daniele Ricci
 */
public class GCMProvider implements PushProvider {

    private static Logger log = Logger.getLogger(GCMProvider.class.getName());

    public static final String PROVIDER_NAME = "gcm";
    private static final String GCM_JID_PREFIX = "gcm.push.";
    private static final String GCM_DESCRIPTION = "Google Cloud Messaging push notifications";
    private static final String GCM_DATA_ACTION = "org.kontalk.CHECK_MESSAGES";
    private static final int GCM_MAX_RETRIES = 3;

    private String gcmProjectId;
    private String gcmApiKey;
    private Sender gcmSender;

    public void init(Map<String, Object> props) throws ConfigurationException {
        gcmProjectId = (String) props.get("gcm-projectid");
        gcmApiKey = (String) props.get("gcm-apikey");

        if (gcmApiKey != null)
            gcmSender = new Sender(gcmApiKey);
    }

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    public String getNode() {
        return gcmProjectId != null ? gcmProjectId : "unconfigured";
    }

    public String getJidPrefix() {
        return GCM_JID_PREFIX;
    }

    public String getDescription() {
        return GCM_DESCRIPTION;
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
        if (gcmSender == null) {
            log.log(Level.WARNING, "GCM provider not configured correctly.");
            return;
        }

        String regId = info.getRegistrationId();
        if (regId != null) {
            com.google.android.gcm.server.Message msg = new com.google.android.gcm.server.Message.Builder()
                .collapseKey("new")
                .addData("action", GCM_DATA_ACTION)
                .priority(Message.Priority.HIGH)
                .build();
            Result result = gcmSender.send(msg, regId, GCM_MAX_RETRIES);
            if (result.getMessageId() != null) {
                log.log(Level.FINE, "GCM message sent: {0}", result.getMessageId());
                String newId = result.getCanonicalRegistrationId();
                if (newId != null) {
                    // update registration id
                    info.setRegistrationId(newId);
                }
            }
            else {
                log.log(Level.INFO, "GCM error: {0}", result.getErrorCodeName());
            }
        }
        else {
            log.log(Level.INFO, "No registration ID found for {0}", jid);
        }
    }

}
