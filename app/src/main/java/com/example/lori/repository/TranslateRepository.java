package com.example.lori.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.lori.data.remote.api.TranslateApi;
import com.example.lori.data.remote.dto.TranslateRequest;
import com.example.lori.data.remote.dto.TranslateResponse;

//Dịch câu/đoạn EN <-> VI qua Backend (tính năng Free, chỉ cần đăng nhập)
//Lỗi 503 nghĩa là dịch vụ dịch phía Backend chưa chạy hoặc không tới được (xem INFRA.md mục 12)
public class TranslateRepository extends BaseRepository {

    private final TranslateApi translateApi;

    public TranslateRepository(Context context) {
        super(context);
        translateApi = createApi(TranslateApi.class);
    }

    //sourceLang/targetLang: "en" hoặc "vi", phải khác nhau; text tối đa 500 ký tự
    public LiveData<ApiResult<TranslateResponse>> translate(String text, String sourceLang, String targetLang) {
        return execute(translateApi.translate(new TranslateRequest(text, sourceLang, targetLang)));
    }
}