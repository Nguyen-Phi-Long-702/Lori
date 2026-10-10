package com.example.lori.data.local;

//Nơi lưu JWT của user
//Được gọi từ luồng của OkHttp nên bản cài đặt phải an toàn khi dùng đa luồng.
public interface TokenStore {

    //null nếu chưa đăng nhập
    String getAccessToken();

    //null nếu chưa đăng nhập
    String getRefreshToken();

    void saveTokens(String accessToken, String refreshToken);

    //Xóa cả hai token (đăng xuất, hoặc refresh token đã hết hạn/bị thu hồi)
    void clear();
}