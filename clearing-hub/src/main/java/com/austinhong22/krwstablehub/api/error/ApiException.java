package com.austinhong22.krwstablehub.api.error;

import org.springframework.http.HttpStatus;

import java.util.Objects;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    public ApiException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
