package org.kontalk.xmppserver.probe;


import tigase.db.TigaseDBException;

import java.util.List;

/**
 * Interface to a server list repository.
 * @author Daniele Ricci
 */
public interface ServerlistRepository {

    public void reload() throws TigaseDBException;

    public List<ServerInfo> getList();

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
