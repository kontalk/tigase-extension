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
import tigase.db.DataRepository;
import tigase.db.RepositoryFactory;
import tigase.db.TigaseDBException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


/**
 * Validation code repository backed by a {@link tigase.db.DataRepository}.
 * @author Daniele Ricci
 */
public class DataServerlistRepository implements ServerlistRepository {

    private static final String SELECT_QUERY_SQL = "SELECT fingerprint, host, enabled FROM servers";

    private DataRepository repo;
    private List<ServerInfo> serverlist;

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
            }
        }
        catch (Exception e) {
            throw new DBInitException("error initializing push data storage", e);
        }
    }

    @Override
    public List<ServerInfo> getList() {
        return serverlist;
    }

    @Override
    public boolean isNetworkDomain(String domain) {
        if (serverlist != null) {
            for (ServerInfo server : serverlist) {
                if (server.getHost().equalsIgnoreCase(domain))
                    return true;
            }
        }
        return false;
    }

    @Override
    public void reload() throws TigaseDBException {
        Statement stm = null;
        ResultSet rs = null;
        try {
            stm = repo.createStatement(null);
            rs = stm.executeQuery(SELECT_QUERY_SQL);
            serverlist = new LinkedList<ServerInfo>();
            while (rs.next()) {
                String fpr = rs.getString(1);
                String host = rs.getString(2);
                int enabled = rs.getInt(3);
                serverlist.add(new ServerInfo(fpr, host, enabled != 0));
            }
        }
        catch (SQLException e) {
            throw new TigaseDBException(e.getMessage(), e);
        }
        finally {
            repo.release(stm, rs);
        }
    }

}
