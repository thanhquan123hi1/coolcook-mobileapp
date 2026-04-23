package com.coolcook.app.feature.chatbot.data;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.coolcook.app.BuildConfig;
import com.coolcook.app.feature.chatbot.model.ChatMessage;
import com.google.gson.annotations.SerializedName;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public class GeminiRepository {

    public interface StreamCallback {
        void onStart();

        void onChunk(@NonNull String accumulatedText);

        void onCompleted(@NonNull String finalText);

        void onError(@NonNull String friendlyError);
    }

    private static final String BASE_URL = "https://api.groq.com/openai/v1/";
    private static final String MODEL = "meta-llama/llama-4-scout-17b-16e-instruct";
    private static final String FRIENDLY_ERROR = "Ôi hehe, mình hơi lag một chút, bạn thử lại nhé!";
    private static final int MAX_HISTORY_MESSAGES = 8;
    private static final int MAX_HISTORY_TOTAL_CHARS = 1800;
    private static final int MAX_SINGLE_MESSAGE_CHARS = 360;
    private static final int MAX_USER_PROMPT_CHARS = 480;
    private static final int MAX_OUTPUT_TOKENS = 800;
    private static final String SYSTEM_PROMPT = "Bạn là AI Agent ẩm thực trong ứng dụng Android CoolCook. "
            + "Phản hồi phải là văn bản thuần tiếng Việt, không JSON, không ký tự lạ. "
            + "Nhiệm vụ bắt buộc: "
            + "(A) Nếu có ảnh món ăn: trước tiên nhận diện món chính trong ảnh và ghi đúng tên món đó, sau đó hướng dẫn nấu chính món vừa nhận diện. "
            + "(B) Nếu chỉ có văn bản: dùng món ăn người dùng nêu để trả công thức. "
            + "Không chào hỏi, không dẫn dắt, không kết luận dài dòng. "
            + "Yêu cầu chi tiết bắt buộc: "
            + "(1) định lượng rõ ràng theo g, ml, muỗng cà phê/canh hoặc số lượng, "
            + "(2) trong từng bước phải có thời gian ước tính và mức lửa (nhỏ/vừa/lớn), "
            + "(3) nêu nêm nếm ở các pha chính: ướp, nấu, nếm cuối, "
            + "(4) ưu tiên cách làm thực tế tại nhà, ít bước thừa. "
            + "Bắt buộc đúng khuôn mẫu sau:\n"
            + "### [Tên món ăn]\n"
            + "**Khẩu phần:** [2-4 người]\n"
            + "**Nguyên liệu:**\n"
            + "- [Nguyên liệu + định lượng]\n"
            + "- [Nguyên liệu + định lượng]\n\n"
            + "**Các bước thực hiện:**\n"
            + "1. [Bước 1 + thời gian + mức lửa + lượng nêm]\n"
            + "2. [Bước 2 + thời gian + mức lửa + lượng nêm]\n"
            + "3. [Bước 3 + nếm cuối và điều chỉnh vị]\n\n"
            + "**Mẹo tối ưu:**\n"
            + "- [1-2 mẹo ngắn, thực tế]\n"
            + "Nếu ảnh quá mờ hoặc không có món ăn rõ ràng, chỉ trả đúng một dòng: Mình chưa nhận diện rõ món trong ảnh, bạn gửi ảnh rõ hơn nhé. "
            + "Nếu đầu vào văn bản quá mơ hồ, chỉ trả đúng một dòng: Vui lòng nhập tên món ăn cụ thể.";

    private final GroqApiService groqApiService;
    private final ExecutorService executorService;

    public GeminiRepository() {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        this.groqApiService = retrofit.create(GroqApiService.class);
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public void streamChatResponse(
            @NonNull List<ChatMessage> history,
            @Nullable String userPrompt,
            @Nullable String imageMimeType,
            @Nullable String imageBase64,
            @NonNull StreamCallback callback) {

        final String apiKey = resolveApiKey();
        if (TextUtils.isEmpty(apiKey)) {
            callback.onError("Thiếu GROQ_API_KEY. Kiểm tra lại .env hoặc local.properties nha.");
            return;
        }

        final String normalizedPrompt = trimMessage(userPrompt, MAX_USER_PROMPT_CHARS);
        if (TextUtils.isEmpty(normalizedPrompt) && TextUtils.isEmpty(imageBase64)) {
            callback.onStart();
            callback.onChunk("Vui lòng nhập tên món ăn cụ thể hoặc gửi hình ảnh.");
            callback.onCompleted("Vui lòng nhập tên món ăn cụ thể hoặc gửi hình ảnh.");
            return;
        }

        executorService.execute(() -> {
            callback.onStart();
            try {
                ChatRequest requestBody = buildRequestBody(
                        history,
                        normalizedPrompt,
                        imageMimeType,
                        imageBase64);

                Response<ChatResponse> response = groqApiService
                        .createChatCompletion("Bearer " + apiKey, requestBody)
                        .execute();

                if (!response.isSuccessful()) {
                    callback.onError(mapHttpError(response.code()));
                    return;
                }

                ChatResponse body = response.body();
                String finalText = body == null ? "" : extractAssistantText(body);
                if (TextUtils.isEmpty(finalText)) {
                    callback.onError("Mình chưa nhận được phản hồi rõ ràng, thử lại giúp mình nha.");
                    return;
                }

                callback.onChunk(finalText);
                callback.onCompleted(finalText);

            } catch (Exception e) {
                callback.onError(FRIENDLY_ERROR);
            }
        });
    }

    @NonNull
    public static String[] readImageAsBase64(@NonNull InputStream inputStream, @NonNull String mimeType)
            throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8 * 1024];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }

        byte[] raw = outputStream.toByteArray();
        String encoded = Base64.encodeToString(raw, Base64.NO_WRAP);
        return new String[] { mimeType, encoded };
    }

    @NonNull
    public static String detectMimeType(@NonNull android.content.ContentResolver resolver, @NonNull Uri uri) {
        String mimeType = resolver.getType(uri);
        return TextUtils.isEmpty(mimeType) ? "image/jpeg" : mimeType;
    }

    @NonNull
    private ChatRequest buildRequestBody(
            @NonNull List<ChatMessage> history,
            @Nullable String userPrompt,
            @Nullable String imageMimeType,
            @Nullable String imageBase64) {

        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", SYSTEM_PROMPT));

        for (ChatMessage item : compactHistory(history)) {
            String text = trimMessage(item.getContent(), MAX_SINGLE_MESSAGE_CHARS);
            if (TextUtils.isEmpty(text)) {
                continue;
            }
            messages.add(new Message(item.isUser() ? "user" : "assistant", text));
        }

        String currentPrompt = trimMessage(userPrompt, MAX_USER_PROMPT_CHARS);

        if (!TextUtils.isEmpty(imageBase64)) {
            List<Object> contentParts = new ArrayList<>();
            String imagePromptText;
            if (TextUtils.isEmpty(currentPrompt)) {
                imagePromptText = "Nhận diện chính xác món ăn trong ảnh này rồi hướng dẫn nấu chính món đó theo khuôn mẫu bắt buộc.";
            } else {
                imagePromptText = "Dựa trên ảnh món ăn này và yêu cầu sau: \""
                        + currentPrompt
                        + "\". Trước tiên phải gọi đúng tên món trong ảnh, sau đó hướng dẫn nấu chính món đó theo khuôn mẫu bắt buộc.";
            }
            contentParts.add(new TextContent("text", imagePromptText));

            String safeMimeType = TextUtils.isEmpty(imageMimeType) ? "image/jpeg" : imageMimeType;
            contentParts.add(
                    new ImageUrlContent("image_url", new ImageUrl("data:" + safeMimeType + ";base64," + imageBase64)));

            messages.add(new Message("user", contentParts));
        } else {
            messages.add(new Message("user", currentPrompt));
        }

        return new ChatRequest(MODEL, messages, 0.25f, 0.9f, MAX_OUTPUT_TOKENS);
    }

    @NonNull
    private static String extractAssistantText(@NonNull ChatResponse response) {
        if (response.choices == null || response.choices.isEmpty()) {
            return "";
        }

        for (Choice choice : response.choices) {
            if (choice == null || choice.message == null || choice.message.content == null) {
                continue;
            }
            Object contentObj = choice.message.content;
            String text = contentObj instanceof String ? (String) contentObj : String.valueOf(contentObj);
            if (!TextUtils.isEmpty(text)) {
                return text.trim();
            }
        }
        return "";
    }

    @NonNull
    private static String mapHttpError(int code) {
        if (code == 401 || code == 403) {
            return "API key Groq không hợp lệ hoặc không có quyền truy cập model.";
        }
        if (code == 429) {
            return "Bạn đã chạm giới hạn tốc độ hoặc quota Groq, thử lại sau ít phút nhé.";
        }
        if (code == 400) {
            return "Yêu cầu chưa hợp lệ, vui lòng thử lại với tên món ăn cụ thể hơn.";
        }
        if (code >= 500) {
            return "Server Groq đang quá tải, bạn thử lại giúp mình sau ít phút nhé.";
        }
        return FRIENDLY_ERROR + " (Mã lỗi: " + code + ")";
    }

    @NonNull
    private static String resolveApiKey() {
        if (!TextUtils.isEmpty(BuildConfig.GROQ_API_KEY)) {
            return BuildConfig.GROQ_API_KEY;
        }
        return BuildConfig.GEMINI_API_KEY;
    }

    @NonNull
    private static List<ChatMessage> compactHistory(@NonNull List<ChatMessage> history) {
        if (history.isEmpty()) {
            return Collections.emptyList();
        }

        int startIndex = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
        int usedChars = 0;
        List<ChatMessage> selected = new ArrayList<>();

        for (int i = history.size() - 1; i >= startIndex; i--) {
            ChatMessage message = history.get(i);
            String text = trimMessage(message.getContent(), MAX_SINGLE_MESSAGE_CHARS);
            if (TextUtils.isEmpty(text)) {
                continue;
            }

            if (usedChars + text.length() > MAX_HISTORY_TOTAL_CHARS) {
                int remain = MAX_HISTORY_TOTAL_CHARS - usedChars;
                if (remain <= 32) {
                    break;
                }
                text = trimMessage(text, remain);
            }

            usedChars += text.length();
            selected.add(message.copyWithContent(text));
            if (usedChars >= MAX_HISTORY_TOTAL_CHARS) {
                break;
            }
        }

        Collections.reverse(selected);
        return selected;
    }

    @NonNull
    private static String trimMessage(@Nullable String text, int maxChars) {
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        String normalized = text.trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(normalized.length() - maxChars);
    }

    private interface GroqApiService {
        @POST("chat/completions")
        Call<ChatResponse> createChatCompletion(
                @Header("Authorization") @NonNull String authorization,
                @Body @NonNull ChatRequest request);
    }

    private static class ChatRequest {
        @SerializedName("model")
        final String model;

        @SerializedName("messages")
        final List<Message> messages;

        @SerializedName("temperature")
        final float temperature;

        @SerializedName("top_p")
        final float topP;

        @SerializedName("max_tokens")
        final int maxTokens;

        ChatRequest(
                @NonNull String model,
                @NonNull List<Message> messages,
                float temperature,
                float topP,
                int maxTokens) {
            this.model = model;
            this.messages = messages;
            this.temperature = temperature;
            this.topP = topP;
            this.maxTokens = maxTokens;
        }
    }

    private static class Message {
        @SerializedName("role")
        final String role;

        @SerializedName("content")
        final Object content;

        Message(@NonNull String role, @NonNull Object content) {
            this.role = role;
            this.content = content;
        }
    }

    private static class TextContent {
        @SerializedName("type")
        final String type;

        @SerializedName("text")
        final String text;

        TextContent(@NonNull String type, @NonNull String text) {
            this.type = type;
            this.text = text;
        }
    }

    private static class ImageUrlContent {
        @SerializedName("type")
        final String type;

        @SerializedName("image_url")
        final ImageUrl imageUrl;

        ImageUrlContent(@NonNull String type, @NonNull ImageUrl imageUrl) {
            this.type = type;
            this.imageUrl = imageUrl;
        }
    }

    private static class ImageUrl {
        @SerializedName("url")
        final String url;

        ImageUrl(@NonNull String url) {
            this.url = url;
        }
    }

    private static class ChatResponse {
        @SerializedName("choices")
        List<Choice> choices;
    }

    private static class Choice {
        @SerializedName("message")
        Message message;
    }
}
