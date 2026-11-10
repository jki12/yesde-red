package node.functional;

import lombok.Getter;
import lombok.Setter;
import node.BaseNode;
import node.Inputable;
import node.Message;
import node.NodeType;
import node.Outputable;
import node.option.OptionField;
import node.option.OptionInputType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Selector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * payload의 body(HTML 또는 XML/RSS)에서 CSS 선택자로 값을 뽑습니다.
 * 항목 선택자에 맞는 요소마다 메시지 하나를 내보내고, 필드는 "이름 = 선택자 [@속성]" 줄로 정의합니다.
 * 예) title = h2 a / link = h2 a @href / price = .price
 */
@Getter
@Setter
public class HtmlExtractNode extends BaseNode implements Inputable, Outputable {
    @OptionField(label = "항목 선택자 (CSS, 비우면 문서 전체가 한 항목)")
    private String selector;
    @OptionField(type = OptionInputType.TEXT_AREA, label = "필드 (줄마다 이름 = 선택자 [@속성])")
    private String fields;
    // HTML 파서는 <link>를 빈 태그로 취급해 RSS의 링크 본문이 사라집니다. RSS/XML은 XML 파서로 읽어야 합니다.
    @OptionField(type = OptionInputType.CHECK_BOX, label = "XML/RSS로 파싱")
    private boolean xml;

    public HtmlExtractNode(String id) {
        super(NodeType.HTML_EXTRACT, id);
    }

    @Override
    protected void process() throws Exception {
        Message in = getInbox().take();
        String body = in.getString(HttpRequestNode.BODY);
        if (body == null) {
            warn("payload에 body가 없습니다. HTTP 요청 노드 뒤에 연결하세요.");
            return;
        }

        Map<String, String> mappings;
        try {
            mappings = FieldMappings.parse(fields);
        } catch (IllegalArgumentException e) {
            warn("필드 옵션 오류: " + e.getMessage());
            return;
        }

        String baseUrl = in.getString(HttpRequestNode.URL);
        Document document = xml
                ? Jsoup.parse(body, baseUrl == null ? "" : baseUrl, Parser.xmlParser())
                : Jsoup.parse(body, baseUrl == null ? "" : baseUrl);
        try {
            for (Element item : items(document)) {
                Map<String, Object> extracted = new LinkedHashMap<>(in.getPayload());
                extracted.remove(HttpRequestNode.BODY); // 본문은 크고 더 이상 필요 없습니다.
                for (Map.Entry<String, String> mapping : mappings.entrySet()) {
                    extracted.put(mapping.getKey(), extract(item, mapping.getValue()));
                }
                send(new Message(extracted));
            }
        } catch (Selector.SelectorParseException e) {
            warn("선택자 오류: " + e.getMessage());
        }
    }

    private List<Element> items(Document document) {
        if (selector == null || selector.isBlank()) return List.of(document);
        return new ArrayList<>(document.select(selector));
    }

    // "선택자 @속성" 형식. 속성이 없으면 텍스트, href/src 같은 URL 속성은 절대 주소로 바꿉니다.
    private static String extract(Element scope, String expression) {
        String css = expression;
        String attribute = null;
        int at = expression.lastIndexOf('@');
        if (at >= 0) {
            css = expression.substring(0, at).strip();
            attribute = expression.substring(at + 1).strip();
        }
        Element element = css.isEmpty() ? scope : scope.selectFirst(css);
        if (element == null) return null;
        if (attribute == null) return element.text();
        String absolute = element.absUrl(attribute);
        return absolute.isEmpty() ? element.attr(attribute) : absolute;
    }
}
