package com.matrixlive.security.auth;

import static com.matrixlive.security.auth.AuthModels.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {
  private final IdentityAdminService identity;

  public UserAdminController(IdentityAdminService identity) { this.identity = identity; }

  @GetMapping
  public List<UserResponse> list() { return identity.listUsers(); }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse create(@Valid @RequestBody CreateUserRequest request) { return identity.createUser(request); }

  @PatchMapping("/{userId}")
  public UserResponse update(@PathVariable UUID userId, @Valid @RequestBody UpdateUserRequest request) {
    return identity.updateUser(userId, request);
  }

  @PatchMapping("/{userId}/enabled")
  public UserResponse setEnabled(@PathVariable UUID userId, @Valid @RequestBody UpdateUserStatusRequest request) {
    return identity.setEnabled(userId, request.enabled());
  }
}
