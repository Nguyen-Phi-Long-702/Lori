package com.example.lori.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.lori.utils.NetworkStateManager;

//Cầu nối cho View quan sát trạng thái mạng (banner offline, vô hiệu hóa nút cần mạng) mà không chạm trực tiếp vào NetworkStateManager
public class NetworkViewModel extends AndroidViewModel {

    private final LiveData<Boolean> online;

    public NetworkViewModel(@NonNull Application application) {
        super(application);
        online = NetworkStateManager.getInstance(application).getOnlineState();
    }

    //true = đang có mạng, false = offline; phát lại mỗi khi trạng thái đổi
    public LiveData<Boolean> getOnline() {
        return online;
    }
}