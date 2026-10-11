package com.example.lori.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

//Quản lý phiên đăng nhập: lưu JWT trong EncryptedSharedPreferences (khóa mã hóa nằm trong Android Keystore)
//Là bản cài đặt thật của TokenStore, được truyền vào RetrofitClient. Singleton để toàn app dùng chung một kho token
//Thư viện security-crypto đã bị Google đánh dấu deprecated nhưng vẫn hoạt động; kế hoạch yêu cầu dùng nó nên chỉ tắt cảnh báo
@SuppressWarnings("deprecation")
public final class SessionManager implements TokenStore {

    //Tên tệp SharedPreferences đã mã hóa
    public static final String PREFS_FILE_NAME = "lori_secure_prefs";

    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";

    private static volatile SessionManager instance;

    private final SharedPreferences prefs;

    public static SessionManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private SessionManager(Context context) {
        prefs = createEncryptedPrefs(context);
    }

    //Mở kho mã hóa; nếu khóa Keystore và dữ liệu không còn khớp (vd khôi phục sang máy mới) thì xóa tệp hỏng và tạo lại
    //Hậu quả duy nhất là người dùng phải đăng nhập lại
    private static SharedPreferences createEncryptedPrefs(Context context) {
        try {
            return openEncryptedPrefs(context);
        } catch (GeneralSecurityException | IOException e) {
            context.deleteSharedPreferences(PREFS_FILE_NAME);
            try {
                return openEncryptedPrefs(context);
            } catch (GeneralSecurityException | IOException retryError) {
                throw new IllegalStateException("Không khởi tạo được kho token mã hóa", retryError);
            }
        }
    }

    private static SharedPreferences openEncryptedPrefs(Context context)
            throws GeneralSecurityException, IOException {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
        return EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
    }

    @Override
    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    @Override
    public String getRefreshToken() {
        return prefs.getString(KEY_REFRESH_TOKEN, null);
    }

    //Dùng commit() (ghi đồng bộ) vì Backend xoay vòng refresh token: nếu app bị tắt trước khi ghi xong,
    //refresh token cũ đã bị thu hồi còn token mới chưa lưu → mất phiên đăng nhập
    @Override
    public void saveTokens(String accessToken, String refreshToken) {
        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .commit();
    }

    @Override
    public void clear() {
        prefs.edit().clear().commit();
    }

    //Phiên đăng nhập sống bằng refresh token (7 ngày); access token chỉ 15 phút và sẽ được TokenAuthenticator tự làm mới
    public boolean isLoggedIn() {
        return getRefreshToken() != null;
    }
}