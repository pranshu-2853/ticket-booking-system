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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_shouldPersistUserAndReturnSuccessMessage_whenUserDoesNotExist() {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@example.com");
        request.setPassword("secret123");

        Role role = new Role("ROLE_USER");
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-secret");
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(role));

        // Act
        String result = authService.register(request);

        // Assert
        assertEquals("User registered successfully", result);
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(user -> {
            assertEquals("user@example.com", user.getEmail());
            assertEquals("encoded-secret", user.getPassword());
            assertSame(role, user.getRole());
            return true;
        }));
    }

    @Test
    void register_shouldThrowBadRequest_whenUserAlreadyExists() {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@example.com");
        request.setPassword("secret123");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(new User()));

        // Act
        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.register(request));

        // Assert
        assertEquals("User already exists", ex.getMessage());
        verify(passwordEncoder, never()).encode(any());
        verifyNoInteractions(roleRepository);
    }

    @Test
    void login_shouldReturnTokensAndPersistRefreshToken_whenCredentialsAreValid() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("secret123");

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword("encoded-secret");
        user.setRole(new Role("ROLE_USER"));
        try {
            java.lang.reflect.Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, 11L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user.getEmail(), user.getId())).thenReturn("access-token");

        // Act
        LoginResponse response = authService.login(request);

        // Assert
        assertEquals("access-token", response.getAccessToken());
        assertEquals(36, response.getRefreshToken().length());
        verify(authenticationManager).authenticate(new UsernamePasswordAuthenticationToken("user@example.com", "secret123"));
        verify(refreshTokenRepository).deleteByUserId(11L);
        verify(refreshTokenRepository).save(org.mockito.ArgumentMatchers.argThat(token -> {
            assertEquals(11L, token.getUserId());
            assertEquals(response.getRefreshToken(), token.getToken());
            assertEquals(LocalDateTime.now().getYear(), token.getExpiryDate().getYear());
            return true;
        }));
    }

    @Test
    void login_shouldThrowResourceNotFound_whenUserMissing() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("secret123");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

        // Act
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> authService.login(request));

        // Assert
        assertEquals("User not found", ex.getMessage());
        verify(authenticationManager).authenticate(new UsernamePasswordAuthenticationToken("user@example.com", "secret123"));
        verifyNoInteractions(jwtUtil, refreshTokenRepository);
    }

    @Test
    void refresh_shouldReturnNewAccessToken_whenRefreshTokenIsValid() {
        // Arrange
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("refresh-token");

        RefreshToken token = new RefreshToken();
        token.setUserId(22L);
        token.setToken("refresh-token");
        token.setExpiryDate(LocalDateTime.now().plusDays(1));

        User user = new User();
        user.setEmail("user@example.com");
        user.setPassword("encoded-secret");
        user.setRole(new Role("ROLE_USER"));
        try {
            java.lang.reflect.Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, 22L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(Optional.of(token));
        when(userRepository.findById(22L)).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user.getEmail(), user.getId())).thenReturn("new-access-token");

        // Act
        LoginResponse response = authService.refresh(request);

        // Assert
        assertEquals("new-access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        verify(jwtUtil).generateToken("user@example.com", 22L);
    }

    @Test
    void refresh_shouldThrowBadRequest_whenRefreshTokenIsInvalid() {
        // Arrange
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("invalid-token");

        when(refreshTokenRepository.findByToken("invalid-token")).thenReturn(Optional.empty());

        // Act
        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.refresh(request));

        // Assert
        assertEquals("Invalid refresh token", ex.getMessage());
        verifyNoInteractions(userRepository, jwtUtil);
    }

    @Test
    void refresh_shouldThrowBadRequest_whenRefreshTokenIsExpired() {
        // Arrange
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("expired-token");

        RefreshToken token = new RefreshToken();
        token.setUserId(22L);
        token.setToken("expired-token");
        token.setExpiryDate(LocalDateTime.now().minusMinutes(1));

        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

        // Act
        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.refresh(request));

        // Assert
        assertEquals("Refresh token expired", ex.getMessage());
        verifyNoInteractions(userRepository, jwtUtil);
    }

    @Test
    void logout_shouldDeleteRefreshTokenAndReturnSuccess_whenHeaderIsValid() {
        // Arrange
        String authHeader = "Bearer valid-token";
        User user = new User();
        user.setEmail("user@example.com");
        user.setPassword("encoded-secret");
        user.setRole(new Role("ROLE_USER"));
        try {
            java.lang.reflect.Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, 33L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(jwtUtil.extractUsername("valid-token")).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        // Act
        String result = authService.logout(authHeader);

        // Assert
        assertEquals("Logged out successfully", result);
        verify(jwtUtil).extractUsername("valid-token");
        verify(refreshTokenRepository).deleteByUserId(33L);
    }

    @Test
    void logout_shouldThrowBadRequest_whenAuthorizationHeaderIsInvalid() {
        // Arrange
        String authHeader = "Invalid header";

        // Act
        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.logout(authHeader));

        // Assert
        assertEquals("Invalid Authorization header", ex.getMessage());
        verifyNoInteractions(jwtUtil, userRepository, refreshTokenRepository);
    }
}
