package com.example.lori.data.remote.dto;

//Body lỗi chuẩn của Backend, ví dụ {"status":403,"message":"PREMIUM_REQUIRED"}
public class ApiErrorResponse {
    public int status;
    public String message;
}