package content;

import annotation.OptionField;
import node.BaseNode;
import ui.UiTheme;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.Dimension;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** @OptionField가 붙은 필드를 찾아 입력란을 만들고, 적용할 때 해당 필드에 값을 저장합니다. */
public final class OptionSettingsEditor {
    private final BaseNode node;
    private final BooleanSupplier boardEditable;
    private boolean editingEnabled;
    private final JPanel panel = new JPanel();
    private final Map<Field, JComponent> inputs = new LinkedHashMap<>();
    private final JButton apply = UiTheme.button("적용");
    private final JLabel feedback = UiTheme.label("실행 전에 설정을 적용하세요.", false);

    public OptionSettingsEditor(BaseNode node, BooleanSupplier boardEditable) {
        this.node = node;
        this.boardEditable = boardEditable;
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        // 1. 현재 클래스와 부모 클래스에서 어노테이션이 붙은 변경 가능한 필드를 찾습니다.
        for (Class<?> type = node.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                OptionField option = field.getAnnotation(OptionField.class);
                if (option == null || Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) continue;
                field.setAccessible(true);
                JComponent input = createInput(field, option);
                input.setName("option." + field.getName());
                inputs.put(field, input);
                JLabel label = UiTheme.label(field.getName(), false);
                label.setLabelFor(input);
                panel.add(label);
                panel.add(Box.createVerticalStrut(4));
                JComponent view = input instanceof JTextArea ? new JScrollPane(input) : input;
                int height = input instanceof JTextArea ? 130 : input instanceof JPanel ? field.getType().getEnumConstants().length * 30 : 32;
                view.setPreferredSize(new Dimension(180, height));
                view.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
                view.setAlignmentX(JComponent.LEFT_ALIGNMENT);
                panel.add(view);
                panel.add(Box.createVerticalStrut(10));
            }
        }
        apply.setName("applyNodeSettings");
        apply.addActionListener(e -> applySettings());
        panel.add(apply);
        panel.add(Box.createVerticalStrut(8));
        feedback.setName("nodeSettingsFeedback");
        panel.add(feedback);
        refreshState();
    }

    // 2. 어노테이션의 type으로 Swing 입력 컴포넌트를 선택합니다.
    private JComponent createInput(Field field, OptionField option) {
        return switch (option.type()) {
            case TEXT -> new JTextField();
            case TEXT_AREA -> {
                JTextArea area = new JTextArea();
                area.setFont(UiTheme.getFont());
                yield area;
            }
            case SENSITIVE_TEXT -> new JPasswordField();
            case CHECK_BOX -> { JCheckBox box = new JCheckBox(); box.setOpaque(false); yield box; }
            case RADIO_BUTTON -> {
                JPanel choices = new JPanel();
                choices.setLayout(new BoxLayout(choices, BoxLayout.Y_AXIS));
                choices.setOpaque(false);
                ButtonGroup group = new ButtonGroup();
                for (Object value : field.getType().getEnumConstants()) {
                    JRadioButton button = new JRadioButton(((Enum<?>) value).name());
                    button.putClientProperty("value", value);
                    button.setOpaque(false);
                    group.add(button);
                    choices.add(button);
                }
                yield choices;
            }
        };
    }

    public JComponent getComponent() { return panel; }

    public boolean hasOptions() { return !inputs.isEmpty(); }

    public void refreshState() {
        boolean canEdit = canEditSettings();
        inputs.forEach((field, input) -> {
            boolean editable = canEdit && field.getAnnotation(OptionField.class).editable();
            if (input instanceof JTextComponent text) text.setEditable(editable);
            else input.setEnabled(editable);
            if (input instanceof JPanel choices) {
                for (var button : choices.getComponents()) button.setEnabled(editable);
            }
        });
        apply.setEnabled(canEdit && inputs.keySet().stream().anyMatch(field -> field.getAnnotation(OptionField.class).editable()));
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

    private boolean canEditSettings() {
        return boardEditable.getAsBoolean() && !node.isExecutionActive();
    }

    private void loadSettings() {
        try {
            for (var entry : inputs.entrySet()) {
                Object value = entry.getKey().get(node);
                JComponent input = entry.getValue();
                if (input instanceof JTextComponent text) text.setText(value == null ? "" : value.toString());
                else if (input instanceof JCheckBox box) box.setSelected(Boolean.TRUE.equals(value));
                else if (input instanceof JPanel choices) {
                    for (var component : choices.getComponents()) {
                        JRadioButton button = (JRadioButton) component;
                        button.setSelected(button.getClientProperty("value").equals(value));
                    }
                }
            }
        } catch (IllegalAccessException e) {
            feedback.setText("설정값을 읽을 수 없습니다.");
        }
    }

    // 3. 입력값을 필드 타입으로 변환한 뒤 저장합니다. 노드별 동작 검증은 해당 노드가 담당합니다.
    private void applySettings() {
        if (!canEditSettings()) { refreshState(); return; }
        try {
            Map<Field, Object> values = new LinkedHashMap<>();
            for (var entry : inputs.entrySet()) {
                if (entry.getKey().getAnnotation(OptionField.class).editable()) values.put(entry.getKey(), readInput(entry.getKey(), entry.getValue()));
            }
            // start()와 같은 잠금을 사용해 최종 확인과 저장 사이에 노드가 시작되지 않게 합니다.
            synchronized (node) {
                if (!canEditSettings()) { refreshState(); return; }
                // 숫자 변환에 실패했을 때 일부 필드만 바뀌지 않도록 변환을 모두 마친 뒤 저장합니다.
                for (var entry : values.entrySet()) entry.getKey().set(node, entry.getValue());
            }
            feedback.setText("적용했습니다.");
        } catch (IllegalArgumentException | IllegalAccessException e) {
            // 입력 원문에는 웹훅 토큰이 포함될 수 있으므로 예외 메시지는 그대로 표시하지 않습니다.
            feedback.setText("입력값과 필드 타입을 확인하세요.");
        }
    }

    private Object readInput(Field field, JComponent input) {
        if (input instanceof JCheckBox box) return box.isSelected();
        if (input instanceof JPanel choices) {
            for (var component : choices.getComponents()) {
                JRadioButton button = (JRadioButton) component;
                if (button.isSelected()) return button.getClientProperty("value");
            }
            return null;
        }
        String value;
        if (input instanceof JPasswordField password) {
            char[] chars = password.getPassword();
            try { value = new String(chars); } finally { Arrays.fill(chars, '\0'); }
        } else value = ((JTextComponent) input).getText();
        if (field.getType() == String.class) return value.isEmpty() ? null : value;
        if (field.getType() == int.class || field.getType() == Integer.class) return Integer.valueOf(value.strip());
        if (field.getType() == long.class || field.getType() == Long.class) return Long.valueOf(value.strip());
        throw new IllegalArgumentException("지원하지 않는 필드 타입입니다.");
    }
}
