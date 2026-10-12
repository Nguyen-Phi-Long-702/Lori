package com.example.lori.data.sync;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

//Lên lịch / hủy lịch đồng bộ tiến trình học bằng WorkManager
public final class SyncManager {

    //Tên duy nhất của công việc định kỳ: lên lịch nhiều lần vẫn chỉ có một công việc
    private static final String WORK_NAME = "lori_progress_sync";
    private static final long INTERVAL_MINUTES = 30;

    private SyncManager() {
    }

    //Gọi khi vào MainActivity. KEEP: đã có lịch rồi thì giữ nguyên, không đặt lại chu kỳ mỗi lần mở app
    //Lần lên lịch đầu tiên chạy gần như ngay (khi có mạng), sau đó cứ 30 phút một lần
    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                SyncWorker.class, INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    //Gọi khi đăng xuất
    public static void cancel(Context context) {
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(WORK_NAME);
    }
}