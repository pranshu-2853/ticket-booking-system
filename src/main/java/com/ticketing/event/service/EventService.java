package com.ticketing.event.service;

import com.ticketing.event.dto.EventRequestDTO;
import com.ticketing.event.dto.EventResponseDTO;
import com.ticketing.event.entity.Event;
import com.ticketing.event.repository.EventRepository;
import com.ticketing.shared.exception.ResourceNotFoundException;
import com.ticketing.shared.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public EventResponseDTO createEvent(EventRequestDTO request) {

        // 1. Basic validation
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BadRequestException("Event name cannot be empty");
        }

        if (request.getLocation() == null || request.getLocation().isBlank()) {
            throw new BadRequestException("Location cannot be empty");
        }

        if (request.getEventTime() == null) {
            throw new BadRequestException("Date cannot be null");
        }

        // 2. Duplicate check
        boolean exists = eventRepository.existsByName(request.getName());
        if (exists) {
            throw new BadRequestException("Event with this name already exists");
        }

        // 3. DTO → Entity
        Event event = new Event();
        event.setName(request.getName());
        event.setLocation(request.getLocation());
        event.setEventTime(request.getEventTime());

        // 4. Save
        Event savedEvent = eventRepository.save(event);

        // 5. Entity → DTO
        return mapToResponse(savedEvent);
    }

    public EventResponseDTO getEventById(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        return mapToResponse(event);
    }

    public List<EventResponseDTO> getAllEvents() {
        return eventRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private EventResponseDTO mapToResponse(Event event) {
        EventResponseDTO dto = new EventResponseDTO();
        dto.setId(event.getId());
        dto.setName(event.getName());
        dto.setLocation(event.getLocation());
        dto.setEventTime(event.getEventTime());
        return dto;
    }
}