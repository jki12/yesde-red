package workflow;

/** 워크플로(보드) 하나의 실행 상태입니다. STOPPING은 종료 요청 후 모든 노드 스레드가 끝나기 전까지의 상태입니다. */
public enum RunState { IDLE, RUNNING, STOPPING }
