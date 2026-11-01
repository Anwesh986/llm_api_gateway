package com.gateway.api.controller;

import com.gateway.api.dto.AuthRequest;
import com.gateway.api.dto.LoginResponse;
import com.gateway.api.dto.RegisterResponse;
import com.gateway.api.entity.User;
import com.gateway.api.repository.UserRepository;
import com.gateway.api.security.JwtTokenProvider;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController 
@RequestMapping ("/api/v1/auth")
public class AuthController {
    
    private static final long ACCESS_TOKEN_SECONDS = 15 * 60;

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthController(
            UserRepository userRepository,
            JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
        @RequestBody AuthRequest request) {
            String email = request.email().trim().toLowerCase(Locale.ROOT);

            if(userRepository.existsByEmail(email)) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }

            User user = new User();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user.setRole("ROLE_USER");
            user.setTier("FREE");

            User savedUser = userRepository.save(user);

            return ResponseEntity.status(HttpStatus.CREATED).body(new RegisterResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getRole(),
                savedUser.getTier()
            ));
        }

        @PostMapping ("/login")
        public ResponseEntity<LoginResponse> login(
            @RequestBody AuthRequest request) {
                String email = request.email().trim().toLowerCase(Locale.ROOT);

                return userRepository.findByEmail(email)
                        .filter(user -> passwordEncoder.matches(
                            request.password(),
                            user.getPasswordHash()))
                        .map(user -> {
                            String token = jwtTokenProvider.generateToken(
                                user.getId(),
                                List.of(user.getRole()),
                                user.getTier());

                            return ResponseEntity.ok(new LoginResponse(
                                token,
                                "Bearer",
                                ACCESS_TOKEN_SECONDS));
                        })
                        .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
            }
}
