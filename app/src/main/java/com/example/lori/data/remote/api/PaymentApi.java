package com.example.lori.data.remote.api;

import com.example.lori.data.remote.dto.MockPurchaseRequest;
import com.example.lori.data.remote.dto.SubscriptionResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

//Endpoint thanh toán của Backend. Hiện chỉ có mua giả lập (Mock, $0) theo kế hoạch mục 13.3
public interface PaymentApi {

    @POST("api/payment/mock-purchase")
    Call<SubscriptionResponse> mockPurchase(@Body MockPurchaseRequest request);
}