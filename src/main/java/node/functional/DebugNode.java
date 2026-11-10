package node.functional;

import ui.console.ConsolePanel;
import ui.YesdeRedFrame;
import node.BaseNode;
import node.Inputable;
import node.Message;
import node.NodeType;

public class DebugNode extends BaseNode implements Inputable {
    // 생성 시점이 아니라 실행 시작 때 조회합니다. 프레임 없이(불러오기·테스트) 노드를 만들 수 있어야 합니다.
    private ConsolePanel console;

    public DebugNode() {
        super(NodeType.DEBUG);
    }

    /** 저장된 워크플로를 불러올 때처럼 ID가 정해져 있는 경우에 씁니다. */
    public DebugNode(String id) {
        super(NodeType.DEBUG, id);
    }

    @Override
    protected void onStart() {
        super.onStart();
        console = YesdeRedFrame.getInstance().getConsole();
    }

    @Override
    protected void process() throws Exception {
        Message message = getInbox().take();

        console.append("DEBUG " + getId(), message.toString()); // EDT에서 모델을 바꾸므로 테이블이 스스로 다시 그립니다.
    }
}
