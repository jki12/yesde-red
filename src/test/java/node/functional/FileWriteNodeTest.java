package node.functional;

import node.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileWriteNodeTest {

    private static void write(FileWriteNode node, Message... messages) throws Exception {
        for (Message message : messages) node.receive(message);
        node.start();
        long deadline = System.currentTimeMillis() + 5_000;
        while (!node.getInbox().isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10);
        Thread.sleep(50); // 마지막 메시지의 쓰기가 끝날 시간
        node.stop();
        node.getThread().join(2_000);
    }

    @Test
    void writesCsvWithHeaderOnceAndRfc4180Quoting(@TempDir Path dir) throws Exception {
        FileWriteNode node = new FileWriteNode("file01");
        node.setPath(dir.resolve("out/items.csv").toString()); // 없는 하위 폴더도 만듦
        node.setFields("name\nprice\nnote");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("name", "콤마, 포함"); second.put("price", null); second.put("note", "따옴표 \"안\"\n줄바꿈"); second.put("extra", "ignored");

        write(node, new Message(Map.of("name", "a", "price", 10, "note", "plain")), new Message(second));

        List<String> lines = Files.readAllLines(dir.resolve("out/items.csv"), StandardCharsets.UTF_8);
        assertEquals("name,price,note", lines.get(0));
        assertEquals("a,10,plain", lines.get(1));
        assertEquals("\"콤마, 포함\",,\"따옴표 \"\"안\"\"", lines.get(2), "따옴표는 두 번, null은 빈칸");
        assertEquals("줄바꿈\"", lines.get(3), "줄바꿈이 든 값은 따옴표 안에서 이어짐");
        assertEquals(4, lines.size());
    }

    @Test
    void appendsWithoutRepeatingTheHeaderAndTruncatesWhenAskedTo(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("log.csv");
        FileWriteNode first = new FileWriteNode("file01");
        first.setPath(file.toString());
        write(first, new Message(Map.of("k", 1)));

        FileWriteNode second = new FileWriteNode("file02");
        second.setPath(file.toString());
        write(second, new Message(Map.of("k", 2)));
        assertEquals(List.of("k", "1", "2"), Files.readAllLines(file), "이어 쓰기는 머리글을 다시 쓰지 않음");

        FileWriteNode third = new FileWriteNode("file03");
        third.setPath(file.toString());
        third.setAppend(false);
        write(third, new Message(Map.of("k", 3)));
        assertEquals(List.of("k", "3"), Files.readAllLines(file), "이어 쓰기를 끄면 새로 씀");
    }

    @Test
    void writesJsonLinesUsingAllFieldsOfTheFirstMessage(@TempDir Path dir) throws Exception {
        FileWriteNode node = new FileWriteNode("file01");
        node.setPath(dir.resolve("items.jsonl").toString());
        node.setFormat(FileWriteNode.Format.JSONL);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "a"); row.put("tags", List.of("x", "y"));

        write(node, new Message(row), new Message(Map.of("name", "b")));

        List<String> lines = Files.readAllLines(dir.resolve("items.jsonl"));
        assertEquals("{\"name\":\"a\",\"tags\":[\"x\",\"y\"]}", lines.get(0));
        assertEquals("{\"name\":\"b\"}", lines.get(1), "첫 메시지의 열만 쓰고 없는 값은 생략");
    }

    @Test
    void emptyPathIsANodeFailureNotAWarning(@TempDir Path dir) throws Exception {
        FileWriteNode node = new FileWriteNode("file01");
        node.start();
        node.getThread().join(2_000);
        assertNotNull(node.getFailure());
        assertTrue(node.getFailure().getMessage().contains("파일 경로"));
    }
}
