package com.example.lori.service;

import com.example.lori.dto.ContentUpdateItem;
import com.example.lori.dto.ContentUpdatesResponse;
import com.example.lori.dto.ContentVersionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Nghiep vu phien ban noi dung (/api/content): doc content-manifest.json mot lan luc khoi dong. */
@Service
public class ContentService {

    private static final String MANIFEST_PATH = "content/content-manifest.json";

    private final Manifest manifest;

    public ContentService(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource(MANIFEST_PATH).getInputStream()) {
            this.manifest = objectMapper.readValue(in, Manifest.class);
        } catch (IOException e) {
            // Thieu/hong file manifest la loi cau hinh: de app dung ngay luc khoi dong thay vi chay sai
            throw new IllegalStateException("Cannot read " + MANIFEST_PATH, e);
        }
    }

    public ContentVersionResponse getVersion() {
        return new ContentVersionResponse(manifest.version());
    }

    public ContentUpdatesResponse getUpdates(int sinceVersion) {
        List<ContentUpdateItem> newer = manifest.updates().stream()
                .filter(update -> update.version() > sinceVersion)
                .toList();
        return new ContentUpdatesResponse(manifest.version(), newer);
    }

    /** Cau truc file content-manifest.json. */
    public record Manifest(int version, List<ContentUpdateItem> updates) {
    }
}