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

package org.kontalk.xmppserver.messages;

import tigase.db.*;
import tigase.xml.DomBuilderHandler;
import tigase.xml.Element;
import tigase.xml.SimpleParser;
import tigase.xml.SingletonFactory;
import tigase.xmpp.BareJID;
import tigase.xmpp.NotAuthorizedException;
import tigase.xmpp.XMPPResourceConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Queue;
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
            " `"+MSG_ID_COLUMN+"` bigint(20) NOT NULL AUTO_INCREMENT," +
            " `"+MSG_UID_COLUMN+"` bigint(20) unsigned NOT NULL," +
            " `"+MSG_STANZA_COLUMN+"` mediumtext NOT NULL," +
            " `"+MSG_TIMESTAMP_COLUMN+"` datetime NOT NULL," +
            " `"+MSG_EXPIRED_COLUMN+"` datetime DEFAULT NULL" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='Offline message storage'";
    private static final String[] MYSQL_INDEX_MSG_TABLE = {
            "ALTER TABLE `"+MSG_TABLE+"`" +
                " ADD PRIMARY KEY (`"+MSG_ID_COLUMN+"`)," +
                " ADD KEY `uid` (`"+MSG_UID_COLUMN+"`)",
            "ALTER TABLE `"+MSG_TABLE+"`" +
                " ADD CONSTRAINT `"+MSG_ID_COLUMN+"` FOREIGN KEY (`fk_uid`) REFERENCES `tig_users` (`uid`);"
    };

    private static final String MSG_QUERY_LOAD_ID = "messages_load";
    private static final String MSG_QUERY_LOAD_SQL = "select * from " + MSG_TABLE + " where "+MSG_UID_COLUMN+" = ?";

    private boolean initialized = false;

    private DataRepository data_repo;

    private SimpleParser parser = SingletonFactory.getParserInstance();

    @Override
    public int expireMessages() throws TigaseDBException {
        // TODO
        return 0;
    }


    @Override
    public Queue<Element> loadMessagesToJID(XMPPResourceConnection conn, boolean delete) throws TigaseDBException {
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            BareJID jid = conn.getBareJID();
            long uid = conn.getUserRepository().getUserUID(jid);
            stmt = data_repo.getPreparedStatement(jid, MSG_QUERY_LOAD_ID);
            stmt.setLong(1, uid);
            rs = stmt.executeQuery();

            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                String stanza = rs.getString(MSG_STANZA_COLUMN);
                sb.append(stanza);
            }

            if (sb.length() > 0) {
                DomBuilderHandler domHandler = new DomBuilderHandler();
                char[] data = sb.toString().toCharArray();
                parser.parse(domHandler, data, 0, data.length);

                return domHandler.getParsedElements();
            }
        }
        catch (SQLException e) {
            throw new TigaseDBException("database error", e);
        }
        catch (NotAuthorizedException e) {
            throw new TigaseDBException("shouldn't happen!", e);
        }
        finally {
            data_repo.release(stmt, rs);
        }

        return null;
    }

    @Override
    public void storeMessage(XMPPResourceConnection conn, Element msg) throws TigaseDBException {
        // TODO
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
        }
        catch (Exception e) {
            log.log(Level.WARNING, "Error initializing message repository", e);
        }
    }

    /** Performs database check, creates missing schema if necessary. */
    private void checkDB() throws SQLException {
        Statement statement = null;
        DataRepository.dbTypes databaseType = data_repo.getDatabaseType();
        try {
            switch (databaseType) {
                case mysql:
                    if (!data_repo.checkTable(MSG_TABLE, MYSQL_CREATE_MSG_TABLE)) {
                        statement = data_repo.createStatement(null);
                        for (String sql : MYSQL_INDEX_MSG_TABLE)
                            statement.execute(sql);
                    }
                    break;
                // TODO support for other databases
            }
        }
        finally {
            data_repo.release(statement, null);
        }
    }

}
