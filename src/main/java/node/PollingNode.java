package node;

import node.option.OptionField;
import node.option.OptionInputType;
import com.google.gson.annotations.Expose;
import lombok.Getter;
import lombok.Setter;

/** 입력 없이 정해진 간격마다 스스로 process()를 실행하는 노드입니다. 간격은 옵션으로 조정합니다. */
@Getter
@Setter
public abstract class PollingNode extends BaseNode {
    private static final long DEFAULT_INTERVAL_MILLIS = 1_000;

    @Expose
    @OptionField(type = OptionInputType.TEXT, min = 0)
    private long intervalMillis = DEFAULT_INTERVAL_MILLIS;

    protected PollingNode(NodeType type) {
        super(type);
    }

    /** 음수 간격은 Thread.sleep에서 예외를 일으켜 노드가 조용히 멈추므로 코드 경로에서도 막습니다. */
    public void setIntervalMillis(long intervalMillis) {
        if (intervalMillis < 0) throw new IllegalArgumentException("intervalMillis은(는) 0 이상이어야 합니다.");
        this.intervalMillis = intervalMillis;
    }

    @Override
    protected void awaitNextCycle() throws InterruptedException {
        Thread.sleep(intervalMillis);
    }
}
