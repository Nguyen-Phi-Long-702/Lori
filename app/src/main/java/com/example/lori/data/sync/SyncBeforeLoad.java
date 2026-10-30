package com.example.lori.data.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.work.WorkInfo;

import com.example.lori.data.local.AppDatabase;
import com.example.lori.utils.NetworkStateManager;

//Đẩy tiến trình học còn nằm trên máy lên server (nếu có) rồi mới chạy bước tiếp theo, để các API AI và Learning Engine
//đọc đúng kết quả quiz vừa làm thay vì chờ chu kỳ đồng bộ 30 phút. Mỗi ViewModel dùng một đối tượng riêng và gọi cancel() trong onCleared()
public class SyncBeforeLoad {

    private final Context context;
    private final AppDatabase db;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    //Lượt đồng bộ đang được chờ kết quả (để gỡ observer khi xong hoặc khi bị hủy)
    @Nullable
    private LiveData<WorkInfo> syncSource;
    @Nullable
    private Observer<WorkInfo> syncObserver;
    private boolean cancelled;

    public SyncBeforeLoad(@NonNull Context context) {
        this.context = context.getApplicationContext();
        db = AppDatabase.getInstance(this.context);
    }

    //onReady chạy một lần ở luồng chính: ngay khi không có gì cần đẩy hoặc máy mất mạng (API sẽ tự báo lỗi mạng),
    //ngược lại sau khi đồng bộ kết thúc, dù thành công hay thất bại (API báo kết quả thật)
    public void run(@NonNull Runnable onReady) {
        stopWatching();
        db.getQueryExecutor().execute(() -> {
            boolean pending = !db.progressDao().getUnsyncedProgress().isEmpty();
            mainHandler.post(() -> {
                if (cancelled) {
                    return;
                }
                if (pending && NetworkStateManager.getInstance(context).isOnline()) {
                    syncThen(onReady);
                } else {
                    onReady.run();
                }
            });
        });
    }

    public void cancel() {
        cancelled = true;
        stopWatching();
    }

    private void syncThen(Runnable onReady) {
        LiveData<WorkInfo> source = SyncManager.syncNow(context);
        Observer<WorkInfo> observer = info -> {
            if (info == null) {
                return;
            }
            //Lỗi mạng tạm thời đưa lượt này về ENQUEUED để thử lại sau: không chờ thêm
            boolean waitingRetry = info.getState() == WorkInfo.State.ENQUEUED && info.getRunAttemptCount() > 0;
            if (!info.getState().isFinished() && !waitingRetry) {
                return;
            }
            stopWatching();
            onReady.run();
        };
        syncSource = source;
        syncObserver = observer;
        source.observeForever(observer);
    }

    private void stopWatching() {
        if (syncSource != null && syncObserver != null) {
            syncSource.removeObserver(syncObserver);
        }
        syncSource = null;
        syncObserver = null;
    }
}