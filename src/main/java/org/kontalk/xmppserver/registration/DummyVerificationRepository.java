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

package org.kontalk.xmppserver.registration;

import tigase.db.TigaseDBException;
import tigase.xmpp.BareJID;


/**
 * A dummy verification repository using a given value.
 * @see #DEFAULT_CODE
 */
public class DummyVerificationRepository implements VerificationRepository {
    private static final String DEFAULT_CODE = "123456";

    private final String code;

    public DummyVerificationRepository() {
        this(null);
    }

    public DummyVerificationRepository(String code) {
        this.code = code != null ? code : DEFAULT_CODE;
    }

    @Override
    public String generateVerificationCode(BareJID jid) throws AlreadyRegisteredException, TigaseDBException {
        return code;
    }

    @Override
    public boolean verifyCode(BareJID jid, String code) throws TigaseDBException {
        return this.code.equals(code);
    }

    @Override
    public void purge() throws TigaseDBException {
    }
}
