package com.ticketing.event.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EventResponseDTO {
    private Long id;
    private String name;
    private String location;
    private LocalDateTime eventTime;
}