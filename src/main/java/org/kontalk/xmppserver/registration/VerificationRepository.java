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

    /** Purges old verification entries from storage. */
    public void purge() throws TigaseDBException;

    /** Exception thrown when the user has already tried to register recently. */
    public static final class AlreadyRegisteredException extends Exception {
        private static final long serialVersionUID = 1L;
    }

}
