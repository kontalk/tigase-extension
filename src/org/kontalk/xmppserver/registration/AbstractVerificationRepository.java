package org.kontalk.xmppserver.registration;


/**
 * Interface to a validation code repository.
 * @author Daniele Ricci
 */
public abstract class AbstractVerificationRepository implements VerificationRepository {

    protected String verificationCode() {
        return generateRandomString(VERIFICATION_CODE_LENGTH);
    }

    private static String generateRandomString(int length) {
        StringBuilder buffer = new StringBuilder();
        final String characters = "1234567890";

        int charactersLength = characters.length();

        for (int i = 0; i < length; i++) {
            double index = Math.random() * charactersLength;
            buffer.append(characters.charAt((int) index));
        }
        return buffer.toString();
    }

}
