package com.czcode.authservice.auth.service;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UserServiceClient {

  private final RestClient userServiceRestClient;
  private final Retry retry;
  private final CircuitBreaker circuitBreaker;

  public UserServiceClient(
      RestClient userServiceRestClient,
      @Value("${services.user-service.resilience.retry.max-attempts:3}") int retryMaxAttempts,
      @Value("${services.user-service.resilience.retry.wait-duration-ms:200}") long retryWaitDurationMs,
      @Value("${services.user-service.resilience.circuit-breaker.sliding-window-size:20}") int cbSlidingWindowSize,
      @Value("${services.user-service.resilience.circuit-breaker.minimum-number-of-calls:10}") int cbMinimumCalls,
      @Value("${services.user-service.resilience.circuit-breaker.failure-rate-threshold:50}") double cbFailureRateThreshold,
      @Value("${services.user-service.resilience.circuit-breaker.wait-duration-open-ms:10000}") long cbOpenWaitDurationMs,
      @Value("${services.user-service.resilience.circuit-breaker.permitted-calls-in-half-open-state:3}") int cbHalfOpenPermittedCalls) {
    this.userServiceRestClient = userServiceRestClient;

    RetryConfig retryConfig = RetryConfig.custom()
        .maxAttempts(Math.max(1, retryMaxAttempts))
        .waitDuration(Duration.ofMillis(Math.max(1L, retryWaitDurationMs)))
        .retryExceptions(RestClientException.class, ResponseStatusException.class)
        .failAfterMaxAttempts(true)
        .build();
    this.retry = Retry.of("user-service-create-profile", retryConfig);

    CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
        .slidingWindowSize(Math.max(2, cbSlidingWindowSize))
        .minimumNumberOfCalls(Math.max(1, Math.min(cbMinimumCalls, cbSlidingWindowSize)))
        .failureRateThreshold((float) Math.max(1d, Math.min(100d, cbFailureRateThreshold)))
        .waitDurationInOpenState(Duration.ofMillis(Math.max(100L, cbOpenWaitDurationMs)))
        .permittedNumberOfCallsInHalfOpenState(Math.max(1, cbHalfOpenPermittedCalls))
        .recordExceptions(RestClientException.class, ResponseStatusException.class)
        .build();
    this.circuitBreaker = CircuitBreaker.of("user-service-create-profile", circuitBreakerConfig);
  }

  public UUID createDefaultUserProfile(String email, String nickname) {
    String finalNickname = (nickname == null || nickname.isBlank())
        ? email.substring(0, email.indexOf('@'))
        : nickname;

    CreateUserProfileRequest request = new CreateUserProfileRequest(
        finalNickname,
        null,
        null,
        "UTC",
        "zh-CN",
        (short) 1);

    try {
      Supplier<UUID> protectedCall = Retry.decorateSupplier(
          retry,
          CircuitBreaker.decorateSupplier(circuitBreaker, () -> createProfile(request)));
      return protectedCall.get();
    } catch (CallNotPermittedException ex) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "user service circuit is open");
    } catch (RestClientException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "user service call failed");
    }
  }

  private UUID createProfile(CreateUserProfileRequest request) {
    UserProfileResponse response = userServiceRestClient.post()
        .uri("/api/v1/users")
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .retrieve()
        .body(UserProfileResponse.class);

    if (response == null || response.id() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "user service returned empty response");
    }
    return response.id();
  }

  private record CreateUserProfileRequest(
      String nickname,
      String avatarUrl,
      String bio,
      String timezone,
      String locale,
      short status
  ) {
  }

  private record UserProfileResponse(
      UUID id
  ) {
  }
}
