package com.hotelio.bookingservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingEvent {
    private String id;
    private String userId;
    private String hotelId;
    private String promoCode;
    private double discountPercent;
    private double price;
    private Instant createdAt;
}