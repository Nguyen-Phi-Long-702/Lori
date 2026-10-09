-- verify_exam_data.sql: kiem tra du lieu de thi sau khi nhap (chi doc, khong sua du lieu).
-- Cach chay: xem INFRA.md muc 11.

\echo '=== 1. Tong quan moi de (so section, so cau Listening / Reading) ==='
SELECT p.exam_type, p.title, p.difficulty, p.duration_minutes,
       count(DISTINCT s.id)                                   AS sections,
       count(q.id) FILTER (WHERE s.skill = 'LISTENING')       AS listening_q,
       count(q.id) FILTER (WHERE s.skill = 'READING')         AS reading_q
FROM exam_papers p
JOIN exam_sections s ON s.paper_id = p.id
LEFT JOIN exam_questions q ON q.section_id = s.id
GROUP BY p.id
ORDER BY p.exam_type, p.title;

\echo '=== 2. So cau theo dang cau hoi ==='
SELECT p.title, q.question_type, count(*) AS so_cau
FROM exam_papers p
JOIN exam_sections s ON s.paper_id = p.id
JOIN exam_questions q ON q.section_id = s.id
GROUP BY p.title, q.question_type
ORDER BY p.title, q.question_type;

\echo '=== 3. Kiem tra toan ven: moi dong phai co bad_rows = 0 ==='
SELECT 'section khong co cau hoi' AS kiem_tra, count(*) AS bad_rows
FROM exam_sections s WHERE NOT EXISTS (SELECT 1 FROM exam_questions q WHERE q.section_id = s.id)
UNION ALL
SELECT 'Listening thieu audio_url', count(*)
FROM exam_sections WHERE skill = 'LISTENING' AND (audio_url IS NULL OR audio_url NOT LIKE 'https://%')
UNION ALL
SELECT 'Reading co audio_url', count(*)
FROM exam_sections WHERE skill = 'READING' AND audio_url IS NOT NULL
UNION ALL
SELECT 'trac nghiem/matching thieu options', count(*)
FROM exam_questions WHERE question_type IN ('MULTIPLE_CHOICE', 'MATCHING') AND options IS NULL
UNION ALL
SELECT 'options khong phai mang JSON', count(*)
FROM exam_questions WHERE options IS NOT NULL AND jsonb_typeof(options::jsonb) <> 'array'
UNION ALL
SELECT 'dap an trac nghiem/matching ngoai pham vi options', count(*)
FROM exam_questions
WHERE question_type IN ('MULTIPLE_CHOICE', 'MATCHING') AND options IS NOT NULL
  AND (length(correct_answer) <> 1
       OR ascii(correct_answer) - 64 NOT BETWEEN 1 AND jsonb_array_length(options::jsonb))
UNION ALL
SELECT 'dien tu / T-F-NG co options', count(*)
FROM exam_questions WHERE question_type IN ('FILL_BLANK', 'TRUE_FALSE_NOT_GIVEN') AND options IS NOT NULL
UNION ALL
SELECT 'T-F-NG sai gia tri dap an', count(*)
FROM exam_questions WHERE question_type = 'TRUE_FALSE_NOT_GIVEN' AND correct_answer NOT IN ('TRUE', 'FALSE', 'NOT GIVEN')
UNION ALL
SELECT 'dap an rong', count(*)
FROM exam_questions WHERE btrim(correct_answer) = '';

\echo '=== 4. Danh sach audio_url (de thu mo tung link tren trinh duyet) ==='
SELECT p.title, s.order_index, s.audio_url
FROM exam_papers p
JOIN exam_sections s ON s.paper_id = p.id
WHERE s.audio_url IS NOT NULL
ORDER BY p.title, s.order_index
LIMIT 12;