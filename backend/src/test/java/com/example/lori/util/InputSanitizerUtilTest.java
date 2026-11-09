package com.example.lori.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InputSanitizerUtilTest {

    @Test
    void nullStaysNull() {
        assertThat(InputSanitizerUtil.sanitize(null)).isNull();
    }

    @Test
    void htmlTagsAreRemovedAndTheTextIsKept() {
        assertThat(InputSanitizerUtil.sanitize("<b>Linh</b> Nguyen")).isEqualTo("Linh Nguyen");
    }

    @Test
    void scriptTagsAreRemovedAndTheNameSurvives() {
        assertThat(InputSanitizerUtil.sanitize("<script>alert(1)</script>Linh"))
                .doesNotContain("<", ">")
                .endsWith("Linh");
    }

    @Test
    void whitespaceIsCollapsedAndTrimmed() {
        assertThat(InputSanitizerUtil.sanitize("  Linh \t  Nguyen \n")).isEqualTo("Linh Nguyen");
    }

    @Test
    void angleBracketsLeftInPlainTextAreDropped() {
        assertThat(InputSanitizerUtil.sanitize("a < b > c")).doesNotContain("<", ">");
    }

    @Test
    void inputWithOnlyTagsBecomesEmpty() {
        assertThat(InputSanitizerUtil.sanitize("<b></b>")).isEmpty();
    }
}