package com.example.lori.data.remote.api;

import com.example.lori.data.remote.dto.LeaderboardEntryResponse;
import com.example.lori.data.remote.dto.LeaderboardMeResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

//Các endpoint /api/leaderboard/* của Backend. Cần đăng nhập, user Free cũng dùng được
//period = global | weekly | monthly
public interface LeaderboardApi {

    //Một trang của bảng xếp hạng: page bắt đầu từ 0, size 1-50
    @GET("api/leaderboard/{period}")
    Call<List<LeaderboardEntryResponse>> top(@Path("period") String period,
                                             @Query("page") int page,
                                             @Query("size") int size);

    //Hạng và điểm của chính user đang đăng nhập
    @GET("api/leaderboard/{period}/me")
    Call<LeaderboardMeResponse> me(@Path("period") String period);
}