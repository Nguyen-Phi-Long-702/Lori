package com.example.lori.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.example.lori.R;
import com.example.lori.data.remote.dto.AiChatResponse;
import com.example.lori.repository.AiRepository;
import com.example.lori.repository.ApiResult;
import com.example.lori.utils.NetworkStateManager;

import java.util.ArrayList;
import java.util.List;

//Quản lý màn Hỏi đáp với AI Tutor: danh sách tin nhắn, trạng thái đang chờ trả lời, lỗi, và 403 PREMIUM_REQUIRED
//Lịch sử chat chỉ sống cùng ViewModel (xoay màn hình vẫn còn, thoát màn thì mất), không lưu xuống Room
//Giao diện chỉ quan sát các LiveData bên dưới; thông báo lỗi là mã chuỗi R.string (null = không lỗi)
public class AiChatViewModel extends AndroidViewModel {

    private final AiRepository aiRepository;
    private final LiveData<Boolean> online;

    //Tin nhắn thật, không gồm bong bóng "đang trả lời"
    private final List<ChatItem> messages = new ArrayList<>();
    private final MutableLiveData<List<ChatItem>> items = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> errorMessage = new MutableLiveData<>();
    //true khi Backend trả 403 PREMIUM_REQUIRED: giao diện mở PremiumPromptDialog rồi gọi consumePremiumRequired()
    private final MutableLiveData<Boolean> premiumRequired = new MutableLiveData<>();
    //Nút Gửi chỉ bấm được khi có mạng và không đang chờ câu trả lời
    private final MediatorLiveData<Boolean> sendEnabled = new MediatorLiveData<>();

    private long nextId = 1;

    public AiChatViewModel(@NonNull Application application) {
        super(application);
        aiRepository = new AiRepository(application);
        online = NetworkStateManager.getInstance(application).getOnlineState();
        sendEnabled.addSource(online, value -> updateSendEnabled());
        sendEnabled.addSource(loading, value -> updateSendEnabled());

        messages.add(new ChatItem(nextId++, ChatItem.TYPE_BOT,
                application.getString(R.string.ai_chat_welcome), false));
        publish();
    }

    //Bấm Gửi. Trả về true nếu tin được nhận (giao diện xóa ô nhập); false khi đang bận hoặc ô nhập trống
    public boolean send(String input) {
        String text = input == null ? "" : input.trim();
        if (text.isEmpty() || isBusy()) {
            return false;
        }
        errorMessage.setValue(null);
        messages.add(new ChatItem(nextId++, ChatItem.TYPE_USER, text, false));
        loading.setValue(true);
        publish();

        LiveData<ApiResult<AiChatResponse>> source = aiRepository.chat(text);
        //Nghe một lần rồi gỡ (giống TranslateViewModel)
        source.observeForever(new Observer<ApiResult<AiChatResponse>>() {
            @Override
            public void onChanged(ApiResult<AiChatResponse> apiResult) {
                source.removeObserver(this);
                handleResult(apiResult);
            }
        });
        return true;
    }

    private void handleResult(ApiResult<AiChatResponse> apiResult) {
        loading.setValue(false);
        AiChatResponse body = apiResult.getData();
        if (apiResult.isSuccess() && body != null && body.answer != null && !body.answer.trim().isEmpty()) {
            messages.add(new ChatItem(nextId++, ChatItem.TYPE_BOT, body.answer.trim(), body.truncated));
        } else if (apiResult.isPremiumRequired()) {
            premiumRequired.setValue(true);
        } else {
            errorMessage.setValue(AiErrorMessages.of(apiResult));
        }
        publish();
    }

    //Đẩy danh sách mới cho giao diện: tin nhắn thật, cộng bong bóng "đang trả lời" khi còn chờ
    private void publish() {
        List<ChatItem> list = new ArrayList<>(messages);
        if (isBusy()) {
            list.add(new ChatItem(ChatItem.TYPING_ID, ChatItem.TYPE_TYPING, "", false));
        }
        items.setValue(list);
    }

    public void consumePremiumRequired() {
        premiumRequired.setValue(null);
    }

    private void updateSendEnabled() {
        sendEnabled.setValue(Boolean.TRUE.equals(online.getValue()) && !isBusy());
    }

    private boolean isBusy() {
        return Boolean.TRUE.equals(loading.getValue());
    }

    public LiveData<List<ChatItem>> getItems() { return items; }
    //Lỗi hiện phía trên ô nhập; null = không lỗi
    public LiveData<Integer> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getPremiumRequired() { return premiumRequired; }
    public LiveData<Boolean> getSendEnabled() { return sendEnabled; }
    public LiveData<Boolean> getOnline() { return online; }
}