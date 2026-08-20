package com.example.lori.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.lori.data.local.dao.ChatMessageDao;
import com.example.lori.data.local.dao.DictionaryDao;
import com.example.lori.data.local.dao.GrammarDao;
import com.example.lori.data.local.dao.ProgressDao;
import com.example.lori.data.local.dao.QuizDao;
import com.example.lori.data.local.dao.TopicDao;
import com.example.lori.data.local.dao.TopicWordDao;
import com.example.lori.data.local.entity.ChatMessage;
import com.example.lori.data.local.entity.Definition;
import com.example.lori.data.local.entity.DictionaryWord;
import com.example.lori.data.local.entity.GrammarExample;
import com.example.lori.data.local.entity.GrammarLesson;
import com.example.lori.data.local.entity.GrammarQuiz;
import com.example.lori.data.local.entity.Topic;
import com.example.lori.data.local.entity.TopicWord;
import com.example.lori.data.local.entity.UserProgressLocal;
import com.example.lori.data.local.entity.VocabQuiz;
import com.example.lori.data.local.entity.WordDefinition;
import com.example.lori.data.local.entity.WordPronunciation;
import com.example.lori.data.local.entity.WordRelation;

@Database(
        entities = {
                DictionaryWord.class, Definition.class, WordPronunciation.class,
                WordDefinition.class, WordRelation.class,
                Topic.class, TopicWord.class,
                GrammarLesson.class, GrammarExample.class,
                VocabQuiz.class, GrammarQuiz.class,
                UserProgressLocal.class, ChatMessage.class
        },
        version = 1,
        exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    public abstract DictionaryDao dictionaryDao();
    public abstract TopicDao topicDao();
    public abstract TopicWordDao topicWordDao();
    public abstract GrammarDao grammarDao();
    public abstract QuizDao quizDao();
    public abstract ProgressDao progressDao();
    public abstract ChatMessageDao chatMessageDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "dictionary.db"
                            )
                            .createFromAsset("databases/dictionary.db")
                            .addCallback(new Callback() {
                                @Override
                                public void onOpen(@NonNull SupportSQLiteDatabase db) {
                                    super.onOpen(db);
                                    db.execSQL("PRAGMA case_sensitive_like = ON");
                                }
                            })
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}