package org.learn.exception;

import org.learn.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends ApiException {
    public ServiceUnavailableException(ErrorCode code, String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, code.getCode(), message);
    }
}
