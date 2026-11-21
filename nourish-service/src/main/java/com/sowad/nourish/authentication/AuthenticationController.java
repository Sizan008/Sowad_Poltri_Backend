package com.sowad.nourish.authentication;

import com.sowad.nourish.authentication.command.Login;
import com.sowad.nourish.authentication.query.LoginResult;
import com.sowad.nourish.common.security.*;

import jakarta.validation.Valid;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthenticationController {
    private final AuthenticationService service;
    private final CurrentUserProvider users;

    public AuthenticationController(AuthenticationService service, CurrentUserProvider users) {
        this.service = service;
        this.users = users;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResult> login(@Valid @RequestBody Login command) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.process(command));
    }

    @GetMapping("/me")
    public CurrentUser currentUser() {
        return users.getCurrentUser();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        service.logout();
        return ResponseEntity.noContent().build();
    }
}
