package com.example.lori.service;

import com.example.lori.dto.ContentUpdatesResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Doc content-manifest.json that cua Backend nen khong gan cung so phien ban trong test. */
class ContentServiceTest {

    private final ContentService service = new ContentService(new ObjectMapper());

    @Test
    void updatesAfterTheCurrentVersionAreEmpty() {
        int current = service.getVersion().version();

        ContentUpdatesResponse response = service.getUpdates(current);

        assertThat(response.currentVersion()).isEqualTo(current);
        assertThat(response.updates()).isEmpty();
    }

    @Test
    void updatesSinceVersionZeroListEveryNewerVersion() {
        ContentUpdatesResponse response = service.getUpdates(0);

        assertThat(response.currentVersion()).isEqualTo(service.getVersion().version());
        assertThat(response.updates()).isNotEmpty();
        assertThat(response.updates()).allSatisfy(update -> assertThat(update.version()).isGreaterThan(0));
    }
}