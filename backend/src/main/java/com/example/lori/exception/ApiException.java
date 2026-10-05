package com.example.lori.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Loi nghiep vu kem ma HTTP. Service nem ra, Spring tu tra dung ma HTTP.
 * GlobalExceptionHandler (Ngay 5-6) se chuan hoa body loi sau.
 */
public class ApiException extends ResponseStatusException {

    public ApiException(HttpStatus status, String message) {
        super(status, message);
    }
}