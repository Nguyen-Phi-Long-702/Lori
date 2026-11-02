package com.example.lori.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.example.lori.R;
import com.example.lori.data.remote.dto.AiConversationRequest;
import com.example.lori.data.remote.dto.AiConversationResponse;
import com.example.lori.model.ConversationMode;
import com.example.lori.repository.AiRepository;
import com.example.lori.repository.ApiResult;
import com.example.lori.utils.NetworkStateManager;

import java.util.ArrayList;
import java.util.List;

//Quản lý một buổi hội thoại luyện tập với AI (Premium), dùng chung cho 3 màn: chọn tình huống, chat, nhận xét
//Server không lưu gì nên ViewModel giữ lịch sử và gửi toàn bộ lịch sử mỗi lượt; lịch sử sống cùng nav graph nav_ai_conversation
//Giao diện chỉ quan sát các LiveData bên dưới; thông báo lỗi là mã chuỗi R.string (null = không lỗi)
public class AiConversationViewModel extends AndroidViewModel {

    //Backend nhận tối đa 30 tin mỗi request, nên danh sách không bao giờ được vượt 30
    private static final int MAX_MESSAGES = 30;

    private final AiRepository aiRepository;
    private final LiveData<Boolean> online;

    //Tin nhắn thật của cả hai bên, không gồm bong bóng "đang trả lời"
    private final List<ChatItem> messages = new ArrayList<>();
    private final MutableLiveData<ConversationMode> mode = new MutableLiveData<>();
    private final MutableLiveData<List<ChatItem>> items = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> errorMessage = new MutableLiveData<>();
    //true khi Backend trả 403 PREMIUM_REQUIRED: giao diện mở PremiumPromptDialog rồi gọi consumePremiumRequired()
    private final MutableLiveData<Boolean> premiumRequired = new MutableLiveData<>();
    //Nhận xét cuối buổi; khác null thì giao diện chuyển sang màn nhận xét
    private final MutableLiveData<AiConversationResponse> feedback = new MutableLiveData<>();
    //Nút Gửi chỉ bấm được khi có mạng, không đang chờ AI và chưa đầy 30 tin
    private final MediatorLiveData<Boolean> sendEnabled = new MediatorLiveData<>();
    //Nút Kết thúc chỉ bấm được khi có mạng, không đang chờ AI và người học đã nói ít nhất một câu
    private final MediatorLiveData<Boolean> finishEnabled = new MediatorLiveData<>();

    private long nextId = 1;
    //Tăng mỗi khi bắt đầu buổi mới: kết quả của buổi cũ về muộn thì bị bỏ
    private int sessionId = 0;

    public AiConversationViewModel(@NonNull Application application) {
        super(application);
        aiRepository = new AiRepository(application);
        online = NetworkStateManager.getInstance(application).getOnlineState();
        sendEnabled.addSource(online, value -> updateButtons());
        sendEnabled.addSource(loading, value -> updateButtons());
        sendEnabled.addSource(items, value -> updateButtons());
        finishEnabled.addSource(online, value -> updateButtons());
        finishEnabled.addSource(loading, value -> updateButtons());
        finishEnabled.addSource(items, value -> updateButtons());
    }

    //Chọn một tình huống: xóa buổi cũ và nhờ AI mở đầu. Nếu user Free thì lời gọi này nhận 403 và hiện PremiumPromptDialog
    public void startSession(String modeId) {
        ConversationMode selected = ConversationMode.find(modeId);
        if (selected == null) {
            return;
        }
        sessionId++;
        messages.clear();
        nextId = 1;
        mode.setValue(selected);
        feedback.setValue(null);
        errorMessage.setValue(null);
        premiumRequired.setValue(null);
        loading.setValue(false);
        request(false);
    }

    //Bấm Gửi. Trả về true nếu tin được nhận (giao diện xóa ô nhập); false khi đang bận, đã đầy hoặc ô nhập trống
    public boolean send(String input) {
        String text = input == null ? "" : input.trim();
        //mode == null: tiến trình bị hệ thống thu hồi rồi khôi phục thẳng vào màn chat, chưa chọn tình huống
        if (text.isEmpty() || mode.getValue() == null || isBusy() || isFull()) {
            return false;
        }
        errorMessage.setValue(null);
        messages.add(new ChatItem(nextId++, ChatItem.TYPE_USER, text, false));
        request(false);
        return true;
    }

    //Bấm "Kết thúc buổi": xin nhận xét; thành công thì feedback khác null
    public void finishSession() {
        if (mode.getValue() == null || isBusy() || !hasUserMessage()) {
            return;
        }
        errorMessage.setValue(null);
        request(true);
    }

    //Gửi toàn bộ lịch sử hiện có; finish = false nhận câu tiếp theo của AI, finish = true nhận nhận xét
    private void request(boolean finish) {
        int session = sessionId;
        loading.setValue(true);
        publish();

        LiveData<ApiResult<AiConversationResponse>> source =
                aiRepository.conversation(mode.getValue().getId(), toTurns(), finish);
        //Nghe một lần rồi gỡ (giống AiChatViewModel)
        source.observeForever(new Observer<ApiResult<AiConversationResponse>>() {
            @Override
            public void onChanged(ApiResult<AiConversationResponse> apiResult) {
                source.removeObserver(this);
                if (session != sessionId) {
                    return;
                }
                loading.setValue(false);
                if (finish) {
                    handleFeedback(apiResult);
                } else {
                    handleReply(apiResult);
                }
            }
        });
    }

    private void handleReply(ApiResult<AiConversationResponse> apiResult) {
        AiConversationResponse body = apiResult.getData();
        if (apiResult.isSuccess() && body != null && body.reply != null && !body.reply.trim().isEmpty()) {
            messages.add(new ChatItem(nextId++, ChatItem.TYPE_BOT, body.reply.trim(), false));
            if (isFull()) {
                errorMessage.setValue(R.string.ai_conversation_full);
            }
        } else {
            //400: nội dung lượt vừa gửi bị từ chối (ví dụ chỉ toàn ký tự bị làm sạch). Bỏ lượt đó, nếu không mọi lượt sau
            //đều gửi kèm nó và bị từ chối theo. Lỗi khác (mất mạng, 429, 503) giữ nguyên lượt để lịch sử không mất
            if (apiResult.getHttpCode() == 400) {
                dropLastUserMessage();
            }
            handleFailure(apiResult);
        }
        publish();
    }

    private void handleFeedback(ApiResult<AiConversationResponse> apiResult) {
        AiConversationResponse body = apiResult.getData();
        if (apiResult.isSuccess() && body != null && body.summary != null && !body.summary.trim().isEmpty()) {
            feedback.setValue(body);
        } else {
            handleFailure(apiResult);
        }
        publish();
    }

    private void handleFailure(ApiResult<AiConversationResponse> apiResult) {
        if (apiResult.isPremiumRequired()) {
            premiumRequired.setValue(true);
        } else {
            errorMessage.setValue(AiErrorMessages.of(apiResult));
        }
    }

    private void dropLastUserMessage() {
        int last = messages.size() - 1;
        if (last >= 0 && messages.get(last).type == ChatItem.TYPE_USER) {
            messages.remove(last);
        }
    }

    private List<AiConversationRequest.Turn> toTurns() {
        List<AiConversationRequest.Turn> turns = new ArrayList<>();
        for (ChatItem item : messages) {
            turns.add(new AiConversationRequest.Turn(
                    item.type == ChatItem.TYPE_USER ? "user" : "assistant", item.text));
        }
        return turns;
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

    private void updateButtons() {
        boolean ready = Boolean.TRUE.equals(online.getValue()) && !isBusy();
        sendEnabled.setValue(ready && !isFull());
        finishEnabled.setValue(ready && hasUserMessage());
    }

    private boolean isBusy() {
        return Boolean.TRUE.equals(loading.getValue());
    }

    //Còn chỗ cho 1 tin của người học và 1 câu trả lời của AI thì mới cho gửi tiếp; nhờ vậy lịch sử luôn ≤ 30 tin
    private boolean isFull() {
        return messages.size() >= MAX_MESSAGES - 1;
    }

    private boolean hasUserMessage() {
        for (ChatItem item : messages) {
            if (item.type == ChatItem.TYPE_USER) {
                return true;
            }
        }
        return false;
    }

    public LiveData<ConversationMode> getMode() { return mode; }
    public LiveData<List<ChatItem>> getItems() { return items; }
    //Lỗi hiện phía trên ô nhập; null = không lỗi
    public LiveData<Integer> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getPremiumRequired() { return premiumRequired; }
    public LiveData<AiConversationResponse> getFeedback() { return feedback; }
    public LiveData<Boolean> getSendEnabled() { return sendEnabled; }
    public LiveData<Boolean> getFinishEnabled() { return finishEnabled; }
    public LiveData<Boolean> getOnline() { return online; }
}