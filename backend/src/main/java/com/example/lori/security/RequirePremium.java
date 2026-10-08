package com.example.lori.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Danh dau ham (hoac ca Controller) chi danh cho user Premium.
 * PremiumAspect chan user Free bang 403 PREMIUM_REQUIRED truoc khi vao ham.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequirePremium {
}