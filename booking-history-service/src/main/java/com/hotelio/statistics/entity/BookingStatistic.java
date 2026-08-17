package com.hotelio.statistics.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "booking_statistics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BookingStatistic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long totalBookings = 0L;

    @Column(nullable = false)
    private Double totalRevenue = 0.0;

    @Column(nullable = false)
    private Double totalDiscount = 0.0;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public void applyBooking(double price, double discountAmount) {
        this.totalBookings++;
        this.totalRevenue += price;
        this.totalDiscount += discountAmount;
        this.updatedAt = Instant.now();
    }
}