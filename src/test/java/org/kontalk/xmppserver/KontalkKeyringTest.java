package org.kontalk.xmppserver;

import org.junit.Before;
import org.junit.Test;
import tigase.util.Base64;

import static org.junit.Assert.*;


/** Test class for {@link KontalkKeyring}. */
public class KontalkKeyringTest {

    private static final String TEST_PUBLIC_KEY =
        "-----BEGIN PGP PUBLIC KEY BLOCK-----\n" +
        "Version: GnuPG v1\n" +
        "\n" +
        "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\n" +
        "=Q8Gl\n" +
        "-----END PGP PUBLIC KEY BLOCK-----";

    private static final String TEST_LEGACY_TOKEN = "owGbwMvMwCHoM9VyC+MPpTmMp02SGEJ2zd+bmJKbmVdjbO5i4GpmbuHs4mJo6eZk6WRoYeRkbGFg4mxpamzpZGJg6GZhZGTp3BHHwiDIwcDGygTSy8DFKQAzsHg3w1/Rx9eY9Mz/XOUKODthhV5fl13opUTrK78rN28vX3BOaE0SI8Ncphfmm6zEzbVrzgrdCCjLPHN2zk9WrcyYCv15jcI3tI4CAA==";

    private KontalkKeyring keyring;

    @Before
    public void setUp() {
        keyring = new KontalkKeyring("beta.kontalk.net", "37D0E678CDD19FB9B182B3804C9539B401F8229C");
        keyring.importKey(TEST_PUBLIC_KEY.getBytes());
    }

    @Test
    public void testVerifyLegacyToken() throws Exception {
        KontalkUser user = keyring.verifyLegacyToken(Base64.decode(TEST_LEGACY_TOKEN), "37D0E678CDD19FB9B182B3804C9539B401F8229C");
        assertNotNull(user);
        assertNotNull(user.getJID());
        assertEquals(user.getJID().toString(), "admin@beta.kontalk.net");
    }
}
