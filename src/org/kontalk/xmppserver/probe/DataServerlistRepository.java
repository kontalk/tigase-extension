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


/**
 * Validation code repository backed by a {@link tigase.db.DataRepository}.
 * @author Daniele Ricci
 */
public class DataServerlistRepository implements ServerlistRepository {

    private static final String SELECT_QUERY_SQL = "SELECT fingerprint, host, enabled FROM servers";

    private DataRepository repo;
    private List<ServerInfo> serverlist;

    public DataServerlistRepository(String dbUri) throws ClassNotFoundException,
            DBInitException, InstantiationException, SQLException, IllegalAccessException {
        repo = RepositoryFactory.getDataRepository(null, dbUri, null);
    }

    @Override
    public List<ServerInfo> getList() {
        return serverlist;
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
