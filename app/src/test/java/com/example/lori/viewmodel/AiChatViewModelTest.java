package com.example.lori.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.app.Application;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.lori.R;
import com.example.lori.data.remote.dto.AiChatResponse;
import com.example.lori.repository.AiRepository;
import com.example.lori.repository.ApiResult;
import com.example.lori.utils.NetworkStateManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

public class AiChatViewModelTest {

    private static final String WELCOME = "Xin chào!";

    //Cho LiveData chạy đồng bộ trên luồng test, không cần Looper của Android
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private final MutableLiveData<Boolean> online = new MutableLiveData<>(true);

    private MockedStatic<NetworkStateManager> networkStateManagerStatic;
    private MockedConstruction<AiRepository> aiRepositoryConstruction;
    private AiRepository aiRepository;
    private AiChatViewModel viewModel;

    @Before
    public void setUp() {
        Application application = mock(Application.class);
        when(application.getString(R.string.ai_chat_welcome)).thenReturn(WELCOME);
        NetworkStateManager networkStateManager = mock(NetworkStateManager.class);
        when(networkStateManager.getOnlineState()).thenReturn(online);
        networkStateManagerStatic = Mockito.mockStatic(NetworkStateManager.class);
        networkStateManagerStatic.when(() -> NetworkStateManager.getInstance(application))
                .thenReturn(networkStateManager);
        //ViewModel tự tạo AiRepository nên thay phép tạo này bằng đối tượng giả
        aiRepositoryConstruction = Mockito.mockConstruction(AiRepository.class);

        viewModel = new AiChatViewModel(application);
        aiRepository = aiRepositoryConstruction.constructed().get(0);
    }

    @After
    public void tearDown() {
        aiRepositoryConstruction.close();
        networkStateManagerStatic.close();
    }

    @Test
    public void start_showsWelcomeMessageFromBot() {
        List<ChatItem> items = viewModel.getItems().getValue();

        assertEquals(1, items.size());
        assertEquals(ChatItem.TYPE_BOT, items.get(0).type);
        assertEquals(WELCOME, items.get(0).text);
        assertFalse(items.get(0).truncated);
    }

    @Test
    public void send_blankInput_isRejectedWithoutCallingBackend() {
        assertFalse(viewModel.send("   "));
        assertFalse(viewModel.send(null));

        assertEquals(1, viewModel.getItems().getValue().size());
        verifyNoInteractions(aiRepository);
    }

    @Test
    public void send_success_appendsUserMessageThenTrimmedAnswer() {
        when(aiRepository.chat("Hello")).thenReturn(resultOf(ApiResult.success(answer("  Hi there  ", false), 200)));

        assertTrue(viewModel.send("  Hello  "));

        List<ChatItem> items = viewModel.getItems().getValue();
        assertEquals(3, items.size());
        assertEquals(ChatItem.TYPE_USER, items.get(1).type);
        assertEquals("Hello", items.get(1).text);
        assertEquals(ChatItem.TYPE_BOT, items.get(2).type);
        assertEquals("Hi there", items.get(2).text);
        assertNull(viewModel.getErrorMessage().getValue());
    }

    @Test
    public void send_success_keepsTruncatedFlagOfTheAnswer() {
        when(aiRepository.chat("Hello")).thenReturn(resultOf(ApiResult.success(answer("Hi", true), 200)));

        viewModel.send("Hello");

        List<ChatItem> items = viewModel.getItems().getValue();
        assertTrue(items.get(items.size() - 1).truncated);
    }

    @Test
    public void send_whileWaiting_showsTypingBubbleAndRejectsSecondSend() {
        MutableLiveData<ApiResult<AiChatResponse>> pending = new MutableLiveData<>();
        when(aiRepository.chat("Hello")).thenReturn(pending);

        assertTrue(viewModel.send("Hello"));

        List<ChatItem> waiting = viewModel.getItems().getValue();
        assertEquals(3, waiting.size());
        assertEquals(ChatItem.TYPE_TYPING, waiting.get(2).type);
        assertEquals(ChatItem.TYPING_ID, waiting.get(2).id);
        assertFalse(viewModel.send("Again"));
        verify(aiRepository, times(1)).chat(anyString());

        pending.setValue(ApiResult.success(answer("Hi", false), 200));

        List<ChatItem> done = viewModel.getItems().getValue();
        assertEquals(3, done.size());
        assertEquals(ChatItem.TYPE_BOT, done.get(2).type);
    }

    @Test
    public void send_premiumRequired_asksToUpgradeWithoutShowingAnError() {
        when(aiRepository.chat("Hello"))
                .thenReturn(resultOf(ApiResult.httpError(403, ApiResult.PREMIUM_REQUIRED)));

        viewModel.send("Hello");

        assertEquals(Boolean.TRUE, viewModel.getPremiumRequired().getValue());
        assertNull(viewModel.getErrorMessage().getValue());
        assertEquals(2, viewModel.getItems().getValue().size());

        viewModel.consumePremiumRequired();

        assertNull(viewModel.getPremiumRequired().getValue());
    }

    @Test
    public void send_connectionProblems_mapToMessages() {
        assertEquals(R.string.ai_offline_hint, errorAfterSending(ApiResult.offline()));
        assertEquals(R.string.auth_error_timeout, errorAfterSending(ApiResult.timeout()));
        assertEquals(R.string.auth_error_network, errorAfterSending(ApiResult.networkError()));
    }

    @Test
    public void send_httpErrors_mapToMessages() {
        assertEquals(R.string.ai_error_invalid_input, errorAfterSending(ApiResult.httpError(400, null)));
        assertEquals(R.string.premium_error_session_expired, errorAfterSending(ApiResult.httpError(401, null)));
        //403 không có PREMIUM_REQUIRED là Backend từ chối ứng dụng
        assertEquals(R.string.auth_error_forbidden, errorAfterSending(ApiResult.httpError(403, null)));
        assertEquals(R.string.ai_error_not_found, errorAfterSending(ApiResult.httpError(404, null)));
        assertEquals(R.string.ai_error_too_many_requests, errorAfterSending(ApiResult.httpError(429, null)));
        assertEquals(R.string.ai_error_unavailable, errorAfterSending(ApiResult.httpError(503, null)));
        assertEquals(R.string.auth_error_server, errorAfterSending(ApiResult.httpError(500, null)));
    }

    @Test
    public void send_blankAnswer_isTreatedAsServerError() {
        when(aiRepository.chat("Hello")).thenReturn(resultOf(ApiResult.success(answer("  ", false), 200)));

        viewModel.send("Hello");

        assertEquals(Integer.valueOf(R.string.auth_error_server), viewModel.getErrorMessage().getValue());
        assertEquals(2, viewModel.getItems().getValue().size());
    }

    @Test
    public void send_afterAnError_clearsTheOldError() {
        errorAfterSending(ApiResult.timeout());
        when(aiRepository.chat("Hello")).thenReturn(resultOf(ApiResult.success(answer("Hi", false), 200)));

        viewModel.send("Hello");

        assertNull(viewModel.getErrorMessage().getValue());
    }

    @Test
    public void sendEnabled_followsOnlineStateAndWaiting() {
        //MediatorLiveData chỉ tính giá trị khi có observer
        viewModel.getSendEnabled().observeForever(enabled -> { });
        assertEquals(Boolean.TRUE, viewModel.getSendEnabled().getValue());

        online.setValue(false);
        assertEquals(Boolean.FALSE, viewModel.getSendEnabled().getValue());

        online.setValue(true);
        assertEquals(Boolean.TRUE, viewModel.getSendEnabled().getValue());

        MutableLiveData<ApiResult<AiChatResponse>> pending = new MutableLiveData<>();
        when(aiRepository.chat("Hello")).thenReturn(pending);
        viewModel.send("Hello");
        assertEquals(Boolean.FALSE, viewModel.getSendEnabled().getValue());

        pending.setValue(ApiResult.success(answer("Hi", false), 200));
        assertEquals(Boolean.TRUE, viewModel.getSendEnabled().getValue());
    }

    //Giả lập Backend trả đúng kết quả này cho một lần gửi, rồi trả về mã thông báo lỗi đang hiện
    private int errorAfterSending(ApiResult<AiChatResponse> result) {
        when(aiRepository.chat("Hello")).thenReturn(resultOf(result));
        viewModel.send("Hello");
        return viewModel.getErrorMessage().getValue();
    }

    private static AiChatResponse answer(String text, boolean truncated) {
        AiChatResponse response = new AiChatResponse();
        response.type = "chat";
        response.answer = text;
        response.truncated = truncated;
        return response;
    }

    private static LiveData<ApiResult<AiChatResponse>> resultOf(ApiResult<AiChatResponse> result) {
        return new MutableLiveData<>(result);
    }
}