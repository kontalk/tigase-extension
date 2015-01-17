package org.kontalk.xmppserver.auth;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslServerFactory;
import java.util.Map;


/**
 * A custom SASL server factory to support KONTALK-TOKEN auth mechanism.
 * @author Daniele Ricci
 */
public class KontalkSaslServerFactory implements SaslServerFactory {

    @Override
    public SaslServer createSaslServer(final String mechanism, final String protocol, final String serverName,
                                       final Map<String, ?> props, final CallbackHandler callbackHandler) throws SaslException {
        if (mechanism.equals(SaslKontalkToken.MECHANISM)) {
            return new SaslKontalkToken(props, callbackHandler);
        }
        else {
            throw new SaslException("Mechanism not supported yet.");
        }
    }

    @Override
    public String[] getMechanismNames(Map<String, ?> props) {
        return new String[] { SaslKontalkToken.MECHANISM };
    }

}
