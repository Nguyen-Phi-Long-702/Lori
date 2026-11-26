package com.example.lori.validation;

import com.example.lori.dto.ChangePasswordRequest;
import com.example.lori.dto.LoginRequest;
import com.example.lori.dto.RegisterRequest;
import com.example.lori.dto.ResetPasswordRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MaxUtf8BytesValidatorTest {

    /** Chu "e" co dau: 1 ky tu = 3 byte UTF-8. */
    private static final String E_DAU = "ệ";

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void ascii72KyTu_hopLe() {
        assertThat(truongSai(new RegisterRequest("Lan", "lan@example.com", "a".repeat(72)))).isEmpty();
    }

    @Test
    void ascii73KyTu_biTuChoi() {
        assertThat(truongSai(new RegisterRequest("Lan", "lan@example.com", "a".repeat(73))))
                .containsExactly("password");
    }

    @Test
    void tiengViet24KyTu72Byte_hopLe() {
        assertThat(truongSai(new RegisterRequest("Lan", "lan@example.com", E_DAU.repeat(24)))).isEmpty();
    }

    @Test
    void tiengViet25KyTu75Byte_biTuChoi() {
        assertThat(truongSai(new RegisterRequest("Lan", "lan@example.com", E_DAU.repeat(25))))
                .containsExactly("password");
    }

    @Test
    void moiTruongMatKhau_deuApDungGioiHanByte() {
        String quaDai = E_DAU.repeat(25);

        assertThat(truongSai(new LoginRequest("lan@example.com", quaDai))).containsExactly("password");
        assertThat(truongSai(new ResetPasswordRequest("lan@example.com", "123456", quaDai)))
                .containsExactly("newPassword");
        assertThat(truongSai(new ChangePasswordRequest(quaDai, "Valid@1234"))).containsExactly("currentPassword");
        assertThat(truongSai(new ChangePasswordRequest("Old@12345", quaDai))).containsExactly("newPassword");
    }

    private Set<String> truongSai(Object request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
