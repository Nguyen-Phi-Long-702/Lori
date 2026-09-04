package com.example.lori.viewmodel;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.util.Locale;
import com.example.lori.utils.ThemeHelper;

//Quản lý dữ liệu và thao tác của màn hình cài đặt: tuỳ chọn lưu local, bộ nhớ đệm, thông tin ứng dụng
public class SettingsViewModel extends AndroidViewModel {

    private static final String PREFS_NAME = "lori_prefs";
    private static final String KEY_NOTIFICATION_ENABLED = "notification_enabled";
    private static final String KEY_SOUND_ENABLED = "sound_enabled";

    private final SharedPreferences prefs;
    private final MutableLiveData<String> cacheSizeLabel = new MutableLiveData<>();

    public SettingsViewModel(@NonNull Application application) {
        super(application);
        prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        refreshCacheSize();
    }

    public boolean isNotificationEnabled() {
        return prefs.getBoolean(KEY_NOTIFICATION_ENABLED, true);
    }

    //Lưu tuỳ chọn thông báo học tập
    public void setNotificationEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_ENABLED, enabled).apply();
    }

    public boolean isSoundEnabled() {
        return prefs.getBoolean(KEY_SOUND_ENABLED, true);
    }

    //Lưu tuỳ chọn âm thanh phát âm
    public void setSoundEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply();
    }

    public boolean isDarkModeEnabled() {
        return AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES;
    }

    //Bật/tắt giao diện tối và lưu lại lựa chọn để áp dụng lại ở lần mở app tiếp theo
    public void setDarkModeEnabled(boolean enabled) {
        AppCompatDelegate.setDefaultNightMode(
                enabled ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        ThemeHelper.saveDarkModePreference(getApplication(), enabled);
    }

    //Xóa sạch bộ nhớ đệm rồi cập nhật lại dung lượng hiển thị
    public void clearCache() {
        deleteDirectoryContents(getApplication().getCacheDir());
        refreshCacheSize();
    }

    //Tính lại dung lượng bộ nhớ đệm hiện tại và cập nhật livedata
    private void refreshCacheSize() {
        long sizeBytes = getDirectorySize(getApplication().getCacheDir());
        cacheSizeLabel.setValue(formatFileSize(sizeBytes));
    }

    //Tính tổng dung lượng của một thư mục, bao gồm các thư mục con
    private long getDirectorySize(File directory) {
        long size = 0;
        File[] files = directory != null ? directory.listFiles() : null;
        if (files == null) return 0;
        for (File file : files) {
            size += file.isDirectory() ? getDirectorySize(file) : file.length();
        }
        return size;
    }

    //Xóa toàn bộ nội dung bên trong một thư mục nhưng vẫn giữ lại thư mục gốc
    private void deleteDirectoryContents(File directory) {
        File[] files = directory != null ? directory.listFiles() : null;
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                deleteDirectoryContents(file);
            }
            file.delete();
        }
    }

    //Định dạng dung lượng byte thành chuỗi dễ đọc
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exponent = (int) (Math.log(bytes) / Math.log(1024));
        String unit = "KMGT".charAt(exponent - 1) + "B";
        return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024, exponent), unit);
    }

    //Lấy tên phiên bản hiện tại của ứng dụng
    public String getAppVersionName() {
        try {
            PackageManager packageManager = getApplication().getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(getApplication().getPackageName(), 0);
            return packageInfo.versionName != null ? packageInfo.versionName : "1.0";
        } catch (PackageManager.NameNotFoundException e) {
            return "1.0";
        }
    }

    public LiveData<String> getCacheSizeLabel() {
        return cacheSizeLabel;
    }
}