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

package org.kontalk.xmppserver.probe;


import tigase.db.DBInitException;
import tigase.db.TigaseDBException;

import java.util.List;
import java.util.Map;

/**
 * Interface to a server list repository.
 * @author Daniele Ricci
 */
public interface ServerlistRepository {

    public void init(Map<String, Object> props) throws DBInitException;

    public void reload() throws TigaseDBException;

    public List<ServerInfo> getList();

    public boolean isNetworkDomain(String domain);

    public static class ServerInfo {
        private String fingerprint;
        private String host;
        private boolean enabled;

        protected ServerInfo(String fingerprint, String host, boolean enabled) {
            this.fingerprint = fingerprint;
            this.host = host;
            this.enabled = enabled;
        }

        public String getFingerprint() {
            return fingerprint;
        }

        public String getHost() {
            return host;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }

}
