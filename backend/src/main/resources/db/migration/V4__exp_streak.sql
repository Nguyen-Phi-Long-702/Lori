-- V4__exp_streak.sql
-- He thong EXP + Streak
-- users.total_exp: tong EXP hien co cua user. Chi duoc cong them bang cau lenh UPDATE total_exp = total_exp + n.
-- user_study_days: moi dong = 1 ngay user da hoc (ngay theo gio Viet Nam). Streak KHONG luu san ma tinh tu bang nay,
-- nen so ngay lien tiep van dung du dong bo den tre hoac sai thu tu.

ALTER TABLE users
    ADD COLUMN total_exp INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_users_total_exp CHECK (total_exp >= 0);

CREATE TABLE user_study_days (
    id          UUID  PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID  NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    study_date  DATE  NOT NULL,
    CONSTRAINT uq_user_study_days UNIQUE (user_id, study_date)
);