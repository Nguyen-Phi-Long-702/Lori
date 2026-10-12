package com.example.lori.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.lori.data.remote.api.ExamApi;
import com.example.lori.data.remote.dto.ExamDetailResponse;
import com.example.lori.data.remote.dto.ExamResultDetailResponse;
import com.example.lori.data.remote.dto.ExamResultResponse;
import com.example.lori.data.remote.dto.ExamSummaryResponse;
import com.example.lori.data.remote.dto.StartExamResponse;
import com.example.lori.data.remote.dto.SubmitExamRequest;

import java.util.List;

//Đề thi TOEIC/IELTS (Premium). User Free nhận HTTP_ERROR 403 với ApiResult.isPremiumRequired() = true
public class ExamRepository extends BaseRepository {

    private final ExamApi examApi;

    public ExamRepository(Context context) {
        super(context);
        examApi = createApi(ExamApi.class);
    }

    //Danh sách đề
    public LiveData<ApiResult<List<ExamSummaryResponse>>> list() {
        return execute(examApi.list());
    }

    //Chi tiết một đề (các phần và câu hỏi)
    public LiveData<ApiResult<ExamDetailResponse>> detail(String examId) {
        return execute(examApi.detail(examId));
    }

    //Bắt đầu làm bài: nhận sessionToken (dùng một lần khi nộp bài)
    public LiveData<ApiResult<StartExamResponse>> start(String examId) {
        return execute(examApi.start(examId));
    }

    //Nộp bài, nhận điểm
    public LiveData<ApiResult<ExamResultResponse>> submit(String examId, SubmitExamRequest request) {
        return execute(examApi.submit(examId, request));
    }

    //Lịch sử các lần thi
    public LiveData<ApiResult<List<ExamResultResponse>>> results() {
        return execute(examApi.results());
    }

    //Chi tiết một lần thi (để xem lại từng câu)
    public LiveData<ApiResult<ExamResultDetailResponse>> resultDetail(String resultId) {
        return execute(examApi.resultDetail(resultId));
    }
}