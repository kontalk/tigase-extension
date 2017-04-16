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

import tigase.db.DBInitException;
import tigase.db.DataRepository;
import tigase.db.RepositoryFactory;
import tigase.db.TigaseDBException;
import tigase.xmpp.BareJID;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


/**
 * Push registration repository backed by a {@link tigase.db.DataRepository}.
 * @author Daniele Ricci
 */
public class DataPushRepository implements PushRepository {

    private static final String CREATE_QUERY_ID = "push-create-query";
    private static final String CREATE_QUERY_SQL = "REPLACE INTO push VALUES (?, ?, ?)";

    private static final String SELECT_QUERY_ID = "push-select-query";
    private static final String SELECT_QUERY_SQL = "SELECT provider, reg_id FROM push WHERE user_id = ?";

    private static final String DELETE_QUERY_ID = "push-delete-query";
    private static final String DELETE_QUERY_SQL = "DELETE FROM push WHERE user_id = ? AND provider = ?";

    private DataRepository repo;

    @Override
    public void init(Map<String, Object> props) throws DBInitException {
        try {
            String dbUri = (String) props.get("db-uri");
            if (dbUri == null) {
                // fallback on user database
                dbUri = System.getProperty(RepositoryFactory.GEN_USER_DB_URI_PROP_KEY);
            }
            if (dbUri != null) {
                repo = RepositoryFactory.getDataRepository(null, dbUri, null);
                repo.initPreparedStatement(CREATE_QUERY_ID, CREATE_QUERY_SQL);
                repo.initPreparedStatement(SELECT_QUERY_ID, SELECT_QUERY_SQL);
                repo.initPreparedStatement(DELETE_QUERY_ID, DELETE_QUERY_SQL);
            }
        }
        catch (Exception e) {
            throw new DBInitException("error initializing push data storage", e);
        }
    }

    @Override
    public void register(BareJID jid, String provider, String registrationId) throws TigaseDBException {
        PreparedStatement stm;
        try {
            stm = repo.getPreparedStatement(jid, CREATE_QUERY_ID);
            synchronized (stm) {
                stm.setString(1, jid.toString());
                stm.setString(2, provider);
                stm.setString(3, registrationId);
                stm.execute();
            }
        }
        catch (SQLException e) {
            throw new TigaseDBException(e.getMessage(), e);
        }
    }

    @Override
    public void unregister(BareJID jid, String provider) throws TigaseDBException {
        PreparedStatement stm;
        try {
            stm = repo.getPreparedStatement(jid, DELETE_QUERY_ID);
            synchronized (stm) {
                stm.setString(1, jid.toString());
                stm.setString(2, provider);
                stm.execute();
            }
        }
        catch (SQLException e) {
            throw new TigaseDBException(e.getMessage(), e);
        }
    }

    @Override
    public List<PushRegistrationInfo> getRegistrationInfo(BareJID jid) throws TigaseDBException {
        PreparedStatement stm;
        ResultSet rs = null;
        try {
            stm = repo.getPreparedStatement(jid, SELECT_QUERY_ID);
            synchronized (stm) {
                stm.setString(1, jid.toString());
                rs = stm.executeQuery();

                List<PushRegistrationInfo> list = new LinkedList<PushRegistrationInfo>();
                if (rs.next()) {
                    String provider = rs.getString(1);
                    String regId = rs.getString(2);
                    list.add(new PushRegistrationInfo(provider, regId));
                }

                return list;
            }
        }
        catch (SQLException e) {
            throw new TigaseDBException(e.getMessage(), e);
        }
        finally {
            repo.release(null, rs);
        }
    }

}
