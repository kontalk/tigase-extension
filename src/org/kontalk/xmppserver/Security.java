package org.kontalk.xmppserver;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * Security provider initialization.
 * @author Daniele Ricci
 */
public class Security {

    public static final String PROVIDER = "BC";

    static {
        java.security.Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

}
