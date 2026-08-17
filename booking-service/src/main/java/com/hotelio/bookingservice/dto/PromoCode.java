package com.hotelio.bookingservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromoCode {
    private String code;
    private double discount;
    private boolean vipOnly;
    private boolean expired;
    private LocalDate validUntil;
    private String description;
}
