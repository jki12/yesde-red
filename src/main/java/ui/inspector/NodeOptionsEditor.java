package ui.inspector;

import node.option.NodeOptions;
import node.option.OptionField;
import node.BaseNode;
import ui.theme.UiTheme;

import javax.swing.*;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** @OptionField가 붙은 필드를 찾아 입력란을 만들고, 적용할 때 해당 필드에 값을 저장합니다. */
public final class NodeOptionsEditor {
    private static final int INPUT_WIDTH = 180;

    private final BaseNode node;
    private final BooleanSupplier boardEditable;
    private boolean editingEnabled;
    private final JPanel panel = new JPanel();
    private final Map<Field, OptionInput> inputs = new LinkedHashMap<>();
    private final JButton apply = UiTheme.button("적용");
    private final JLabel feedback = UiTheme.label("실행 전에 설정을 적용하세요.", false);

    public NodeOptionsEditor(BaseNode node, BooleanSupplier boardEditable) {
        this.node = node;
        this.boardEditable = boardEditable;
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);

        for (Field field : NodeOptions.fields(node)) {
            OptionField option = field.getAnnotation(OptionField.class);
            OptionInput input = OptionInput.create(field, option);
            inputs.put(field, input);
            addRow(field.getName(), option.label().isEmpty() ? field.getName() : option.label(), input);
        }

        apply.setName("applyNodeOptions");
        apply.addActionListener(e -> applyOptions());
        panel.add(apply);
        panel.add(Box.createVerticalStrut(8));
        feedback.setName("nodeOptionsFeedback");
        panel.add(feedback);
        refreshState();
    }

    private void addRow(String name, String labelText, OptionInput input) {
        JComponent editor = input.getEditor();
        editor.setName("option." + name);
        JLabel label = UiTheme.label(labelText, false);
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
        boolean canEdit = canEditOptions();
        inputs.forEach((field, input) -> input.setEditable(canEdit && isEditable(field)));
        apply.setEnabled(canEdit && inputs.keySet().stream().anyMatch(NodeOptionsEditor::isEditable));
        if (!canEdit) {
            loadOptions();
            feedback.setText("실행 중이거나 종료 중에는 수정할 수 없습니다.");
        } else if (!editingEnabled) {
            // 최초 표시·종료 완료 때만 다시 읽고, 일반 화면 갱신에서는 작성 중인 입력을 유지합니다.
            loadOptions();
            feedback.setText("실행 전에 설정을 적용하세요.");
        }
        editingEnabled = canEdit;
    }

    private static boolean isEditable(Field field) {
        return field.getAnnotation(OptionField.class).editable();
    }

    private boolean canEditOptions() {
        return boardEditable.getAsBoolean() && !node.isAlive();
    }

    private void loadOptions() {
        try {
            for (var entry : inputs.entrySet()) entry.getValue().setValue(entry.getKey().get(node));
        } catch (IllegalAccessException e) {
            feedback.setText("설정값을 읽을 수 없습니다.");
        }
    }

    // 입력값을 필드 타입으로 변환한 뒤 저장합니다. 노드별 동작 검증은 해당 노드가 담당합니다.
    private void applyOptions() {
        if (!canEditOptions()) { refreshState(); return; }
        try {
            // 숫자 변환에 실패했을 때 일부 필드만 바뀌지 않도록 변환을 모두 마친 뒤 저장합니다.
            Map<Field, Object> values = new LinkedHashMap<>();
            for (var entry : inputs.entrySet()) {
                if (isEditable(entry.getKey())) values.put(entry.getKey(), entry.getValue().getValue());
            }
            // start()와 같은 잠금을 사용해 최종 확인과 저장 사이에 노드가 시작되지 않게 합니다.
            synchronized (node) {
                if (!canEditOptions()) { refreshState(); return; }
                for (var entry : values.entrySet()) entry.getKey().set(node, entry.getValue());
            }
            feedback.setText("적용했습니다.");
        } catch (OptionInput.InvalidValueException e) {
            feedback.setText(e.getMessage()); // 범위·타입 안내문. 입력 원문은 들어 있지 않습니다.
        } catch (IllegalArgumentException | IllegalAccessException e) {
            // 숫자 형식 오류 메시지에는 입력 원문(웹훅 토큰일 수 있음)이 포함되므로 그대로 표시하지 않습니다.
            feedback.setText("입력값과 필드 타입을 확인하세요.");
        }
    }
}
