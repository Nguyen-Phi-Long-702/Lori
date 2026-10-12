package com.example.lori.repository;

import android.content.Context;

import com.example.lori.data.remote.api.LeaderboardApi;

//Chưa có method: Backend chưa có endpoint /api/leaderboard/* (làm ở Tuần 8)
//Khi Backend xong: thêm method vào LeaderboardApi rồi bọc bằng execute(...) tại đây, giống TranslateRepository
public class LeaderboardRepository extends BaseRepository {

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private final LeaderboardApi leaderboardApi;

    public LeaderboardRepository(Context context) {
        super(context);
        leaderboardApi = createApi(LeaderboardApi.class);
    }
}