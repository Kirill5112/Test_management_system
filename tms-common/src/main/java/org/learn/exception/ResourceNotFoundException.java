package org.learn.exception;

import org.learn.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(ErrorCode code, String message) {
        super(HttpStatus.NOT_FOUND, code.getCode(), message);
    }
}