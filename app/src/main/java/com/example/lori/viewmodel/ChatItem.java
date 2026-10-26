package com.example.lori.viewmodel;

//Một dòng trong khung chat: tin của user, câu trả lời của AI hoặc bong bóng "đang trả lời"
public class ChatItem {

    public static final int TYPE_USER = 0;
    public static final int TYPE_BOT = 1;
    public static final int TYPE_TYPING = 2;

    //Id cố định của bong bóng "đang trả lời" (luôn chỉ có một)
    static final long TYPING_ID = -1;

    public final long id;
    public final int type;
    public final String text;
    //true khi AI Service cắt bớt câu trả lời vì quá dài
    public final boolean truncated;

    public ChatItem(long id, int type, String text, boolean truncated) {
        this.id = id;
        this.type = type;
        this.text = text;
        this.truncated = truncated;
    }
}