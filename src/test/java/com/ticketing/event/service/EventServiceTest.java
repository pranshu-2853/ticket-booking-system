package com.ticketing.event.service;

import com.ticketing.event.dto.EventRequestDTO;
import com.ticketing.event.dto.EventResponseDTO;
import com.ticketing.event.entity.Event;
import com.ticketing.event.repository.EventRepository;
import com.ticketing.shared.exception.BadRequestException;
import com.ticketing.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    @Test
    void createEvent_shouldPersistAndReturnMappedResponse_whenRequestIsValid() {
        // Arrange
        EventRequestDTO request = new EventRequestDTO();
        request.setName("Concert Night");
        request.setLocation("Main Hall");
        request.setEventTime(LocalDateTime.of(2026, 6, 30, 19, 30));

        Event savedEvent = new Event();
        savedEvent.setId(101L);
        savedEvent.setName(request.getName());
        savedEvent.setLocation(request.getLocation());
        savedEvent.setEventTime(request.getEventTime());

        when(eventRepository.existsByName("Concert Night")).thenReturn(false);
        when(eventRepository.save(any(Event.class))).thenReturn(savedEvent);

        // Act
        EventResponseDTO response = eventService.createEvent(request);

        // Assert
        assertEquals(101L, response.getId());
        assertEquals("Concert Night", response.getName());
        assertEquals("Main Hall", response.getLocation());
        assertEquals(request.getEventTime(), response.getEventTime());
        verify(eventRepository).existsByName("Concert Night");
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    void createEvent_shouldThrowBadRequest_whenNameIsBlank() {
        // Arrange
        EventRequestDTO request = new EventRequestDTO();
        request.setName(" ");
        request.setLocation("Main Hall");
        request.setEventTime(LocalDateTime.now());

        // Act
        BadRequestException ex = assertThrows(BadRequestException.class, () -> eventService.createEvent(request));

        // Assert
        assertEquals("Event name cannot be empty", ex.getMessage());
        verifyNoInteractions(eventRepository);
    }

    @Test
    void createEvent_shouldThrowBadRequest_whenLocationIsBlank() {
        // Arrange
        EventRequestDTO request = new EventRequestDTO();
        request.setName("Concert Night");
        request.setLocation("  ");
        request.setEventTime(LocalDateTime.now());

        // Act
        BadRequestException ex = assertThrows(BadRequestException.class, () -> eventService.createEvent(request));

        // Assert
        assertEquals("Location cannot be empty", ex.getMessage());
        verifyNoInteractions(eventRepository);
    }

    @Test
    void createEvent_shouldThrowBadRequest_whenEventTimeIsNull() {
        // Arrange
        EventRequestDTO request = new EventRequestDTO();
        request.setName("Concert Night");
        request.setLocation("Main Hall");
        request.setEventTime(null);

        // Act
        BadRequestException ex = assertThrows(BadRequestException.class, () -> eventService.createEvent(request));

        // Assert
        assertEquals("Date cannot be null", ex.getMessage());
        verifyNoInteractions(eventRepository);
    }

    @Test
    void createEvent_shouldThrowBadRequest_whenEventNameAlreadyExists() {
        // Arrange
        EventRequestDTO request = new EventRequestDTO();
        request.setName("Concert Night");
        request.setLocation("Main Hall");
        request.setEventTime(LocalDateTime.now());

        when(eventRepository.existsByName("Concert Night")).thenReturn(true);

        // Act
        BadRequestException ex = assertThrows(BadRequestException.class, () -> eventService.createEvent(request));

        // Assert
        assertEquals("Event with this name already exists", ex.getMessage());
        verify(eventRepository).existsByName("Concert Night");
        verify(eventRepository, never()).save(any());
    }

    @Test
    void getEventById_shouldReturnMappedResponse_whenEventExists() {
        // Arrange
        Long eventId = 11L;
        Event event = buildEvent(eventId, "Concert Night", "Main Hall");
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        // Act
        EventResponseDTO response = eventService.getEventById(eventId);

        // Assert
        assertEquals(eventId, response.getId());
        assertEquals("Concert Night", response.getName());
        assertEquals("Main Hall", response.getLocation());
        verify(eventRepository).findById(eventId);
    }

    @Test
    void getEventById_shouldThrowResourceNotFound_whenEventMissing() {
        // Arrange
        Long eventId = 11L;
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        // Act
        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> eventService.getEventById(eventId)
        );

        // Assert
        assertEquals("Event not found", ex.getMessage());
        verify(eventRepository).findById(eventId);
    }

    @Test
    void getAllEvents_shouldReturnMappedResponses() {
        // Arrange
        Event eventOne = buildEvent(1L, "Concert One", "Hall A");
        Event eventTwo = buildEvent(2L, "Concert Two", "Hall B");
        when(eventRepository.findAll()).thenReturn(List.of(eventOne, eventTwo));

        // Act
        List<EventResponseDTO> responses = eventService.getAllEvents();

        // Assert
        assertEquals(2, responses.size());
        assertEquals("Concert One", responses.get(0).getName());
        assertEquals("Concert Two", responses.get(1).getName());
        verify(eventRepository).findAll();
    }

    @Test
    void getEventEntityById_shouldReturnEvent_whenEventExists() {
        // Arrange
        Long eventId = 11L;
        Event event = buildEvent(eventId, "Concert Night", "Main Hall");
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        // Act
        Event result = eventService.getEventEntityById(eventId);

        // Assert
        assertSame(event, result);
        verify(eventRepository).findById(eventId);
    }

    @Test
    void getEventEntityById_shouldThrowResourceNotFound_whenEventMissing() {
        // Arrange
        Long eventId = 11L;
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        // Act
        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> eventService.getEventEntityById(eventId)
        );

        // Assert
        assertEquals("Event not found with id: " + eventId, ex.getMessage());
        verify(eventRepository).findById(eventId);
    }

    private Event buildEvent(Long id, String name, String location) {
        Event event = new Event();
        event.setId(id);
        event.setName(name);
        event.setLocation(location);
        event.setEventTime(LocalDateTime.of(2026, 6, 30, 19, 30));
        return event;
    }
}
