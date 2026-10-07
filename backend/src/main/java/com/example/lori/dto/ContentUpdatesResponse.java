package com.example.lori.dto;

import java.util.List;

/** Phien ban hien tai + cac cap nhat co version lon hon sinceVersion app da gui. */
public record ContentUpdatesResponse(int currentVersion, List<ContentUpdateItem> updates) {
}