package ui.theme;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

/** 자주 쓰는 Swing 리스너를 짧게 등록하는 도우미입니다. */
public final class Listeners {
    private Listeners() { }

    /** 입력란의 내용이 바뀔 때마다 action을 실행합니다. 삽입·삭제·속성 변경을 구분하지 않습니다. */
    public static void onTextChange(JTextComponent text, Runnable action) {
        text.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { action.run(); }
            @Override public void removeUpdate(DocumentEvent e) { action.run(); }
            @Override public void changedUpdate(DocumentEvent e) { action.run(); }
        });
    }
}
