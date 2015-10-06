/*
 * Kontalk XMPP Tigase extension
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.xmppserver.presence;

import tigase.db.*;
import tigase.db.jdbc.JDBCRepository;
import tigase.xmpp.BareJID;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Repository for advanced access to user information.
 * @author Daniele Ricci
 */
public class JDBCPresenceRepository extends JDBCRepository {
    private static final Logger log = Logger.getLogger(JDBCPresenceRepository.class.getName());

    private static final String GET_EXPIRED_USERS_QUERY_ID  = "presence_get_expired_users";
    private static final String GET_EXPIRED_USERS_QUERY_SQL  = "select user_id from " + JDBCRepository.DEF_USERS_TBL + " where last_logout > 0 and unix_timestamp(last_logout) < (unix_timestamp() - ?)";

    private static final String GET_LOGOUT_QUERY_ID  = "presence_get_last_logout";
    private static final String GET_LOGOUT_QUERY_SQL  = "select last_logout from " + JDBCRepository.DEF_USERS_TBL + " where sha1_user_id = sha1(?)";

    private boolean initialized = false;

    @Override
    public void initRepository(String resource_uri, Map<String, String> params) throws DBInitException {
        if (initialized) {
            return;
        }

        super.initRepository(resource_uri, params);

        initialized = true;
        log.log(Level.INFO, "Initializing message repository: {0}", resource_uri);

        try {
            DataRepository data_repo = getRepository();

            data_repo.initPreparedStatement(GET_EXPIRED_USERS_QUERY_ID, GET_EXPIRED_USERS_QUERY_SQL);
            data_repo.initPreparedStatement(GET_LOGOUT_QUERY_ID, GET_LOGOUT_QUERY_SQL);
        }
        catch (Exception e) {
            log.log(Level.WARNING, "Error initializing message repository", e);
        }
    }

    public List<BareJID> getExpiredUsers(long seconds) throws TigaseDBException {
        ResultSet rs        = null;
        List<BareJID> users = null;
        DataRepository data_repo = getRepository();

        try {
            PreparedStatement stmt = data_repo.getPreparedStatement(null,
                    GET_EXPIRED_USERS_QUERY_ID);

            synchronized (stmt) {
                stmt.setLong(1, seconds);
                rs = stmt.executeQuery();
                users = new ArrayList<BareJID>();
                while (rs.next()) {
                    users.add(BareJID.bareJIDInstanceNS(rs.getString(1)));
                }
            }
        }
        catch (SQLException e) {
            throw new TigaseDBException("Problem loading user list from repository", e);
        }
        finally {
            data_repo.release(null, rs);
        }

        return users;
    }

    public Date getLastLogout(BareJID user) throws TigaseDBException {
        ResultSet rs        = null;
        DataRepository data_repo = getRepository();

        try {
            PreparedStatement stmt = data_repo.getPreparedStatement(null,
                    GET_LOGOUT_QUERY_ID);

            synchronized (stmt) {
                stmt.setString(1, user.toString());
                rs = stmt.executeQuery();
                if (rs.next()) {
                    try {
                        return rs.getTimestamp(1);
                    }
                    catch (SQLException e) {
                        return null;
                    }
                }
            }

            return null;
        }
        catch (SQLException e) {
            throw new TigaseDBException("Problem loading user info from repository", e);
        }
        finally {
            data_repo.release(null, rs);
        }
    }

}
