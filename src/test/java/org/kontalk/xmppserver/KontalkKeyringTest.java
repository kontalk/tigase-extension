package org.kontalk.xmppserver;

import org.junit.Test;
import tigase.util.Base64;

import static org.junit.Assert.*;


/** Test class for {@link KontalkKeyring}. */
public class KontalkKeyringTest {

    private static final String TEST_LEGACY_TOKEN = "owGbwMvMwCHoM9VyC+MPpTmMp02SGEJ2zd+bmJKbmVdjbO5i4GpmbuHs4mJo6eZk6WRoYeRkbGFg4mxpamzpZGJg6GZhZGTp3BHHwiDIwcDGygTSy8DFKQAzsHg3w1/Rx9eY9Mz/XOUKODthhV5fl13opUTrK78rN28vX3BOaE0SI8Ncphfmm6zEzbVrzgrdCCjLPHN2zk9WrcyYCv15jcI3tI4CAA==";

    @Test
    public void testVerifyLegacyToken() throws Exception {
        KontalkKeyring k = new KontalkKeyring("beta.kontalk.net", "37D0E678CDD19FB9B182B3804C9539B401F8229C");
        KontalkUser user = k.verifyLegacyToken(Base64.decode(TEST_LEGACY_TOKEN), "37D0E678CDD19FB9B182B3804C9539B401F8229C");
        assertNotNull(user);
        assertNotNull(user.getJID());
        assertEquals(user.getJID().toString(), "admin@beta.kontalk.net");
    }
}
