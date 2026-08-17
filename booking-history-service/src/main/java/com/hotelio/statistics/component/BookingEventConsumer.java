package com.hotelio.statistics.component;

import com.hotelio.statistics.dto.BookingEvent;
import com.hotelio.statistics.service.StatisticsUpdateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BookingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingEventConsumer.class);
    private final StatisticsUpdateService updateService;

    public BookingEventConsumer(StatisticsUpdateService updateService) {
        this.updateService = updateService;
    }

    @KafkaListener(
            topics = "${spring.kafka.topic.booking-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeBookingEvent(BookingEvent event) {
        log.info("Received booking event: id={}, userId={}", event.getId(), event.getUserId());
        try {
            updateService.updateStatistics(event);
        } catch (Exception e) {
            log.error("Failed to process booking event: {}", event.getId(), e);
        }
    }
}