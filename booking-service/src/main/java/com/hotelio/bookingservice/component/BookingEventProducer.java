package com.hotelio.bookingservice.component;

import com.hotelio.bookingservice.dto.BookingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class BookingEventProducer {
    private static final Logger log = LoggerFactory.getLogger(BookingEventProducer.class);
    private final KafkaTemplate<String, BookingEvent> kafkaTemplate;
    private final String topic;

    public BookingEventProducer(KafkaTemplate<String, BookingEvent> kafkaTemplate,
                                @Value("${kafka.topic.booking-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void sendBookingEvent(BookingEvent event) {
        CompletableFuture<SendResult<String, BookingEvent>> future = kafkaTemplate.send(topic, event.getUserId(), event);
        future.whenComplete((result, ex) -> {
            if (ex == null)
                log.info("Booking event sent successfully: {} to topic {} partition {}",
                        event.getId(), result.getRecordMetadata().topic(), result.getRecordMetadata().partition());
            else
                log.error("Failed to send booking event: {}", event.getId(), ex);
        });
    }
}