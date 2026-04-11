package com.ticketing.auth.controller;

import com.ticketing.auth.dto.LoginRequest;
import com.ticketing.auth.dto.LoginResponse;
import com.ticketing.auth.dto.RegisterRequest;
import com.ticketing.auth.entity.Role;
import com.ticketing.auth.entity.User;
import com.ticketing.auth.repository.RoleRepository;
import com.ticketing.auth.repository.UserRepository;
import com.ticketing.auth.security.JwtUtil;

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

    public AuthController(AuthenticationManager authenticationManager,
                          JwtUtil jwtUtil,
                          UserRepository userRepository,
                          RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder) {

        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
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

    // ✅ LOGIN API
    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {

        // 1. Authenticate
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // 2. Generate JWT
        String token = jwtUtil.generateToken(request.getEmail());

        // 3. Return response
        return new LoginResponse(token);
    }
}