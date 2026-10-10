package com.example.lori.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

//Tính SHA-256 của chứng chỉ ký app để gửi lên Backend ở header X-App-Signature
//Định dạng: hex chữ thường, liền nhau, KHÔNG có dấu ':' (khớp biến APP_SIGNATURE trên Render)
public final class AppSignatureHelper {

    private AppSignatureHelper() {
    }

    @SuppressWarnings("deprecation")
    public static String getSha256(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            String packageName = context.getPackageName();
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo info = packageManager.getPackageInfo(
                        packageName, PackageManager.GET_SIGNING_CERTIFICATES);
                signatures = info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
            } else {
                //minSdk = 24 nên máy Android 7-8 vẫn dùng cách cũ
                PackageInfo info = packageManager.getPackageInfo(
                        packageName, PackageManager.GET_SIGNATURES);
                signatures = info.signatures;
            }
            if (signatures == null || signatures.length == 0) {
                return "";
            }
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (PackageManager.NameNotFoundException | NoSuchAlgorithmException e) {
            return "";
        }
    }
}