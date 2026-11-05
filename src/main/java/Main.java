// [빌드] 원본의 frame 패키지 대신 실제 파일이 선언된 ui.frame을 참조합니다.
import ui.frame.YesdeRedFrame;
import ui.UiTheme;
import javax.swing.SwingUtilities;

public class Main {

    public static void main(String[] args) throws Exception {
        // HttpRequestBuilderImpl checkNameAndValue() restrictedHeaders 확인.
        // System.setProperty("jdk.httpclient.allowRestrictedHeaders", "connection,content-length,expect,host,upgrade");

        SwingUtilities.invokeLater(() -> {
            UiTheme.install();
            YesdeRedFrame.getInstance().setVisible(true);
        });
    }
}
