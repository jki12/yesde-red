-- 핫딜모음 수집 DB 스키마 (PostgreSQL).
-- yesde-red의 DB 저장 노드가 쓰고, 사이트 백엔드가 읽습니다. docker compose가 처음 기동할 때 자동으로 실행합니다.

-- 글 하나에 한 행. 같은 글이 다시 수집되면 행을 늘리지 않고 변하는 값(가격·댓글·조회수)만 갱신합니다.
CREATE TABLE IF NOT EXISTS deals (
    id             TEXT PRIMARY KEY,            -- 출처:글번호 (예: clien:900001)
    source         TEXT NOT NULL,               -- clien, ruliweb, ppomppu …
    source_post_id TEXT NOT NULL,
    post_url       TEXT NOT NULL,
    title          TEXT NOT NULL,
    shop           TEXT,                        -- 제목 머리의 [쇼핑몰]
    product        TEXT,                        -- 쇼핑몰·가격을 뺀 상품명
    price          BIGINT,                      -- 원 단위, 모르면 NULL
    category       TEXT,
    author         TEXT,
    comments       INTEGER,
    hits           INTEGER,
    posted_at      TIMESTAMPTZ,                 -- 원글 작성 시각
    first_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),  -- 처음 수집한 시각 (INSERT 때만)
    last_seen_at   TIMESTAMPTZ NOT NULL DEFAULT now()   -- 마지막으로 수집한 시각 (수집마다 갱신)
);

CREATE INDEX IF NOT EXISTS deals_posted_at_idx ON deals (posted_at DESC);
CREATE INDEX IF NOT EXISTS deals_source_posted_at_idx ON deals (source, posted_at DESC);

-- 수집할 때마다 한 행. 가격 추이·댓글 증가 같은 시계열의 재료입니다.
CREATE TABLE IF NOT EXISTS deal_snapshots (
    deal_id      TEXT NOT NULL REFERENCES deals (id) ON DELETE CASCADE,
    collected_at TIMESTAMPTZ NOT NULL,
    price        BIGINT,
    comments     INTEGER,
    hits         INTEGER,
    PRIMARY KEY (deal_id, collected_at)
);
