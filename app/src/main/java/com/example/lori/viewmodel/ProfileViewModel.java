package com.example.lori.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.local.entity.UserProgressLocal;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

//Quản lý dữ liệu lịch streak học tập hiển thị trên profilefragment
public class ProfileViewModel extends AndroidViewModel {

    //Đại diện một ô trong lưới 30 ngày: số thứ tự ngày và đã học hay chưa
    public static class StreakDay {
        public final int dayNumber;
        public final boolean studied;

        public StreakDay(int dayNumber, boolean studied) {
            this.dayNumber = dayNumber;
            this.studied = studied;
        }
    }

    private final LiveData<List<StreakDay>> streakGrid;
    private final LiveData<Integer> currentStreak;
    private final LiveData<Integer> bestStreak;

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);
        LiveData<List<UserProgressLocal>> studyRecords = db.progressDao().getAllStudyDates();

        streakGrid = Transformations.map(studyRecords, this::buildStreakGrid);
        currentStreak = Transformations.map(studyRecords, this::calculateCurrentStreak);
        bestStreak = Transformations.map(studyRecords, this::calculateBestStreak);
    }

    //Gom các bản ghi tiến độ về tập hợp những ngày (định dạng yyyy-mm-dd) có hoạt động học
    private Set<String> toStudiedDaySet(List<UserProgressLocal> records) {
        Set<String> days = new HashSet<>();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        if (records != null) {
            for (UserProgressLocal record : records) {
                if (record.lastStudiedAt != null) {
                    days.add(format.format(record.lastStudiedAt));
                }
            }
        }
        return days;
    }

    //Dựng lưới 30 ngày gần nhất (kể cả hôm nay) để hiển thị trên màn hình hồ sơ
    private List<StreakDay> buildStreakGrid(List<UserProgressLocal> records) {
        Set<String> studiedDays = toStudiedDaySet(records);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        List<StreakDay> grid = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -29);
        for (int i = 0; i < 30; i++) {
            boolean studied = studiedDays.contains(format.format(calendar.getTime()));
            grid.add(new StreakDay(i + 1, studied));
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        return grid;
    }

    //Tính số ngày học liên tiếp tính đến hôm nay, dừng lại khi gặp ngày trống đầu tiên
    private int calculateCurrentStreak(List<UserProgressLocal> records) {
        Set<String> studiedDays = toStudiedDaySet(records);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar calendar = Calendar.getInstance();
        int streak = 0;
        while (studiedDays.contains(format.format(calendar.getTime()))) {
            streak++;
            calendar.add(Calendar.DAY_OF_YEAR, -1);
        }
        return streak;
    }

    //Tính chuỗi ngày học liên tiếp dài nhất từng đạt được, không giới hạn trong 30 ngày
    private int calculateBestStreak(List<UserProgressLocal> records) {
        Set<String> studiedDays = toStudiedDaySet(records);
        List<String> sortedDays = new ArrayList<>(studiedDays);
        Collections.sort(sortedDays);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        int best = 0;
        int current = 0;
        Calendar previous = null;
        for (String day : sortedDays) {
            try {
                Calendar calendarDay = Calendar.getInstance();
                calendarDay.setTime(format.parse(day));
                if (previous != null) {
                    Calendar expected = (Calendar) previous.clone();
                    expected.add(Calendar.DAY_OF_YEAR, 1);
                    current = isSameDay(expected, calendarDay) ? current + 1 : 1;
                } else {
                    current = 1;
                }
                best = Math.max(best, current);
                previous = calendarDay;
            } catch (ParseException e) {
                //Bỏ qua bản ghi ngày không hợp lệ, không nên xảy ra vì ngày được tự sinh bởi định dạng cố định
            }
        }
        return best;
    }

    private boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    public LiveData<List<StreakDay>> getStreakGrid() { return streakGrid; }
    public LiveData<Integer> getCurrentStreak() { return currentStreak; }
    public LiveData<Integer> getBestStreak() { return bestStreak; }
}