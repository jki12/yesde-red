package node;

import annotation.OptionField;
import com.google.gson.annotations.Expose;
import common.enums.FieldType;
import lombok.Getter;
import lombok.Setter;

/** 입력 없이 정해진 간격마다 스스로 process()를 실행하는 노드입니다. 간격은 옵션으로 조정합니다. */
@Getter
@Setter
public abstract class PollingNode extends BaseNode {
    private static final long DEFAULT_INTERVAL_MILLI = 1_000;

    @Expose
    @OptionField(type = FieldType.TEXT)
    private long intervalMilli = DEFAULT_INTERVAL_MILLI;

    protected PollingNode(Type type) {
        super(type);
    }

    @Override
    protected void pause() throws InterruptedException {
        Thread.sleep(intervalMilli);
    }
}
