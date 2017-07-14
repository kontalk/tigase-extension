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

import tigase.conf.ConfigurationException;
import tigase.db.DBInitException;
import tigase.db.TigaseDBException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Verification provider that does not send a SMS in any way.
 * Useful for testing in local environments.
 * @author Daniele Ricci
 */
public class DummyProvider extends SMSDataStoreVerificationProvider {
    private static Logger log = Logger.getLogger(DummyProvider.class.getName());

    @Override
    public void init(Map<String, Object> settings) throws TigaseDBException, ConfigurationException {
        super.init(log, settings);
    }

    @Override
    protected VerificationRepository createVerificationRepository(String dbUri, int timeout) throws ClassNotFoundException, DBInitException, InstantiationException, SQLException, IllegalAccessException {
        return new DummyVerificationRepository(senderId);
    }

    @Override
    public String getAckInstructions() {
        return "A verification code has been stored in the server database.";
    }

    @Override
    protected void sendVerificationCode(String phoneNumber, String code) throws IOException {
        log.log(Level.INFO, "Registering " + phoneNumber + " with verification code " + code);
    }

}
