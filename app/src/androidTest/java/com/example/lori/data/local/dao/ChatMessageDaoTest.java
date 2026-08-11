package com.example.lori.data.local.dao;

import static com.example.lori.data.local.LiveDataTestUtil.getOrAwaitValue;
import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.local.entity.ChatMessage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Date;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ChatMessageDaoTest {

    private AppDatabase db;
    private ChatMessageDao chatMessageDao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        chatMessageDao = db.chatMessageDao();
    }

    @After
    public void closeDb() throws IOException {
        db.close();
    }

    @Test
    public void insertAndGetAllMessages() throws InterruptedException {
        ChatMessage msg1 = new ChatMessage();
        msg1.role = "user";
        msg1.content = "Xin chào";
        msg1.timestamp = new Date(1000L);
        msg1.isError = false;
        chatMessageDao.insertMessage(msg1);

        ChatMessage msg2 = new ChatMessage();
        msg2.role = "bot";
        msg2.content = "Chào bạn!";
        msg2.timestamp = new Date(2000L);
        msg2.isError = false;
        chatMessageDao.insertMessage(msg2);

        List<ChatMessage> result = getOrAwaitValue(chatMessageDao.getAllMessages());

        assertEquals(2, result.size());
        assertEquals("Xin chào", result.get(0).content);
    }

    @Test
    public void deleteMessage() throws InterruptedException {
        ChatMessage msg = new ChatMessage();
        msg.role = "user";
        msg.content = "Tạm biệt";
        msg.timestamp = new Date(3000L);
        msg.isError = false;
        chatMessageDao.insertMessage(msg);

        List<ChatMessage> beforeDelete = getOrAwaitValue(chatMessageDao.getAllMessages());
        chatMessageDao.deleteMessage(beforeDelete.get(0));

        List<ChatMessage> afterDelete = getOrAwaitValue(chatMessageDao.getAllMessages());
        assertEquals(0, afterDelete.size());
    }

    @Test
    public void clearAllMessages() throws InterruptedException {
        ChatMessage msg1 = new ChatMessage();
        msg1.role = "user";
        msg1.content = "A";
        msg1.timestamp = new Date(4000L);
        msg1.isError = false;
        chatMessageDao.insertMessage(msg1);

        ChatMessage msg2 = new ChatMessage();
        msg2.role = "bot";
        msg2.content = "B";
        msg2.timestamp = new Date(5000L);
        msg2.isError = true;
        chatMessageDao.insertMessage(msg2);

        chatMessageDao.clearAllMessages();

        List<ChatMessage> result = getOrAwaitValue(chatMessageDao.getAllMessages());
        assertEquals(0, result.size());
    }
}