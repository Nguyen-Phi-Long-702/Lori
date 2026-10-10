package com.example.lori.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

//Theo dõi trạng thái kết nối mạng của thiết bị. ViewModel/Repository quan sát getOnlineState() (LiveData) hoặc gọi isOnline() để kiểm tra tức thời. Singleton sống cùng tiến trình app nên không cần hủy đăng ký callback
public final class NetworkStateManager {

    private static volatile NetworkStateManager instance;

    private final ConnectivityManager connectivityManager;
    private final MutableLiveData<Boolean> onlineState;

    public static NetworkStateManager getInstance(Context context) {
        if (instance == null) {
            synchronized (NetworkStateManager.class) {
                if (instance == null) {
                    instance = new NetworkStateManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private NetworkStateManager(Context context) {
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        onlineState = new MutableLiveData<>(checkOnline());
        //Theo dõi mạng mặc định của hệ thống (Wi-Fi hoặc dữ liệu di động); minSdk 24 hỗ trợ sẵn hàm này
        connectivityManager.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                onlineState.postValue(true);
            }

            @Override
            public void onLost(@NonNull Network network) {
                onlineState.postValue(false);
            }
        });
    }

    //true = đang online, false = offline; phát lại mỗi khi trạng thái đổi
    public LiveData<Boolean> getOnlineState() {
        return onlineState;
    }

    //Kiểm tra tức thời tại thời điểm gọi
    public boolean isOnline() {
        return checkOnline();
    }

    private boolean checkOnline() {
        Network network = connectivityManager.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}