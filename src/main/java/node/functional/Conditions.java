package node.functional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 메시지를 거르는 조건식을 읽고 평가합니다.
 * <pre>
 *   price < 20000 and title contains "키보드"
 *   (source = "clien" or source = "ruliweb") and not shop = null
 *   title matches "^\\[.*\\]"
 * </pre>
 * 비교: = != < <= > >= contains matches. 양쪽이 숫자면 숫자로, 아니면 문자열로 비교하며 null은 =·!=에만 맞습니다.
 * 논리: and or not, 괄호. 값: 필드 이름, "문자열", 숫자, null. 대소문자는 구분하지 않습니다(contains 포함).
 */
final class Conditions {
    private Conditions() { }

    interface Condition {
        boolean test(Map<String, Object> payload);
    }

    static Condition parse(String text) {
        Parser parser = new Parser(tokens(text == null ? "" : text));
        Condition condition = parser.or();
        parser.expectEnd();
        return condition;
    }

    // ---- 토큰 ----

    private static final Pattern TOKEN = Pattern.compile(
            "\\s*(?:(\"(?:[^\"\\\\]|\\\\.)*\")|(-?\\d+(?:\\.\\d+)?)|([A-Za-z_][A-Za-z0-9_]*)|(<=|>=|!=|<>|==|=|<|>|\\(|\\)))");

    private record Token(String kind, String text) { } // kind: string, number, word, op, end

    private static List<Token> tokens(String text) {
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(text);
        int position = 0;
        while (position < text.length()) {
            if (!matcher.region(position, text.length()).lookingAt()) {
                if (text.substring(position).isBlank()) break;
                throw new IllegalArgumentException("읽을 수 없는 글자입니다 (위치 " + position + "): " + text);
            }
            if (matcher.group(1) != null) tokens.add(new Token("string", unescape(matcher.group(1))));
            else if (matcher.group(2) != null) tokens.add(new Token("number", matcher.group(2)));
            else if (matcher.group(3) != null) tokens.add(new Token("word", matcher.group(3)));
            else tokens.add(new Token("op", matcher.group(4)));
            position = matcher.end();
        }
        tokens.add(new Token("end", ""));
        return tokens;
    }

    private static String unescape(String quoted) {
        StringBuilder value = new StringBuilder();
        for (int i = 1; i < quoted.length() - 1; i++) {
            char c = quoted.charAt(i);
            if (c == '\\' && i + 1 < quoted.length() - 1) {
                char next = quoted.charAt(++i);
                value.append(switch (next) { case 'n' -> '\n'; case 't' -> '\t'; default -> next; });
            } else value.append(c);
        }
        return value.toString();
    }

    // ---- 파서: or > and > not > 비교 ----

    private static final class Parser {
        private final List<Token> tokens;
        private int position;

        Parser(List<Token> tokens) { this.tokens = tokens; }

        Condition or() {
            Condition left = and();
            while (word("or")) { Condition right = and(); Condition l = left; left = p -> l.test(p) || right.test(p); }
            return left;
        }

        Condition and() {
            Condition left = not();
            while (word("and")) { Condition right = not(); Condition l = left; left = p -> l.test(p) && right.test(p); }
            return left;
        }

        Condition not() {
            if (word("not")) { Condition inner = not(); return p -> !inner.test(p); }
            if (op("(")) { Condition inner = or(); if (!op(")")) throw error("')'가 와야 합니다"); return inner; }
            return comparison();
        }

        Condition comparison() {
            Operand left = operand();
            Token operator = tokens.get(position++);
            String name = operator.text().toLowerCase(Locale.ROOT);
            if (!(operator.kind().equals("op") && !name.equals("(") && !name.equals(")")) && !name.equals("contains") && !name.equals("matches")) {
                position--;
                throw error("비교 연산자가 와야 합니다");
            }
            Operand right = operand();
            return switch (name) {
                case "=", "==" -> p -> equal(left.value(p), right.value(p));
                case "!=", "<>" -> p -> !equal(left.value(p), right.value(p));
                case "<" -> p -> compare(left.value(p), right.value(p), c -> c < 0);
                case "<=" -> p -> compare(left.value(p), right.value(p), c -> c <= 0);
                case ">" -> p -> compare(left.value(p), right.value(p), c -> c > 0);
                case ">=" -> p -> compare(left.value(p), right.value(p), c -> c >= 0);
                case "contains" -> p -> {
                    String haystack = text(left.value(p)), needle = text(right.value(p));
                    return haystack != null && needle != null && haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
                };
                case "matches" -> {
                    Pattern pattern = compile(right);
                    yield p -> { String s = text(left.value(p)); return s != null && pattern.matcher(s).find(); };
                }
                default -> throw error("모르는 연산자입니다: " + operator.text());
            };
        }

        private Operand operand() {
            Token token = tokens.get(position++);
            return switch (token.kind()) {
                case "string" -> p -> token.text();
                case "number" -> p -> new BigDecimal(token.text());
                case "word" -> token.text().equalsIgnoreCase("null") ? p -> null : p -> p.get(token.text());
                default -> { position--; throw error("필드 이름이나 값이 와야 합니다"); }
            };
        }

        private Pattern compile(Operand right) {
            String regex = text(right.value(Map.of()));
            if (regex == null) throw error("matches의 패턴은 \"문자열\"이어야 합니다");
            try { return Pattern.compile(regex); }
            catch (PatternSyntaxException e) { throw error("정규식 오류: " + e.getDescription()); }
        }

        private boolean word(String keyword) {
            Token token = tokens.get(position);
            if (!token.kind().equals("word") || !token.text().equalsIgnoreCase(keyword)) return false;
            position++;
            return true;
        }

        private boolean op(String symbol) {
            Token token = tokens.get(position);
            if (!token.kind().equals("op") || !token.text().equals(symbol)) return false;
            position++;
            return true;
        }

        void expectEnd() {
            if (!tokens.get(position).kind().equals("end")) throw error("식 뒤에 남는 글자가 있습니다");
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " (토큰 " + (position + 1) + ": \"" + tokens.get(Math.min(position, tokens.size() - 1)).text() + "\")");
        }
    }

    private interface Operand {
        Object value(Map<String, Object> payload);
    }

    // ---- 비교 ----

    static boolean equal(Object left, Object right) {
        if (left == null || right == null) return left == right;
        BigDecimal a = number(left), b = number(right);
        if (a != null && b != null) return a.compareTo(b) == 0;
        return text(left).equalsIgnoreCase(text(right));
    }

    private static boolean compare(Object left, Object right, java.util.function.IntPredicate accept) {
        BigDecimal a = number(left), b = number(right);
        if (a != null && b != null) return accept.test(a.compareTo(b));
        if (left == null || right == null) return false;
        return accept.test(text(left).compareToIgnoreCase(text(right)));
    }

    private static BigDecimal number(Object value) {
        if (value instanceof BigDecimal d) return d;
        if (value instanceof Number n) return new BigDecimal(n.toString());
        if (value instanceof String s) {
            try { return new BigDecimal(s.strip()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
