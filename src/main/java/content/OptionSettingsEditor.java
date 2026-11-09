package content;

import annotation.OptionField;
import node.BaseNode;
import ui.UiTheme;

import javax.swing.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** @OptionField가 붙은 필드를 찾아 입력란을 만들고, 적용할 때 해당 필드에 값을 저장합니다. */
public final class OptionSettingsEditor {
    private static final int INPUT_WIDTH = 180;

    private final BaseNode node;
    private final BooleanSupplier boardEditable;
    private boolean editingEnabled;
    private final JPanel panel = new JPanel();
    private final Map<Field, OptionInput> inputs = new LinkedHashMap<>();
    private final JButton apply = UiTheme.button("적용");
    private final JLabel feedback = UiTheme.label("실행 전에 설정을 적용하세요.", false);

    public OptionSettingsEditor(BaseNode node, BooleanSupplier boardEditable) {
        this.node = node;
        this.boardEditable = boardEditable;
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);

        for (Field field : optionFields(node)) {
            OptionInput input = OptionInput.create(field, field.getAnnotation(OptionField.class));
            inputs.put(field, input);
            addRow(field.getName(), input);
        }

        apply.setName("applyNodeSettings");
        apply.addActionListener(e -> applySettings());
        panel.add(apply);
        panel.add(Box.createVerticalStrut(8));
        feedback.setName("nodeSettingsFeedback");
        panel.add(feedback);
        refreshState();
    }

    // 현재 클래스와 부모 클래스에서 어노테이션이 붙은 변경 가능한 필드를 선언 순서대로 찾습니다.
    private static List<Field> optionFields(BaseNode node) {
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

    private void addRow(String name, OptionInput input) {
        JComponent editor = input.getEditor();
        editor.setName("option." + name);
        JLabel label = UiTheme.label(name, false);
        label.setLabelFor(editor);

        JComponent view = input.getView();
        UiTheme.fixHeight(view, INPUT_WIDTH, input.getPreferredHeight());

        panel.add(label);
        panel.add(Box.createVerticalStrut(4));
        panel.add(view);
        panel.add(Box.createVerticalStrut(10));
    }

    public JComponent getComponent() { return panel; }

    public boolean hasOptions() { return !inputs.isEmpty(); }

    public void refreshState() {
        boolean canEdit = canEditSettings();
        inputs.forEach((field, input) -> input.setEditable(canEdit && isEditable(field)));
        apply.setEnabled(canEdit && inputs.keySet().stream().anyMatch(OptionSettingsEditor::isEditable));
        if (!canEdit) {
            loadSettings();
            feedback.setText("실행 중이거나 종료 중에는 수정할 수 없습니다.");
        } else if (!editingEnabled) {
            // 최초 표시·종료 완료 때만 다시 읽고, 일반 화면 갱신에서는 작성 중인 입력을 유지합니다.
            loadSettings();
            feedback.setText("실행 전에 설정을 적용하세요.");
        }
        editingEnabled = canEdit;
    }

    private static boolean isEditable(Field field) {
        return field.getAnnotation(OptionField.class).editable();
    }

    private boolean canEditSettings() {
        return boardEditable.getAsBoolean() && !node.isExecutionActive();
    }

    private void loadSettings() {
        try {
            for (var entry : inputs.entrySet()) entry.getValue().setValue(entry.getKey().get(node));
        } catch (IllegalAccessException e) {
            feedback.setText("설정값을 읽을 수 없습니다.");
        }
    }

    // 입력값을 필드 타입으로 변환한 뒤 저장합니다. 노드별 동작 검증은 해당 노드가 담당합니다.
    private void applySettings() {
        if (!canEditSettings()) { refreshState(); return; }
        try {
            // 숫자 변환에 실패했을 때 일부 필드만 바뀌지 않도록 변환을 모두 마친 뒤 저장합니다.
            Map<Field, Object> values = new LinkedHashMap<>();
            for (var entry : inputs.entrySet()) {
                if (isEditable(entry.getKey())) values.put(entry.getKey(), entry.getValue().getValue());
            }
            // start()와 같은 잠금을 사용해 최종 확인과 저장 사이에 노드가 시작되지 않게 합니다.
            synchronized (node) {
                if (!canEditSettings()) { refreshState(); return; }
                for (var entry : values.entrySet()) entry.getKey().set(node, entry.getValue());
            }
            feedback.setText("적용했습니다.");
        } catch (IllegalArgumentException | IllegalAccessException e) {
            // 입력 원문에는 웹훅 토큰이 포함될 수 있으므로 예외 메시지는 그대로 표시하지 않습니다.
            feedback.setText("입력값과 필드 타입을 확인하세요.");
        }
    }
}
