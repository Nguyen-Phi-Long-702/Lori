package com.example.lori.viewmodel;

import androidx.annotation.StringRes;

import com.example.lori.R;
import com.example.lori.repository.ApiResult;

//Đổi kết quả lỗi của API AI thành câu thông báo tiếng Việt (403 PREMIUM_REQUIRED do ViewModel xử lý riêng, không qua đây)
final class AiErrorMessages {

    private AiErrorMessages() {
    }

    @StringRes
    static int of(ApiResult<?> apiResult) {
        switch (apiResult.getStatus()) {
            case OFFLINE:
                return R.string.ai_offline_hint;
            case TIMEOUT:
                return R.string.auth_error_timeout;
            case NETWORK_ERROR:
                return R.string.auth_error_network;
            default:
                return ofHttp(apiResult.getHttpCode());
        }
    }

    @StringRes
    private static int ofHttp(int httpCode) {
        switch (httpCode) {
            case 400:
                return R.string.ai_error_invalid_input;
            case 401:
                return R.string.premium_error_session_expired;
            case 403:
                return R.string.auth_error_forbidden;
            case 429:
                return R.string.ai_error_too_many_requests;
            case 503:
                return R.string.ai_error_unavailable;
            default:
                return R.string.auth_error_server;
        }
    }
}