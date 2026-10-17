-- V3__exam_section_image.sql
-- Them anh cho section de thi: TOEIC Part 1 (Photographs) va cac cau do hoa o Part 3-4.
-- NULL = section khong co anh (cac section khac cua TOEIC va toan bo de IELTS hien co).
-- URL day du cua file anh tren Supabase Storage (cung bucket voi audio), do Dev nap bang SQL.
ALTER TABLE exam_sections ADD COLUMN image_url VARCHAR(500);