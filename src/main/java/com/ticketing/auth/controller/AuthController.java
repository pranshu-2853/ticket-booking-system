package com.ticketing.auth.controller;

import com.ticketing.auth.dto.LoginRequest;
import com.ticketing.auth.dto.LoginResponse;
import com.ticketing.auth.dto.RefreshRequest;
import com.ticketing.auth.dto.RegisterRequest;
import com.ticketing.auth.entity.RefreshToken;
import com.ticketing.auth.entity.Role;
import com.ticketing.auth.entity.User;
import com.ticketing.auth.repository.RefreshTokenRepository;
import com.ticketing.auth.repository.RoleRepository;
import com.ticketing.auth.repository.UserRepository;
import com.ticketing.auth.security.JwtUtil;

import jakarta.transaction.Transactional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtUtil jwtUtil,
                          UserRepository userRepository,
                          RoleRepository roleRepository,
                          RefreshTokenRepository refreshTokenRepository,
                          PasswordEncoder passwordEncoder) {

        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    // ✅ REGISTER API
    @PostMapping("/register")
    public String register(@RequestBody RegisterRequest request) {
        // 1. Encode password
        String encodedPassword = passwordEncoder.encode(request.getPassword());


        // 2. Get ROLE_USER
        Role role = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("Role not found"));

        // 3. Create user
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(encodedPassword);
        user.setRole(role);


        userRepository.save(user);

        return "User registered successfully";
    }

    //  LOGIN API
    @Transactional
    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {

        // 1. Authenticate
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // 2. Generate access token
        String accessToken = jwtUtil.generateToken(request.getEmail());

        // 3. Generate refresh token
        String refreshToken = java.util.UUID.randomUUID().toString();

        // 4. Get user from DB
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow();

        // 5. Delete old refresh token (important)
        refreshTokenRepository.deleteByUserId(user.getId());

        // 6. Save new refresh token
        RefreshToken tokenEntity = new RefreshToken();
        tokenEntity.setUserId(user.getId());
        tokenEntity.setToken(refreshToken);
        tokenEntity.setExpiryDate(
                java.time.LocalDateTime.now().plusDays(7)
        );

        refreshTokenRepository.save(tokenEntity);

        // 7. Return both tokens
        return new LoginResponse(accessToken, refreshToken);
    }

    // refresh token api
    @PostMapping("/refresh")
    public LoginResponse refresh(@RequestBody RefreshRequest request) {

        // 1. Get token from request
        String refreshToken = request.getRefreshToken();

        // 2. Find token in DB
        RefreshToken token = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        // 3. Check expiry
        if (token.getExpiryDate().isBefore(java.time.LocalDateTime.now())) {
            throw new RuntimeException("Refresh token expired");
        }

        // 4. Get user
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 5. Generate new access token
        String newAccessToken = jwtUtil.generateToken(user.getEmail());

        // 6. Return new access token + SAME refresh token
        return new LoginResponse(newAccessToken, refreshToken);
    }
    @Transactional
    @PostMapping("/logout")
    public String logout(@RequestHeader("Authorization") String authHeader) {

        // 1. Extract token
        String token = authHeader.substring(7); // remove "Bearer "

        // 2. Extract email
        String email = jwtUtil.extractUsername(token);

        // 3. Get user
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 4. Delete refresh token
        refreshTokenRepository.deleteByUserId(user.getId());

        return "Logged out successfully";
    }
}