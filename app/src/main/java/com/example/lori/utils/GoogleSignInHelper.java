package com.example.lori.utils;

import android.app.Activity;
import android.os.CancellationSignal;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.example.lori.BuildConfig;
import com.example.lori.R;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException;

//Lấy Google ID Token bằng Credential Manager (bảng chọn tài khoản Google của hệ thống)
//serverClientId là Web client ID nên ID Token có aud = Web client ID, khớp biến GOOGLE_CLIENT_ID của Backend
public final class GoogleSignInHelper {

    private static final String TAG = "GoogleSignIn";

    public interface Callback {
        //Lấy được ID Token, cần gửi lên Backend
        void onIdToken(String idToken);

        //Người dùng đóng bảng chọn tài khoản
        void onCancelled();

        //Thất bại; messageRes là chuỗi hiển thị cho người dùng
        void onError(@StringRes int messageRes);
    }

    private GoogleSignInHelper() {
    }

    //activity phải là Activity đang hiển thị (Credential Manager cần nó để mở bảng chọn tài khoản)
    public static void signIn(Activity activity, Callback callback) {
        String webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID;
        if (webClientId.isEmpty()) {
            callback.onError(R.string.auth_error_google_not_configured);
            return;
        }

        GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(webClientId).build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build();

        CredentialManager credentialManager = CredentialManager.create(activity);
        credentialManager.getCredentialAsync(
                activity,
                request,
                new CancellationSignal(),
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        handleResult(result, callback);
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        handleError(e, callback);
                    }
                });
    }

    private static void handleResult(GetCredentialResponse result, Callback callback) {
        Credential credential = result.getCredential();
        if (credential instanceof CustomCredential
                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            try {
                GoogleIdTokenCredential googleCredential = GoogleIdTokenCredential
                        .createFrom(((CustomCredential) credential).getData());
                callback.onIdToken(googleCredential.getIdToken());
            } catch (GoogleIdTokenParsingException e) {
                Log.e(TAG, "ID Token Google không đọc được", e);
                callback.onError(R.string.auth_error_google_failed);
            }
            return;
        }
        Log.e(TAG, "Loại thông tin xác thực không mong đợi: " + credential.getType());
        callback.onError(R.string.auth_error_google_failed);
    }

    private static void handleError(GetCredentialException e, Callback callback) {
        if (e instanceof GetCredentialCancellationException) {
            callback.onCancelled();
        } else if (e instanceof NoCredentialException) {
            callback.onError(R.string.auth_error_google_no_account);
        } else {
            //Ghi log để dò lỗi cấu hình (SHA-1, package, Test users…): lọc Logcat theo thẻ GoogleSignIn
            Log.e(TAG, "Đăng nhập Google lỗi: " + e.getType() + " - " + e.getMessage());
            callback.onError(R.string.auth_error_google_failed);
        }
    }
}