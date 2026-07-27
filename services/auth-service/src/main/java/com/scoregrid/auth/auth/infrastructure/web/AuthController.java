package com.scoregrid.auth.auth.infrastructure.web;

import com.scoregrid.auth.auth.infrastructure.persistence.RoleRepository;
import com.scoregrid.auth.auth.infrastructure.persistence.UserEntity;
import com.scoregrid.auth.auth.infrastructure.persistence.UserRepository;
import com.scoregrid.auth.shared.security.CurrentUser;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final CurrentUser currentUser;

    AuthController(UserRepository userRepository,
                   RoleRepository roleRepository,
                   PasswordEncoder passwordEncoder,
                   JwtEncoder jwtEncoder,
                   CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.currentUser = currentUser;
    }

    @PostMapping("/register")
    @Transactional
    ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByUsernameOrEmail(request.username(), request.email())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        UserEntity user = new UserEntity();
        injectValues(user, request.username(), request.email(), request.password());
        user = userRepository.save(user);

        var playerRole = roleRepository.findByName("PLAYER");
        if (playerRole.isPresent()) {
            user.getRoles().add(playerRole.get());
            user = userRepository.save(user);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(buildAuthResponse(user));
    }

    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        var user = userRepository.findByUsernameOrEmail(request.usernameOrEmail(), request.usernameOrEmail())
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(buildAuthResponse(user));
    }

    @GetMapping("/me")
    ResponseEntity<AuthResponse.UserProfile> me() {
        String userId = currentUser.requireId();
        var user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<String> roles = user.getRoles().stream()
                .map(r -> r.getName())
                .toList();

        return ResponseEntity.ok(new AuthResponse.UserProfile(
                user.getId().toString(), user.getUsername(), user.getEmail(), roles));
    }

    private AuthResponse buildAuthResponse(UserEntity user) {
        List<String> plainRoles = user.getRoles().stream()
                .map(r -> r.getName())
                .toList();

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(86400);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("scoregrid-auth")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("roles", plainRoles)
                .build();

        JwsHeader header = JwsHeader.with(() -> "HS256").build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new AuthResponse(token, expiresAt,
                new AuthResponse.UserProfile(user.getId().toString(), user.getUsername(), user.getEmail(), plainRoles));
    }

    private void injectValues(UserEntity user, String username, String email, String rawPassword) {
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
    }
}
