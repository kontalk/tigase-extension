package org.kontalk.xmppserver.pgp;

import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.junit.*;

import java.io.InputStream;

import static org.junit.Assert.*;

@Ignore
public class KyotoPGPLocalKeyringTest {
    private static final String TEST_FILENAME = "keyring";
    private static final String TEST_KEY = "/test.key";

    private PGPLocalKeyring keyring;

    @Before
    public void setUp() throws Exception {
        keyring = new KyotoPGPLocalKeyring(TEST_FILENAME);
    }

    @After
    public void tearDown() throws Exception {
        keyring.close();
    }

    @Test
    public void testGetKey() throws Exception {
        InputStream in = getClass().getResourceAsStream(TEST_KEY);
        PGPPublicKeyRing key = keyring.importKey(in);
        in.close();
        String fpr = PGPUtils.getFingerprint(key);
        PGPPublicKeyRing newKey = keyring.getKey(fpr);
        assertArrayEquals(key.getEncoded(), newKey.getEncoded());
    }

    @Test
    public void testImportKey() throws Exception {
        InputStream in = getClass().getResourceAsStream(TEST_KEY);
        PGPPublicKeyRing key = keyring.importKey(in);
        in.close();
        assertNotNull(key);
    }
}
