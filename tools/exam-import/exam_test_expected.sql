-- exam_test_expected.sql: tinh ket qua KY VONG de doi chieu khi kiem thu module de thi (chi doc, khong sua du lieu).
-- Cach chay: xem INFRA.md muc 14.

\echo '=== 1. TOEIC: ket qua ky vong cua cac kich ban kiem thu ==='
\echo 'Kich ban A: chon A o MOI cau, tru cac cau 10, 50, 120, 180 de TRONG'
\echo 'Kich ban C: (thoi luong tam doi) chi chon A o cau 1-5, con lai bo trong, de het gio tu nop'
\echo 'Cong thuc diem moi ky nang (khop ToeicScoreUtil): lam tron den boi so cua 5 cua (5 + dung/tong x 490)'
WITH ans AS (
    SELECT p.title, s.skill, q.order_index, q.correct_answer
    FROM exam_papers p
    JOIN exam_sections s ON s.paper_id = p.id
    JOIN exam_questions q ON q.section_id = s.id
    WHERE p.exam_type = 'TOEIC'
), scen AS (
    SELECT 'A' AS kich_ban, title, skill,
           (correct_answer = 'A' AND order_index NOT IN (10, 50, 120, 180)) AS dung
    FROM ans
    UNION ALL
    SELECT 'C', title, skill, (correct_answer = 'A' AND order_index <= 5)
    FROM ans
), agg AS (
    SELECT kich_ban, title,
           count(*) FILTER (WHERE skill = 'LISTENING')            AS l_tong,
           count(*) FILTER (WHERE skill = 'LISTENING' AND dung)   AS l_dung,
           count(*) FILTER (WHERE skill = 'READING')              AS r_tong,
           count(*) FILTER (WHERE skill = 'READING' AND dung)     AS r_dung
    FROM scen
    GROUP BY kich_ban, title
)
SELECT kich_ban, title,
       l_dung || '/' || l_tong AS listening_dung,
       r_dung || '/' || r_tong AS reading_dung,
       l_dung + r_dung         AS tong_dung,
       (l_tong + r_tong) - (l_dung + r_dung) AS sai_hoac_bo_trong,
       (round((5 + l_dung::numeric / l_tong * 490) / 5) * 5)::int AS listening_score,
       (round((5 + r_dung::numeric / r_tong * 490) / 5) * 5)::int AS reading_score,
       (round((5 + l_dung::numeric / l_tong * 490) / 5) * 5)::int
         + (round((5 + r_dung::numeric / r_tong * 490) / 5) * 5)::int AS tong_diem
FROM agg
ORDER BY kich_ban, title;

\echo ''
\echo '=== 2. IELTS: BANG HANH DONG cho tung cau (lam theo dung cot hanh_dong khi kiem thu) ==='
\echo 'so_cau la so cau hien trong app ("Question N" tren man hinh), danh so lai tu 1 o moi ky nang'
WITH q AS (
    SELECT p.id AS paper_id, p.title, s.skill, q.order_index AS so_cau, q.question_type, q.correct_answer,
           row_number() OVER (PARTITION BY p.id, s.skill, q.question_type ORDER BY q.order_index) AS thu_tu_trong_dang
    FROM exam_papers p
    JOIN exam_sections s ON s.paper_id = p.id
    JOIN exam_questions q ON q.section_id = s.id
    WHERE p.exam_type = 'IELTS'
), plan AS (
    SELECT *,
           CASE
               WHEN skill = 'LISTENING' AND so_cau = 40 THEN 'BO TRONG'
               WHEN skill = 'READING' AND question_type = 'FILL_BLANK' AND thu_tu_trong_dang = 1 THEN 'GO: wrongtest'
               WHEN question_type IN ('MULTIPLE_CHOICE', 'MATCHING') THEN 'CHON: A'
               WHEN question_type = 'TRUE_FALSE_NOT_GIVEN' THEN 'CHON: TRUE'
               ELSE 'GO: ' || correct_answer
           END AS hanh_dong,
           CASE
               WHEN skill = 'LISTENING' AND so_cau = 40 THEN false
               WHEN skill = 'READING' AND question_type = 'FILL_BLANK' AND thu_tu_trong_dang = 1 THEN false
               WHEN question_type IN ('MULTIPLE_CHOICE', 'MATCHING') THEN correct_answer = 'A'
               WHEN question_type = 'TRUE_FALSE_NOT_GIVEN' THEN correct_answer = 'TRUE'
               ELSE true
           END AS se_dung
    FROM q
)
SELECT skill, so_cau, question_type, hanh_dong
FROM plan
ORDER BY title, skill, so_cau;

\echo ''
\echo '=== 3. IELTS: so cau dung ky vong theo bang hanh dong o muc 2 (tra band theo bang trong IeltsScoreUtil) ==='
WITH q AS (
    SELECT p.title, s.skill, q.order_index AS so_cau, q.question_type, q.correct_answer,
           row_number() OVER (PARTITION BY p.id, s.skill, q.question_type ORDER BY q.order_index) AS thu_tu_trong_dang
    FROM exam_papers p
    JOIN exam_sections s ON s.paper_id = p.id
    JOIN exam_questions q ON q.section_id = s.id
    WHERE p.exam_type = 'IELTS'
), plan AS (
    SELECT *,
           CASE
               WHEN skill = 'LISTENING' AND so_cau = 40 THEN false
               WHEN skill = 'READING' AND question_type = 'FILL_BLANK' AND thu_tu_trong_dang = 1 THEN false
               WHEN question_type IN ('MULTIPLE_CHOICE', 'MATCHING') THEN correct_answer = 'A'
               WHEN question_type = 'TRUE_FALSE_NOT_GIVEN' THEN correct_answer = 'TRUE'
               ELSE true
           END AS se_dung
    FROM q
)
SELECT title, skill,
       count(*)                          AS tong_cau,
       count(*) FILTER (WHERE se_dung)   AS so_cau_dung_ky_vong
FROM plan
GROUP BY title, skill
ORDER BY title, skill;

\echo ''
\echo '=== 4. Section LISTENING co passage_text (neu co: mo cau do trong app xem khung "Doan van" co lo loi thoai khong) ==='
SELECT p.title, s.order_index, s.title AS section, left(replace(s.passage_text, E'\n', ' '), 120) AS passage_text
FROM exam_papers p
JOIN exam_sections s ON s.paper_id = p.id
WHERE s.skill = 'LISTENING' AND s.passage_text IS NOT NULL AND btrim(s.passage_text) <> ''
ORDER BY p.title, s.order_index;