package com.example.lori.viewmodel;

import com.example.lori.data.local.entity.GrammarQuiz;
import com.example.lori.data.local.entity.VocabQuiz;

public class QuizQuestion {
    public final String question;
    public final String optionA;
    public final String optionB;
    public final String optionC;
    public final String optionD;
    public final String correctAnswer;
    public final String explanation;

    public QuizQuestion(String question, String optionA, String optionB, String optionC,
                        String optionD, String correctAnswer, String explanation) {
        this.question = question;
        this.optionA = optionA;
        this.optionB = optionB;
        this.optionC = optionC;
        this.optionD = optionD;
        this.correctAnswer = correctAnswer;
        this.explanation = explanation;
    }

    public static QuizQuestion fromVocabQuiz(VocabQuiz quiz) {
        return new QuizQuestion(quiz.question, quiz.optionA, quiz.optionB, quiz.optionC,
                quiz.optionD, quiz.correctAnswer, quiz.explanation);
    }

    public static QuizQuestion fromGrammarQuiz(GrammarQuiz quiz) {
        return new QuizQuestion(quiz.question, quiz.optionA, quiz.optionB, quiz.optionC,
                quiz.optionD, quiz.correctAnswer, quiz.explanation);
    }
}