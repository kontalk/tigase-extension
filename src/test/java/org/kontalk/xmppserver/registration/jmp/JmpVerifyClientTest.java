package org.kontalk.xmppserver.registration.jmp;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class JmpVerifyClientTest {

    private static final String API_KEY = null;
    private static final String API_SECRET = null;

    // insert values here to execute a verify test
    private static final String TEST_NUMBER = null;
    private static final String TEST_BRAND = null;
    // insert values here to execute a verify check test
    private static final String TEST_REQUEST_ID = null;
    private static final String TEST_CODE = null;

    private JmpVerifyClient client;

    @Before
    public void setUp() throws Exception {
        client = new JmpVerifyClient(API_KEY, API_SECRET);
    }

    private void assertStatus(BaseResult res) {
        assertEquals(BaseResult.STATUS_OK, res.getStatus());
    }

    @Test
    public void testRequest() throws Exception {
        if (TEST_NUMBER != null && TEST_BRAND != null) {
            VerifyResult res = client.verify(TEST_NUMBER, TEST_BRAND);
            System.out.println(res.getRequestId());
            assertStatus(res);
        }
    }

    @Test
    public void testCheck() throws Exception {
        if (TEST_REQUEST_ID != null && TEST_CODE != null) {
            CheckResult res = client.check(TEST_REQUEST_ID, TEST_CODE);
            assertStatus(res);
        }
    }
}
