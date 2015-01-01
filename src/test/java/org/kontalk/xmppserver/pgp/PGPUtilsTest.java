package org.kontalk.xmppserver.pgp;

import org.junit.Test;

import static org.junit.Assert.*;


/** Test class for {@link PGPUtils}. */
public class PGPUtilsTest {

    @Test
    public void testParseUserID() throws Exception {
        PGPUserID uid;
        uid = PGPUtils.parseUserID("Example User <user@example.com>");
        assertNotNull(uid);
        assertEquals("Example User", uid.getName());
        assertNull(uid.getComment());
        assertEquals("user@example.com", uid.getEmail());

        uid = PGPUtils.parseUserID("Example User (Test comment) <user@example.com>");
        assertNotNull(uid);
        assertEquals("Example User", uid.getName());
        assertEquals("Test comment", uid.getComment());
        assertEquals("user@example.com", uid.getEmail());
    }
}
