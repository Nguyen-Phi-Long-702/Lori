package com.example.lori.service;

import com.example.lori.dto.AiImageResponse;
import com.example.lori.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiImageServiceTest {

    private final AiRateLimiter rateLimiter = mock(AiRateLimiter.class);
    private final AiServiceClient aiServiceClient = mock(AiServiceClient.class);
    private final AiImageService service = new AiImageService(rateLimiter, aiServiceClient);
    private final UUID userId = UUID.randomUUID();

    @Test
    void identifySendsTheImageBytesToAiService() {
        byte[] bytes = {1, 2, 3};
        AiImageResponse result = new AiImageResponse("image_identification", List.of());
        when(aiServiceClient.identifyImage(bytes)).thenReturn(result);

        assertThat(service.identify(userId, new MockMultipartFile("image", "a.jpg", "image/jpeg", bytes)))
                .isEqualTo(result);
        verify(rateLimiter).checkIdentifyImage(userId);
    }

    @Test
    void emptyImageIsRejectedWithoutCallingAiService() {
        MultipartFile empty = new MockMultipartFile("image", new byte[0]);

        assertThatThrownBy(() -> service.identify(userId, empty)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        verifyNoInteractions(aiServiceClient);
    }

    @Test
    void unreadableImageIsRejectedWith400() throws IOException {
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.isEmpty()).thenReturn(false);
        when(broken.getBytes()).thenThrow(new IOException("disk"));

        assertThatThrownBy(() -> service.identify(userId, broken)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        verifyNoInteractions(aiServiceClient);
    }

    @Test
    void rateLimitedUserNeverReachesAiService() {
        doThrow(new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"))
                .when(rateLimiter).checkIdentifyImage(userId);

        assertThatThrownBy(() -> service.identify(userId, new MockMultipartFile("image", new byte[]{1})))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(aiServiceClient);
    }
}