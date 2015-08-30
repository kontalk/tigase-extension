package org.kontalk.xmppserver.registration.cognalys;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;


public class CognalysVerifyClientTest {

    private static final String APP_ID = "your-app-id";
    private static final String TOKEN = "your-token";

    // insert a value here to execute a request test
    private static final String TEST_NUMBER = null;
    // insert values here to execute a confirm test
    private static final String TEST_KEYMATCH = null;
    private static final String TEST_OTP = null;

    private CognalysVerifyClient client;

    @Before
    public void setUp() throws Exception {
        client = new CognalysVerifyClient(APP_ID, TOKEN);
    }

    private void assertStatus(AbstractResult res) {
        assertEquals(AbstractResult.STATUS_SUCCESS, res.getStatus());
    }

    @Test
    public void testRequest() throws Exception {
        if (TEST_NUMBER != null) {
            RequestResult res = client.request(TEST_NUMBER);
            assertStatus(res);
        }
    }

    @Test
    public void testConfirm() throws Exception {
        if (TEST_KEYMATCH != null && TEST_OTP != null) {
            ConfirmResult res = client.confirm(TEST_KEYMATCH, TEST_OTP);
            assertStatus(res);
        }
    }
}
