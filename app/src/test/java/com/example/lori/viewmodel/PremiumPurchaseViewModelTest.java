package com.example.lori.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.lori.R;
import com.example.lori.data.remote.dto.SubscriptionResponse;
import com.example.lori.repository.ApiResult;
import com.example.lori.repository.PaymentRepository;
import com.example.lori.utils.NetworkStateManager;
import com.example.lori.utils.PremiumManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class PremiumPurchaseViewModelTest {

    //Cho LiveData chạy đồng bộ trên luồng test, không cần Looper của Android
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private final MutableLiveData<Boolean> online = new MutableLiveData<>(true);

    private Application application;
    private MockedStatic<NetworkStateManager> networkStateManagerStatic;
    private MockedStatic<PremiumManager> premiumManagerStatic;
    private MockedConstruction<PaymentRepository> paymentRepositoryConstruction;
    private PaymentRepository paymentRepository;
    private PremiumPurchaseViewModel viewModel;

    @Before
    public void setUp() {
        application = mock(Application.class);
        NetworkStateManager networkStateManager = mock(NetworkStateManager.class);
        when(networkStateManager.getOnlineState()).thenReturn(online);
        networkStateManagerStatic = Mockito.mockStatic(NetworkStateManager.class);
        networkStateManagerStatic.when(() -> NetworkStateManager.getInstance(application))
                .thenReturn(networkStateManager);
        premiumManagerStatic = Mockito.mockStatic(PremiumManager.class);
        //ViewModel tự tạo PaymentRepository nên thay phép tạo này bằng đối tượng giả
        paymentRepositoryConstruction = Mockito.mockConstruction(PaymentRepository.class);

        viewModel = new PremiumPurchaseViewModel(application);
        paymentRepository = paymentRepositoryConstruction.constructed().get(0);
    }

    @After
    public void tearDown() {
        paymentRepositoryConstruction.close();
        premiumManagerStatic.close();
        networkStateManagerStatic.close();
    }

    @Test
    public void selectedPlan_startsAsYearly() {
        assertEquals(PremiumPurchaseViewModel.PLAN_YEARLY, viewModel.getSelectedPlan().getValue());
    }

    @Test
    public void selectPlan_changesTheSelectedPlan() {
        viewModel.selectPlan(PremiumPurchaseViewModel.PLAN_LIFETIME);

        assertEquals(PremiumPurchaseViewModel.PLAN_LIFETIME, viewModel.getSelectedPlan().getValue());
    }

    @Test
    public void purchase_success_savesPremiumFlagAndMarksPurchased() {
        when(paymentRepository.mockPurchase(PremiumPurchaseViewModel.PLAN_YEARLY))
                .thenReturn(resultOf(ApiResult.success(new SubscriptionResponse(), 201)));

        viewModel.purchase();

        assertEquals(Boolean.TRUE, viewModel.getPurchased().getValue());
        assertEquals(Boolean.FALSE, viewModel.getLoading().getValue());
        assertNull(viewModel.getErrorMessage().getValue());
        premiumManagerStatic.verify(() -> PremiumManager.setPremium(application, true));
    }

    @Test
    public void purchase_usesTheSelectedPlan() {
        when(paymentRepository.mockPurchase(PremiumPurchaseViewModel.PLAN_MONTHLY))
                .thenReturn(resultOf(ApiResult.success(new SubscriptionResponse(), 201)));
        viewModel.selectPlan(PremiumPurchaseViewModel.PLAN_MONTHLY);

        viewModel.purchase();

        verify(paymentRepository).mockPurchase(PremiumPurchaseViewModel.PLAN_MONTHLY);
        assertEquals(Boolean.TRUE, viewModel.getPurchased().getValue());
    }

    @Test
    public void purchase_failure_doesNotSavePremiumFlag() {
        purchaseWith(ApiResult.httpError(409, null));

        assertEquals(Boolean.FALSE, viewModel.getPurchased().getValue());
        assertEquals(Boolean.FALSE, viewModel.getLoading().getValue());
        premiumManagerStatic.verify(() -> PremiumManager.setPremium(any(), anyBoolean()), never());
    }

    @Test
    public void purchase_connectionProblems_mapToMessages() {
        purchaseWith(ApiResult.offline());
        assertError(R.string.premium_offline_note);

        purchaseWith(ApiResult.timeout());
        assertError(R.string.auth_error_timeout);

        purchaseWith(ApiResult.networkError());
        assertError(R.string.auth_error_network);
    }

    @Test
    public void purchase_httpErrors_mapToMessages() {
        purchaseWith(ApiResult.httpError(401, null));
        assertError(R.string.premium_error_session_expired);

        purchaseWith(ApiResult.httpError(403, null));
        assertError(R.string.premium_error_forbidden);

        purchaseWith(ApiResult.httpError(409, null));
        assertError(R.string.premium_error_already_lifetime);

        purchaseWith(ApiResult.httpError(429, null));
        assertError(R.string.auth_error_too_many_requests);

        purchaseWith(ApiResult.httpError(500, null));
        assertError(R.string.auth_error_server);
    }

    @Test
    public void purchase_whileProcessing_ignoresSecondPurchaseAndPlanChange() {
        when(paymentRepository.mockPurchase(PremiumPurchaseViewModel.PLAN_YEARLY))
                .thenReturn(new MutableLiveData<>());

        viewModel.purchase();
        viewModel.purchase();
        viewModel.selectPlan(PremiumPurchaseViewModel.PLAN_MONTHLY);

        assertEquals(Boolean.TRUE, viewModel.getLoading().getValue());
        assertEquals(PremiumPurchaseViewModel.PLAN_YEARLY, viewModel.getSelectedPlan().getValue());
        verify(paymentRepository, times(1)).mockPurchase(PremiumPurchaseViewModel.PLAN_YEARLY);
    }

    @Test
    public void purchase_afterSuccess_isIgnored() {
        when(paymentRepository.mockPurchase(PremiumPurchaseViewModel.PLAN_YEARLY))
                .thenReturn(resultOf(ApiResult.success(new SubscriptionResponse(), 201)));
        viewModel.purchase();

        viewModel.purchase();

        verify(paymentRepository, times(1)).mockPurchase(PremiumPurchaseViewModel.PLAN_YEARLY);
    }

    @Test
    public void purchase_clearsThePreviousError() {
        purchaseWith(ApiResult.httpError(500, null));
        assertError(R.string.auth_error_server);
        when(paymentRepository.mockPurchase(PremiumPurchaseViewModel.PLAN_YEARLY))
                .thenReturn(new MutableLiveData<>());

        viewModel.purchase();

        assertNull(viewModel.getErrorMessage().getValue());
    }

    @Test
    public void purchaseEnabled_followsOnlineStateAndProcessing() {
        //MediatorLiveData chỉ tính giá trị khi có observer
        viewModel.getPurchaseEnabled().observeForever(enabled -> { });
        assertEquals(Boolean.TRUE, viewModel.getPurchaseEnabled().getValue());

        online.setValue(false);
        assertEquals(Boolean.FALSE, viewModel.getPurchaseEnabled().getValue());

        online.setValue(true);
        assertEquals(Boolean.TRUE, viewModel.getPurchaseEnabled().getValue());

        when(paymentRepository.mockPurchase(PremiumPurchaseViewModel.PLAN_YEARLY))
                .thenReturn(new MutableLiveData<>());
        viewModel.purchase();
        assertEquals(Boolean.FALSE, viewModel.getPurchaseEnabled().getValue());
    }

    //Giả lập Backend trả đúng kết quả này cho lần mua của gói đang chọn (mặc định Gói Năm)
    private void purchaseWith(ApiResult<SubscriptionResponse> result) {
        when(paymentRepository.mockPurchase(PremiumPurchaseViewModel.PLAN_YEARLY)).thenReturn(resultOf(result));
        viewModel.purchase();
    }

    private void assertError(int expectedMessageRes) {
        assertEquals(Integer.valueOf(expectedMessageRes), viewModel.getErrorMessage().getValue());
    }

    private static LiveData<ApiResult<SubscriptionResponse>> resultOf(ApiResult<SubscriptionResponse> result) {
        return new MutableLiveData<>(result);
    }
}