package node.option;

import node.BaseNode;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 노드의 {@link OptionField} 필드를 찾아 이름으로 읽고 씁니다. 옵션 편집기와 워크플로 저장·불러오기가 함께 씁니다. */
public final class NodeOptions {
    private NodeOptions() { }

    /** 현재 클래스와 부모 클래스에서 어노테이션이 붙은 변경 가능한 필드를 선언 순서대로 찾습니다. */
    public static List<Field> fields(BaseNode node) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> type = node.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getAnnotation(OptionField.class) == null) continue;
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) continue;
                field.setAccessible(true);
                fields.add(field);
            }
        }
        return fields;
    }

    /** 옵션 이름 → 현재 값. 값이 null인 옵션도 포함합니다. */
    public static Map<String, Object> read(BaseNode node) {
        Map<String, Object> values = new LinkedHashMap<>();
        try {
            for (Field field : fields(node)) values.put(field.getName(), field.get(node));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("옵션 값을 읽을 수 없습니다: " + node.getId(), e);
        }
        return values;
    }

    /**
     * 이름으로 옵션을 씁니다. JSON에서 읽은 값(Double, String, Boolean)을 필드 타입에 맞게 바꿉니다.
     * 노드에 없는 이름은 무시해, 옵션이 추가·삭제된 뒤에도 예전 파일을 읽을 수 있게 합니다.
     */
    public static void write(BaseNode node, Map<String, ?> values) {
        try {
            for (Field field : fields(node)) {
                if (!values.containsKey(field.getName())) continue;
                field.set(node, convert(values.get(field.getName()), field.getType(), field.getName()));
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("옵션 값을 쓸 수 없습니다: " + node.getId(), e);
        }
    }

    private static Object convert(Object value, Class<?> type, String name) {
        if (value == null) {
            if (type.isPrimitive()) throw new IllegalArgumentException(name + "에는 null을 넣을 수 없습니다.");
            return null;
        }
        if (type == String.class) return value.toString();
        if (type == int.class || type == Integer.class) return number(value, name).intValue();
        if (type == long.class || type == Long.class) return number(value, name).longValue();
        if (type == boolean.class || type == Boolean.class) {
            if (value instanceof Boolean b) return b;
            throw new IllegalArgumentException(name + "은(는) true/false여야 합니다.");
        }
        if (type.isEnum()) {
            for (Object constant : type.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals(value.toString())) return constant;
            }
            throw new IllegalArgumentException(name + "에 없는 값입니다: " + value);
        }
        if (type.isInstance(value)) return value;
        throw new IllegalArgumentException(name + ": 지원하지 않는 필드 타입입니다.");
    }

    private static Number number(Object value, String name) {
        if (value instanceof Number n) return n;
        try { return Long.valueOf(value.toString().strip()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(name + "은(는) 숫자여야 합니다."); }
    }
}
