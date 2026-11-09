package node;

/** 메시지를 받을 수 있는 노드의 입력 동작을 정의합니다. */
public interface MessageReceiver {
    void receive(Message message);
}
