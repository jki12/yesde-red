import frame.YesdeRedFrame;

public class Main {

    public static void main(String[] args) throws Exception {
        // HttpRequestBuilderImpl checkNameAndValue() restrictedHeaders 확인.
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "connection,content-length,expect,host,upgrade");

        YesdeRedFrame f = YesdeRedFrame.getInstance();
        f.setVisible(true);
    }
}