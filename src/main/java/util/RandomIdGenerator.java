package util;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 소문자 16진수 6자리 ID를 만듭니다. 같은 생성기에서 나온 ID는 중복되지 않습니다.
 * 이전 구현은 현재 밀리초를 해시해서 같은 밀리초에 만든 노드가 같은 ID를 받았습니다.
 */
public final class RandomIdGenerator implements Supplier<String> {
    public static final int LENGTH = 6;

    private final SecureRandom random = new SecureRandom();
    private final Set<String> issued = new HashSet<>();

    /** 밖에서 들어온 ID(불러온 워크플로 등)를 발급된 것으로 기록해 새 ID와 겹치지 않게 합니다. */
    public synchronized void reserve(String id) {
        issued.add(id);
    }

    @Override
    public synchronized String get() {
        while (true) {
            String id = String.format("%0" + LENGTH + "x", random.nextInt(1 << (4 * LENGTH)));
            if (issued.add(id)) return id;
        }
    }
}
