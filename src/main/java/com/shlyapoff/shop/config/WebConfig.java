package com.shlyapoff.shop.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.web.server.Cookie;
import org.springframework.boot.web.server.servlet.CookieSameSiteSupplier;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public CookieSameSiteSupplier sessionCookieSameSiteSupplier() {
        // HTTPS Mini App can run inside Telegram Web's cross-site iframe.
        // Keep plain HTTP localhost usable for the admin panel.
        return cookie -> "JSESSIONID".equals(cookie.getName())
                ? (cookie.getSecure() ? Cookie.SameSite.NONE : Cookie.SameSite.LAX) : null;
    }

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + uploadDir + "/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
    }
}
