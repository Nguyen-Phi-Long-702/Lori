package com.example.lori.security;

import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private static final String REMOTE_ADDR = "198.51.100.9";

    @Test
    void motProxy_layPhanTuCuoiCuaXForwardedFor() throws Exception {
        assertThat(keyFor("203.0.113.7, 192.0.2.1", 1)).isEqualTo("rate_limit:192.0.2.1");
    }

    @Test
    void haiProxy_layPhanTuThuHaiTinhTuCuoi() throws Exception {
        assertThat(keyFor("203.0.113.7, 192.0.2.1, 10.0.0.5", 2)).isEqualTo("rate_limit:192.0.2.1");
    }

    @Test
    void khachGiaIpOBenTrai_khongDoiDuocKey() throws Exception {
        assertThat(keyFor("1.1.1.1, 192.0.2.1", 1)).isEqualTo(keyFor("2.2.2.2, 192.0.2.1", 1));
    }

    @Test
    void headerNganHonSoProxy_layPhanTuDau() throws Exception {
        assertThat(keyFor("203.0.113.7", 2)).isEqualTo("rate_limit:203.0.113.7");
    }

    @Test
    void khongCoHoacHongHeader_dungIpKetNoi() throws Exception {
        assertThat(keyFor(null, 1)).isEqualTo("rate_limit:" + REMOTE_ADDR);
        assertThat(keyFor(",", 1)).isEqualTo("rate_limit:" + REMOTE_ADDR);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void soProxyNhoHon1_khoiTaoThatBai() {
        ProxyManager<String> proxyManager = mock(ProxyManager.class);

        assertThatThrownBy(() -> new RateLimitFilter(proxyManager, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String keyFor(String forwardedFor, int trustedProxyCount) throws Exception {
        ProxyManager<String> proxyManager = mock(ProxyManager.class);
        BucketProxy bucket = mock(BucketProxy.class);
        ConsumptionProbe probe = mock(ConsumptionProbe.class);
        when(probe.isConsumed()).thenReturn(true);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(proxyManager.getProxy(anyString(), any(Supplier.class))).thenReturn(bucket);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.setRemoteAddr(REMOTE_ADDR);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }

        new RateLimitFilter(proxyManager, trustedProxyCount)
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(proxyManager).getProxy(key.capture(), any(Supplier.class));
        return key.getValue();
    }
}
