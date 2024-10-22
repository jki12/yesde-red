package util;

import content.BoardPanel;
import frame.YesdeRedFrame;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.swing.*;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Utils {
    private static final long TIMEOUT_DURATION = 5;
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^[0-9]*$");

    public static boolean isNumeric(String s) {
        return NUMERIC_PATTERN.matcher(s).matches();
    }

    public static HttpClient createHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_DURATION))
                .build();
    }

    public static TabInfo getSelectedTabInfo() {
        JTabbedPane contentPane = (JTabbedPane) YesdeRedFrame.getInstance().getContentPane().getComponent(0);
        int selectedIndex = contentPane.getSelectedIndex();

        return new TabInfo(selectedIndex, ((BoardPanel) contentPane.getComponentAt(selectedIndex)));
    }

    /**
     * drag 이벤트 발생시에도 해당 함수가 많이 호출될 가능성이 있어 재귀가 아닌 반복문으로 구현.
     */
    public static long gcd(long x, long y) {
        long temp;

        if (x < y) { // x > y로 가정하기 위해.
            temp = x;
            x = y;
            y = temp;
        }

        while (y != 0) {
            temp = x % y;

            x = y;
            y = temp;
        }

        return x;
    }
}
