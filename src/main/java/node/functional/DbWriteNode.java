package node.functional;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.Setter;
import node.BaseNode;
import node.Inputable;
import node.Message;
import node.NodeType;
import node.option.OptionField;
import node.option.OptionInputType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 받은 메시지를 PostgreSQL 테이블에 upsert합니다. 키 컬럼 값이 같은 행이 있으면 나머지 컬럼을 갱신하고, 없으면 새 행을 넣습니다.
 * 컬럼은 "컬럼 = 필드" 줄로 payload 필드에 대응시키고, "컬럼"만 쓰면 같은 이름의 필드를 씁니다.
 * 연결 실패는 노드 실패(워크플로 종료), 한 행의 제약 위반은 경고입니다.
 */
@Getter
@Setter
public class DbWriteNode extends BaseNode implements Inputable {
    private static final Gson GSON = new Gson();
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern TABLE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

    @OptionField(label = "JDBC URL (예: jdbc:postgresql://localhost:5432/hotdeal)")
    private String url;
    @OptionField(label = "사용자")
    private String user;
    @OptionField(type = OptionInputType.SENSITIVE_TEXT, label = "비밀번호")
    private String password;
    @OptionField(label = "테이블")
    private String table = "deals";
    @OptionField(label = "키 컬럼 (같은 값의 행이 있으면 갱신)")
    private String keyColumn = "id";
    @OptionField(type = OptionInputType.TEXT_AREA, label = "컬럼 (줄마다 컬럼 [= 필드], 비우면 payload 필드 이름 그대로)")
    private String columns;

    private Connection connection;
    private PreparedStatement upsert;
    private Map<String, String> mapping; // 컬럼 → 필드. columns 옵션이 비면 첫 메시지에서 정합니다.

    public DbWriteNode(String id) {
        super(NodeType.DB_WRITE, id);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (url == null || url.isBlank()) throw new IllegalStateException("JDBC URL이 비어 있습니다.");
        mapping = parseColumns(columns);
        if (mapping != null) upsertSql(table, keyColumn, mapping); // 옵션 오류는 연결 전에 드러냅니다.
        try {
            connection = DriverManager.getConnection(url, user, password);
            connection.setAutoCommit(false);
            if (mapping != null) upsert = connection.prepareStatement(upsertSql(table, keyColumn, mapping));
        } catch (SQLException e) {
            throw new IllegalStateException("DB에 연결할 수 없습니다: " + e.getMessage(), e);
        }
    }

    @Override
    protected void process() throws Exception {
        Message message = getInbox().take();
        try {
            if (upsert == null) {
                mapping = new LinkedHashMap<>();
                for (String field : message.getPayload().keySet()) mapping.put(field, field);
                upsert = connection.prepareStatement(upsertSql(table, keyColumn, mapping));
            }
            int index = 1;
            for (String field : mapping.values()) bind(upsert, index++, message.get(field));
            upsert.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            rollbackQuietly();
            if (!connection.isValid(2)) throw new IllegalStateException("DB 연결이 끊겼습니다: " + e.getMessage(), e);
            warn("DB 쓰기 실패: " + e.getMessage().strip());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException e) {
            warn("DB 연결을 닫는 중 오류: " + e.getMessage());
        } finally {
            connection = null;
            upsert = null;
        }
    }

    private void rollbackQuietly() {
        try { connection.rollback(); } catch (SQLException ignored) { }
    }

    /** "컬럼 [= 필드]" 줄들을 컬럼 → 필드 맵으로 읽습니다. 줄이 없으면 null입니다. */
    static Map<String, String> parseColumns(String text) {
        List<String> lines = FieldMappings.names(text);
        if (lines == null) return null;
        Map<String, String> mapping = new LinkedHashMap<>();
        for (String line : lines) {
            int separator = line.indexOf('=');
            String column = (separator < 0 ? line : line.substring(0, separator)).strip();
            String field = (separator < 0 ? line : line.substring(separator + 1)).strip();
            if (column.isEmpty() || field.isEmpty()) throw new IllegalArgumentException("\"컬럼 = 필드\" 형식이 아닙니다: " + line);
            mapping.put(column, field);
        }
        return mapping;
    }

    /** INSERT … ON CONFLICT (키) DO UPDATE SET 나머지 = EXCLUDED.나머지. 식별자는 영문·숫자·밑줄만 허용합니다. */
    static String upsertSql(String table, String keyColumn, Map<String, String> mapping) {
        if (table == null || !TABLE.matcher(table).matches()) throw new IllegalArgumentException("테이블 이름이 올바르지 않습니다: " + table);
        if (keyColumn == null || !mapping.containsKey(keyColumn)) throw new IllegalArgumentException("키 컬럼이 컬럼 목록에 없습니다: " + keyColumn);
        List<String> updates = new ArrayList<>();
        for (String column : mapping.keySet()) {
            if (!IDENTIFIER.matcher(column).matches()) throw new IllegalArgumentException("컬럼 이름이 올바르지 않습니다: " + column);
            if (!column.equals(keyColumn)) updates.add(column + " = EXCLUDED." + column);
        }
        String sql = "INSERT INTO " + table + " (" + String.join(", ", mapping.keySet()) + ") VALUES ("
                + String.join(", ", java.util.Collections.nCopies(mapping.size(), "?")) + ") ON CONFLICT (" + keyColumn + ") ";
        return sql + (updates.isEmpty() ? "DO NOTHING" : "DO UPDATE SET " + String.join(", ", updates));
    }

    // 숫자·불리언은 그대로, 문자열은 타입을 정하지 않고 보내 서버가 컬럼 타입(TIMESTAMPTZ 등)으로 바꾸게 합니다. 목록·맵은 JSON 문자열입니다.
    private static void bind(PreparedStatement statement, int index, Object value) throws SQLException {
        if (value == null) statement.setNull(index, Types.NULL);
        else if (value instanceof Number || value instanceof Boolean) statement.setObject(index, value);
        else statement.setObject(index, value instanceof String s ? s : GSON.toJson(value), Types.OTHER);
    }
}
