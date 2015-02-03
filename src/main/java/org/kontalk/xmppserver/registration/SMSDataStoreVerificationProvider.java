/*
 * Kontalk XMPP Tigase extension
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.xmppserver.registration;

import tigase.db.TigaseDBException;
import tigase.xmpp.BareJID;

import java.sql.SQLException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Base class for a SMS verification provider based on a data store repository.
 * @author Daniele Ricci
 */
public abstract class SMSDataStoreVerificationProvider extends AbstractSMSVerificationProvider {
    private Logger log;

    /** Time in seconds between calls to {@link VerificationRepository#purge()}. */
    private static final int EXPIRED_TIMEOUT = 60000;

    private VerificationRepository repo;

    public void init(Logger log, Map<String, Object> settings) throws TigaseDBException {
        super.init(settings);

        this.log = log;

        // database parameters
        String dbUri = (String) settings.get("db-uri");
        Object _timeout = settings.get("expire");
        int timeout = (_timeout != null) ? (Integer) _timeout : 0;
        try {
            repo = new DataVerificationRepository(dbUri, timeout);
        }
        catch (ClassNotFoundException e) {
            throw new TigaseDBException("Repository class not found (uri=" + dbUri + ")", e);
        }
        catch (InstantiationException e) {
            throw new TigaseDBException("Unable to create instance for repository (uri=" + dbUri + ")", e);
        }
        catch (SQLException e) {
            throw new TigaseDBException("SQL exception (uri=" + dbUri + ")", e);
        }
        catch (IllegalAccessException e) {
            throw new TigaseDBException("Unknown error (uri=" + dbUri + ")", e);
        }

        if (timeout > 0) {
            // create a scheduler for our own use
            Timer timer = new Timer("SMSDataStoreVerification tasks", true);
            // setup looping task for verification codes expiration
            timer.scheduleAtFixedRate(new PurgeTask(log, repo), EXPIRED_TIMEOUT, EXPIRED_TIMEOUT);
        }
    }

    protected String generateVerificationCode(BareJID jid)
            throws VerificationRepository.AlreadyRegisteredException, TigaseDBException {
        return repo.generateVerificationCode(jid);
    }

    protected boolean verify(BareJID jid, String code) throws TigaseDBException {
        return repo.verifyCode(jid, code);
    }

    /** A task to purge old registration entries. */
    private static final class PurgeTask extends TimerTask {
        private Logger log;
        private VerificationRepository repo;

        public PurgeTask(Logger log, VerificationRepository repo) {
            this.log = log;
            this.repo = repo;
        }

        @Override
        public void run() {
            try {
                if (log.isLoggable(Level.FINEST)) {
                    log.finest("Purging expired registration entries.");
                }
                repo.purge();
            }
            catch (TigaseDBException e) {
                log.log(Level.WARNING, "unable to purge old registration entries");
            }
        }
    }

}
