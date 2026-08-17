package com.hotelio.statistics.service;

import com.hotelio.statistics.dto.BookingEvent;
import com.hotelio.statistics.entity.BookingStatistic;
import com.hotelio.statistics.repository.BookingStatisticRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StatisticsUpdateService {

    private static final Logger log = LoggerFactory.getLogger(StatisticsUpdateService.class);
    private final BookingStatisticRepository repository;

    public StatisticsUpdateService(BookingStatisticRepository repository) {
        this.repository = repository;
    }

    /**
     * Агрегированная статистика по всем бронированиям.
     * Поля BookingStatistic:
     * - totalBookings: общее количество бронирований
     * - totalRevenue: общая выручка (сумма всех финальных цен)
     * - totalDiscount: общая сумма скидок, применённых ко всем бронированиям
     */
    @Transactional
    public void updateStatistics(BookingEvent event) {
        BookingStatistic stats = repository.findById(1L)
                .orElseGet(() -> {
                    BookingStatistic newStats = new BookingStatistic();
                    newStats.setId(1L);
                    newStats.setTotalBookings(0L);
                    newStats.setTotalRevenue(0.0);
                    newStats.setTotalDiscount(0.0);
                    return newStats;
                });

        double discountAmount = event.getPrice() * (event.getDiscountPercent() / 100.0);
        stats.applyBooking(event.getPrice(), discountAmount);

        repository.save(stats);
        log.info("Updated statistics: totalBookings={}, totalRevenue={}, totalDiscount={}",
                stats.getTotalBookings(), stats.getTotalRevenue(), stats.getTotalDiscount());
    }
}