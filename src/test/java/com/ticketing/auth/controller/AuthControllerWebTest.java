package com.ticketing.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.auth.dto.LoginRequest;
import com.ticketing.auth.dto.LoginResponse;
import com.ticketing.auth.dto.RefreshRequest;
import com.ticketing.auth.dto.RegisterRequest;
import com.ticketing.auth.security.JwtUtil;
import com.ticketing.auth.service.UserDetailsServiceImpl;
import com.ticketing.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    @Test
    void register_shouldReturnSuccessMessage() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@example.com");
        request.setPassword("secret123");
        when(authService.register(any(RegisterRequest.class))).thenReturn("User registered successfully");

        // Act + Assert
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("User registered successfully"));

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void login_shouldReturnTokens() throws Exception {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("secret123");
        LoginResponse response = new LoginResponse("access-token", "refresh-token");
        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        // Act + Assert
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));

        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    void refresh_shouldReturnNewAccessToken() throws Exception {
        // Arrange
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("refresh-token");
        LoginResponse response = new LoginResponse("new-access-token", "refresh-token");
        when(authService.refresh(any(RefreshRequest.class))).thenReturn(response);

        // Act + Assert
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));

        verify(authService).refresh(any(RefreshRequest.class));
    }

    @Test
    void logout_shouldReturnSuccessMessage() throws Exception {
        // Arrange
        String authHeader = "Bearer valid-token";
        when(authService.logout(authHeader)).thenReturn("Logged out successfully");

        // Act + Assert
        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(content().string("Logged out successfully"));

        verify(authService).logout(authHeader);
    }
}
