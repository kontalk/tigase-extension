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

    private static final String CREATE_QUERY_ID = "create-query";
    private static final String CREATE_QUERY_SQL = "INSERT INTO validations VALUES (?, ?, SYSDATE())";

    private static final String SELECT_QUERY_ID = "select-query";
    private static final String SELECT_QUERY_SQL = "SELECT user_id FROM validations WHERE code = ?";

    private static final String DELETE_QUERY_ID = "delete-query";
    private static final String DELETE_QUERY_SQL = "DELETE FROM validations WHERE code = ?";

    private DataRepository repo;

    public DataVerificationRepository(String dbUri) throws ClassNotFoundException,
            DBInitException, InstantiationException, SQLException, IllegalAccessException {
        repo = RepositoryFactory.getDataRepository(null, dbUri, null);
        repo.initPreparedStatement(CREATE_QUERY_ID, CREATE_QUERY_SQL);
        repo.initPreparedStatement(SELECT_QUERY_ID, SELECT_QUERY_SQL);
        repo.initPreparedStatement(DELETE_QUERY_ID, DELETE_QUERY_SQL);
    }

    @Override
    public String generateVerificationCode(BareJID jid) throws AlreadyRegisteredException, TigaseDBException {
        String code = verificationCode();
        PreparedStatement stm = null;
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
        finally {
            repo.release(stm, null);
        }
    }

    @Override
    public boolean verifyCode(BareJID jid, String code) throws TigaseDBException {
        PreparedStatement stm = null;
        ResultSet rs = null;
        try {
            stm = repo.getPreparedStatement(jid, SELECT_QUERY_ID);
            stm.setString(1, code);
            rs = stm.executeQuery();
            if (rs.next()) {
                String userid = rs.getString(1);
                if (jid.toString().equals(userid)) {
                    repo.release(stm, null);
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
            repo.release(stm, rs);
        }
    }

}
