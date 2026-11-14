package org.learn.exception;

import org.learn.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {
    public ConflictException(ErrorCode code, String message) {
        super(HttpStatus.CONFLICT, code.getCode(), message);
    }
}
