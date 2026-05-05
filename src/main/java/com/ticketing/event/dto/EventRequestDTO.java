package com.ticketing.event.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EventRequestDTO {
    private String name;
    private String location;
    private LocalDateTime eventTime; // ✅ same as entity
}