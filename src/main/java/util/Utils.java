package util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import content.BoardPanel;
import frame.YesdeRedFrame;
import json.FlowNodeAdapter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import menu.FlowNode;
import net.HttpRequestPacket;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final Gson GSON = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .registerTypeAdapter(FlowNode.class, new FlowNodeAdapter())
            .setPrettyPrinting()
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
        JTabbedPane contentPane = (JTabbedPane) YesdeRedFrame.getInstance().getContentPane().getComponent(0);
        int selectedIndex = contentPane.getSelectedIndex();

        return new TabInfo(selectedIndex, ((BoardPanel) contentPane.getComponentAt(selectedIndex)));
    }

    public static String toJson(Object object) {
        return GSON.toJson(object);
    }

    /**
     * drag 이벤트 발생시에도 해당 함수가 많이 호출될 가능성이 있어 재귀가 아닌 반복문으로 구현.
     */
    public static long gcd(long x, long y) {
        long temp;

        if (x < y) { // x > y로 가정하기 위해.
            temp = x;
            x = y;
            y = temp;
        }

        while (y != 0) {
            temp = x % y;

            x = y;
            y = temp;
        }

        return x;
    }

    /**
     * Point a, b 좌표를 기준으로 3차 함수의 계수를 구합니다.
     */
    @Deprecated
    public static double[] gaussianElimination(Point a, Point b) {
        double[][] matrix = {
                { Math.pow(a.x, 3), Math.pow(a.x, 2), a.x, 1, a.y },
                { Math.pow(b.x, 3), Math.pow(b.x, 2), b.x, 1, b.y },
                { 0, 3 * Math.pow(a.x, 2), 2 * a.x, a.x, 0 },
                { 0, 3 * Math.pow(b.x, 2), 2 * b.x, b.x, 0 }
        };

        double temp;
        for (int i = 0; i < matrix.length; ++i) {
            double[] cur = matrix[i];

            // leading 1을 만들어준다.
            if (cur[i] != 1) {
                for (int j = cur.length - 1; j >= 0; --j) cur[j] /= cur[i];
            }

            // 기약 사다리꼴을 만들기 위한 로직.
            for (int j = i + 1; j < matrix.length; ++j) {
                if (matrix[j][i] == 0) continue;

                temp = matrix[j][i];
                for (int k = 0; k < cur.length; ++k) {
                    matrix[j][k] -= (cur[k] * temp);
                }
            }
        }

        // 기약 사다리꼴을 만들기 위한 로직, 이미 leading 1이 만들어져 있으므로 기약 사다리꼴을 만들기 위한 로직만 있으면 됨.
        for (int i = matrix.length - 1; i >= 0; --i) {
            double[] cur = matrix[i];

            for (int j = i - 1; j >= 0; --j) {
                if (matrix[j][i] == 0) continue;

                temp = matrix[j][i];
                for (int k = cur.length - 1; k >= 0; --k) {
                    matrix[j][k] -= (cur[k] * temp);
                }
            }
        }

        double[] result = new double[matrix.length];
        for (int i = 0; i < matrix.length; ++i) result[i] = matrix[i][matrix[0].length - 1];

        return result;
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
