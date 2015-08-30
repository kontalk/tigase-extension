package org.kontalk.xmppserver.registration.cognalys;

import java.util.ArrayList;
import java.util.List;


abstract class AbstractResult {

    // internal status codes
    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_FAILED = 1;

    /** Internal status code. */
    protected final int status;
    /** List of errors. */
    protected List<CognalysError> errors;

    protected AbstractResult(int status) {
        this(status, (status == STATUS_SUCCESS) ? null : new ArrayList<CognalysError>());
    }

    protected AbstractResult(int status, List<CognalysError> errors) {
        this.status = status;
        this.errors = errors;
    }

    public int getStatus() {
        return status;
    }

    public boolean isSuccess() {
        return status == STATUS_SUCCESS;
    }

    public List<CognalysError> getErrors() {
        return errors;
    }

}
