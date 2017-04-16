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


import tigase.db.DBInitException;
import tigase.db.DataRepository;
import tigase.db.RepositoryFactory;
import tigase.db.TigaseDBException;
import tigase.xmpp.BareJID;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

/**
 * Verification code repository backed by a {@link DataRepository}.
 * @author Daniele Ricci
 */
public class DataVerificationRepository extends AbstractVerificationRepository {

    private static final String CREATE_QUERY_ID = "verification-create-query";
    private static final String CREATE_QUERY_SQL = "INSERT INTO validations VALUES (?, ?, SYSDATE())";

    private static final String SELECT_QUERY_ID = "verification-select-query";
    private static final String SELECT_QUERY_SQL = "SELECT user_id FROM validations WHERE code = ?";

    private static final String DELETE_QUERY_ID = "verification-delete-query";
    private static final String DELETE_QUERY_SQL = "DELETE FROM validations WHERE code = ?";

    private static final String PURGE_QUERY_ID = "verification-purge-query";
    private static final String PURGE_QUERY_SQL = "DELETE FROM validations WHERE UNIX_TIMESTAMP() > (UNIX_TIMESTAMP(timestamp) + ?)";

    private DataRepository repo;
    private int timeout;

    public DataVerificationRepository(String dbUri, int expirationTimeout) throws ClassNotFoundException,
            DBInitException, InstantiationException, SQLException, IllegalAccessException {
        timeout = expirationTimeout;
        repo = RepositoryFactory.getDataRepository(null, dbUri, null);
        repo.initPreparedStatement(CREATE_QUERY_ID, CREATE_QUERY_SQL);
        repo.initPreparedStatement(SELECT_QUERY_ID, SELECT_QUERY_SQL);
        repo.initPreparedStatement(DELETE_QUERY_ID, DELETE_QUERY_SQL);
        repo.initPreparedStatement(PURGE_QUERY_ID, PURGE_QUERY_SQL);
    }

    @Override
    public String generateVerificationCode(BareJID jid) throws AlreadyRegisteredException, TigaseDBException {
        String code = verificationCode();
        PreparedStatement stm;
        try {
            stm = repo.getPreparedStatement(jid, CREATE_QUERY_ID);
            stm.setString(1, jid.toString());
            stm.setString(2, code);
            stm.execute();
            return code;
        }
        catch (SQLException e) {
            if (e instanceof SQLIntegrityConstraintViolationException)
                throw new AlreadyRegisteredException();

            throw new TigaseDBException(e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyCode(BareJID jid, String code) throws TigaseDBException {
        PreparedStatement stm;
        ResultSet rs = null;
        try {
            stm = repo.getPreparedStatement(jid, SELECT_QUERY_ID);
            stm.setString(1, code);
            rs = stm.executeQuery();
            if (rs.next()) {
                String userid = rs.getString(1);
                if (jid.toString().equals(userid)) {
                    // delete the code
                    stm = repo.getPreparedStatement(jid, DELETE_QUERY_ID);
                    stm.setString(1, code);
                    stm.execute();
                    return true;
                }
            }

            return false;
        }
        catch (SQLException e) {
            throw new TigaseDBException(e.getMessage(), e);
        }
        finally {
            repo.release(null, rs);
        }
    }

    public void purge() throws TigaseDBException {
        PreparedStatement stm;
        try {
            stm = repo.getPreparedStatement(null, PURGE_QUERY_ID);
            stm.setInt(1, timeout);
            stm.execute();
        }
        catch (SQLException e) {
            throw new TigaseDBException(e.getMessage(), e);
        }
    }

}
