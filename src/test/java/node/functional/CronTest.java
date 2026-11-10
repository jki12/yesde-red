package node.functional;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CronTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final ZonedDateTime TUESDAY = ZonedDateTime.of(2026, 11, 10, 16, 42, 30, 0, SEOUL); // 2026-11-10은 화요일

    private static String next(String cron, ZonedDateTime after) {
        ZonedDateTime next = Cron.parse(cron).next(after);
        return next == null ? null : next.toLocalDateTime().toString();
    }

    @Test
    void everyNMinutesAndFixedTimes() {
        assertEquals("2026-11-10T16:50", next("*/10 * * * *", TUESDAY));
        assertEquals("2026-11-10T16:43", next("* * * * *", TUESDAY), "매분: 다음 분");
        assertEquals("2026-11-10T20:30", next("30 8,20 * * *", TUESDAY));
        assertEquals("2026-11-11T08:30", next("30 8,20 * * *", TUESDAY.withHour(21)));
        assertEquals("2026-11-10T16:45", next("5/10 * * * *", TUESDAY), "5부터 10 간격: 5,15,25,35,45,55");
    }

    @Test
    void weekdaysDaysAndMonths() {
        assertEquals("2026-11-11T09:00", next("0 9 * * 1-5", TUESDAY), "평일 9시");
        assertEquals("2026-11-16T09:00", next("0 9 * * 1-5", TUESDAY.withDayOfMonth(13).withHour(10)), "금요일 10시 → 다음 월요일");
        assertEquals("2026-11-15T09:00", next("0 9 * * 0", TUESDAY), "일요일(0)");
        assertEquals("2026-11-15T09:00", next("0 9 * * 7", TUESDAY), "7도 일요일");
        assertEquals("2026-12-01T00:00", next("0 0 1 * *", TUESDAY), "매월 1일");
        assertEquals("2027-01-01T00:00", next("0 0 1 1 *", TUESDAY), "1월 1일");
        assertEquals("2026-11-13T09:00", next("0 9 13 * 1", TUESDAY), "일과 요일을 둘 다 주면 둘 중 하나: 13일이 월요일(16일)보다 먼저");
        assertNull(next("0 0 30 2 *", TUESDAY), "2월 30일은 없음");
    }

    @Test
    void rejectsBadExpressions() {
        for (String bad : new String[] {"", "* * * *", "60 * * * *", "* 24 * * *", "* * 0 * *", "* * * 13 *", "* * * * 8", "a * * * *", "*/0 * * * *", "10-5 * * * *"}) {
            assertThrows(IllegalArgumentException.class, () -> Cron.parse(bad), bad);
        }
    }
}
