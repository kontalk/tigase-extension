package org.kontalk.xmppserver;

import org.apache.log4j.BasicConfigurator;
import org.bouncycastle.openpgp.PGPException;
import org.junit.Before;
import org.junit.Test;
import tigase.util.TigaseStringprepException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


/** Test class for {@link KontalkKeyring}. */
public class KontalkKeyringTest {
    private KontalkKeyring keyring;

    @Before
    public void setUp() throws IOException, PGPException {
        BasicConfigurator.configure();
        keyring = KontalkKeyring.getInstance("beta.kontalk.net");
    }

    @Test
    public void testAuthenticate() throws IOException, PGPException {
        File f = new File("test.key");
        byte[] buf = new byte[(int) f.length()];
        FileInputStream in = new FileInputStream("test.key");
        in.read(buf);
        in.close();
        assertNotNull(keyring.authenticate(buf));
    }

    @Test
    public void testPostAuthenticate() throws IOException, PGPException, TigaseStringprepException {
        File f = new File("test.key");
        byte[] buf = new byte[(int) f.length()];
        FileInputStream in = new FileInputStream("test.key");
        in.read(buf);
        in.close();
        // TODO
        assertTrue(keyring.postAuthenticate(
                new KontalkUser("...@beta.kontalk.net",
                        "..."), null));
    }

}
