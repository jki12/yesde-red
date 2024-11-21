package node;

/**
 * marker 인터페이스 입니다.
 */
public interface Outputable {

    default void connect(Inputable input) {
        ((InputOutputNode) this).tryConnect((InputOutputNode) input);
    }
}
