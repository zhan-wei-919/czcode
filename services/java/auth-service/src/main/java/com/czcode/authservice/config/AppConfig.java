package com.czcode.authservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public RestClient userServiceRestClient(
      @Value("${services.user-service.base-url:http://localhost:18082}") String userServiceBaseUrl) {
    return RestClient.builder()
        .baseUrl(userServiceBaseUrl)
        .build();
  }
}
