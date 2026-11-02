package com.example.lori.model;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.example.lori.R;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

//Một tình huống hội thoại luyện tập. id phải khớp mã mode ở Backend (AiConversationRequest) và AI Service (conversation_modes.py)
public class ConversationMode {

    //Thứ tự hiển thị: 4 tình huống của UI mẫu trước, rồi English Teacher, Conversation Partner, IELTS Speaking
    public static final List<ConversationMode> ALL = Collections.unmodifiableList(Arrays.asList(
            new ConversationMode("restaurant", R.string.ai_conversation_mode_restaurant,
                    R.string.ai_conversation_role_restaurant, R.drawable.ic_feature_restaurant),
            new ConversationMode("airport", R.string.ai_conversation_mode_airport,
                    R.string.ai_conversation_role_airport, R.drawable.ic_feature_airport),
            new ConversationMode("interview", R.string.ai_conversation_mode_interview,
                    R.string.ai_conversation_role_interview, R.drawable.ic_feature_interview),
            new ConversationMode("daily", R.string.ai_conversation_mode_daily,
                    R.string.ai_conversation_role_daily, R.drawable.ic_feature_daily),
            new ConversationMode("teacher", R.string.ai_conversation_mode_teacher,
                    R.string.ai_conversation_role_teacher, R.drawable.ic_feature_teacher),
            new ConversationMode("partner", R.string.ai_conversation_mode_partner,
                    R.string.ai_conversation_role_partner, R.drawable.ic_feature_partner),
            new ConversationMode("ielts_speaking", R.string.ai_conversation_mode_ielts_speaking,
                    R.string.ai_conversation_role_ielts_speaking, R.drawable.ic_feature_speaking)));

    private final String id;
    @StringRes
    private final int titleRes;
    //Dòng phụ ở đầu màn chat: AI đang đóng vai gì
    @StringRes
    private final int roleRes;
    @DrawableRes
    private final int iconRes;

    private ConversationMode(String id, int titleRes, int roleRes, int iconRes) {
        this.id = id;
        this.titleRes = titleRes;
        this.roleRes = roleRes;
        this.iconRes = iconRes;
    }

    //Trả về null nếu id không thuộc danh sách
    public static ConversationMode find(String id) {
        for (ConversationMode mode : ALL) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return null;
    }

    public String getId() { return id; }
    public int getTitleRes() { return titleRes; }
    public int getRoleRes() { return roleRes; }
    public int getIconRes() { return iconRes; }
}