package org.kontalk.xmppserver.registration.cognalys;

import java.util.List;


public class ConfirmResult extends AbstractResult {

    private final String mobile;

    public ConfirmResult(String mobile) {
        super(STATUS_SUCCESS);
        this.mobile = mobile;
    }

    public ConfirmResult(String mobile, List<CognalysError> errors) {
        super(STATUS_FAILED, errors);
        this.mobile = mobile;
    }

    public String getMobile() {
        return mobile;
    }

}
