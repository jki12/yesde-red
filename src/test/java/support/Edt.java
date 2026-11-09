package support;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;

/** 테스트 본문을 EDT에서 실행하고, 그 안에서 난 단언 실패·예외를 호출한 테스트 스레드로 그대로 전달합니다. */
public final class Edt {
    private Edt() { }

    public interface Action {
        void run() throws Exception;
    }

    public static void run(Action action) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    action.run();
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error error) throw error;
            if (cause instanceof Exception exception) throw exception;
            throw e;
        }
    }
}
