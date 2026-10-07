package com.example.lori.dto;

/** Ket qua dong bo: received = so muc nhan duoc, applied = so muc thuc su duoc ghi (them moi hoac moi hon ban tren server). */
public record SyncProgressResponse(int received, int applied) {
}