package com.shlyapoff.shop.config;

import com.shlyapoff.shop.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // 1. Публичные страницы (доступны всем)
                        .requestMatchers("/", "/catalog", "/product/**", "/css/**", "/js/**", "/images/**", "/login", "/error").permitAll()

                        // 2. ИСПРАВЛЕНИЕ: Разрешаем гостям работать с корзиной (добавлять/удалять товары)
                        .requestMatchers("/cart/**").permitAll()

                        // 3. Админка доступна ТОЛЬКО админу
                        .requestMatchers("/admin/**").hasAuthority("ROLE_ADMIN")

                        // 4. Всё остальное требует входа
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/admin", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/")
                        .permitAll()
                )
                .userDetailsService(customUserDetailsService);

        return http.build();
    }
}