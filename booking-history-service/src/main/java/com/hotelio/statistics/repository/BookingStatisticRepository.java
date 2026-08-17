package com.hotelio.statistics.repository;

import com.hotelio.statistics.entity.BookingStatistic;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingStatisticRepository extends JpaRepository<BookingStatistic, Long> { }
