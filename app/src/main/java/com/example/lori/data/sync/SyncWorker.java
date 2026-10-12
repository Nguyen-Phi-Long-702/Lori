package com.example.lori.data.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.local.SessionManager;
import com.example.lori.data.local.dao.ProgressDao;
import com.example.lori.data.local.entity.UserProgressLocal;
import com.example.lori.data.remote.RetrofitClient;
import com.example.lori.data.remote.api.ProgressApi;
import com.example.lori.data.remote.api.UserApi;
import com.example.lori.data.remote.dto.ProgressItemRequest;
import com.example.lori.data.remote.dto.ProgressItemResponse;
import com.example.lori.data.remote.dto.SyncProgressRequest;
import com.example.lori.data.remote.dto.SyncProgressResponse;
import com.example.lori.data.remote.dto.UserResponse;
import com.example.lori.utils.IsoDateUtils;
import com.example.lori.utils.PremiumManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import retrofit2.Response;

//Một lượt đồng bộ: đẩy tiến trình chưa sync lên server → kéo tiến trình từ server về gộp vào máy → làm mới cờ Premium
//Chạy ở luồng nền do WorkManager quản lý nên được phép gọi mạng và Room trực tiếp
public class SyncWorker extends Worker {

    private static final String TAG = "SyncWorker";

    private static final int OUTCOME_OK = 0;
    //Lỗi tạm thời (Render/Neon đang khởi động, bị giới hạn tốc độ): WorkManager sẽ thử lại sau
    private static final int OUTCOME_RETRY = 1;
    //Lỗi không thể tự hết (401/403/400...): bỏ lượt này, chu kỳ 30 phút sau chạy lại bình thường
    private static final int OUTCOME_STOP = 2;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SessionManager sessionManager = SessionManager.getInstance(context);
        //Chưa đăng nhập (vd vừa đăng xuất): không có gì để đồng bộ
        if (!sessionManager.isLoggedIn()) {
            return Result.success();
        }

        RetrofitClient client = RetrofitClient.getInstance(context, sessionManager);
        ProgressApi progressApi = client.create(ProgressApi.class);
        UserApi userApi = client.create(UserApi.class);
        AppDatabase db = AppDatabase.getInstance(context);

        try {
            int outcome = pushUnsynced(db.progressDao(), progressApi);
            if (outcome == OUTCOME_OK) {
                outcome = pullAndMerge(db, progressApi, sessionManager);
            }
            if (outcome == OUTCOME_OK) {
                refreshPremium(context, userApi, sessionManager);
                Log.i(TAG, "Đồng bộ xong");
            }
            return toResult(outcome);
        } catch (IOException e) {
            //Mất mạng giữa chừng hoặc hết thời gian chờ (Render đang ngủ): các bước đều lặp lại an toàn nên cứ thử lại
            Log.w(TAG, "Đồng bộ gặp lỗi mạng, sẽ thử lại", e);
            return Result.retry();
        }
    }

    //Bước 1: đẩy các bản ghi is_synced = 0 lên server
    private int pushUnsynced(ProgressDao dao, ProgressApi api) throws IOException {
        List<UserProgressLocal> sent = new ArrayList<>();
        List<ProgressItemRequest> items = new ArrayList<>();
        for (UserProgressLocal progress : dao.getUnsyncedProgress()) {
            //Backend bắt buộc có lastStudiedAt; bản ghi thiếu mốc này không thể đồng bộ
            if (progress.lastStudiedAt == null) {
                continue;
            }
            sent.add(progress);
            items.add(new ProgressItemRequest(
                    progress.itemType,
                    progress.itemId,
                    progress.status,
                    progress.correctCount,
                    progress.incorrectCount,
                    IsoDateUtils.format(progress.lastStudiedAt)));
        }
        if (items.isEmpty()) {
            return OUTCOME_OK;
        }

        Response<SyncProgressResponse> response = api.sync(new SyncProgressRequest(items)).execute();
        int outcome = outcomeOf(response);
        if (outcome != OUTCOME_OK) {
            return outcome;
        }
        Log.i(TAG, "Đã đẩy " + items.size() + " mục lên server");

        //Server đã xử lý toàn bộ (mục nào cũ hơn bản trên server sẽ bị bỏ qua và được kéo về ở bước 2)
        for (UserProgressLocal progress : sent) {
            dao.markSyncedIfUnchanged(progress.itemType, progress.itemId, progress.lastStudiedAt);
        }
        return OUTCOME_OK;
    }

    //Bước 2: kéo toàn bộ tiến trình trên server về, bản nào server mới hơn thì ghi đè bản trên máy
    private int pullAndMerge(AppDatabase db, ProgressApi api, SessionManager sessionManager) throws IOException {
        Response<List<ProgressItemResponse>> response = api.pull().execute();
        int outcome = outcomeOf(response);
        if (outcome != OUTCOME_OK) {
            return outcome;
        }
        List<ProgressItemResponse> serverItems = response.body();
        if (serverItems == null || serverItems.isEmpty()) {
            return OUTCOME_OK;
        }
        Log.i(TAG, "Server trả về " + serverItems.size() + " mục");

        ProgressDao dao = db.progressDao();
        //Gộp trong một transaction để không xen vào giữa lúc người dùng đang lưu bài học
        db.runInTransaction(() -> {
            //Kiểm tra lại ngay trước khi ghi: nếu người dùng vừa đăng xuất thì không được ghi dữ liệu của tài khoản cũ vào máy
            if (isStopped() || !sessionManager.isLoggedIn()) {
                return;
            }
            for (ProgressItemResponse item : serverItems) {
                mergeOne(dao, item);
            }
        });
        return OUTCOME_OK;
    }

    //Gộp một mục từ server vào máy theo quy tắc "lastStudiedAt mới hơn thì thắng"
    private static void mergeOne(ProgressDao dao, ProgressItemResponse item) {
        Date serverTime = IsoDateUtils.parse(item.lastStudiedAt);
        if (item.itemType == null || item.status == null || serverTime == null) {
            return;
        }

        UserProgressLocal local = dao.getProgress(item.itemType, item.itemId);
        if (local == null) {
            //Chưa có trên máy (vd đăng nhập trên máy mới): tạo mới từ server
            local = new UserProgressLocal();
            local.itemType = item.itemType;
            local.itemId = item.itemId;
        } else if (local.lastStudiedAt != null && !serverTime.after(local.lastStudiedAt)) {
            //Bản trên máy mới hơn hoặc bằng bản trên server: giữ nguyên (nếu chưa sync thì lượt sau sẽ được đẩy lên)
            return;
        }

        local.status = item.status;
        local.correctCount = item.correctCount;
        local.incorrectCount = item.incorrectCount;
        local.lastStudiedAt = serverTime;
        //Dữ liệu vừa lấy từ server nên không cần đẩy ngược lên
        local.isSynced = true;
        dao.insertOrUpdate(local);
    }

    //Bước 3: làm mới cờ Premium (phản ánh mua trên máy khác hoặc Premium đã hết hạn)
    private void refreshPremium(Context context, UserApi userApi, SessionManager sessionManager) {
        try {
            Response<UserResponse> response = userApi.getMe().execute();
            UserResponse me = response.body();
            if (response.isSuccessful() && me != null && sessionManager.isLoggedIn()) {
                PremiumManager.setPremium(context, me.premium);
            }
        } catch (IOException e) {
            //Không để lỗi ở bước phụ này làm hỏng cả lượt đồng bộ; lượt sau sẽ thử lại
            Log.w(TAG, "Không làm mới được trạng thái Premium", e);
        }
    }

    private static int outcomeOf(Response<?> response) {
        if (response.isSuccessful()) {
            return OUTCOME_OK;
        }
        int code = response.code();
        Log.w(TAG, "Backend trả mã " + code);
        //429 và 5xx là lỗi tạm thời đáng thử lại; 400/401/403 thử lại cũng vô ích
        return (code == 429 || code >= 500) ? OUTCOME_RETRY : OUTCOME_STOP;
    }

    private static Result toResult(int outcome) {
        switch (outcome) {
            case OUTCOME_OK:
                return Result.success();
            case OUTCOME_RETRY:
                return Result.retry();
            default:
                return Result.failure();
        }
    }
}