package com.example.lori.exception;

import com.example.lori.dto.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void uploadOverTheLimitIs413() {
        var response = handler.handleMultipart(new MaxUploadSizeExceededException(3_145_728L));

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody()).isEqualTo(new ApiError(413, "Payload Too Large"));
    }

    @Test
    void brokenMultipartRequestIs400() {
        var response = handler.handleMultipart(new MultipartException("Failed to parse multipart servlet request"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isEqualTo(new ApiError(400, "Bad Request"));
    }
}