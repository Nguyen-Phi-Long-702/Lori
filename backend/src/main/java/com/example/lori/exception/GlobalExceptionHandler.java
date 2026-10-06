package com.example.lori.exception;

import com.example.lori.dto.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * Chuan hoa body loi cua toan bo Controller thanh ApiError {status, message}.
 * Ke thua ResponseEntityExceptionHandler de van giu dung ma HTTP cua cac loi MVC co san (405, 400...),
 * ApiException (ResponseStatusException) di qua handleExceptionInternal nen cung duoc chuan hoa.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** Loi khong luong truoc: ghi log day du, chi tra message chung (khong lo chi tiet noi bo). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal server error"));
    }

    /** Loi validate (@Valid): gop thanh "truong: loi; truong: loi". */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (message.isEmpty()) {
            message = "Invalid request";
        }
        return ResponseEntity.status(status).headers(headers).body(new ApiError(status.value(), message));
    }

    /** Diem chung cua ApiException va cac loi MVC con lai (405, 400 JSON hong, 404...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        String message = null;
        if (ex instanceof ResponseStatusException responseStatusException) {
            // ApiException la ResponseStatusException: message chinh la "reason" ma Service da truyen vao
            message = responseStatusException.getReason();
        } else if (ex instanceof ErrorResponse errorResponse) {
            message = errorResponse.getBody().getDetail();
        }
        if (message == null) {
            message = reasonPhrase(statusCode);
        }
        return ResponseEntity.status(statusCode).headers(headers).body(new ApiError(statusCode.value(), message));
    }

    private static String reasonPhrase(HttpStatusCode statusCode) {
        HttpStatus resolved = HttpStatus.resolve(statusCode.value());
        return resolved != null ? resolved.getReasonPhrase() : "Request failed";
    }
}