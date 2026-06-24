package com.ticketing.seat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.auth.security.JwtUtil;
import com.ticketing.auth.service.UserDetailsServiceImpl;
import com.ticketing.seat.dto.SeatHoldRequest;
import com.ticketing.seat.dto.SeatRequest;
import com.ticketing.seat.entity.Seat;
import com.ticketing.seat.entity.SeatStatus;
import com.ticketing.seat.service.SeatHoldService;
import com.ticketing.seat.service.SeatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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

@WebMvcTest(SeatController.class)
@AutoConfigureMockMvc(addFilters = false)
class SeatControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SeatService seatService;

    @MockBean
    private SeatHoldService seatHoldService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    @Test
    void addSeat_shouldReturnCreatedSeatResponse() throws Exception {
        // Arrange
        SeatRequest request = new SeatRequest();
        request.setSeatNumber("A1");

        Seat seat = org.mockito.Mockito.mock(Seat.class);
        com.ticketing.event.entity.Event event = org.mockito.Mockito.mock(com.ticketing.event.entity.Event.class);
        when(seatService.addSeat(10L, "A1")).thenReturn(seat);
        when(seat.getId()).thenReturn(100L);
        when(seat.getSeatNumber()).thenReturn("A1");
        when(seat.getStatus()).thenReturn(SeatStatus.AVAILABLE);
        when(seat.getEvent()).thenReturn(event);
        when(event.getId()).thenReturn(10L);

        // Act + Assert
        mockMvc.perform(post("/events/10/seats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.eventId").value(10))
                .andExpect(jsonPath("$.seatNumber").value("A1"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        verify(seatService).addSeat(10L, "A1");
    }

    @Test
    void getSeatsByEvent_shouldReturnSeatResponses() throws Exception {
        // Arrange
        Seat seatOne = org.mockito.Mockito.mock(Seat.class);
        Seat seatTwo = org.mockito.Mockito.mock(Seat.class);
        com.ticketing.event.entity.Event event = org.mockito.Mockito.mock(com.ticketing.event.entity.Event.class);
        when(seatOne.getId()).thenReturn(1L);
        when(seatOne.getSeatNumber()).thenReturn("A1");
        when(seatOne.getStatus()).thenReturn(SeatStatus.AVAILABLE);
        when(seatOne.getEvent()).thenReturn(event);
        when(seatTwo.getId()).thenReturn(2L);
        when(seatTwo.getSeatNumber()).thenReturn("A2");
        when(seatTwo.getStatus()).thenReturn(SeatStatus.BOOKED);
        when(seatTwo.getEvent()).thenReturn(event);
        when(event.getId()).thenReturn(10L);
        when(seatService.getSeatsByEvent(10L)).thenReturn(List.of(seatOne, seatTwo));

        // Act + Assert
        mockMvc.perform(get("/events/10/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].eventId").value(10))
                .andExpect(jsonPath("$[0].seatNumber").value("A1"))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].eventId").value(10))
                .andExpect(jsonPath("$[1].seatNumber").value("A2"))
                .andExpect(jsonPath("$[1].status").value("BOOKED"));

        verify(seatService).getSeatsByEvent(10L);
    }

    @Test
    void holdSeat_shouldReturnSuccessWhenSeatIsHeld() throws Exception {
        // Arrange
        SeatHoldRequest request = new SeatHoldRequest();
        request.setEventId(10L);
        request.setSeatId(20L);
        when(jwtUtil.extractUserId("token")).thenReturn(42L);
        when(seatHoldService.tryHoldSeat(10L, 20L, 42L)).thenReturn(true);

        // Act + Assert
        mockMvc.perform(post("/events/hold")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Seat held successfully"));

        verify(jwtUtil).extractUserId("token");
        verify(seatHoldService).tryHoldSeat(10L, 20L, 42L);
    }

    @Test
    void holdSeat_shouldReturnBadRequestWhenSeatIsAlreadyHeld() throws Exception {
        // Arrange
        SeatHoldRequest request = new SeatHoldRequest();
        request.setEventId(10L);
        request.setSeatId(20L);
        when(jwtUtil.extractUserId("token")).thenReturn(42L);
        when(seatHoldService.tryHoldSeat(10L, 20L, 42L)).thenReturn(false);

        // Act + Assert
        mockMvc.perform(post("/events/hold")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Seat is already held"));

        verify(jwtUtil).extractUserId("token");
        verify(seatHoldService).tryHoldSeat(10L, 20L, 42L);
    }

    @Test
    void holdSeat_shouldReturnBadRequestWhenAuthorizationHeaderIsInvalid() throws Exception {
        // Arrange
        SeatHoldRequest request = new SeatHoldRequest();
        request.setEventId(10L);
        request.setSeatId(20L);

        // Act + Assert
        mockMvc.perform(post("/events/hold")
                        .header("Authorization", "Invalid token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid Authorization header"));

        verify(seatHoldService, never()).tryHoldSeat(any(), any(), any());
    }
}
