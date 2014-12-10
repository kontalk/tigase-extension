package org.kontalk.xmppserver.registration;

import tigase.db.TigaseDBException;
import tigase.xmpp.BareJID;


/**
 * Interface to a validation code repository.
 * @author Daniele Ricci
 */
public interface VerificationRepository {

    /** Length of a verification code. */
    public static final int VERIFICATION_CODE_LENGTH = 6;

    /** Registers a new verification code for the given user. */
    public String generateVerificationCode(BareJID jid) throws AlreadyRegisteredException, TigaseDBException;

    /** Verifies and delete the given verification. */
    public boolean verifyCode(BareJID jid, String code) throws TigaseDBException;

    /** Exception thrown when the user has already tried to register recently. */
    public static final class AlreadyRegisteredException extends Exception {
        private static final long serialVersionUID = 1L;
    }

}
