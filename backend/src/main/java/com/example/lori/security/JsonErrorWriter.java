package com.example.lori.security;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Ghi response loi dang JSON cho cac filter/entry point. Message phai la hang so, khong chua ky tu " . */
public final class JsonErrorWriter {

    private JsonErrorWriter() {
    }

    public static void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }
}