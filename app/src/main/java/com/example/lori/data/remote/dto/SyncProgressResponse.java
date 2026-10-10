package com.example.lori.data.remote.dto;

//received = số mục server nhận; applied = số mục thực sự được ghi (mới hơn bản trên server)
public class SyncProgressResponse {
    public int received;
    public int applied;
}