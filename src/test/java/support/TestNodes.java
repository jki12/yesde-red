package support;

import node.BaseNode;
import node.Inputable;
import node.Message;
import node.NodeFactory;
import node.Outputable;
import node.PollingNode;
import node.Type;

import javax.swing.Icon;
import java.util.concurrent.atomic.AtomicInteger;

/** 네트워크나 프레임에 의존하지 않는 테스트용 노드입니다. ID를 고정해 카드·연결선 대응을 읽기 쉽게 합니다. */
public final class TestNodes {
    private TestNodes() { }

    /** 간격마다 "m1", "m2", … 메시지를 보내는 폴링 출력 노드입니다. */
    public static final class Source extends PollingNode implements Outputable {
        private final String id;
        public final AtomicInteger sent = new AtomicInteger();

        public Source(String id) { super(Type.INJECT); this.id = id; }

        @Override public String getId() { return id; }
        @Override public Icon getIcon() { return NodeFactory.getIcon(getType()); }
        @Override public String getDisplayName() { return "테스트 출력"; }
        @Override public String getDescription() { return "테스트용 출력 노드"; }
        @Override protected void process() { send(new Message("m" + sent.incrementAndGet())); }

        /** 워커 스레드를 거치지 않고 메시지 하나를 바로 보냅니다. */
        public void emit() { process(); }
    }

    /** 메시지를 받으면 예외를 던져 멈추는 입력 노드입니다. 노드 실패 처리 검사에 씁니다. */
    public static final class Failing extends BaseNode implements Inputable {
        private final String id;

        public Failing(String id) { super(Type.DEBUG); this.id = id; }

        @Override public String getId() { return id; }
        @Override public Icon getIcon() { return NodeFactory.getIcon(getType()); }
        @Override public String getDisplayName() { return "테스트 실패"; }
        @Override public String getDescription() { return "메시지를 받으면 예외를 던지는 테스트 노드"; }
        @Override protected void process() throws Exception {
            getIn().take();
            throw new IllegalStateException("boom");
        }
    }

    /** 받은 메시지 수만 세는 입력 노드입니다. */
    public static final class Sink extends BaseNode implements Inputable {
        private final String id;
        public final AtomicInteger seen = new AtomicInteger();

        public Sink(String id) { super(Type.DEBUG); this.id = id; }

        @Override public String getId() { return id; }
        @Override public Icon getIcon() { return NodeFactory.getIcon(getType()); }
        @Override public String getDisplayName() { return "테스트 입력"; }
        @Override public String getDescription() { return "테스트용 입력 노드"; }
        @Override protected void process() throws Exception { getIn().take(); seen.incrementAndGet(); }
    }
}
