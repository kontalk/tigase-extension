package org.kontalk.xmppserver.registration.cognalys;

import java.util.List;


public class RequestResult extends AbstractResult {

    private final String keymatch;
    private final String otpStart;

    public RequestResult(String keymatch, String otpStart) {
        super(STATUS_SUCCESS);
        this.keymatch = keymatch;
        this.otpStart = otpStart;
    }

    public RequestResult(List<CognalysError> errors) {
        super(STATUS_FAILED, errors);
        this.keymatch = null;
        this.otpStart = null;
    }

    public String getKeymatch() {
        return keymatch;
    }

    public String getOtpStart() {
        return otpStart;
    }

}
