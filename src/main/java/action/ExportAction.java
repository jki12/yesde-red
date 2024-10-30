package action;

import lombok.extern.slf4j.Slf4j;
import util.TabInfo;
import util.Utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

@Slf4j
public class ExportAction extends AbstractAction {

    public ExportAction() {
        super("export");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        TabInfo tabInfo = Utils.getSelectedTabInfo();

        for (Component c : tabInfo.getBoard().getComponents()) { // TODO, file에 쓰는 로직 작성.
            log.info(Utils.toJson(c));
        }

        // TODO, json 형태로 추출하는 코드, 저장 위치 정하는 file chooser처럼 dialog 구현.
        log.info("{} 보드가 추출되었습니다. (index : {})", tabInfo.getBoard().getName(), tabInfo.getSelectedIndex());
    }
}
