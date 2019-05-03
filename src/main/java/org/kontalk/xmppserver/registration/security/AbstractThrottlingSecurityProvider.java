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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.kontalk.xmppserver.registration.PhoneNumberVerificationProvider;
import tigase.conf.ConfigurationException;
import tigase.db.TigaseDBException;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import java.util.Map;
import java.util.concurrent.TimeUnit;


/**
 * Security provider for phone-based throttling.
 * @author Daniele Ricci
 */
public abstract class AbstractThrottlingSecurityProvider implements SecurityProvider {

    private static final int DEFAULT_THROTTLING_ATTEMPTS = 3;
    private static final long DEFAULT_THROTTLING_DELAY = TimeUnit.MINUTES.toSeconds(30);

    /** Stores useful data for detecting registration throttling. */
    private static final class LastRegisterRequest {
        /** Number of requests so far. */
        public int attempts;
        /** Timestamp of last request. */
        public long lastTimestamp;
    }

    private Cache<String, LastRegisterRequest> throttlingRequests;

    private int delayMillis;
    private int triggerAttempts;

    /**
     * Child classes should return the identifier to use as a key for throttling purposes.
     * @return an identifier to use as throttling key or null to pass automatically.
     */
    protected abstract String getIdentifier(JID connectionId, BareJID jid, String phone);

    @Override
    public void init(Map<String, Object> settings) throws TigaseDBException, ConfigurationException {
        delayMillis = (int) settings.getOrDefault("delay", DEFAULT_THROTTLING_DELAY) * 1000;
        triggerAttempts = (int) settings.getOrDefault("trigger-attempts", DEFAULT_THROTTLING_ATTEMPTS);

        throttlingRequests = CacheBuilder.newBuilder()
                .expireAfterAccess(delayMillis, TimeUnit.MILLISECONDS)
                .maximumSize(100000)
                .build();
    }

    @Override
    public boolean pass(JID connectionId, BareJID jid, String phone, PhoneNumberVerificationProvider provider) {
        String identifier = getIdentifier(connectionId, jid, phone);
        if (identifier != null) {
            return !isThrottling(identifier);
        }
        return true;
    }

    @Override
    public void clear(JID connectionId, BareJID jid, String phone) {
        clearThrottling(getIdentifier(connectionId, jid, phone));
    }

    private synchronized boolean isThrottling(String id) {
        long now = System.currentTimeMillis();
        LastRegisterRequest request = throttlingRequests.getIfPresent(id);
        try {
            if (request != null) {
                if (request.attempts >= triggerAttempts) {
                    return true;
                }
                else {
                    request.attempts++;
                    return false;
                }
            }
            else {
                request = new LastRegisterRequest();
                request.attempts = 1;
                return false;
            }
        }
        finally {
            request.lastTimestamp = now;
            throttlingRequests.put(id, request);
        }
    }

    private void clearThrottling(String id) {
        throttlingRequests.invalidate(id);
    }

}
