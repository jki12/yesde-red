package util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import json.WorkflowNodeAdapter;
import json.WorkflowNodeSetAdapter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.HttpRequestPacket;
import node.BaseNode;
import content.BoardPanel;
import ui.frame.YesdeRedFrame;
import ui.menu.NodePanel;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Utils {
    private static final long TIMEOUT_DURATION = 5;
    // (ip, tcp, http)."로 시작하는 key, value 값, _index(패킷의 시작 지점)를 찾는 정규식.
    private static final String PACKET_DATA_REGEX = "\"ip\\..[^:]*\": \".*?\"|\"tcp\\..[^:]*\": \".*?\"|\"http\\..[^:]*: \".*\"|_index";
    private static final Pattern PACKET_DATA_PATTERN = Pattern.compile(PACKET_DATA_REGEX);
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^[0-9]*$");
    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .excludeFieldsWithoutExposeAnnotation()
            .registerTypeAdapter(NodePanel.class, new WorkflowNodeAdapter())
            .registerTypeAdapter(new TypeToken<Set<BaseNode>>() {}.getType(), new WorkflowNodeSetAdapter())
            .create();

    public static boolean isNumeric(String s) {
        return NUMERIC_PATTERN.matcher(s).matches();
    }

    public static HttpClient createHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_DURATION))
                .build();
    }

    public static TabInfo getSelectedTabInfo() {
        JTabbedPane contentPane = YesdeRedFrame.getInstance().getTabs();
        int selectedIndex = contentPane.getSelectedIndex();

        return new TabInfo(selectedIndex, ((BoardPanel) contentPane.getComponentAt(selectedIndex)));
    }

    public static String toJson(Object object) {
        return GSON.toJson(object);
    }

    /**
     * json 형태의 wireshark packet 데이터를 파싱해 리스트 형태로 반환.
     */
    public static List<HttpRequestPacket> parsePacketCapture(File file) {
        StringBuilder sb = new StringBuilder();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String s;

            while ((s = br.readLine()) != null) {
                sb.append(s).append('\n');
            }

        } catch (Exception e) {
            log.error(e.getMessage());
            throw new RuntimeException(e);
        }

        return parsePacketCapture(sb.toString());
    }

    /**
     * json 형태의 wireshark packet 데이터를 파싱해 리스트 형태로 반환.
     * (중첩된 json의 깊이를 1로 전처리 후 파싱)
     */
    public static List<HttpRequestPacket> parsePacketCapture(String json) {
        StringBuilder sb = new StringBuilder();
        Matcher matcher = PACKET_DATA_PATTERN.matcher(json);

        List<Map<String, String>> headers = new ArrayList<>(); // TODO, 올바르게 맵핑이 되는지 확인.
        Map<String, String> header = null;

        int count = 0;
        sb.append("[ ");
        while (matcher.find()) {
            String sub = matcher.group()
                    .replaceAll("\\\\[rn]", ""); // `\r\n`, `\n`을 찾는 정규식.

            if (sub.contains("_index")) { // 새로운 packet 값.
                if (count == 0) {
                    sb.append("{ ");
                }
                else {
                    sb.setLength(sb.length() - 1); // ',' 제거.
                    sb.append("}, { ");
                }

                if (header != null) headers.add(header);
                header = new HashMap<>();

                count++;
                continue;
            }

            String duplicate = "\"http.request.line\": "; // value로 header, cookie 정보를 가지고 있음.
            if (sub.contains(duplicate)) { // headers 파싱.
                sub = sub.replaceAll(duplicate + "|(?<!\\\\)\"", "") // `\"`가 아닌 `"`만 선택하는 정규식 ex) "sec-ch-ua: \"Chromium\";v=\"128\" ... "
                        .replaceAll("(\\\\\")", "\""); // `\"`만 선택하는 정규식

                String[] keyValue = sub.split(": ");
                header.put(keyValue[0], keyValue[1]);

                continue;
            }

            sb.append(sub)
                    .append(',');
        }
        headers.add(header);
        sb.setLength(sb.length() - 1); // ',' 제거.
        sb.append('}')
                .append("]");

        List<HttpRequestPacket> requestData = GSON.fromJson(sb.toString(), new TypeToken<>() {
        });

        if (headers.size() != count) throw new IllegalStateException();

        for (int i = 0; i < count; ++i) {
            requestData.get(i).setHeaders(headers.get(i));
        }

        return requestData;
    }
}
