package com.example.lori.dto;

/** Body loi chuan cua API, cung dang voi JsonErrorWriter: {"status":400,"message":"..."}. */
public record ApiError(int status, String message) {
}