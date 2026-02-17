package com.czcode.userservice.user.controller;

import com.czcode.userservice.user.dto.CreateUserProfileRequest;
import com.czcode.userservice.user.dto.UpdateUserProfileRequest;
import com.czcode.userservice.user.dto.UserProfileResponse;
import com.czcode.userservice.user.service.UserProfileService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserProfileController {

  private final UserProfileService userProfileService;

  public UserProfileController(UserProfileService userProfileService) {
    this.userProfileService = userProfileService;
  }

  @PostMapping
  public ResponseEntity<UserProfileResponse> create(@Valid @RequestBody CreateUserProfileRequest request) {
    UserProfileResponse created = userProfileService.create(request);
    return ResponseEntity
        .created(URI.create("/api/v1/users/" + created.id()))
        .body(created);
  }

  @GetMapping("/{userId}")
  public UserProfileResponse get(@PathVariable UUID userId) {
    return userProfileService.get(userId);
  }

  @GetMapping
  public List<UserProfileResponse> list(@RequestParam(required = false) Short status) {
    return userProfileService.list(status);
  }

  @PatchMapping("/{userId}")
  public UserProfileResponse update(
      @PathVariable UUID userId,
      @Valid @RequestBody UpdateUserProfileRequest request) {
    return userProfileService.update(userId, request);
  }

  @DeleteMapping("/{userId}")
  public ResponseEntity<Void> delete(@PathVariable UUID userId) {
    userProfileService.delete(userId);
    return ResponseEntity.noContent().build();
  }
}
