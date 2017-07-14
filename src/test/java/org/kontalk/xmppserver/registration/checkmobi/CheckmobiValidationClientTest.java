package org.kontalk.xmppserver.registration.checkmobi;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class CheckmobiValidationClientTest {

    private static final String API_KEY = null;

    // insert a value here to execute a request test
    private static final String TEST_NUMBER = null;
    // insert values here to execute a verify test
    private static final String TEST_REQUESTID = null;
    private static final String TEST_PIN = null;

    private CheckmobiValidationClient client;

    @Before
    public void setUp() throws Exception {
        client = CheckmobiValidationClient.reverseCallerID(API_KEY);
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
        if (TEST_REQUESTID != null && TEST_PIN != null) {
            VerifyResult res = client.verify(TEST_REQUESTID, TEST_PIN);
            assertStatus(res);
        }
    }
}
