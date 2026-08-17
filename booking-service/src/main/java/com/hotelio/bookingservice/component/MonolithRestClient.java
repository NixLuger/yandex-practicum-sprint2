package com.hotelio.bookingservice.component;

import com.hotelio.bookingservice.dto.PromoCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Optional;

@Component
public class MonolithRestClient {
    private final WebClient webClient;

    public MonolithRestClient(@Value("${monolith.api.url:http://localhost:8080}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public boolean isUserActive(String userId) {
        try {
            return Boolean.TRUE.equals(webClient.get()
                    .uri("/api/users/{userId}/active", userId)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block()); // синхронный вызов
        } catch (WebClientResponseException e) {
            throw new RuntimeException("isUserActive exception", e);
        }
    }

    public boolean isUserBlacklisted(String userId) {
        try {
            return Boolean.TRUE.equals(webClient.get()
                    .uri("/api/users/{userId}/blacklisted", userId)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block());
        } catch (WebClientResponseException e) {
            throw new RuntimeException("isUserBlacklisted exception", e);
        }
    }

    public boolean isHotelOperational(String hotelId) {
        try {
            return Boolean.TRUE.equals(webClient.get()
                    .uri("/api/hotels/{hotelId}/operational", hotelId)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block());
        } catch (WebClientResponseException e) {
            throw new RuntimeException("isHotelOperational exception", e);
        }
    }

    public boolean isTrustedHotel(String hotelId) {
        try {
            return Boolean.TRUE.equals(webClient.get()
                    .uri("/api/reviews/hotels/{hotelId}/trusted", hotelId)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block());
        } catch (WebClientResponseException e) {
            throw new RuntimeException("isTrustedHotel exception", e);
        }
    }

    public boolean isHotelFullyBooked(String hotelId) {
        try {
            return Boolean.TRUE.equals(webClient.get()
                    .uri("/api/hotels/{hotelId}/fully-booked", hotelId)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block());
        } catch (WebClientResponseException e) {
            throw new RuntimeException("isHotelFullyBooked exception", e);
        }
    }

    public Optional<String> getUserStatus(String userId) {
        try {
            String status = webClient.get()
                    .uri("/api/users/{userId}/status", userId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return Optional.ofNullable(status);
        } catch (WebClientResponseException e) {
            throw new RuntimeException("getUserStatus exception", e);
        }
    }

    public PromoCode validatePromoCode(String promoCode, String userId) {
        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/promos/validate")
                            .queryParam("code", promoCode)
                            .queryParam("userId", userId)
                            .build())
                    .retrieve()
                    .bodyToMono(PromoCode.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw new RuntimeException("validatePromoCode exception", e);
        }
    }
}