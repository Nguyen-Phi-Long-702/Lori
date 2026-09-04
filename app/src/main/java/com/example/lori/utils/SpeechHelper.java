package com.example.lori.utils;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

//Quản lý chức năng chuyển văn bản thành giọng nói.
public class SpeechHelper implements TextToSpeech.OnInitListener {

    private static volatile SpeechHelper INSTANCE;

    private final TextToSpeech tts;
    private boolean ready = false;

    private SpeechHelper(Context context) {
        tts = new TextToSpeech(context.getApplicationContext(), this);
    }

    //Lấy thực thể dùng chung duy nhất, tạo mới nếu đây là lần gọi đầu tiên
    public static SpeechHelper getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SpeechHelper.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SpeechHelper(context);
                }
            }
        }
        return INSTANCE;
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            //Sử dụng giọng đọc tiếng Anh-Mỹ cho từ vựng
            int result = tts.setLanguage(Locale.US);
            ready = result != TextToSpeech.LANG_MISSING_DATA
                    && result != TextToSpeech.LANG_NOT_SUPPORTED;
        }
    }

    //Phát âm đoạn văn bản nếu texttospeech đã sẵn sàng
    public boolean speak(String text, String utteranceId) {
        if (ready && text != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
            return true;
        }
        return false;
    }
}