package com.example.lori.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.lori.data.remote.api.AiApi;
import com.example.lori.data.remote.dto.AiAnalyzeTextRequest;
import com.example.lori.data.remote.dto.AiChatRequest;
import com.example.lori.data.remote.dto.AiChatResponse;
import com.example.lori.data.remote.dto.AiConversationRequest;
import com.example.lori.data.remote.dto.AiConversationResponse;
import com.example.lori.data.remote.dto.AiExamAnalysisRequest;
import com.example.lori.data.remote.dto.AiExamAnalysisResponse;
import com.example.lori.data.remote.dto.AiExerciseResponse;
import com.example.lori.data.remote.dto.AiGenerateExerciseRequest;
import com.example.lori.data.remote.dto.AiGrammarResponse;
import com.example.lori.data.remote.dto.AiQuizAnalysisRequest;
import com.example.lori.data.remote.dto.AiQuizAnalysisResponse;
import com.example.lori.data.remote.dto.AiRecommendResponse;
import com.example.lori.data.remote.dto.AiImageResponse;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

import java.util.List;

//Gọi AI Tutor qua Backend (Premium). User Free nhận HTTP_ERROR 403 với ApiResult.isPremiumRequired() = true
public class AiRepository extends BaseRepository {

    private static final String TYPE_GRAMMAR = "grammar";

    private final AiApi aiApi;

    public AiRepository(Context context) {
        super(context);
        aiApi = createApi(AiApi.class);
    }

    //Hỏi đáp tự do (POST /api/ai/chat)
    public LiveData<ApiResult<AiChatResponse>> chat(String message) {
        return execute(aiApi.chat(new AiChatRequest(message)));
    }

    //Một lượt hội thoại (POST /api/ai/conversation). turns là toàn bộ lịch sử từ đầu buổi; finish = true để nhận nhận xét cuối buổi
    public LiveData<ApiResult<AiConversationResponse>> conversation(
            String mode, List<AiConversationRequest.Turn> turns, boolean finish) {
        return execute(aiApi.conversation(new AiConversationRequest(mode, turns, finish)));
    }

    //Kiểm tra ngữ pháp một câu (POST /api/ai/analyze-text, type = grammar)
    public LiveData<ApiResult<AiGrammarResponse>> analyzeGrammar(String text) {
        return execute(aiApi.analyzeGrammar(new AiAnalyzeTextRequest(TYPE_GRAMMAR, text)));
    }

    //Phân tích kết quả một quiz đã đồng bộ lên server (POST /api/ai/analyze-quiz). quizType: "vocabulary" hoặc "grammar"
    public LiveData<ApiResult<AiQuizAnalysisResponse>> analyzeQuiz(String quizType, int itemId) {
        return execute(aiApi.analyzeQuiz(new AiQuizAnalysisRequest(quizType, itemId)));
    }

    //Phân tích một lần thi đã chấm (POST /api/ai/analyze-exam)
    public LiveData<ApiResult<AiExamAnalysisResponse>> analyzeExam(String resultId) {
        return execute(aiApi.analyzeExam(new AiExamAnalysisRequest(resultId)));
    }

    //Sinh bài tập cho một chủ đề (POST /api/ai/generate-exercise). category: "grammar" hoặc "vocabulary"
    public LiveData<ApiResult<AiExerciseResponse>> generateExercise(String category, int topicId) {
        return execute(aiApi.generateExercise(new AiGenerateExerciseRequest(category, topicId)));
    }

    //Nhận xét và đề xuất ôn tập theo các chủ đề yếu nhất (POST /api/ai/recommend)
    public LiveData<ApiResult<AiRecommendResponse>> recommend() {
        return execute(aiApi.recommend());
    }

    //Nhận diện vật thể trong ảnh (POST /api/ai/identify-image). jpeg là byte ảnh JPEG đã thu nhỏ (ImagePreparer)
    public LiveData<ApiResult<AiImageResponse>> identifyImage(byte[] jpeg) {
        RequestBody body = RequestBody.create(jpeg, MediaType.parse("image/jpeg"));
        return execute(aiApi.identifyImage(MultipartBody.Part.createFormData("image", "image.jpg", body)));
    }
}