package com.ticketing.booking.service;

import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    public boolean process() {

        return Math.random() > 0.3;
    }
}