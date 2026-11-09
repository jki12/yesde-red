package node.functional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 필드 변환 규칙 "대상 = 식"을 읽고 payload에 적용합니다. 식은 다음 중 하나입니다.
 * <pre>
 *   field                         다른 필드 값 복사
 *   "문자열"                       상수
 *   regex(field, "패턴")           첫 캡처 그룹(없으면 전체 일치). 일치 없으면 null
 *   replace(field, "패턴", "치환")  정규식 전체 치환
 *   number(field)                 "1,000원" → 1000, "27.3 k" → 27300, "(3)" → 3
 *   format("{a}:{b}")             다른 필드를 끼워 넣은 문자열
 *   datetime(field, "패턴"[, "시간대"])  ISO 8601(+09:00)로 정규화. 패턴: rfc1123, iso, 또는 DateTimeFormatter 패턴
 *   now()                         현재 시각 ISO 8601
 *   trim(field) / lower(field) / upper(field)
 * </pre>
 * 규칙은 위에서 아래로 적용되므로 앞 규칙이 만든 필드를 뒤 규칙이 쓸 수 있습니다.
 */
final class FieldTransforms {
    static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

    private FieldTransforms() { }

    /** 한 줄의 변환 규칙입니다. */
    record Rule(String target, Expression expression) { }

    /** payload를 받아 값을 돌려줍니다. 실패는 {@link TransformException}으로 알립니다. */
    interface Expression {
        Object evaluate(Map<String, Object> payload);
    }

    /** 메시지 하나의 변환 실패입니다. 메시지는 사용자에게 그대로 보여줄 수 있습니다. */
    static final class TransformException extends RuntimeException {
        TransformException(String message) { super(message); }
    }

    static List<Rule> parseRules(String text) {
        List<Rule> rules = new ArrayList<>();
        for (Map.Entry<String, String> line : FieldMappings.parse(text).entrySet()) {
            rules.add(new Rule(line.getKey(), parseExpression(line.getValue())));
        }
        return rules;
    }

    /** 규칙들을 순서대로 적용한 새 맵을 돌려줍니다. 원본은 바꾸지 않습니다. */
    static Map<String, Object> apply(List<Rule> rules, Map<String, Object> payload) {
        Map<String, Object> result = new java.util.LinkedHashMap<>(payload);
        for (Rule rule : rules) result.put(rule.target(), rule.expression().evaluate(result));
        return result;
    }

    // ---- 식 파서 ----

    static Expression parseExpression(String text) {
        Parser parser = new Parser(text.strip());
        Expression expression = parser.expression();
        parser.expectEnd();
        return expression;
    }

    private static final class Parser {
        private final String text;
        private int position;

        Parser(String text) { this.text = text; }

        Expression expression() {
            skipSpaces();
            if (peek() == '"') return constant(string());
            String name = identifier();
            skipSpaces();
            if (peek() != '(') return fieldReference(name);
            position++; // (
            List<Expression> arguments = new ArrayList<>();
            skipSpaces();
            if (peek() != ')') {
                while (true) {
                    arguments.add(expression());
                    skipSpaces();
                    if (peek() == ',') { position++; continue; }
                    if (peek() == ')') break;
                    throw error("',' 또는 ')'가 와야 합니다");
                }
            }
            position++; // )
            return function(name, arguments);
        }

        void expectEnd() {
            skipSpaces();
            if (position < text.length()) throw error("식 뒤에 남는 글자가 있습니다");
        }

        private String identifier() {
            int start = position;
            while (position < text.length() && (Character.isLetterOrDigit(text.charAt(position)) || text.charAt(position) == '_')) position++;
            if (start == position) throw error("필드 이름이나 함수 이름이 와야 합니다");
            return text.substring(start, position);
        }

        private String string() {
            StringBuilder value = new StringBuilder();
            position++; // 여는 따옴표
            while (position < text.length()) {
                char c = text.charAt(position++);
                if (c == '"') return value.toString();
                if (c == '\\' && position < text.length()) {
                    char next = text.charAt(position++);
                    value.append(switch (next) { case 'n' -> '\n'; case 't' -> '\t'; default -> next; });
                } else {
                    value.append(c);
                }
            }
            throw error("문자열의 닫는 따옴표가 없습니다");
        }

        private void skipSpaces() { while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++; }
        private char peek() { return position < text.length() ? text.charAt(position) : '\0'; }
        private IllegalArgumentException error(String message) { return new IllegalArgumentException(message + " (위치 " + position + "): " + text); }
    }

    // ---- 식 종류 ----

    private static Expression constant(String value) {
        return payload -> value;
    }

    private static Expression fieldReference(String name) {
        return payload -> payload.get(name);
    }

    private static Expression function(String name, List<Expression> args) {
        return switch (name) {
            case "const" -> { require(name, args, 1); yield args.get(0); }
            case "copy" -> { require(name, args, 1); yield args.get(0); }
            case "trim" -> unary(name, args, s -> s.strip());
            case "lower" -> unary(name, args, s -> s.toLowerCase(Locale.ROOT));
            case "upper" -> unary(name, args, s -> s.toUpperCase(Locale.ROOT));
            case "now" -> { require(name, args, 0); yield payload -> ZonedDateTime.now(DEFAULT_ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME); }
            case "number" -> { require(name, args, 1); yield payload -> number(text(args.get(0).evaluate(payload))); }
            case "regex" -> {
                require(name, args, 2);
                Pattern pattern = compile(literal(args.get(1), name));
                yield payload -> regex(text(args.get(0).evaluate(payload)), pattern);
            }
            case "replace" -> {
                require(name, args, 3);
                Pattern pattern = compile(literal(args.get(1), name));
                String replacement = literal(args.get(2), name);
                yield payload -> { String s = text(args.get(0).evaluate(payload)); return s == null ? null : pattern.matcher(s).replaceAll(replacement); };
            }
            case "format" -> {
                require(name, args, 1);
                String template = literal(args.get(0), name);
                yield payload -> format(template, payload);
            }
            case "datetime" -> {
                if (args.size() < 2 || args.size() > 3) throw new IllegalArgumentException("datetime(필드, \"패턴\"[, \"시간대\"])");
                DateTimeFormatter formatter = formatter(literal(args.get(1), name));
                ZoneId zone = args.size() == 3 ? ZoneId.of(literal(args.get(2), name)) : DEFAULT_ZONE;
                yield payload -> datetime(text(args.get(0).evaluate(payload)), formatter, zone);
            }
            default -> throw new IllegalArgumentException("모르는 함수입니다: " + name);
        };
    }

    private static Expression unary(String name, List<Expression> args, Function<String, String> operation) {
        require(name, args, 1);
        return payload -> { String s = text(args.get(0).evaluate(payload)); return s == null ? null : operation.apply(s); };
    }

    private static void require(String name, List<Expression> args, int count) {
        if (args.size() != count) throw new IllegalArgumentException(name + "은(는) 인자 " + count + "개가 필요합니다.");
    }

    // 패턴·템플릿 인자는 문자열 상수여야 합니다. (필드 값으로 패턴을 만들면 매 메시지마다 컴파일해야 하고 오류도 늦게 드러납니다.)
    private static String literal(Expression expression, String function) {
        Object value = expression.evaluate(Map.of());
        if (value instanceof String s) return s;
        throw new IllegalArgumentException(function + "의 패턴·형식 인자는 \"문자열\"이어야 합니다.");
    }

    private static Pattern compile(String pattern) {
        try { return Pattern.compile(pattern); }
        catch (java.util.regex.PatternSyntaxException e) { throw new IllegalArgumentException("정규식 오류: " + e.getDescription()); }
    }

    // ---- 변환 구현 ----

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    static String regex(String input, Pattern pattern) {
        if (input == null) return null;
        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) return null;
        return matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
    }

    private static final Pattern NUMBER = Pattern.compile("-?\\d[\\d,]*(?:\\.\\d+)?|-?\\.\\d+");

    /** "1,000원" → 1000, "27.3 k" → 27300, "(3)" → 3, "1.2만" → 12000. 숫자가 없으면 null. */
    static Object number(String input) {
        if (input == null) return null;
        Matcher matcher = NUMBER.matcher(input);
        if (!matcher.find()) return null;
        BigDecimal value = new BigDecimal(matcher.group().replace(",", ""));
        String rest = input.substring(matcher.end()).strip().toLowerCase(Locale.ROOT);
        if (rest.startsWith("k") || rest.startsWith("천")) value = value.multiply(BigDecimal.valueOf(1_000));
        else if (rest.startsWith("만")) value = value.multiply(BigDecimal.valueOf(10_000));
        else if (rest.startsWith("m") || rest.startsWith("백만")) value = value.multiply(BigDecimal.valueOf(1_000_000));
        value = value.stripTrailingZeros();
        return value.scale() <= 0 ? (Object) value.longValueExact() : (Object) value.doubleValue();
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)}");

    static String format(String template, Map<String, Object> payload) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            Object value = payload.get(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : value.toString()));
        }
        return matcher.appendTail(out).toString();
    }

    private static DateTimeFormatter formatter(String pattern) {
        return switch (pattern.toLowerCase(Locale.ROOT)) {
            case "rfc1123" -> DateTimeFormatter.RFC_1123_DATE_TIME;
            case "iso" -> DateTimeFormatter.ISO_DATE_TIME;
            default -> {
                try { yield DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH); }
                catch (IllegalArgumentException e) { throw new IllegalArgumentException("날짜 패턴 오류: " + pattern); }
            }
        };
    }

    /** 시간대가 있으면 변환, 날짜·시각만 있으면 zone으로 해석, 시각만 있으면 zone의 오늘 날짜를 붙입니다. */
    static String datetime(String input, DateTimeFormatter formatter, ZoneId zone) {
        if (input == null || input.isBlank()) return null;
        TemporalAccessor parsed;
        try {
            parsed = formatter.parse(input.strip());
        } catch (DateTimeParseException e) {
            throw new TransformException("시각을 읽을 수 없습니다: \"" + input + "\"");
        }
        ZonedDateTime result;
        if (parsed.isSupported(java.time.temporal.ChronoField.OFFSET_SECONDS)) {
            result = ZonedDateTime.from(parsed).withZoneSameInstant(zone);
        } else if (parsed.isSupported(java.time.temporal.ChronoField.YEAR)) {
            boolean hasTime = parsed.isSupported(java.time.temporal.ChronoField.HOUR_OF_DAY);
            result = hasTime ? LocalDateTime.from(parsed).atZone(zone) : LocalDate.from(parsed).atStartOfDay(zone);
        } else {
            result = LocalTime.from(parsed).atDate(LocalDate.now(zone)).atZone(zone);
        }
        return result.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
