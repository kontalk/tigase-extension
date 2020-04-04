package org.kontalk.xmppserver.registration;


/**
 * Interface implemented by registration request objects.
 * For use by registration providers.
 * @author Daniele Ricci
 */
public interface RegistrationRequest {

    /** Returns the sender id of the call/SMS/whatever. */
    String getSenderId();

}
