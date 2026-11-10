package node.functional;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.BitSet;

/**
 * 다섯 칸 cron 식("분 시 일 월 요일")입니다. 각 칸은 {@code *}, 숫자, {@code a-b}, {@code a,b,c}, {@code * /n}, {@code a-b/n}을 받습니다.
 * 요일은 0~7(0과 7이 일요일)입니다. 일과 요일을 둘 다 지정하면 표준 cron처럼 둘 중 하나만 맞아도 됩니다.
 * <pre>
 *   * /10 * * * *     10분마다
 *   0 9 * * 1-5      평일 9시
 *   30 8,20 * * *    매일 8:30, 20:30
 * </pre>
 */
final class Cron {
    private final BitSet minutes = new BitSet(60);
    private final BitSet hours = new BitSet(24);
    private final BitSet daysOfMonth = new BitSet(32);
    private final BitSet months = new BitSet(13);
    private final BitSet daysOfWeek = new BitSet(8);
    private final boolean anyDayOfMonth;
    private final boolean anyDayOfWeek;

    private Cron(String[] fields) {
        fill(minutes, fields[0], 0, 59, "분");
        fill(hours, fields[1], 0, 23, "시");
        fill(daysOfMonth, fields[2], 1, 31, "일");
        fill(months, fields[3], 1, 12, "월");
        fill(daysOfWeek, fields[4], 0, 7, "요일");
        if (daysOfWeek.get(7)) daysOfWeek.set(0); // 7도 일요일
        anyDayOfMonth = fields[2].equals("*");
        anyDayOfWeek = fields[4].equals("*");
    }

    static Cron parse(String expression) {
        String[] fields = expression == null ? new String[0] : expression.strip().split("\\s+");
        if (fields.length != 5) throw new IllegalArgumentException("cron 식은 \"분 시 일 월 요일\" 다섯 칸이어야 합니다: " + expression);
        return new Cron(fields);
    }

    /** after 다음에 처음 맞는 시각(초·나노초는 0)입니다. 1년 안에 없으면 null입니다(예: 2월 30일). */
    ZonedDateTime next(ZonedDateTime after) {
        ZonedDateTime candidate = after.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
        ZonedDateTime limit = after.plusYears(1);
        while (candidate.isBefore(limit)) {
            if (!months.get(candidate.getMonthValue())) { candidate = candidate.plusMonths(1).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS); continue; }
            if (!matchesDay(candidate)) { candidate = candidate.plusDays(1).truncatedTo(ChronoUnit.DAYS); continue; }
            if (!hours.get(candidate.getHour())) { candidate = candidate.plusHours(1).truncatedTo(ChronoUnit.HOURS); continue; }
            if (!minutes.get(candidate.getMinute())) { candidate = candidate.plusMinutes(1); continue; }
            return candidate;
        }
        return null;
    }

    private boolean matchesDay(ZonedDateTime time) {
        boolean dayOfMonth = daysOfMonth.get(time.getDayOfMonth());
        boolean dayOfWeek = daysOfWeek.get(time.getDayOfWeek().getValue() % 7); // 월=1 … 일=0
        if (anyDayOfMonth) return dayOfWeek;
        if (anyDayOfWeek) return dayOfMonth;
        return dayOfMonth || dayOfWeek;
    }

    private static void fill(BitSet set, String field, int min, int max, String name) {
        for (String part : field.split(",")) {
            String range = part;
            int step = 1;
            int slash = part.indexOf('/');
            if (slash >= 0) {
                range = part.substring(0, slash);
                step = parse(part.substring(slash + 1), name);
                if (step < 1) throw new IllegalArgumentException(name + " 칸의 간격은 1 이상이어야 합니다: " + part);
            }
            int from, to;
            if (range.equals("*")) { from = min; to = max; }
            else if (range.contains("-")) {
                String[] bounds = range.split("-", 2);
                from = parse(bounds[0], name);
                to = parse(bounds[1], name);
            } else {
                from = parse(range, name);
                to = slash >= 0 ? max : from; // "5/10"은 5부터 끝까지 10 간격
            }
            if (from < min || to > max || from > to) throw new IllegalArgumentException(name + " 칸의 범위는 " + min + "~" + max + "입니다: " + part);
            for (int value = from; value <= to; value += step) set.set(value);
        }
    }

    private static int parse(String text, String name) {
        try {
            return Integer.parseInt(text.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " 칸을 읽을 수 없습니다: " + text);
        }
    }
}
