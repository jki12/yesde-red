# yesde-red

노드를 연결해 데이터를 수집하는 데스크톱 도구입니다. 보드에 노드 카드를 놓고 포트를 끌어 연결한 뒤 실행하면,
각 노드가 자기 스레드에서 메시지를 받아 처리하고 다음 노드로 넘깁니다. Java 17 + Swing(FlatLaf)으로 만들었습니다.

```
[메시지 주입] → [HTTP 요청] → [HTML 추출 | JSON 추출] → [파일 저장]
                                                   ↘ [디버그] (콘솔에서 확인)
```

## 실행

- JDK 17 이상, Maven.
- 빌드와 테스트: `mvn clean test`
- 실행: IntelliJ에서 `Main` 실행 구성, 또는 `mvn compile exec:java`(플러그인 추가 시).

## 사용법

| 동작 | 방법 |
|---|---|
| 노드 추가 | 툴바 **노드 추가**, 보드 우클릭, 또는 팝업에서 검색 후 Enter |
| 연결 | 출력 포트(오른쪽 원)에서 다른 카드의 입력 포트(왼쪽 원)로 드래그. 반대 방향으로 끌어도 됩니다 |
| 옵션 | 카드를 선택하면 오른쪽 **노드 속성**에서 편집 → **적용**. 실행 중에는 잠깁니다 |
| 삭제 | 카드 또는 연결선을 선택하고 Delete/Backspace, 또는 우클릭 메뉴 |
| 실행/종료 | 툴바 **실행**·**종료**, 또는 Run 메뉴 |
| 저장/열기 | File 메뉴 **내보내기…** / **불러오기…** (JSON). 불러온 워크플로는 새 탭으로 열립니다 |
| 탭 | ⌘T 새 워크플로, ⌘W 닫기(실행 중이면 닫히지 않음) |

노드가 메시지 하나를 처리하지 못하면(404, 선택자 불일치 등) 콘솔에 **경고**를 남기고 계속 실행합니다.
노드가 예외로 멈추면 **오류**로 기록되고 워크플로 전체가 종료됩니다.

## 노드

| 노드 | 역할 | 주요 옵션 |
|---|---|---|
| 메시지 주입 | 간격마다 메시지를 보냅니다. 흐름의 시작점 | `message`, `intervalMillis` |
| HTTP 요청 | payload의 `url`(없으면 옵션 URL)을 GET으로 호출해 `url`·`status`·`contentType`·`body`를 더합니다 | URL, 헤더, User-Agent, 타임아웃, 요청 사이 최소 간격 |
| HTML 추출 | `body`(HTML 또는 XML/RSS)에서 CSS 선택자로 항목마다 메시지를 만들고 필드를 뽑습니다 | 항목 선택자, 필드(`이름 = 선택자 [@속성]`), XML/RSS로 파싱 |
| JSON 추출 | `body`(JSON)에서 경로로 항목마다 메시지를 만들고 필드를 뽑습니다 | 항목 경로(`data.items[*]`), 필드(`이름 = 경로`) |
| 파일 저장 | 메시지를 CSV 또는 JSON Lines로 한 줄씩 기록합니다 | 파일 경로, 형식, 기록할 필드, 이어 쓰기 |
| 중복 제거 | 기준 필드 값이 처음 보이는 메시지만 통과시킵니다. 기억 파일을 지정하면 다시 실행해도 유지됩니다 | 기준 필드, 최대 개수, 기억 파일 |
| 필드 변환 | 규칙(`대상 = 식`)으로 필드를 뽑고 바꾸고 만들어 출처마다 다른 데이터를 공통 스키마로 맞춥니다 | 변환 규칙, 남길 필드 |
| Mock 서버 | 실행 중 `127.0.0.1`에 HTTP 서버를 띄워 폴더의 파일을 응답으로 내줍니다. 경로 규칙으로 404·503을, 장애 주입으로 N번째 요청 500을 흉내 내고, 요청마다 메시지를 내보냅니다 | 포트, 응답 파일 폴더, 지연, 경로 규칙, 장애 주입 |
| 디버그 | 받은 메시지를 콘솔에 표시합니다 | 없음 |

메시지는 이름 → 값 payload입니다. 각 노드는 받은 payload에 자기 필드를 더해 넘기므로 상류의 `url` 같은 값이 저장 단계까지 남습니다.

### 필드 문법

- HTML: `title = h2 a`(텍스트), `link = h2 a @href`(속성, 상대 주소는 절대 주소로), `price = .price`
- JSON: `name = title`, `price = price.amount`, `first = tags[0]`. 항목 경로 끝의 `[*]`만 배열 전개를 뜻합니다.
- RSS: **XML/RSS로 파싱**을 켜고 항목 선택자 `item`, 필드 `title = title`, `link = link`, `time = pubDate`처럼 태그 이름을 씁니다.
- 줄마다 하나, 빈 줄과 `#` 주석은 무시합니다.

### 변환 규칙 문법 (필드 변환 노드)

줄마다 `대상 = 식`. 규칙은 위에서 아래로 적용되므로 앞 규칙이 만든 필드를 뒤 규칙이 쓸 수 있습니다.

| 식 | 뜻 |
|---|---|
| `"clien"` | 상수 |
| `title` | 다른 필드 복사 |
| `regex(link, "/jirum/(\\d+)")` | 첫 캡처 그룹(없으면 전체 일치). 일치 없으면 null |
| `replace(link, "\\?.*$", "")` | 정규식 전체 치환 |
| `number(hits)` | `"1,000원"` → 1000, `"27.3 k"` → 27300, `"(3)"` → 3. 첫 번째 숫자만 읽음 |
| `format("{source}:{source_post_id}")` | 다른 필드를 끼워 넣은 문자열 |
| `datetime(time, "rfc1123")` | ISO 8601(+09:00)로 정규화. 패턴은 `rfc1123`, `iso`, 또는 `yyyy-MM-dd HH:mm:ss` 같은 DateTimeFormatter 패턴. 시각만 있으면 오늘 날짜 |
| `now()` | 수집 시각 |
| `trim(x)`, `lower(x)`, `upper(x)` | 문자열 정리 |

규칙 문법 오류는 실행 시작 때 노드 실패로, 값 변환 실패(읽을 수 없는 시각 등)는 경고와 함께 그 메시지만 건너뜁니다.
문자열 안의 백슬래시는 `\\`로 씁니다.

### 샘플

`src/main/resources/samples/`의 파일을 **File → 불러오기…**로 열어 바로 실행해 볼 수 있습니다.

- `json-api.json`: GitHub API에서 저장소 정보를 1분마다 받아 `output/github-jsoup.csv`에 기록
- `html-list.json`: Hacker News 첫 페이지 제목·링크를 5분마다 `output/hackernews.csv`에 기록
- `hotdeal.json`: 클리앙 알뜰구매(HTML), 루리웹 핫딜(RSS), 뽐뿌(RSS)를 10분마다 받아, 출처별 필드 변환 노드가 공통 스키마(`id`, `source`, `source_post_id`, `post_url`, `title`, `shop`, `product`, `price`, `category`, `author`, `comments`, `hits`, `posted_at`, `collected_at`)로 맞춘 뒤 중복 제거 노드가 `id` 기준으로 새 글만 골라 `output/hotdeal.csv`에 기록합니다. 본 글은 `output/hotdeal-seen.txt`에 남아 앱을 다시 켜도 유지됩니다. 사이트가 RSS를 제공하면 HTML 목록보다 RSS가 안정적이고 예의 바른 경로입니다

- `mock-hotdeal.json`: 위 핫딜 파이프라인을 **인터넷 없이** 돌립니다. Mock 서버 노드가 `mock-data/`의 가상 게시판(HTML 1, RSS 2)을 `http://127.0.0.1:8099/`로 내주고, 같은 추출·변환·중복 제거 흐름이 `output/mock-hotdeal.csv`에 9건을 씁니다. 새 노드를 만들 때 입력을 고정하는 용도로도 씁니다

수집 대상 사이트의 이용약관과 robots.txt를 확인하고, 요청 사이 간격을 충분히 두세요.

## 저장 형식

```json
{ "version": 1, "name": "워크플로 01",
  "nodes": [ { "id": "3f9a1c", "type": "INJECT", "x": 75, "y": 95, "options": { "message": "hello", "intervalMillis": 1000 } } ],
  "connections": [ { "from": "3f9a1c", "to": "b02e77" } ] }
```

노드 구현이 아니라 `json.WorkflowDocument`가 형식을 정의합니다. 노드에 없는 옵션 이름은 무시해 예전 파일도 읽을 수 있습니다.

## 새 노드 추가하기

1. `node/functional/`에 `BaseNode`(큐 소비) 또는 `PollingNode`(주기 실행)를 상속한 클래스를 만들고 `process()`를 구현합니다.
   입력을 받으면 `Inputable`, 보내면 `Outputable`을 붙입니다. 옵션은 필드에 `@OptionField`를 붙이면 편집기·저장에 자동 반영됩니다.
2. `node/NodeType.java`에 표시명·설명·아이콘(codicons 파일명)·생성자 한 줄을 추가합니다. 팝업·팩토리·저장이 모두 이 목록을 봅니다.
3. 메시지 하나를 건너뛸 문제는 `warn(...)`, 노드가 계속할 수 없는 문제는 예외로 던집니다.

## 패키지

| 패키지 | 내용 |
|---|---|
| `node`, `node.functional`, `node.option` | 노드 모델, 구현, 옵션 어노테이션 |
| `workflow` | 실행 상태(`RunState`)와 노드 스레드 생명주기(`WorkflowRunner`) |
| `json` | 저장 형식과 변환기 |
| `ui.workspace` / `ui.board` / `ui.inspector` / `ui.console` / `ui.menu` / `ui.action` / `ui.theme` | 화면 영역별 Swing 코드 |
