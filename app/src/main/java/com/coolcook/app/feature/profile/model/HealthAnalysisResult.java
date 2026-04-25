package com.coolcook.app.feature.profile.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HealthAnalysisResult {

    public static final String DISCLAIMER =
            "CoolCook chỉ gợi ý ăn uống tham khảo, không thay thế tư vấn y tế.";

    @NonNull
    private final String summary;
    @NonNull
    private final List<String> tags;
    @NonNull
    private final List<String> shouldEat;
    @NonNull
    private final List<String> shouldLimit;
    @NonNull
    private final String warning;

    public HealthAnalysisResult(
            @NonNull String summary,
            @NonNull List<String> tags,
            @NonNull List<String> shouldEat,
            @NonNull List<String> shouldLimit,
            @NonNull String warning) {
        this.summary = summary;
        this.tags = new ArrayList<>(tags);
        this.shouldEat = new ArrayList<>(shouldEat);
        this.shouldLimit = new ArrayList<>(shouldLimit);
        this.warning = warning;
    }

    @NonNull
    public String getSummary() {
        return summary;
    }

    @NonNull
    public List<String> getTags() {
        return Collections.unmodifiableList(tags);
    }

    @NonNull
    public List<String> getShouldEat() {
        return Collections.unmodifiableList(shouldEat);
    }

    @NonNull
    public List<String> getShouldLimit() {
        return Collections.unmodifiableList(shouldLimit);
    }

    @NonNull
    public String getWarning() {
        return warning;
    }

    @NonNull
    public String getDisclaimer() {
        return DISCLAIMER;
    }
}
