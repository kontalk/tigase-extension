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

package org.kontalk.xmppserver.registration;

import org.kontalk.xmppserver.auth.KontalkAuth;
import tigase.conf.ConfigurationException;
import tigase.db.DBInitException;
import tigase.db.TigaseDBException;
import tigase.xmpp.BareJID;
import tigase.xmpp.XMPPResourceConnection;

import java.io.IOException;
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

    protected void init(Logger log, Map<String, Object> settings) throws TigaseDBException, ConfigurationException {
        super.init(settings);

        this.log = log;

        // database parameters
        String dbUri = (String) settings.get("db-uri");
        Object _timeout = settings.get("expire");
        int timeout = (_timeout != null) ? (Integer) _timeout : 0;
        try {
            repo = createVerificationRepository(dbUri, timeout);
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

    protected VerificationRepository createVerificationRepository(String dbUri, int timeout)
            throws ClassNotFoundException, DBInitException, InstantiationException, SQLException, IllegalAccessException {
        return new DataVerificationRepository(dbUri, timeout);
    }

    @Override
    public RegistrationRequest startVerification(String domain, String phoneNumber)
            throws IOException, VerificationRepository.AlreadyRegisteredException, TigaseDBException {

        // generate verification code
        BareJID jid = KontalkAuth.toBareJID(phoneNumber, domain);
        String code = generateVerificationCode(jid);

        // call implementation
        sendVerificationCode(phoneNumber, code);

        // request id will be used to map back JID
        return new SMSDataStoreRequest(jid);
    }

    @Override
    public boolean endVerification(XMPPResourceConnection session, RegistrationRequest request, String proof)
            throws IOException, TigaseDBException {
        if (proof == null || proof.length() == 0) {
            return false;
        }

        SMSDataStoreRequest myRequest = (SMSDataStoreRequest) request;
        return verify(myRequest.getJid(), proof);
    }

    /** This will be called in the implementation to do the actual sending. */
    abstract protected void sendVerificationCode(String phoneNumber, String code) throws IOException;

    private String generateVerificationCode(BareJID jid)
            throws VerificationRepository.AlreadyRegisteredException, TigaseDBException {
        return repo.generateVerificationCode(jid);
    }

    private boolean verify(BareJID jid, String code) throws TigaseDBException {
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

    @Override
    public boolean supportsRequest(RegistrationRequest request) {
        return request instanceof SMSDataStoreRequest;
    }

    @Override
    public String getChallengeType() {
        return CHALLENGE_PIN;
    }

    private static final class SMSDataStoreRequest implements RegistrationRequest {
        private final BareJID jid;
        public SMSDataStoreRequest(BareJID jid) {
            this.jid = jid;
        }

        @Override
        public String getSenderId() {
            return null;
        }

        public BareJID getJid() {
            return jid;
        }
    }

}
