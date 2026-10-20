package com.example.lori.data.sync;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

//Lên lịch / hủy lịch đồng bộ tiến trình học bằng WorkManager
public final class SyncManager {

    //Tên duy nhất của công việc định kỳ: lên lịch nhiều lần vẫn chỉ có một công việc
    private static final String WORK_NAME = "lori_progress_sync";
    //Tên duy nhất của lượt đồng bộ chạy ngay (màn Home gọi để EXP vừa kiếm được hiện ra mà không phải chờ chu kỳ 30 phút)
    private static final String SYNC_NOW_WORK_NAME = "lori_progress_sync_now";
    private static final long INTERVAL_MINUTES = 30;

    private SyncManager() {
    }

    //Gọi khi vào MainActivity. KEEP: đã có lịch rồi thì giữ nguyên, không đặt lại chu kỳ mỗi lần mở app
    //Lần lên lịch đầu tiên chạy gần như ngay (khi có mạng), sau đó cứ 30 phút một lần
    public static void schedule(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                SyncWorker.class, INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(requireNetwork())
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    //Đồng bộ ngay một lượt khi có mạng (chưa có mạng thì chờ tới khi có). Trả về trạng thái của chính lượt này
    //REPLACE: lượt "ngay" cũ còn chờ/đang chạy bị hủy và thay bằng lượt mới, nên LiveData trả về luôn theo dõi đúng lượt mới
    public static LiveData<WorkInfo> syncNow(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(requireNetwork())
                .build();
        WorkManager workManager = WorkManager.getInstance(context.getApplicationContext());
        workManager.enqueueUniqueWork(SYNC_NOW_WORK_NAME, ExistingWorkPolicy.REPLACE, request);
        return workManager.getWorkInfoByIdLiveData(request.getId());
    }

    //Gọi khi đăng xuất: hủy cả lịch định kỳ lẫn lượt đồng bộ ngay đang chờ
    public static void cancel(Context context) {
        WorkManager workManager = WorkManager.getInstance(context.getApplicationContext());
        workManager.cancelUniqueWork(WORK_NAME);
        workManager.cancelUniqueWork(SYNC_NOW_WORK_NAME);
    }

    private static Constraints requireNetwork() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }
}