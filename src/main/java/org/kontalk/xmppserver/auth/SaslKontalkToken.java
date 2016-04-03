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

package org.kontalk.xmppserver.auth;

import org.bouncycastle.openpgp.PGPException;
import org.kontalk.xmppserver.KontalkKeyring;
import org.kontalk.xmppserver.KontalkUser;
import tigase.auth.XmppSaslException;
import tigase.auth.mechanisms.AbstractSasl;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslException;
import java.io.IOException;
import java.util.Map;


/**
 * SASL mechanism for Kontalk authentication tokens.
 * @author Daniele Ricci
 */
public class SaslKontalkToken extends AbstractSasl {

    public static final String MECHANISM = "KONTALK-TOKEN";

    private final String legacyServerFingerprint;
    private final String serverName;

    SaslKontalkToken(Map<? super String, ?> props, CallbackHandler callbackHandler) {
        super(props, callbackHandler);
        legacyServerFingerprint = (String) props.get("legacy-fingerprint");
        serverName = (String) props.get("host");
    }

    @Override
    public byte[] evaluateResponse(byte[] response) throws SaslException {
        KontalkKeyring keyring;
        try {
            keyring = getKeyring();
        }
        catch (IOException | PGPException e) {
            throw new XmppSaslException(XmppSaslException.SaslError.temporary_auth_failure);
        }

        KontalkUser user;
        try {
            user = keyring.verifyLegacyToken(response, legacyServerFingerprint);
        }
        catch (Exception e) {
            throw new XmppSaslException(XmppSaslException.SaslError.temporary_auth_failure);
        }

        if (user != null) {
            authorizedId = user.getJID().toString();
        }
        else {
            throw new XmppSaslException(XmppSaslException.SaslError.not_authorized);
        }

        complete = true;

        return null;
    }

    @Override
    public String getAuthorizationID() {
        return authorizedId;
    }

    @Override
    public String getMechanismName() {
        return MECHANISM;
    }

    @Override
    public byte[] unwrap(byte[] incoming, int offset, int len) throws SaslException {
        return null;
    }

    @Override
    public byte[] wrap(byte[] outgoing, int offset, int len) throws SaslException {
        return null;
    }

    private KontalkKeyring getKeyring() throws IOException, PGPException {
        return KontalkKeyring.getInstance(serverName);
    }

}
