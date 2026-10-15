package com.example.lori.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

//Phát audio Listening của đề thi bằng AndroidX Media3 (ExoPlayer). Audio được phát trực tiếp từ Supabase qua HTTPS
//Lớp này chỉ bọc ExoPlayer và báo trạng thái về giao diện qua Listener; không chứa nghiệp vụ của đề thi
//Mọi hàm phải gọi từ luồng chính
public class ExamAudioPlayer {

    //Giao diện nhận trạng thái phát
    public interface Listener {
        //Bắt đầu/dừng phát
        void onPlayingChanged(boolean playing);

        //Vị trí và độ dài hiện tại (durationMs = 0 khi chưa biết)
        void onProgress(long positionMs, long durationMs);

        //Không phát được (mất mạng, link audio hỏng, Supabase đang bị tạm dừng...)
        void onError();
    }

    private static final long PROGRESS_INTERVAL_MS = 500L;
    private static final long SEEK_STEP_MS = 10_000L;

    private final Context appContext;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Nullable
    private ExoPlayer player;
    @Nullable
    private String currentUrl;

    //Cập nhật thanh tiến trình mỗi 0,5 giây trong lúc đang phát
    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            reportProgress();
            if (player != null && player.isPlaying()) {
                handler.postDelayed(this, PROGRESS_INTERVAL_MS);
            }
        }
    };

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            listener.onPlayingChanged(isPlaying);
            handler.removeCallbacks(progressTicker);
            if (isPlaying) {
                handler.post(progressTicker);
            } else {
                reportProgress();
            }
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            //Có độ dài file (STATE_READY) hoặc phát xong (STATE_ENDED) thì cập nhật thanh tiến trình
            if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                reportProgress();
            }
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            listener.onError();
        }
    };

    public ExamAudioPlayer(@NonNull Context context, @NonNull Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
    }

    //Nạp file audio mới, bắt đầu từ startPositionMs. autoPlay = true thì phát ngay
    public void load(@NonNull String url, long startPositionMs, boolean autoPlay) {
        if (player == null) {
            player = new ExoPlayer.Builder(appContext).build();
            player.addListener(playerListener);
        }
        currentUrl = url;
        player.setMediaItem(MediaItem.fromUri(url), startPositionMs);
        player.setPlayWhenReady(autoPlay);
        player.prepare();
        reportProgress();
    }

    //Bấm nút giữa: đang phát thì tạm dừng, ngược lại thì phát
    public void togglePlayPause() {
        if (player == null) {
            return;
        }
        if (player.isPlaying()) {
            player.pause();
            return;
        }
        int state = player.getPlaybackState();
        if (state == Player.STATE_ENDED) {
            //Đã nghe hết: phát lại từ đầu
            player.seekTo(0L);
        } else if (state == Player.STATE_IDLE) {
            //Lần trước bị lỗi: nạp lại rồi mới phát
            player.prepare();
        }
        player.play();
    }

    public void rewind() {
        seekBy(-SEEK_STEP_MS);
    }

    public void forward() {
        seekBy(SEEK_STEP_MS);
    }

    private void seekBy(long deltaMs) {
        if (player == null) {
            return;
        }
        long target = Math.max(0L, player.getCurrentPosition() + deltaMs);
        long duration = getDurationMs();
        if (duration > 0L) {
            target = Math.min(target, duration);
        }
        player.seekTo(target);
        reportProgress();
    }

    public void pause() {
        if (player != null) {
            player.pause();
        }
    }

    //Dừng hẳn và quên file hiện tại (khi sang câu không có audio)
    public void stop() {
        handler.removeCallbacks(progressTicker);
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        currentUrl = null;
    }

    //Giải phóng ExoPlayer; gọi khi Activity bị hủy
    public void release() {
        handler.removeCallbacks(progressTicker);
        if (player != null) {
            player.release();
            player = null;
        }
        currentUrl = null;
    }

    @Nullable
    public String getCurrentUrl() {
        return currentUrl;
    }

    public long getPositionMs() {
        return player == null ? 0L : Math.max(0L, player.getCurrentPosition());
    }

    //Độ dài file; 0 khi chưa biết (ExoPlayer trả C.TIME_UNSET là số âm)
    private long getDurationMs() {
        if (player == null) {
            return 0L;
        }
        long duration = player.getDuration();
        return duration > 0L ? duration : 0L;
    }

    private void reportProgress() {
        listener.onProgress(getPositionMs(), getDurationMs());
    }
}