package com.example.lori.service;

import com.example.lori.dto.AiImageResponse;
import com.example.lori.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Module 5.10 Image Learning: chuyen anh cua user sang AI Service (YOLO nhan dien vat -> tra tu dien Lori -> AI viet
 * cau vi du). Kich thuoc anh do cau hinh multipart gioi han (application.properties); dinh dang va noi dung anh do
 * AI Service kiem tra (400 neu khong doc duoc). Khong gui userId, khong gui Learning Context (AiContextPolicy: IDENTIFY_IMAGE
 * khong nhan phan nao). Khong gan @Transactional o day de khong giu ket noi DB trong luc cho AI Service tra loi.
 */
@Service
@RequiredArgsConstructor
public class AiImageService {

    private final AiRateLimiter aiRateLimiter;
    private final AiServiceClient aiServiceClient;

    public AiImageResponse identify(UUID userId, MultipartFile image) {
        aiRateLimiter.checkIdentifyImage(userId);
        if (image.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Image is empty");
        }
        try {
            return aiServiceClient.identifyImage(image.getBytes());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid image");
        }
    }
}