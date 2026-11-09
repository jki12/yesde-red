package ui.inspector;

import annotation.OptionField;
import ui.theme.UiTheme;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@link OptionField} 하나를 편집하는 입력 컴포넌트입니다.
 * 화면 표시, 값 읽기·쓰기, 편집 가능 전환을 입력 종류별로 한곳에 모아 둡니다.
 */
abstract class OptionInput {
    private static final int DEFAULT_HEIGHT = 32;

    /** 어노테이션의 type에 맞는 입력 컴포넌트를 만듭니다. */
    static OptionInput create(Field field, OptionField option) {
        return switch (option.type()) {
            case TEXT -> new TextInput(field, option, new JTextField());
            case TEXT_AREA -> {
                JTextArea area = new JTextArea();
                area.setFont(UiTheme.getFont());
                yield new TextInput(field, option, area);
            }
            case SENSITIVE_TEXT -> new TextInput(field, option, new JPasswordField());
            case CHECK_BOX -> new CheckBoxInput();
            case RADIO_BUTTON -> new RadioInput(field.getType());
        };
    }

    /** 값을 편집하는 컴포넌트입니다. 이름(setName)과 라벨 연결에 사용합니다. */
    abstract JComponent getEditor();

    /** 설정 패널에 배치할 컴포넌트입니다. 여러 줄 입력은 스크롤 패널로 감싸져 있어 편집 컴포넌트와 다를 수 있습니다. */
    JComponent getView() {
        return getEditor();
    }

    int getPreferredHeight() {
        return DEFAULT_HEIGHT;
    }

    abstract void setEditable(boolean editable);

    /** 필드의 현재 값을 화면에 반영합니다. */
    abstract void setValue(Object value);

    /**
     * 화면의 입력을 필드 타입의 값으로 변환합니다.
     * 형식이 틀리면 NumberFormatException 등 일반 IllegalArgumentException을, 범위를 벗어나거나 지원하지 않는 타입이면
     * 사용자에게 그대로 보여줄 수 있는 메시지를 담은 {@link InvalidValueException}을 던집니다.
     */
    abstract Object getValue();

    /** 메시지에 입력 원문이 들어가지 않아 화면에 바로 표시해도 되는 입력 오류입니다. */
    static final class InvalidValueException extends IllegalArgumentException {
        InvalidValueException(String message) { super(message); }
    }

    /** 한 줄·여러 줄·비밀 문자열 입력. String, int/Integer, long/Long 필드를 지원합니다. */
    private static final class TextInput extends OptionInput {
        private static final int TEXT_AREA_HEIGHT = 130;

        private final String name;
        private final Class<?> type;
        private final long min;
        private final JTextComponent text;
        private final JComponent view;

        TextInput(Field field, OptionField option, JTextComponent text) {
            this.name = field.getName();
            this.type = field.getType();
            this.min = option.min();
            this.text = text;
            this.view = text instanceof JTextArea ? new JScrollPane(text) : text;
        }

        @Override JComponent getEditor() { return text; }
        @Override JComponent getView() { return view; }
        @Override int getPreferredHeight() { return text instanceof JTextArea ? TEXT_AREA_HEIGHT : DEFAULT_HEIGHT; }
        @Override void setEditable(boolean editable) { text.setEditable(editable); }
        @Override void setValue(Object value) { text.setText(value == null ? "" : value.toString()); }

        @Override Object getValue() {
            String value = read();
            if (type == String.class) return value.isEmpty() ? null : value;
            if (type == int.class || type == Integer.class) return atLeastMin(Integer.valueOf(value.strip()));
            if (type == long.class || type == Long.class) return atLeastMin(Long.valueOf(value.strip()));
            throw new InvalidValueException(name + ": 지원하지 않는 필드 타입입니다.");
        }

        private <N extends Number> N atLeastMin(N number) {
            if (number.longValue() < min) throw new InvalidValueException(name + "은(는) " + min + " 이상이어야 합니다.");
            return number;
        }

        private String read() {
            if (!(text instanceof JPasswordField password)) return text.getText();
            char[] chars = password.getPassword();
            try { return new String(chars); } finally { Arrays.fill(chars, '\0'); }
        }
    }

    private static final class CheckBoxInput extends OptionInput {
        private final JCheckBox box = new JCheckBox();

        CheckBoxInput() { box.setOpaque(false); }

        @Override JComponent getEditor() { return box; }
        @Override void setEditable(boolean editable) { box.setEnabled(editable); }
        @Override void setValue(Object value) { box.setSelected(Boolean.TRUE.equals(value)); }
        @Override Object getValue() { return box.isSelected(); }
    }

    /** 열거형 필드의 상수마다 라디오 버튼 하나를 세로로 놓습니다. */
    private static final class RadioInput extends OptionInput {
        private static final int ROW_HEIGHT = 30;

        private final JPanel choices = new JPanel();
        private final List<JRadioButton> buttons = new ArrayList<>();
        private final Object[] values;

        RadioInput(Class<?> enumType) {
            values = enumType.getEnumConstants();
            choices.setLayout(new BoxLayout(choices, BoxLayout.Y_AXIS));
            choices.setOpaque(false);
            ButtonGroup group = new ButtonGroup();
            for (Object value : values) {
                JRadioButton button = new JRadioButton(((Enum<?>) value).name());
                button.setOpaque(false);
                group.add(button);
                buttons.add(button);
                choices.add(button);
            }
        }

        @Override JComponent getEditor() { return choices; }
        @Override int getPreferredHeight() { return values.length * ROW_HEIGHT; }
        @Override void setEditable(boolean editable) {
            choices.setEnabled(editable);
            for (JRadioButton button : buttons) button.setEnabled(editable);
        }
        @Override void setValue(Object value) {
            for (int i = 0; i < buttons.size(); i++) buttons.get(i).setSelected(values[i].equals(value));
        }
        @Override Object getValue() {
            for (int i = 0; i < buttons.size(); i++) if (buttons.get(i).isSelected()) return values[i];
            return null;
        }
    }
}
