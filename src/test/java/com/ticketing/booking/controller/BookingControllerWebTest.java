package com.ticketing.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.auth.security.JwtUtil;
import com.ticketing.auth.service.UserDetailsServiceImpl;
import com.ticketing.booking.dto.BookingRequest;
import com.ticketing.booking.dto.BookingResponse;
import com.ticketing.booking.entity.Booking;
import com.ticketing.booking.service.BookingService;
import com.ticketing.booking.service.IdempotencyService;
import com.ticketing.auth.entity.User;
import com.ticketing.seat.entity.Seat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
@AutoConfigureMockMvc(addFilters = false)
class BookingControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingService bookingService;

    @MockBean
    private IdempotencyService idempotencyService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    @Test
    void createBooking_shouldReturnCreatedResponse_whenNoCachedResponseExists() throws Exception {
        // Arrange
        BookingRequest request = new BookingRequest();
        request.setSeatId(11L);

        Booking booking = org.mockito.Mockito.mock(Booking.class);
        User user = org.mockito.Mockito.mock(User.class);
        Seat seat = org.mockito.Mockito.mock(Seat.class);
        LocalDateTime bookedAt = LocalDateTime.of(2026, 6, 24, 12, 0);

        when(jwtUtil.extractUserId("token")).thenReturn(42L);
        when(idempotencyService.getCachedResponse("idem-1")).thenReturn(Optional.empty());
        when(bookingService.createBooking(42L, 11L)).thenReturn(booking);
        when(booking.getId()).thenReturn(100L);
        when(booking.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(42L);
        when(booking.getSeat()).thenReturn(seat);
        when(seat.getId()).thenReturn(11L);
        when(booking.getBookedAt()).thenReturn(bookedAt);

        // Act + Assert
        mockMvc.perform(post("/bookings")
                        .header("Authorization", "Bearer token")
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").value(100))
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.seatId").value(11))
                .andExpect(jsonPath("$.bookedAt").value("2026-06-24T12:00:00"));

        verify(jwtUtil).extractUserId("token");
        verify(idempotencyService).getCachedResponse("idem-1");
        verify(bookingService).createBooking(42L, 11L);
        verify(idempotencyService).saveResponse(eq("idem-1"), any(BookingResponse.class));
    }

    @Test
    void createBooking_shouldReturnCachedResponse_whenIdempotencyKeyHit() throws Exception {
        // Arrange
        BookingRequest request = new BookingRequest();
        request.setSeatId(11L);
        BookingResponse cached = new BookingResponse(100L, 42L, 11L, LocalDateTime.of(2026, 6, 24, 12, 0));

        when(jwtUtil.extractUserId("token")).thenReturn(42L);
        when(idempotencyService.getCachedResponse("idem-1")).thenReturn(Optional.of(cached));

        // Act + Assert
        mockMvc.perform(post("/bookings")
                        .header("Authorization", "Bearer token")
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(100))
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.seatId").value(11))
                .andExpect(jsonPath("$.bookedAt").value("2026-06-24T12:00:00"));

        verify(jwtUtil).extractUserId("token");
        verify(idempotencyService).getCachedResponse("idem-1");
        verify(bookingService, never()).createBooking(any(), any());
    }

    @Test
    void createBooking_shouldReturnBadRequest_whenAuthorizationHeaderIsInvalid() throws Exception {
        // Arrange
        BookingRequest request = new BookingRequest();
        request.setSeatId(11L);

        // Act + Assert
        mockMvc.perform(post("/bookings")
                        .header("Authorization", "Invalid token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid Authorization header"));

        verify(bookingService, never()).createBooking(any(), any());
    }

    @Test
    void getBookingById_shouldReturnBookingResponse() throws Exception {
        // Arrange
        Booking booking = org.mockito.Mockito.mock(Booking.class);
        User user = org.mockito.Mockito.mock(User.class);
        Seat seat = org.mockito.Mockito.mock(Seat.class);
        LocalDateTime bookedAt = LocalDateTime.of(2026, 6, 24, 12, 0);

        when(bookingService.getBookingById(77L)).thenReturn(booking);
        when(booking.getId()).thenReturn(77L);
        when(booking.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(42L);
        when(booking.getSeat()).thenReturn(seat);
        when(seat.getId()).thenReturn(11L);
        when(booking.getBookedAt()).thenReturn(bookedAt);

        // Act + Assert
        mockMvc.perform(get("/bookings/77"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(77))
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.seatId").value(11))
            .andExpect(jsonPath("$.bookedAt").value("2026-06-24T12:00:00"));

        verify(bookingService).getBookingById(77L);
    }
}
