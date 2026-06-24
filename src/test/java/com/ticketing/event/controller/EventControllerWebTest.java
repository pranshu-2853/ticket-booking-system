package com.ticketing.event.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.auth.security.JwtUtil;
import com.ticketing.auth.service.UserDetailsServiceImpl;
import com.ticketing.event.dto.EventRequestDTO;
import com.ticketing.event.dto.EventResponseDTO;
import com.ticketing.event.service.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
@AutoConfigureMockMvc(addFilters = false)
class EventControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EventService eventService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    @Test
    void createEvent_shouldReturnCreatedResponse() throws Exception {
        // Arrange
        EventRequestDTO request = new EventRequestDTO();
        request.setName("Concert Night");
        request.setLocation("Main Hall");
        request.setEventTime(LocalDateTime.of(2026, 6, 24, 12, 0));

        EventResponseDTO response = new EventResponseDTO();
        response.setId(100L);
        response.setName("Concert Night");
        response.setLocation("Main Hall");
        response.setEventTime(LocalDateTime.of(2026, 6, 24, 12, 0));

        when(eventService.createEvent(request)).thenReturn(response);

        // Act + Assert
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.name").value("Concert Night"))
                .andExpect(jsonPath("$.location").value("Main Hall"))
                .andExpect(jsonPath("$.eventTime").value("2026-06-24T12:00:00"));

        verify(eventService).createEvent(request);
    }

    @Test
    void getAllEvents_shouldReturnEventList() throws Exception {
        // Arrange
        EventResponseDTO responseOne = new EventResponseDTO();
        responseOne.setId(1L);
        responseOne.setName("Concert One");
        responseOne.setLocation("Hall A");
        responseOne.setEventTime(LocalDateTime.of(2026, 6, 24, 12, 0));

        EventResponseDTO responseTwo = new EventResponseDTO();
        responseTwo.setId(2L);
        responseTwo.setName("Concert Two");
        responseTwo.setLocation("Hall B");
        responseTwo.setEventTime(LocalDateTime.of(2026, 6, 25, 12, 0));

        when(eventService.getAllEvents()).thenReturn(List.of(responseOne, responseTwo));

        // Act + Assert
        mockMvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Concert One"))
                .andExpect(jsonPath("$[0].location").value("Hall A"))
                .andExpect(jsonPath("$[0].eventTime").value("2026-06-24T12:00:00"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].name").value("Concert Two"))
                .andExpect(jsonPath("$[1].location").value("Hall B"))
                .andExpect(jsonPath("$[1].eventTime").value("2026-06-25T12:00:00"));

        verify(eventService).getAllEvents();
    }

    @Test
    void getEventById_shouldReturnEventResponse() throws Exception {
        // Arrange
        EventResponseDTO response = new EventResponseDTO();
        response.setId(100L);
        response.setName("Concert Night");
        response.setLocation("Main Hall");
        response.setEventTime(LocalDateTime.of(2026, 6, 24, 12, 0));

        when(eventService.getEventById(100L)).thenReturn(response);

        // Act + Assert
        mockMvc.perform(get("/events/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.name").value("Concert Night"))
                .andExpect(jsonPath("$.location").value("Main Hall"))
                .andExpect(jsonPath("$.eventTime").value("2026-06-24T12:00:00"));

        verify(eventService).getEventById(100L);
    }
}
