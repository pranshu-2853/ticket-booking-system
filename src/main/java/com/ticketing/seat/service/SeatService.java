package com.ticketing.seat.service;

import com.ticketing.event.entity.Event;
import com.ticketing.event.service.EventService;
import com.ticketing.seat.entity.Seat;
import com.ticketing.seat.entity.SeatStatus;
import com.ticketing.seat.repository.SeatRepository;
import com.ticketing.shared.exception.ResourceNotFoundException;
import com.ticketing.shared.exception.SeatAlreadyExistsException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SeatService {

    private final SeatRepository seatRepository;
    private final EventService eventService;

    public SeatService(
            SeatRepository seatRepository,
            EventService eventService) {

        this.seatRepository = seatRepository;
        this.eventService = eventService;
    }

    public Seat addSeat(Long eventId, String seatNumber) {

        Event event = eventService.getEventEntityById(eventId);

        boolean exists =
                seatRepository.existsByEventIdAndSeatNumber(
                        eventId,
                        seatNumber
                );

        if (exists) {
            throw new SeatAlreadyExistsException(
                    "Seat already exists for this event"
            );
        }

        Seat seat = new Seat();

        seat.setSeatNumber(seatNumber);
        seat.setStatus(SeatStatus.AVAILABLE);
        seat.setEvent(event);

        return seatRepository.save(seat);
    }

    public List<Seat> getSeatsByEvent(Long eventId) {

        eventService.getEventEntityById(eventId);

        return seatRepository.findByEventId(eventId);
    }

    public Seat getSeatWithLock(Long seatId) {

        return seatRepository.findByIdWithLock(seatId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Seat not found with id: " + seatId
                        ));
    }

    public Seat getSeatById(Long seatId) {

        return seatRepository.findById(seatId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Seat not found with id: " + seatId
                        ));
    }



}