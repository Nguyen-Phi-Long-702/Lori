package com.example.lori.data.local;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.test.platform.app.InstrumentationRegistry;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class LiveDataTestUtil {

    public static <T> T getOrAwaitValue(final LiveData<T> liveData) throws InterruptedException {
        final Object[] data = new Object[1];
        final CountDownLatch latch = new CountDownLatch(1);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Observer<T> observer = new Observer<T>() {
                @Override
                public void onChanged(T value) {
                    data[0] = value;
                    latch.countDown();
                    liveData.removeObserver(this);
                }
            };
            liveData.observeForever(observer);
        });

        if (!latch.await(2, TimeUnit.SECONDS)) {
            throw new RuntimeException("LiveData value was never set.");
        }

        @SuppressWarnings("unchecked")
        T result = (T) data[0];
        return result;
    }
}