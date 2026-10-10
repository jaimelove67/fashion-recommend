package com.fashion.recommendation.trend;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrendRefreshScheduleTest {
    @Test void refreshRunsAtNoonShanghaiRegardlessOfServerTimezone() throws Exception {
        Scheduled schedule = TrendService.class.getMethod("refresh").getAnnotation(Scheduled.class);
        assertEquals("Asia/Shanghai", schedule.zone());
        assertEquals("${app.trends.refresh-cron:0 0 12 * * *}", schedule.cron());
        CronExpression cron = CronExpression.parse("0 0 12 * * *");
        ZoneId zone = ZoneId.of(schedule.zone());
        var beforeNoon = ZonedDateTime.of(2026, 10, 8, 11, 59, 59, 0, zone);
        var noon = ZonedDateTime.of(2026, 10, 8, 12, 0, 0, 0, zone);
        assertEquals(noon, cron.next(beforeNoon));
        assertEquals(noon.plusDays(1), cron.next(noon));
        assertEquals("2026-10-08T04:00:00Z", cron.next(beforeNoon).toInstant().toString());
    }

    @Test void startupRefreshIsOneShotAndUsesTheSameSources() throws Exception {
        Scheduled startup = TrendService.class.getMethod("refreshOnStartup").getAnnotation(Scheduled.class);
        assertEquals("${app.trends.initial-delay:10000}", startup.initialDelayString());
        assertEquals("", startup.fixedDelayString());
        assertEquals("", startup.fixedRateString());
        TrendService service = spy(new TrendService(List.of(), mock(TrendRepository.class)));
        service.refreshOnStartup();
        verify(service).refresh();
    }
}
