-- V2__exam_tables.sql
-- Cac bang de thi TOEIC / IELTS: exam_papers, exam_sections, exam_questions, exam_results.
-- Noi dung de (papers/sections/questions) do Dev nhap bang SQL; exam_results do Backend ghi khi nop bai.

-- ============================================================
-- exam_papers: mot de thi (VD: "TOEIC Practice Test 1")
-- ============================================================
CREATE TABLE exam_papers (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_type         VARCHAR(20)  NOT NULL,
    title             VARCHAR(255) NOT NULL,
    difficulty        VARCHAR(20)  NOT NULL,
    duration_minutes  INTEGER      NOT NULL,
    CONSTRAINT ck_exam_papers_type       CHECK (exam_type IN ('TOEIC', 'IELTS')),
    CONSTRAINT ck_exam_papers_difficulty CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    CONSTRAINT ck_exam_papers_duration   CHECK (duration_minutes > 0)
);

-- ============================================================
-- exam_sections: mot nhom cau hoi dung chung audio/doan van (VD: TOEIC Part 3 - Conversation 1,
-- IELTS Reading - Passage 1). skill dung de tach diem Listening / Reading.
-- ============================================================
CREATE TABLE exam_sections (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    paper_id      UUID         NOT NULL REFERENCES exam_papers (id) ON DELETE CASCADE,
    skill         VARCHAR(20)  NOT NULL,
    title         VARCHAR(255) NOT NULL,
    order_index   INTEGER      NOT NULL,
    audio_url     VARCHAR(500),
    passage_text  TEXT,
    CONSTRAINT ck_exam_sections_skill CHECK (skill IN ('LISTENING', 'READING')),
    CONSTRAINT uq_exam_sections_order UNIQUE (paper_id, order_index)
);

-- ============================================================
-- exam_questions: mot cau hoi.
-- options: mang JSON cac lua chon theo thu tu A, B, C... (NULL voi TRUE_FALSE_NOT_GIVEN / FILL_BLANK).
-- correct_answer: MULTIPLE_CHOICE/MATCHING = chu cai (A, B...); TRUE_FALSE_NOT_GIVEN = TRUE / FALSE / NOT GIVEN;
-- FILL_BLANK = dap an dang chu.
-- ============================================================
CREATE TABLE exam_questions (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    section_id      UUID         NOT NULL REFERENCES exam_sections (id) ON DELETE CASCADE,
    order_index     INTEGER      NOT NULL,
    question_type   VARCHAR(30)  NOT NULL,
    question_text   TEXT,
    options         TEXT,
    correct_answer  VARCHAR(255) NOT NULL,
    explanation     TEXT,
    CONSTRAINT ck_exam_questions_type CHECK (question_type IN
        ('MULTIPLE_CHOICE', 'TRUE_FALSE_NOT_GIVEN', 'FILL_BLANK', 'MATCHING')),
    CONSTRAINT uq_exam_questions_order UNIQUE (section_id, order_index)
);

-- ============================================================
-- exam_results: ket qua mot lan nop bai cua user.
-- answers: JSON {"<questionId>": "<dap an user chon>"}, chi chua cau da tra loi.
-- Diem: TOEIC = diem quy doi (5-495 moi ky nang, tong 10-990); IELTS = band (0-9, buoc 0.5).
-- ============================================================
CREATE TABLE exam_results (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    paper_id            UUID          NOT NULL REFERENCES exam_papers (id) ON DELETE CASCADE,
    listening_correct   INTEGER       NOT NULL,
    listening_total     INTEGER       NOT NULL,
    reading_correct     INTEGER       NOT NULL,
    reading_total       INTEGER       NOT NULL,
    listening_score     NUMERIC(5, 1) NOT NULL,
    reading_score       NUMERIC(5, 1) NOT NULL,
    total_score         NUMERIC(5, 1) NOT NULL,
    time_spent_seconds  INTEGER       NOT NULL,
    answers             TEXT          NOT NULL,
    submitted_at        TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_exam_results_user_id ON exam_results (user_id, submitted_at DESC);