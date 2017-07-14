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

package org.kontalk.xmppserver.presence;

import org.apache.commons.lang3.StringUtils;
import tigase.conf.Configurable;
import tigase.db.*;
import tigase.db.jdbc.JDBCRepository;
import tigase.server.XMPPServer;
import tigase.server.xmppsession.SessionManager;
import tigase.xmpp.BareJID;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Repository for advanced access to user information.
 * @author Daniele Ricci
 */
public class JDBCPresenceRepository extends JDBCRepository {
    private static final Logger log = Logger.getLogger(JDBCPresenceRepository.class.getName());

    //private static final String[] SYSTEM_USERS = { "db-properties", "vhost-manager" };

    private static final String GET_EXPIRED_USERS_QUERY_ID  = "presence_get_expired_users";
    private static final String GET_EXPIRED_USERS_QUERY_SQL  =
            "select user_id from " + JDBCRepository.DEF_USERS_TBL + " where " +
            "instr(user_id, '@') > 0 %s and (" +
            "(last_logout > 0 and unix_timestamp(last_logout) < (unix_timestamp() - ?)) or " +
            "(last_logout = 0 and unix_timestamp(acc_create_time) < (unix_timestamp() - ?)))";
    private static final String EXPIRED_USERS_EXTRA_SQL = "and user_id not in (%s)";

    private static final String GET_LOGOUT_QUERY_ID  = "presence_get_last_logout";
    private static final String GET_LOGOUT_QUERY_SQL  = "select last_logout from " + JDBCRepository.DEF_USERS_TBL + " where sha1_user_id = sha1(?)";

    private boolean initialized = false;

    private String[] adminUsers;

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

            String extraSql;

            Map<String, Object> props = XMPPServer.getConfigurator().getProperties("message-router");
            adminUsers = (String[]) props.get(Configurable.ADMINS_PROP_KEY);
            if (adminUsers != null && adminUsers.length > 0) {
                String placeholders = StringUtils.repeat("?", ",", adminUsers.length);
                extraSql = String.format(EXPIRED_USERS_EXTRA_SQL, placeholders);
            }
            else {
                extraSql = "";
            }

            data_repo.initPreparedStatement(GET_EXPIRED_USERS_QUERY_ID, String.format(GET_EXPIRED_USERS_QUERY_SQL, extraSql));
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
                int i = 0;
                if (adminUsers != null && adminUsers.length > 0) {
                    for (String admin : adminUsers)
                        stmt.setString(++i, admin);
                }
                stmt.setLong(++i, seconds);
                stmt.setLong(++i, seconds);
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
