package org.kontalk.xmppserver;

import org.junit.Test;
import tigase.util.Base64;

import static org.junit.Assert.*;


/** Test class for {@link KontalkKeyring}. */
public class KontalkKeyringTest {

    private static final String TEST_PUBLIC_KEY =
        "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";

    private static final String TEST_LEGACY_TOKEN = "owGbwMvMwCHoM9VyC+MPpTmMp02SGEJ2zd+bmJKbmVdjbO5i4GpmbuHs4mJo6eZk6WRoYeRkbGFg4mxpamzpZGJg6GZhZGTp3BHHwiDIwcDGygTSy8DFKQAzsHg3w1/Rx9eY9Mz/XOUKODthhV5fl13opUTrK78rN28vX3BOaE0SI8Ncphfmm6zEzbVrzgrdCCjLPHN2zk9WrcyYCv15jcI3tI4CAA==";

    @Test
    public void testVerifyLegacyToken() throws Exception {
        KontalkKeyring keyring = new KontalkKeyring("beta.kontalk.net", null, System.getProperty("gnupg.home"), 1);
        String fpr = keyring.importKeyGlobal(Base64.decode(TEST_PUBLIC_KEY));
        assertNotNull(fpr);
        KontalkUser user = keyring.verifyLegacyToken(Base64.decode(TEST_LEGACY_TOKEN), "37D0E678CDD19FB9B182B3804C9539B401F8229C");
        assertNotNull(user);
        assertNotNull(user.getJID());
        assertEquals(user.getJID().toString(), "admin@beta.kontalk.net");
    }

    private KontalkKeyring createPartitionedKeyring(int partitions) {
        return createPartitionedKeyring(null, partitions);
    }

    private KontalkKeyring createPartitionedKeyring(String fingerprint, int partitions) {
        String home = System.getProperty("gnupg.home");
        if (home == null || home.length() == 0)
            home = "/test/gnupg/home";
        return new KontalkKeyring("beta.kontalk.net", fingerprint, home, partitions);
    }

    @Test
    public void testGetPartition() throws Exception {
        KontalkKeyring keyring;

        keyring = createPartitionedKeyring(1);
        assertEquals(0, keyring.getPartition("37D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(0, keyring.getPartition("07D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(0, keyring.getPartition("47D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(0, keyring.getPartition("A7D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(0, keyring.getPartition("F7D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(0, keyring.getPartition("C7D0E678CDD19FB9B182B3804C9539B401F8229C"));

        keyring = createPartitionedKeyring(2);
        assertEquals(0, keyring.getPartition("37D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(0, keyring.getPartition("27D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(0, keyring.getPartition("07D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(1, keyring.getPartition("A7D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(1, keyring.getPartition("F7D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(1, keyring.getPartition("B7D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(0, keyring.getPartition("67D0E678CDD19FB9B182B3804C9539B401F8229C"));

        keyring = createPartitionedKeyring(4);
        assertEquals(0, keyring.getPartition("37D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(1, keyring.getPartition("47D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(0, keyring.getPartition("07D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(1, keyring.getPartition("77D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(2, keyring.getPartition("A7D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(2, keyring.getPartition("97D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(3, keyring.getPartition("D7D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(3, keyring.getPartition("F7D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(0, keyring.getPartition("17D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(3, keyring.getPartition("C7D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(2, keyring.getPartition("B7D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(0, keyring.getPartition("27D0E678CDD19FB9B182B3804C9539B401F8229C"));

        keyring = createPartitionedKeyring(8);
        assertEquals(1, keyring.getPartition("37D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(2, keyring.getPartition("47D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(0, keyring.getPartition("07D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(3, keyring.getPartition("77D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(5, keyring.getPartition("A7D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(4, keyring.getPartition("97D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(6, keyring.getPartition("D7D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(7, keyring.getPartition("F7D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(0, keyring.getPartition("17D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(6, keyring.getPartition("C7D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(5, keyring.getPartition("B7D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(1, keyring.getPartition("27D0E678CDD19FB9B182B3804C9539B401F8229C"));

        keyring = createPartitionedKeyring(16);
        assertEquals(3, keyring.getPartition("37D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(4, keyring.getPartition("47D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(0, keyring.getPartition("07D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(7, keyring.getPartition("77D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(10, keyring.getPartition("A7D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(9, keyring.getPartition("97D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(13, keyring.getPartition("D7D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(15, keyring.getPartition("F7D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(1, keyring.getPartition("17D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(12, keyring.getPartition("C7D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(11, keyring.getPartition("B7D0E678CDD19FB9B182B3804C9539B401F8229C"));
        assertEquals(2, keyring.getPartition("27D0E678CDD19FB9B182B3804C9539B401F8229C"));
    }

}
