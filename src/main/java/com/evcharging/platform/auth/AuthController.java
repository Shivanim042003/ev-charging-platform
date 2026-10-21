package com.evcharging.platform.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    // Compared against when the username is unknown, so login time does not reveal which usernames exist.
    private final String dummyHash;

    public AuthController(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    @PostMapping("/register")
    public ResponseEntity<Object> register(@Valid @RequestBody RegisterRequest request) {
        String username = request.username().toLowerCase(Locale.ROOT);
        if (users.existsByUsername(username)) {
            return problem(HttpStatus.CONFLICT, "Username already taken");
        }
        users.save(new AppUser(username, passwordEncoder.encode(request.password())));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("username", username));
    }

    @PostMapping("/login")
    public ResponseEntity<Object> login(@Valid @RequestBody LoginRequest request) {
        String username = request.username().toLowerCase(Locale.ROOT);
        AppUser user = users.findByUsername(username).orElse(null);
        boolean ok = passwordEncoder.matches(request.password(), user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !ok) {
            return problem(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        return ResponseEntity.ok(new TokenResponse(
                jwtService.issue(user), "Bearer", jwtService.ttlSeconds(), user.getUsername()));
    }

    private static ResponseEntity<Object> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}