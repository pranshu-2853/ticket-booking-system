package com.ticketing.seat.service;

import com.ticketing.event.entity.Event;
import com.ticketing.event.service.EventService;
import com.ticketing.seat.entity.Seat;
import com.ticketing.seat.entity.SeatStatus;
import com.ticketing.seat.repository.SeatRepository;
import com.ticketing.shared.exception.ResourceNotFoundException;
import com.ticketing.shared.exception.SeatAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class SeatServiceTest {

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private EventService eventService;

    @InjectMocks
    private SeatService seatService;

    @Test
    void addSeat_shouldPersistAvailableSeat_whenSeatDoesNotExist() {
        // Arrange
        Long eventId = 10L;
        String seatNumber = "A1";
        Event event = new Event();
        Seat savedSeat = new Seat();
        savedSeat.setSeatNumber(seatNumber);
        savedSeat.setStatus(SeatStatus.AVAILABLE);
        savedSeat.setEvent(event);

        when(eventService.getEventEntityById(eventId)).thenReturn(event);
        when(seatRepository.existsByEventIdAndSeatNumber(eventId, seatNumber)).thenReturn(false);
        when(seatRepository.save(any(Seat.class))).thenReturn(savedSeat);

        // Act
        Seat result = seatService.addSeat(eventId, seatNumber);

        // Assert
        assertSame(savedSeat, result);
        ArgumentCaptor<Seat> seatCaptor = ArgumentCaptor.forClass(Seat.class);
        verify(seatRepository).save(seatCaptor.capture());
        assertEquals(seatNumber, seatCaptor.getValue().getSeatNumber());
        assertEquals(SeatStatus.AVAILABLE, seatCaptor.getValue().getStatus());
        assertSame(event, seatCaptor.getValue().getEvent());
    }

    @Test
    void addSeat_shouldThrowSeatAlreadyExists_whenSeatNumberAlreadyExists() {
        // Arrange
        Long eventId = 10L;
        String seatNumber = "A1";
        Event event = new Event();

        when(eventService.getEventEntityById(eventId)).thenReturn(event);
        when(seatRepository.existsByEventIdAndSeatNumber(eventId, seatNumber)).thenReturn(true);

        // Act
        SeatAlreadyExistsException ex = assertThrows(
                SeatAlreadyExistsException.class,
                () -> seatService.addSeat(eventId, seatNumber)
        );

        // Assert
        assertEquals("Seat already exists for this event", ex.getMessage());
        verify(seatRepository, never()).save(any());
    }

    @Test
    void getSeatsByEvent_shouldReturnSeats_whenEventExists() {
        // Arrange
        Long eventId = 10L;
        Event event = new Event();
        Seat seat1 = new Seat();
        Seat seat2 = new Seat();
        List<Seat> seats = List.of(seat1, seat2);

        when(eventService.getEventEntityById(eventId)).thenReturn(event);
        when(seatRepository.findByEventId(eventId)).thenReturn(seats);

        // Act
        List<Seat> result = seatService.getSeatsByEvent(eventId);

        // Assert
        assertSame(seats, result);
        verify(eventService).getEventEntityById(eventId);
        verify(seatRepository).findByEventId(eventId);
    }

    @Test
    void getSeatWithLock_shouldReturnSeat_whenSeatExists() {
        // Arrange
        Long seatId = 20L;
        Seat seat = new Seat();
        when(seatRepository.findByIdWithLock(seatId)).thenReturn(Optional.of(seat));

        // Act
        Seat result = seatService.getSeatWithLock(seatId);

        // Assert
        assertSame(seat, result);
        verify(seatRepository).findByIdWithLock(seatId);
    }

    @Test
    void getSeatWithLock_shouldThrowResourceNotFound_whenSeatMissing() {
        // Arrange
        Long seatId = 20L;
        when(seatRepository.findByIdWithLock(seatId)).thenReturn(Optional.empty());

        // Act
        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> seatService.getSeatWithLock(seatId)
        );

        // Assert
        assertEquals("Seat not found with id: " + seatId, ex.getMessage());
        verify(seatRepository).findByIdWithLock(seatId);
    }

    @Test
    void getSeatById_shouldReturnSeat_whenSeatExists() {
        // Arrange
        Long seatId = 20L;
        Seat seat = new Seat();
        when(seatRepository.findById(seatId)).thenReturn(Optional.of(seat));

        // Act
        Seat result = seatService.getSeatById(seatId);

        // Assert
        assertSame(seat, result);
        verify(seatRepository).findById(seatId);
    }

    @Test
    void getSeatById_shouldThrowResourceNotFound_whenSeatMissing() {
        // Arrange
        Long seatId = 20L;
        when(seatRepository.findById(seatId)).thenReturn(Optional.empty());

        // Act
        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> seatService.getSeatById(seatId)
        );

        // Assert
        assertEquals("Seat not found with id: " + seatId, ex.getMessage());
        verify(seatRepository).findById(seatId);
    }
}
