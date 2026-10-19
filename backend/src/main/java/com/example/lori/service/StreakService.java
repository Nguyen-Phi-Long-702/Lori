package com.example.lori.service;

import com.example.lori.repository.UserStudyDayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Streak (so ngay hoc lien tiep). Moi ngay user da hoc duoc luu 1 dong o user_study_days (ngay theo GIO VIET NAM);
 * streak khong luu san ma tinh tu cac ngay do, nen van dung khi du lieu dong bo den tre hoac sai thu tu.
 * Service nay chi lo ngay hoc va streak; viec cong EXP cho ngay hoc moi nam o ExpService.
 */
@Service
@RequiredArgsConstructor
public class StreakService {

    /** Mui gio dung chung cho "ngay hoc" va "hom nay" (app danh cho nguoi dung Viet Nam). */
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final UserStudyDayRepository userStudyDayRepository;

    /** Chuoi hien tai (0 neu da bo lo tu hom kia tro ve truoc) va chuoi dai nhat tung dat. */
    public record Streak(int current, int longest) {
    }

    /** Hom nay theo gio Viet Nam. */
    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /** Ghi nhan user da hoc vao ngay nay. Tra ve true neu day la ngay hoc MOI (truoc do chua co dong nao). */
    @Transactional
    public boolean recordStudyDay(UUID userId, LocalDate day) {
        return userStudyDayRepository.insertIfAbsent(userId, day) == 1;
    }

    @Transactional(readOnly = true)
    public Streak getStreak(UUID userId) {
        return compute(userStudyDayRepository.findStudyDates(userId), today());
    }

    /**
     * Tinh streak tu cac ngay hoc (moi nhat truoc, khong trung lap).
     * Chuoi hien tai tinh tu ngay hoc moi nhat va chi con hieu luc neu ngay do la hom nay hoac hom qua
     * (hom nay chua hoc thi chuoi van chua mat). Chuoi dai nhat la doan ngay lien tiep dai nhat trong lich su.
     */
    static Streak compute(List<LocalDate> daysNewestFirst, LocalDate today) {
        int longest = 0;
        int run = 0;
        int newestRun = 0;
        boolean inNewestRun = true;
        LocalDate previous = null;
        for (LocalDate day : daysNewestFirst) {
            boolean continues = previous != null && previous.minusDays(1).equals(day);
            if (previous != null && !continues) {
                inNewestRun = false;
            }
            run = continues ? run + 1 : 1;
            longest = Math.max(longest, run);
            if (inNewestRun) {
                newestRun = run;
            }
            previous = day;
        }
        boolean alive = !daysNewestFirst.isEmpty() && !daysNewestFirst.get(0).isBefore(today.minusDays(1));
        return new Streak(alive ? newestRun : 0, longest);
    }
}