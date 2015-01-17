package org.kontalk.xmppserver.auth;

import com.freiheit.gnupg.GnuPGException;
import org.kontalk.xmppserver.KontalkKeyring;
import org.kontalk.xmppserver.KontalkUser;
import tigase.auth.XmppSaslException;
import tigase.auth.mechanisms.AbstractSasl;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslException;
import java.util.Map;


/**
 * SASL mechanism for Kontalk authentication tokens.
 * @author Daniele Ricci
 */
public class SaslKontalkToken extends AbstractSasl {

    public static final String MECHANISM = "KONTALK-TOKEN";

    private final String legacyServerFingerprint;
    private final String serverFingerprint;
    private final String serverName;

    SaslKontalkToken(Map<? super String, ?> props, CallbackHandler callbackHandler) {
        super(props, callbackHandler);
        legacyServerFingerprint = (String) props.get("legacy-fingerprint");
        serverFingerprint = (String) props.get("fingerprint");
        serverName = (String) props.get("host");
    }

    @Override
    public byte[] evaluateResponse(byte[] response) throws SaslException {
        KontalkKeyring keyring = getKeyring();

        KontalkUser user;
        try {
            user = keyring.verifyLegacyToken(response, legacyServerFingerprint);
        }
        catch (GnuPGException e) {
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

    private KontalkKeyring getKeyring() {
        return KontalkKeyring.getInstance(serverName, serverFingerprint);
    }

}
