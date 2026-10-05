package com.example.lori.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.AntPathMatcher;

/** Cac duong dan khong yeu cau X-API-Key, khong bi rate limit, va duoc permitAll. */
public final class SecurityPaths {

    public static final String[] OPEN_PATHS = {
            "/actuator/health/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/error"
    };

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private SecurityPaths() {
    }

    public static boolean isOpenPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String pattern : OPEN_PATHS) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}