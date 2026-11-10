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
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 워크플로가 실행되는 동안 127.0.0.1에 읽기 전용 REST API를 띄웁니다. 경로마다 SQL 한 줄을 적으면 결과 행들을 JSON으로 내줍니다.
 * <pre>
 *   /health          = SELECT 'ok' AS status
 *   /items           = SELECT * FROM items WHERE (:kind::text IS NULL OR kind = :kind) LIMIT COALESCE(:limit::int, 50)
 *   /items/{id}      = SELECT * FROM items WHERE id = :id
 * </pre>
 * {@code :이름}은 쿼리스트링(?kind=a)이나 경로 변수({id})에서 채우고, 없으면 NULL입니다. 값은 전부 파라미터로 바인딩되며
 * 타입이 애매하면 {@code :since::timestamptz}처럼 SQL에서 캐스트합니다. 응답은 {@code {"items":[…],"count":n}}이고,
 * 경로 변수가 있는 라우트는 결과가 없으면 404입니다. 잘못된 값(22xxx)은 400, 그 밖의 DB 오류는 500과 경고입니다.
 */
@Getter
@Setter
public class ApiServerNode extends LocalHttpServerNode {
    private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create(); // null도 키를 내보내고, URL의 =·&를 =로 바꾸지 않습니다
    private static final Pattern PARAMETER = Pattern.compile("(?<![:\\w]):([A-Za-z_]\\w*)"); // "::timestamptz" 같은 캐스트는 제외
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([A-Za-z_]\\w*)}");

    @OptionField(label = "JDBC URL (예: jdbc:postgresql://localhost:5432/mydb)")
    private String url;
    @OptionField(label = "사용자")
    private String user;
    @OptionField(type = OptionInputType.SENSITIVE_TEXT, label = "비밀번호")
    private String password;
    @OptionField(type = OptionInputType.TEXT_AREA, label = "라우트 (줄마다 /경로 = SQL, :이름은 ?이름=값 또는 {이름}에서)")
    private String routes;

    // JDBC 연결은 스레드 안전하지 않아 요청을 하나씩 처리합니다. 수집 도구의 조회 API라 이 정도로 충분합니다.
    private Connection connection;
    private List<Route> routeTable = List.of();

    /** 경로 패턴과, :이름을 ?로 바꾼 SQL, 그리고 ?마다 채울 파라미터 이름입니다. */
    record Route(Pattern path, List<String> variables, String sql, List<String> parameters) {
        boolean hasVariables() { return !variables.isEmpty(); }
    }

    public ApiServerNode(String id) {
        super(NodeType.API_SERVER, id, 8080);
    }

    @Override
    protected void onStart() {
        if (url == null || url.isBlank()) throw new IllegalStateException("JDBC URL이 비어 있습니다.");
        routeTable = parseRoutes(routes);
        if (routeTable.isEmpty()) throw new IllegalStateException("라우트가 비어 있습니다. 줄마다 \"/경로 = SQL\"을 적으세요.");
        try {
            connection = DriverManager.getConnection(url, user, password);
            connection.setReadOnly(true);
            for (Route route : routeTable) { // SQL 오류는 시작 때 드러냅니다. 드라이버는 실행 전까지 서버에 보내지 않으므로 메타데이터를 요청해 파싱시킵니다.
                try (PreparedStatement statement = connection.prepareStatement(route.sql())) { statement.getMetaData(); }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("DB 연결 또는 라우트 SQL 오류: " + e.getMessage().strip(), e);
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
        for (Route route : routeTable) {
            Matcher matcher = route.path().matcher(path);
            if (!matcher.matches()) continue;
            Map<String, String> params = queryParameters(exchange.getRequestURI().getRawQuery());
            for (int i = 0; i < route.variables().size(); i++) {
                params.put(route.variables().get(i), URLDecoder.decode(matcher.group(i + 1), StandardCharsets.UTF_8));
            }
            try {
                List<Map<String, Object>> items = query(route, params);
                if (items.isEmpty() && route.hasVariables()) return json(exchange, 404, Map.of("error", "not found: " + path));
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("items", items);
                body.put("count", items.size());
                return json(exchange, 200, body);
            } catch (SQLException e) {
                String message = e.getMessage() == null ? e.getSQLState() : e.getMessage().strip();
                if (e.getSQLState() != null && e.getSQLState().startsWith("22")) return json(exchange, 400, Map.of("error", message)); // 데이터 예외: 잘못된 파라미터 값
                warn("조회 실패 (" + path + "): " + message);
                return json(exchange, 500, Map.of("error", "database error"));
            }
        }
        return json(exchange, 404, Map.of("error", "not found: " + path));
    }

    private List<Map<String, Object>> query(Route route, Map<String, String> params) throws SQLException {
        synchronized (connection) {
            try (PreparedStatement statement = connection.prepareStatement(route.sql())) {
                for (int i = 0; i < route.parameters().size(); i++) {
                    String value = params.get(route.parameters().get(i));
                    if (value == null) statement.setNull(i + 1, Types.NULL);
                    else statement.setObject(i + 1, value, Types.OTHER); // 타입을 정하지 않고 보내 서버가 컬럼·캐스트에 맞춥니다.
                }
                try (ResultSet rows = statement.executeQuery()) { return rows(rows); }
            }
        }
    }

    /** "/경로 = SQL" 줄들입니다. 경로의 {이름}은 변수이고, SQL의 :이름은 ?로 바뀌며 순서를 기억합니다. */
    static List<Route> parseRoutes(String text) {
        List<Route> table = new ArrayList<>();
        for (Map.Entry<String, String> line : FieldMappings.parse(text).entrySet()) {
            String path = line.getKey();
            if (!path.startsWith("/")) throw new IllegalArgumentException("라우트 경로는 /로 시작해야 합니다: " + path);
            List<String> variables = new ArrayList<>();
            StringBuilder regex = new StringBuilder();
            Matcher variable = PATH_VARIABLE.matcher(path);
            int last = 0;
            while (variable.find()) {
                regex.append(Pattern.quote(path.substring(last, variable.start()))).append("([^/]+)");
                variables.add(variable.group(1));
                last = variable.end();
            }
            regex.append(Pattern.quote(path.substring(last)));

            List<String> parameters = new ArrayList<>();
            Matcher parameter = PARAMETER.matcher(line.getValue());
            StringBuilder sql = new StringBuilder();
            while (parameter.find()) {
                parameters.add(parameter.group(1));
                parameter.appendReplacement(sql, "?");
            }
            parameter.appendTail(sql);
            table.add(new Route(Pattern.compile(regex.toString()), variables, sql.toString(), parameters));
        }
        return table;
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
