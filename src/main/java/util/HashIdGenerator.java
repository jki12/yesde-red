package util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Supplier;

public class HashIdGenerator implements Supplier<String> {
    private static final String HASH_ALGORITHM = "sha-1";

    private MessageDigest messageDigest;

    public HashIdGenerator() {
        try {
            messageDigest = MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException ignore) {
            // 절대 발생할 수 없는 예외입니다.
        }
    }

    @Override
    public String get() {
        long now = System.currentTimeMillis();

        while (now != 0) {
            messageDigest.update((byte) (now % 10));
            now /= 10;
        }

        StringBuilder sb = new StringBuilder();
        for (byte b : messageDigest.digest()) {
            sb.append(Integer.toHexString(0xff & b)); // 음수 처리.
        }

        return sb.toString();
    }
}
