package org.kontalk.xmppserver.registration.cognalys;


public class CognalysError {

    private final int code;
    private final String text;

    public CognalysError(int code, String text) {
        this.code = code;
        this.text = text;
    }

    public int getCode() {
        return code;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return code + ": " + text;
    }

}
