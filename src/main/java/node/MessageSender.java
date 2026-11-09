package node;

/** 입력 가능한 노드에 연결하는 출력 동작을 정의합니다. */
public interface MessageSender {
    /** 새 연결을 추가하면 true, 연결할 수 없거나 이미 연결되어 있으면 false를 반환합니다. */
    boolean connect(MessageReceiver input);
}
