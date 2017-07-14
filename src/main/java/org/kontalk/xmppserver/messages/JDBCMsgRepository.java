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

package org.kontalk.xmppserver.messages;

import tigase.db.*;
import tigase.xml.DomBuilderHandler;
import tigase.xml.Element;
import tigase.xml.SimpleParser;
import tigase.xml.SingletonFactory;
import tigase.xmpp.BareJID;

import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * JDBC message repository.
 * @author Daniele
 */
public class JDBCMsgRepository implements MsgRepository {

    private static final Logger log = Logger.getLogger(JDBCMsgRepository.class.getName());

    private static final String MSG_TABLE = "messages";
    private static final String MSG_ID_COLUMN = "id";
    private static final String MSG_UID_COLUMN = "uid";
    private static final String MSG_STANZA_COLUMN = "stanza";
    private static final String MSG_TIMESTAMP_COLUMN = "timestamp";
    private static final String MSG_EXPIRED_COLUMN = "expired";

    private static final String MYSQL_CREATE_MSG_TABLE =
            "CREATE TABLE `"+MSG_TABLE+"` (" +
            " `"+MSG_ID_COLUMN+"` bigint(20) NOT NULL PRIMARY KEY AUTO_INCREMENT," +
            " `"+MSG_UID_COLUMN+"` bigint(20) unsigned NOT NULL," +
            " `"+MSG_STANZA_COLUMN+"` mediumtext NOT NULL," +
            " `"+MSG_TIMESTAMP_COLUMN+"` datetime NOT NULL," +
            " `"+MSG_EXPIRED_COLUMN+"` datetime DEFAULT NULL," +
            "CONSTRAINT FOREIGN KEY (`"+MSG_UID_COLUMN+"`) REFERENCES `tig_users` (`uid`) ON DELETE CASCADE" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Offline message storage'";

    private static final String MSG_QUERY_LOAD_ID = "messages_load";
    private static final String MSG_QUERY_LOAD_SQL = "select * from " + MSG_TABLE + " where "+MSG_UID_COLUMN+" = ?";

    private static final String MSG_QUERY_STORE_ID = "messages_store";
    private static final String MSG_QUERY_STORE_SQL = "insert into " + MSG_TABLE + " VALUES (NULL, ?, ?, ?, ?)";

    private static final String MSG_QUERY_DELETE_ID = "messages_delete";
    private static final String MSG_QUERY_DELETE_SQL = "delete from " + MSG_TABLE + " where "+MSG_UID_COLUMN+" = ?";

    private static final String MSG_QUERY_DELETE_EXPIRED_SQL = "delete from " + MSG_TABLE + " where expired < now()";

    private boolean initialized = false;

    private DataRepository data_repo;
    private UserRepository user_repo;

    private SimpleParser parser = SingletonFactory.getParserInstance();

    @Override
    public int expireMessages() throws TigaseDBException {
        Statement stmt = null;

        try {
            stmt = data_repo.createStatement(null);
            return stmt.executeUpdate(MSG_QUERY_DELETE_EXPIRED_SQL);
        }
        catch (SQLException e) {
            throw new TigaseDBException("database error", e);
        }
        finally {
            data_repo.release(stmt, null);
        }
    }

    @Override
    public Queue<Element> loadMessagesToJID(BareJID user, boolean delete) throws TigaseDBException {
        PreparedStatement stmt;
        ResultSet rs = null;

        try {
            long uid = user_repo.getUserUID(user);
            if (uid <= 0)
                throw new UserNotFoundException("user not found: " + user);
            stmt = data_repo.getPreparedStatement(user, MSG_QUERY_LOAD_ID);
            StringBuilder sb = new StringBuilder();
            synchronized (stmt) {
                stmt.setLong(1, uid);
                rs = stmt.executeQuery();

                while (rs.next()) {
                    String stanza = rs.getString(MSG_STANZA_COLUMN);
                    sb.append(stanza);
                }
            }

            if (sb.length() > 0) {
                DomBuilderHandler domHandler = new DomBuilderHandler();
                char[] data = sb.toString().toCharArray();
                parser.parse(domHandler, data, 0, data.length);

                Queue<Element> elements = domHandler.getParsedElements();

                if (delete)
                    deleteMessages(uid);

                // we will return the data only when everything went well
                return elements;
            }
        }
        catch (SQLException e) {
            throw new TigaseDBException("database error", e);
        }
        finally {
            data_repo.release(null, rs);
        }

        return null;
    }

    private int deleteMessages(long uid) throws SQLException {
        PreparedStatement stmt = data_repo.getPreparedStatement(null, MSG_QUERY_DELETE_ID);
        synchronized (stmt) {
            stmt.setLong(1, uid);
            return stmt.executeUpdate();
        }
    }

    @Override
    public void storeMessage(BareJID user, Element msg, Date expire) throws TigaseDBException {
        PreparedStatement stmt;

        try {
            long uid = user_repo.getUserUID(user);
            if (uid <= 0)
                throw new UserNotFoundException("user not found: " + user);

            stmt = data_repo.getPreparedStatement(user, MSG_QUERY_STORE_ID);
            synchronized (stmt) {
                stmt.setLong(1, uid);
                stmt.setString(2, msg.toString());
                stmt.setTimestamp(3, new java.sql.Timestamp(System.currentTimeMillis()));
                if (expire != null)
                    stmt.setTimestamp(4, new java.sql.Timestamp(expire.getTime()));
                else
                    stmt.setNull(4, Types.TIMESTAMP);
                stmt.execute();
            }
        }
        catch (SQLException e) {
            throw new TigaseDBException("database error", e);
        }
    }

    @Override
    public void initRepository(String resource_uri, Map<String, String> params) throws DBInitException {
        if (initialized) {
            return;
        }

        initialized = true;
        log.log(Level.INFO, "Initializing message repository: {0}", resource_uri);

        try {
            data_repo = RepositoryFactory.getDataRepository(null, resource_uri, params);

            checkDB();
            data_repo.initPreparedStatement(MSG_QUERY_LOAD_ID, MSG_QUERY_LOAD_SQL);
            data_repo.initPreparedStatement(MSG_QUERY_STORE_ID, MSG_QUERY_STORE_SQL);
            data_repo.initPreparedStatement(MSG_QUERY_DELETE_ID, MSG_QUERY_DELETE_SQL);

            user_repo = RepositoryFactory.getUserRepository(null, resource_uri, params);
        }
        catch (Exception e) {
            log.log(Level.WARNING, "Error initializing message repository", e);
        }
    }

    /** Performs database check, creates missing schema if necessary. */
    private void checkDB() throws SQLException {
        DataRepository.dbTypes databaseType = data_repo.getDatabaseType();
        switch (databaseType) {
            case mysql:
                data_repo.checkTable(MSG_TABLE, MYSQL_CREATE_MSG_TABLE);
                break;
            // TODO support for other databases
        }
    }

}
