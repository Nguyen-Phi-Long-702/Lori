-- V5__ai_learning_tables.sql
-- 5 bang phuc vu Learning Context va Evaluation cua AI English Tutor (ke hoach muc 10.2).
-- LLM / AI Service khong doc cac bang nay truc tiep; chi Backend doc va tong hop thanh Learning Context.

-- ============================================================
-- user_learning_profile: ho so hoc tap tong hop cua user (moi user toi da 1 dong)
-- ============================================================
CREATE TABLE user_learning_profile (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    estimated_level  VARCHAR(2)   NOT NULL,
    learning_goal    VARCHAR(255),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_learning_profile_user  UNIQUE (user_id),
    CONSTRAINT ck_user_learning_profile_level CHECK (estimated_level IN ('A1', 'A2', 'B1', 'B2', 'C1'))
);

-- ============================================================
-- user_weakness_record: diem yeu dang theo doi (ngu phap / tu vung), Learning Engine cap nhat status
-- topic_key: khoa chu de, vi du present_perfect, articles, affect_effect
-- ============================================================
CREATE TABLE user_weakness_record (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category          VARCHAR(20)  NOT NULL,
    topic_key         VARCHAR(100) NOT NULL,
    correct_count     INTEGER      NOT NULL DEFAULT 0,
    incorrect_count   INTEGER      NOT NULL DEFAULT 0,
    status            VARCHAR(20)  NOT NULL DEFAULT 'learning',
    last_reviewed_at  TIMESTAMPTZ,
    CONSTRAINT uq_user_weakness_record_topic UNIQUE (user_id, category, topic_key),
    CONSTRAINT ck_user_weakness_record_category CHECK (category IN ('grammar', 'vocabulary')),
    CONSTRAINT ck_user_weakness_record_status   CHECK (status IN ('learning', 'reviewing', 'mastered'))
);

-- ============================================================
-- ai_feedback_metadata: log ngan ket qua AI tra ve (chi tom tat, khong luu prompt hay hoi thoai day du)
-- module: grammar_analysis / quiz_analysis / exam_analysis ...
-- related_entity_id: id lien quan (vd exam_results.id), khong rang buoc khoa ngoai vi tro toi nhieu bang
-- ============================================================
CREATE TABLE ai_feedback_metadata (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    module             VARCHAR(50)  NOT NULL,
    related_entity_id  UUID,
    summary            TEXT         NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_feedback_metadata_user_id ON ai_feedback_metadata (user_id);

-- ============================================================
-- ai_generated_exercise: bai tap do AI sinh, is_validated = false cho den khi duoc kiem tra
-- user_id NULL = bai tap chung (khong gan voi user nao)
-- ============================================================
CREATE TABLE ai_generated_exercise (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         REFERENCES users (id) ON DELETE CASCADE,
    exercise_type    VARCHAR(50)  NOT NULL,
    content          JSONB        NOT NULL,
    source_weakness  VARCHAR(100),
    is_validated     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_generated_exercise_user_id ON ai_generated_exercise (user_id);

-- ============================================================
-- ai_evaluation_result: ket qua moi lan chay Evaluation Dataset (ke hoach muc 16)
-- ============================================================
CREATE TABLE ai_evaluation_result (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    run_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    model_version    VARCHAR(100) NOT NULL,
    total_cases      INTEGER      NOT NULL,
    pass_count       INTEGER      NOT NULL,
    fail_count       INTEGER      NOT NULL,
    avg_latency_ms   INTEGER      NOT NULL,
    notes            TEXT
);