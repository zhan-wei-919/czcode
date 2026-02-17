package com.czcode.authservice.config;

import com.czcode.authservice.common.web.RestSecurityExceptionHandler;
import com.czcode.authservice.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final RestSecurityExceptionHandler restSecurityExceptionHandler;

  public SecurityConfig(
      JwtAuthenticationFilter jwtAuthenticationFilter,
      RestSecurityExceptionHandler restSecurityExceptionHandler) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.restSecurityExceptionHandler = restSecurityExceptionHandler;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(exceptions -> exceptions
            .authenticationEntryPoint(restSecurityExceptionHandler)
            .accessDeniedHandler(restSecurityExceptionHandler))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/v1/health", "/actuator/health", "/actuator/info").permitAll()
            .requestMatchers(
                "/api/v1/auth/send-code",
                "/api/v1/auth/register",
                "/api/v1/auth/login",
                "/api/v1/auth/refresh")
            .permitAll()
            .anyRequest().authenticated())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
