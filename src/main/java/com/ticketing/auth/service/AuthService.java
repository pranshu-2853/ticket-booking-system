package com.ticketing.auth.service;

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
import com.ticketing.shared.exception.BadRequestException;
import com.ticketing.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtUtil jwtUtil,
                       UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       RefreshTokenRepository refreshTokenRepository) {

        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Register
    public String register(RegisterRequest request) {

        // 1. Check if user already exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BadRequestException("User already exists");
        }

        // 2. Encode password
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // 3. Get role
        Role role = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));

        // 4. Create user
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(encodedPassword);
        user.setRole(role);

        userRepository.save(user);

        return "User registered successfully";
    }


    // LOGIN
    @Transactional
    public LoginResponse login(LoginRequest request) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String accessToken = jwtUtil.generateToken(user.getEmail(), user.getId());
        String refreshToken = UUID.randomUUID().toString();

        refreshTokenRepository.deleteByUserId(user.getId());

        RefreshToken tokenEntity = new RefreshToken();
        tokenEntity.setUserId(user.getId());
        tokenEntity.setToken(refreshToken);
        tokenEntity.setExpiryDate(LocalDateTime.now().plusDays(7));

        refreshTokenRepository.save(tokenEntity);

        return new LoginResponse(accessToken, refreshToken);
    }

    // REFRESH
    public LoginResponse refresh(@Valid  RefreshRequest request) {

        String refreshToken = request.getRefreshToken();

        RefreshToken token = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Refresh token expired");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String newAccessToken = jwtUtil.generateToken(user.getEmail(), user.getId());

        return new LoginResponse(newAccessToken, refreshToken);
    }

    // LOGOUT
    @Transactional
    public String logout(String authHeader) {

        String token = authHeader.substring(7);
        String email = jwtUtil.extractUsername(token);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        refreshTokenRepository.deleteByUserId(user.getId());

        return "Logged out successfully";
    }
}