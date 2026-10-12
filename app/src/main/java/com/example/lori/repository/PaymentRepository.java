package com.example.lori.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.lori.data.remote.api.PaymentApi;
import com.example.lori.data.remote.dto.MockPurchaseRequest;
import com.example.lori.data.remote.dto.SubscriptionResponse;

//Mua Premium. Hiện chỉ có mua giả lập (Mock, $0) theo kế hoạch mục 13.3
public class PaymentRepository extends BaseRepository {

    private final PaymentApi paymentApi;

    public PaymentRepository(Context context) {
        super(context);
        paymentApi = createApi(PaymentApi.class);
    }

    //planType: "MONTHLY", "YEARLY" hoặc "LIFETIME". Thành công = HTTP 201 và user đã là Premium
    //Lỗi hay gặp: 409 (đã có Premium vĩnh viễn), 403 (Backend tắt mua thử: PAYMENT_MOCK_ENABLED=false)
    public LiveData<ApiResult<SubscriptionResponse>> mockPurchase(String planType) {
        return execute(paymentApi.mockPurchase(new MockPurchaseRequest(planType)));
    }
}