package com.hotelio.bookingservice.service;

import com.hotelio.bookingservice.component.BookingEventProducer;
import com.hotelio.bookingservice.component.MonolithRestClient;
import com.hotelio.bookingservice.dto.BookingEvent;
import com.hotelio.bookingservice.dto.PromoCode;
import com.hotelio.bookingservice.entity.Booking;
import com.hotelio.bookingservice.repository.BookingRepository;
import com.hotelio.proto.booking.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GrpcBookingService extends BookingServiceGrpc.BookingServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(GrpcBookingService.class);
    private final BookingRepository bookingRepository;
    private final MonolithRestClient monolithClient;
    private final BookingEventProducer eventProducer;

    public GrpcBookingService(BookingRepository bookingRepository, MonolithRestClient monolithClient, BookingEventProducer eventProducer) {
        this.bookingRepository = bookingRepository;
        this.monolithClient = monolithClient;
        this.eventProducer = eventProducer;
    }

    @Override
    public void listBookings(BookingListRequest request, StreamObserver<BookingListResponse> responseObserver) {
        log.info("Received list bookings request: userId={}", request.getUserId());

        try {

            List<Booking> bookings = request.getUserId().isBlank() ? bookingRepository.findAll() : bookingRepository.findByUserId(request.getUserId());
            log.info("Found {} bookings for user {}", bookings.size(), request.getUserId());

            BookingListResponse.Builder responseBuilder = BookingListResponse.newBuilder();
            bookings.stream()
                    .map(this::toBookingResponse)
                    .forEach(responseBuilder::addBookings);

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Failed to list bookings", e);
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Failed to list bookings: " + e.getMessage())
                            .withCause(e)
                            .asRuntimeException()
            );
        }
    }

    @Override
    public void createBooking(BookingRequest request, StreamObserver<BookingResponse> responseObserver) {
        try {
            if (request.getUserId().isBlank() || request.getHotelId().isBlank()) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("promoCode, userId and hotelId are required")
                        .asRuntimeException());
                return;
            }

            log.info("Creating booking: userId={}, hotelId={}, promoCode={}", request.getUserId(), request.getHotelId(), request.getPromoCode());

            validateUser(request.getUserId());
            validateHotel(request.getHotelId());

            double basePrice = resolveBasePrice(request.getUserId());
            double discount = resolvePromoDiscount(request.getPromoCode(), request.getUserId());
            double finalPrice = basePrice - discount;

            log.info("Final price calculated: base={}, discount={}, final={}", basePrice, discount, finalPrice);

            Booking booking = Booking.builder()
                    .userId(request.getUserId())
                    .hotelId(request.getHotelId())
                    .promoCode(request.getPromoCode().isBlank() ? null : request.getPromoCode())
                    .discountPercent(discount)
                    .price(finalPrice)
                    .createdAt(Instant.now())
                    .build();

            bookingRepository.save(booking);
            log.info("Booking saved with id: {}", booking.getId());

            BookingEvent event = new BookingEvent(
                    booking.getId(),
                    booking.getUserId(),
                    booking.getHotelId(),
                    booking.getPromoCode(),
                    booking.getDiscountPercent(),
                    booking.getPrice(),
                    booking.getCreatedAt()
            );
            eventProducer.sendBookingEvent(event);

            BookingResponse response = toBookingResponse(booking);
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error creating booking", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal error: " + e.getMessage())
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    private void validateUser(String userId) {
        if (!monolithClient.isUserActive(userId)) {
            log.warn("User {} is inactive", userId);
            throw new IllegalArgumentException("User is inactive");
        }
        if (monolithClient.isUserBlacklisted(userId)) {
            log.warn("User {} is blacklisted", userId);
            throw new IllegalArgumentException("User is blacklisted");
        }
    }

    private void validateHotel(String hotelId) {
        if (!monolithClient.isHotelOperational(hotelId)) {
            log.warn("Hotel {} is not operational", hotelId);
            throw new IllegalArgumentException("Hotel is not operational");
        }
        if (!monolithClient.isTrustedHotel(hotelId)) {
            log.warn("Hotel {} is not trusted", hotelId);
            throw new IllegalArgumentException("Hotel is not trusted based on reviews");
        }
        if (monolithClient.isHotelFullyBooked(hotelId)) {
            log.warn("Hotel {} is fully booked", hotelId);
            throw new IllegalArgumentException("Hotel is fully booked");
        }
    }

    private double resolveBasePrice(String userId) {
        Optional<String> statusOpt = monolithClient.getUserStatus(userId);
        return statusOpt.map(status -> {
            boolean isVip = status.equalsIgnoreCase("VIP");
            log.debug("User {} has status '{}', base price is {}", userId, status, isVip ? 80.0 : 100.0);
            return isVip ? 80.0 : 100.0;
        }).orElseGet(() -> {
            log.debug("User {} has unknown status, default base price 100.0", userId);
            return 100.0;
        });
    }

    private double resolvePromoDiscount(String promoCode, String userId) {
        if (promoCode == null || promoCode.isBlank()) return 0.0;

        PromoCode promo = monolithClient.validatePromoCode(promoCode, userId);
        if (promo == null) {
            log.info("Promo code '{}' is invalid or not applicable for user {}", promoCode, userId);
            return 0.0;
        }

        log.debug("Promo code '{}' applied with discount {}", promoCode, promo.getDiscount());
        return promo.getDiscount();
    }

    private BookingResponse toBookingResponse(Booking booking) {
        return BookingResponse.newBuilder()
                .setId(booking.getId().toString())
                .setUserId(booking.getUserId())
                .setHotelId(booking.getHotelId())
                .setPromoCode(booking.getPromoCode() != null ? booking.getPromoCode() : "")
                .setDiscountPercent(booking.getDiscountPercent())
                .setPrice(booking.getPrice())
                .setCreatedAt(DateTimeFormatter.ISO_INSTANT.format(booking.getCreatedAt()))
                .build();
    }
}
