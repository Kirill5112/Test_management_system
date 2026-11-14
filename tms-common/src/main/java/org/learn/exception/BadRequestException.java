package org.learn.exception;

import org.learn.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public class BadRequestException extends ApiException {
    public BadRequestException(ErrorCode code, String message) {
        super(HttpStatus.BAD_REQUEST, code.getCode(), message);
    }
}
