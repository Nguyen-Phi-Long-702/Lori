package com.example.lori.config;

import com.example.lori.security.ApiKeyFilter;
import com.example.lori.security.JsonErrorWriter;
import com.example.lori.security.JwtAuthenticationFilter;
import com.example.lori.security.JwtTokenProvider;
import com.example.lori.security.RateLimitFilter;
import com.example.lori.security.SecurityPaths;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Cau hinh bao mat: stateless + JWT. Thu tu filter: ApiKeyFilter -> RateLimitFilter -> JwtAuthenticationFilter.
 * Cac filter KHONG gan @Component de Spring Boot khong dang ky them lan nua o tang servlet.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtTokenProvider jwtTokenProvider,
            ProxyManager<String> rateLimitProxyManager,
            @Value("${app.security.api-key}") String apiKey,
            @Value("${app.security.app-signature}") String appSignature) throws Exception {

        ApiKeyFilter apiKeyFilter = new ApiKeyFilter(apiKey, appSignature);
        RateLimitFilter rateLimitFilter = new RateLimitFilter(rateLimitProxyManager);
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtTokenProvider);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        // Chua dang nhap -> 401 (mac dinh se la 403); Android se dung 401 de goi refresh token
                        .authenticationEntryPoint((request, response, authException) ->
                                JsonErrorWriter.write(response, 401, "Authentication required"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                JsonErrorWriter.write(response, 403, "Access denied")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(SecurityPaths.OPEN_PATHS).permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, ApiKeyFilter.class)
                .addFilterAfter(jwtAuthenticationFilter, RateLimitFilter.class);
        return http.build();
    }
}