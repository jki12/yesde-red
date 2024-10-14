package util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import node.BaseNode;
import node.Type;
import org.apache.tika.utils.StringUtils;
import org.reflections.Reflections;

import java.io.File;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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


}
