package com.example.lori.scheduler;

import com.example.lori.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Chay dinh ky (mac dinh moi gio): danh dau goi het han va ha quyen Premium cua user het han.
 * Neu job loi, Spring ghi log va van chay lan sau. PremiumAspect van kiem tra han theo thoi gian thuc
 * nen job tre khong lam user het han duoc dung Premium.
 */
@Component
@RequiredArgsConstructor
public class SubscriptionExpiryScheduler {

    private final SubscriptionService subscriptionService;

    @Scheduled(cron = "${app.subscription.expiry-cron}")
    public void expireSubscriptions() {
        subscriptionService.expireDueSubscriptions();
    }
}