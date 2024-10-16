package util;

import content.BoardPanel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class TabInfo {
    private final int selectedIndex;
    private final BoardPanel board;
}
