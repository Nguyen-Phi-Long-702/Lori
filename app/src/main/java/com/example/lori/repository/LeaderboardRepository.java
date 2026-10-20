package com.example.lori.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.lori.data.remote.api.LeaderboardApi;
import com.example.lori.data.remote.dto.LeaderboardEntryResponse;
import com.example.lori.data.remote.dto.LeaderboardMeResponse;

import java.util.List;

//Bảng xếp hạng EXP (Free): đọc từng trang và hạng của chính user
public class LeaderboardRepository extends BaseRepository {

    private final LeaderboardApi leaderboardApi;

    public LeaderboardRepository(Context context) {
        super(context);
        leaderboardApi = createApi(LeaderboardApi.class);
    }

    //Một trang của bảng xếp hạng (page bắt đầu từ 0)
    public LiveData<ApiResult<List<LeaderboardEntryResponse>>> top(String period, int page, int size) {
        return execute(leaderboardApi.top(period, page, size));
    }

    //Hạng và điểm của chính user trong bảng
    public LiveData<ApiResult<LeaderboardMeResponse>> me(String period) {
        return execute(leaderboardApi.me(period));
    }
}