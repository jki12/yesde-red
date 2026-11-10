package node.functional;

import lombok.Getter;
import lombok.Setter;
import node.BaseNode;
import node.Message;
import node.NodeType;
import node.Outputable;
import node.option.OptionField;
import node.option.OptionInputType;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * cron 식에 맞는 시각마다 메시지를 보냅니다. 메시지 주입 노드가 "N초마다"라면 이 노드는 "매시 정각", "평일 9시"입니다.
 * 시각은 이 컴퓨터의 시간대 기준입니다. cron 식 오류는 시작 시 노드 실패입니다.
 */
@Getter
@Setter
public class ScheduleNode extends BaseNode implements Outputable {
    @OptionField(label = "cron 식 (분 시 일 월 요일, 예: */10 * * * *)")
    private String cron = "*/10 * * * *";
    @OptionField(type = OptionInputType.TEXT_AREA, label = "메시지 (비우면 실행 시각)")
    private String message;
    @OptionField(type = OptionInputType.CHECK_BOX, label = "시작할 때 한 번 바로 실행")
    private boolean runOnStart = true;

    private Cron compiled;
    private ZonedDateTime nextRunAt; // 다음에 보낼 시각. 화면 표시와 테스트에 씁니다.

    public ScheduleNode(String id) {
        super(NodeType.SCHEDULE, id);
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            compiled = Cron.parse(cron);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("cron 식 오류: " + e.getMessage(), e);
        }
        ZonedDateTime now = ZonedDateTime.now();
        nextRunAt = runOnStart ? now : nextOrFail(now);
    }

    @Override
    protected void process() throws Exception {
        ZonedDateTime due = nextRunAt;
        long wait = Duration.between(ZonedDateTime.now(), due).toMillis();
        if (wait > 0) Thread.sleep(wait); // stop()의 인터럽트로 깨어나 종료됩니다.
        send(new Message(message == null || message.isBlank() ? due.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : message));
        nextRunAt = nextOrFail(due);
    }

    private ZonedDateTime nextOrFail(ZonedDateTime after) {
        ZonedDateTime next = compiled.next(after);
        if (next == null) throw new IllegalStateException("cron 식에 맞는 시각이 1년 안에 없습니다: " + cron);
        return next;
    }
}
