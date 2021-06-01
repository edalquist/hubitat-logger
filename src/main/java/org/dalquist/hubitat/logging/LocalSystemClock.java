package org.dalquist.hubitat.logging;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.LazyArgs;
import com.vlkan.rfos.Clock;

final class LocalSystemClock implements Clock {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private static final LocalSystemClock INSTANCE = new LocalSystemClock();

    LocalSystemClock() {
        // Do nothing.
    }

    public static LocalSystemClock getInstance() {
        return INSTANCE;
    }

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public Instant midnight() {
        ZonedDateTime nextMidnight = LocalDate.now().plusDays(1).atStartOfDay().atZone(ZoneId.systemDefault());
        logger.atInfo().log("Calculated next midnight: %s",
                LazyArgs.lazy(() -> DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(nextMidnight)));
        return nextMidnight.toInstant();
    }

    @Override
    public Instant sundayMidnight() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        int todayIndex = startOfDay.getDayOfWeek().getValue() - 1;
        int sundayOffset = 7 - todayIndex;
        ZonedDateTime nextSundayMidnight = startOfDay.plusDays(sundayOffset).atZone(ZoneId.systemDefault());
        logger.atInfo().log("Calculated next sunday midnight: %s",
                LazyArgs.lazy(() -> DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(nextSundayMidnight)));
        return nextSundayMidnight.toInstant();
    }
}
