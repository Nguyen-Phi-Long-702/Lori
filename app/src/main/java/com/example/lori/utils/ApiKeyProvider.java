package com.example.lori.utils;

import com.example.lori.BuildConfig;

//Nơi duy nhất cung cấp giá trị X-API-Key cho tầng mạng; giá trị đọc từ local.properties lúc build
//Lưu ý: đây chỉ là lớp lọc request bổ sung, không phải bí mật tuyệt đối
public final class ApiKeyProvider {

    private ApiKeyProvider() {
    }

    public static String getApiKey() {
        return BuildConfig.API_KEY;
    }
}