package node.functional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import lombok.Getter;
import lombok.Setter;
import node.NodeType;
import node.option.OptionField;
import node.option.OptionInputType;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 워크플로가 실행되는 동안 127.0.0.1에 REST API를 띄워 DB의 핫딜을 JSON으로 내줍니다. 사이트 프론트가 바로 호출할 수 있습니다.
 * <pre>
 *   GET /health                       {"status":"ok"}
 *   GET /deals?source=&since=&q=&limit=&offset=   최신 글부터. source는 출처, since는 posted_at 하한(ISO 8601), q는 제목 검색
 *   GET /deals/{id}                   글 하나와 수집 이력(snapshots: 시각순 가격·댓글·조회수)
 * </pre>
 * 잘못된 파라미터는 400, 없는 경로는 404, DB 오류는 500이며 경고로도 남깁니다. 응답에는 CORS 허용 헤더가 붙습니다.
 */
@Getter
@Setter
public class ApiServerNode extends LocalHttpServerNode {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create(); // 비어 있는 필드도 키를 내보내 프론트가 모양을 믿을 수 있게
    private static final int DEFAULT_LIMIT = 50;
    static final String DEAL_COLUMNS = "id, source, source_post_id, post_url, title, shop, product, price, category, author, comments, hits, posted_at, first_seen_at, last_seen_at";

    @OptionField(label = "JDBC URL (예: jdbc:postgresql://localhost:5432/hotdeal)")
    private String url;
    @OptionField(label = "사용자")
    private String user;
    @OptionField(type = OptionInputType.SENSITIVE_TEXT, label = "비밀번호")
    private String password;
    @OptionField(min = 1, label = "한 번에 내주는 최대 건수")
    private int maxLimit = 200;

    // JDBC 연결은 스레드 안전하지 않아 요청을 하나씩 처리합니다. 수집 도구의 조회 API라 이 정도로 충분합니다.
    private Connection connection;

    public ApiServerNode(String id) {
        super(NodeType.API_SERVER, id, 8080);
    }

    /** 클라이언트가 400을 받을 요청 오류입니다. */
    static final class BadRequest extends Exception {
        BadRequest(String message) { super(message); }
    }

    @Override
    protected void onStart() {
        if (url == null || url.isBlank()) throw new IllegalStateException("JDBC URL이 비어 있습니다.");
        try {
            connection = DriverManager.getConnection(url, user, password);
            connection.setReadOnly(true);
        } catch (SQLException e) {
            throw new IllegalStateException("DB에 연결할 수 없습니다: " + e.getMessage(), e);
        }
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (connection == null) return;
        try { connection.close(); } catch (SQLException e) { warn("DB 연결을 닫는 중 오류: " + e.getMessage()); }
        connection = null;
    }

    @Override
    protected int serve(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) return json(exchange, 405, Map.of("error", "GET only"));
        String path = exchange.getRequestURI().getPath();
        try {
            if (path.equals("/health")) return json(exchange, 200, Map.of("status", "ok"));
            if (path.equals("/deals")) return json(exchange, 200, listDeals(queryParameters(exchange.getRequestURI().getRawQuery())));
            if (path.startsWith("/deals/")) {
                Map<String, Object> deal = findDeal(URLDecoder.decode(path.substring("/deals/".length()), StandardCharsets.UTF_8));
                return deal == null ? json(exchange, 404, Map.of("error", "not found: " + path)) : json(exchange, 200, deal);
            }
            return json(exchange, 404, Map.of("error", "not found: " + path));
        } catch (BadRequest e) {
            return json(exchange, 400, Map.of("error", e.getMessage()));
        } catch (SQLException e) {
            warn("조회 실패 (" + path + "): " + e.getMessage().strip());
            return json(exchange, 500, Map.of("error", "database error"));
        }
    }

    // 필터는 모두 선택이며, 조건과 바인딩 값을 같은 순서로 모아 SQL을 조립합니다. 값은 전부 파라미터입니다.
    private Map<String, Object> listDeals(Map<String, String> params) throws BadRequest, SQLException {
        List<String> conditions = new ArrayList<>();
        List<Object> binds = new ArrayList<>();
        if (params.containsKey("source")) { conditions.add("source = ?"); binds.add(params.get("source")); }
        if (params.containsKey("since")) { conditions.add("posted_at >= ?::timestamptz"); binds.add(params.get("since")); }
        if (params.containsKey("q")) { conditions.add("title ILIKE ?"); binds.add("%" + params.get("q") + "%"); }
        int limit = integer(params, "limit", DEFAULT_LIMIT, 1, maxLimit);
        int offset = integer(params, "offset", 0, 0, Integer.MAX_VALUE);
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);

        Map<String, Object> result = new LinkedHashMap<>();
        synchronized (connection) {
            try (PreparedStatement count = connection.prepareStatement("SELECT count(*) FROM deals" + where)) {
                bindAll(count, binds);
                try (ResultSet rows = count.executeQuery()) { rows.next(); result.put("total", rows.getLong(1)); }
            } catch (SQLException e) {
                if (!conditions.contains("posted_at >= ?::timestamptz")) throw e;
                throw new BadRequest("since는 ISO 8601 시각이어야 합니다: " + params.get("since"));
            }
            try (PreparedStatement select = connection.prepareStatement("SELECT " + DEAL_COLUMNS + " FROM deals" + where
                    + " ORDER BY posted_at DESC NULLS LAST, id LIMIT ? OFFSET ?")) {
                bindAll(select, binds);
                select.setInt(binds.size() + 1, limit);
                select.setInt(binds.size() + 2, offset);
                try (ResultSet rows = select.executeQuery()) { result.put("items", rows(rows)); }
            }
        }
        result.put("limit", limit);
        result.put("offset", offset);
        return result;
    }

    /** 글 하나와 수집 이력입니다. 없으면 null입니다. */
    private Map<String, Object> findDeal(String id) throws SQLException {
        synchronized (connection) {
            Map<String, Object> deal;
            try (PreparedStatement select = connection.prepareStatement("SELECT " + DEAL_COLUMNS + " FROM deals WHERE id = ?")) {
                select.setString(1, id);
                try (ResultSet rows = select.executeQuery()) {
                    List<Map<String, Object>> found = rows(rows);
                    if (found.isEmpty()) return null;
                    deal = found.get(0);
                }
            }
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT collected_at, price, comments, hits FROM deal_snapshots WHERE deal_id = ? ORDER BY collected_at")) {
                select.setString(1, id);
                try (ResultSet rows = select.executeQuery()) { deal.put("snapshots", rows(rows)); }
            }
            return deal;
        }
    }

    private static void bindAll(PreparedStatement statement, List<Object> binds) throws SQLException {
        for (int i = 0; i < binds.size(); i++) statement.setObject(i + 1, binds.get(i));
    }

    private static int integer(Map<String, String> params, String name, int fallback, int min, int max) throws BadRequest {
        String value = params.get(name);
        if (value == null) return fallback;
        try {
            int number = Integer.parseInt(value.strip());
            if (number < min || number > max) throw new BadRequest(name + "은(는) " + min + "~" + max + " 사이여야 합니다.");
            return number;
        } catch (NumberFormatException e) {
            throw new BadRequest(name + "은(는) 정수여야 합니다: " + value);
        }
    }

    /** 결과 집합을 컬럼 이름 → 값 맵의 목록으로 바꿉니다. 시각은 ISO 8601 문자열입니다. */
    static List<Map<String, Object>> rows(ResultSet rows) throws SQLException {
        ResultSetMetaData meta = rows.getMetaData();
        List<Map<String, Object>> list = new ArrayList<>();
        while (rows.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                Object value = rows.getObject(i);
                if (value instanceof java.sql.Timestamp || value instanceof OffsetDateTime) value = rows.getObject(i, OffsetDateTime.class).toString();
                row.put(meta.getColumnLabel(i), value);
            }
            list.add(row);
        }
        return list;
    }

    static Map<String, String> queryParameters(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) return params;
        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            String name = URLDecoder.decode(separator < 0 ? pair : pair.substring(0, separator), StandardCharsets.UTF_8);
            String value = separator < 0 ? "" : URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
            if (!name.isEmpty() && !value.isEmpty()) params.put(name, value);
        }
        return params;
    }

    private static int json(HttpExchange exchange, int status, Object body) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*"); // 브라우저의 프론트가 다른 포트에서 호출할 수 있게
        respond(exchange, status, "application/json", GSON.toJson(body));
        return status;
    }
}
