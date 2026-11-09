package util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Utils {
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^[0-9]*$");
    public static boolean isNumeric(String s) {
        return NUMERIC_PATTERN.matcher(s).matches();
    }
}
