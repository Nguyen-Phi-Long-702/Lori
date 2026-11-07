package com.example.lori.support;

import com.example.lori.exception.ApiException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Kiem tra gon mot ApiException: dung ma HTTP va dung thong diep. */
public final class ApiAssertions {

    private ApiAssertions() {
    }

    public static void assertApiException(ThrowingCallable call, HttpStatus status, String message) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(status);
            assertThat(e.getReason()).isEqualTo(message);
        });
    }
}