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
        JTabbedPane contentPane = YesdeRedFrame.getInstance().getContentPane();
        int selectedIndex = contentPane.getSelectedIndex();

        return new TabInfo(selectedIndex, ((BoardPanel) contentPane.getComponentAt(selectedIndex)));
    }
}
