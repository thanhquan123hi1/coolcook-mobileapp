package com.coolcook.app.feature.profile.data;

import androidx.annotation.NonNull;

import com.coolcook.app.feature.profile.model.HealthAnalysisResult;
import com.coolcook.app.feature.profile.model.HealthProfile;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HealthAnalyzer {

    private HealthAnalyzer() {
    }

    @NonNull
    public static HealthAnalysisResult analyze(@NonNull HealthProfile profile) {
        List<String> tags = new ArrayList<>();
        List<String> shouldEat = new ArrayList<>();
        List<String> shouldLimit = new ArrayList<>();
        List<String> summaryParts = new ArrayList<>();
        String warning = "";

        if (profile.getSystolicBp() >= 140 || profile.getDiastolicBp() >= 90) {
            add(tags, "huyết áp cao");
            add(tags, "ăn nhạt");
            add(tags, "hạn chế dầu mỡ");
            add(shouldEat, "món luộc");
            add(shouldEat, "món hấp");
            add(shouldEat, "canh thanh");
            add(shouldEat, "nhiều rau");
            add(shouldLimit, "nhiều muối");
            add(shouldLimit, "món quá nhiều dầu");
            summaryParts.add("nên ưu tiên món thanh đạm, ít muối và ít dầu mỡ");
        } else if (profile.getSystolicBp() < 90 || profile.getDiastolicBp() < 60) {
            add(tags, "huyết áp thấp");
            add(tags, "cần năng lượng vừa phải");
            add(shouldEat, "ăn đủ bữa");
            add(shouldEat, "protein vừa phải");
            add(shouldEat, "uống đủ nước");
            add(shouldLimit, "bỏ bữa");
            summaryParts.add("nên ưu tiên bữa ăn đủ chất và đều bữa");
        }

        if (profile.getHeartRateBpm() > 100) {
            add(tags, "nhịp tim cao");
            add(tags, "ăn nhẹ bụng");
            add(shouldEat, "món dễ tiêu");
            add(shouldEat, "món ít cay");
            add(shouldLimit, "món quá cay");
            add(shouldLimit, "món nhiều dầu");
            summaryParts.add("nên ưu tiên món nhẹ bụng và dễ tiêu");
        } else if (profile.getHeartRateBpm() < 60) {
            add(tags, "nhịp tim thấp");
            summaryParts.add("nên chọn món cân bằng và theo dõi cảm giác cơ thể");
        }

        if (profile.getWeightKg() >= 80d) {
            add(tags, "kiểm soát cân nặng");
            add(tags, "ít dầu mỡ");
            add(tags, "nhiều rau");
            add(shouldEat, "rau xanh");
            add(shouldEat, "đạm nạc");
            add(shouldLimit, "đồ chiên");
            add(shouldLimit, "món quá nhiều sốt");
            summaryParts.add("có thể phù hợp với món nhẹ hơn để kiểm soát cân nặng");
        } else if (profile.getWeightKg() < 45d) {
            add(tags, "cần nhiều năng lượng");
            add(tags, "bổ sung đạm");
            add(shouldEat, "đạm nạc");
            add(shouldEat, "tinh bột vừa đủ");
            summaryParts.add("có thể phù hợp với món đủ năng lượng và thêm đạm");
        }

        String normalizedGoal = normalize(profile.getGoal());
        if (normalizedGoal.contains("giam can")) {
            add(tags, "kiểm soát cân nặng");
            add(tags, "ăn thanh đạm");
            add(shouldEat, "món hấp");
            add(shouldEat, "rau xanh");
            add(shouldLimit, "đồ chiên");
        } else if (normalizedGoal.contains("tang co")) {
            add(tags, "tăng cơ bắp");
            add(shouldEat, "đạm nạc");
        } else if (normalizedGoal.contains("thanh dam")) {
            add(tags, "ăn thanh đạm");
        } else if (normalizedGoal.contains("kiem soat huyet ap")) {
            add(tags, "ăn nhạt");
            add(tags, "hạn chế dầu mỡ");
        }

        for (String tag : profile.getHealthTags()) {
            add(tags, tag);
        }

        if (summaryParts.isEmpty()) {
            summaryParts.add("có thể phù hợp với thực đơn cân bằng, nhiều rau và đạm vừa phải");
            add(tags, "ăn cân bằng");
            add(shouldEat, "rau xanh");
            add(shouldEat, "đạm vừa phải");
            add(shouldLimit, "món quá nhiều dầu");
        }

        if (profile.getSystolicBp() >= 180
                || profile.getDiastolicBp() >= 120
                || profile.getHeartRateBpm() > 130
                || profile.getHeartRateBpm() < 45) {
            warning = "Chỉ số này có thể bất thường. Nếu bạn thấy mệt, đau ngực, khó thở, chóng mặt, hãy liên hệ cơ sở y tế.";
        } else if (profile.getHeartRateBpm() < 60) {
            warning = "Nhịp tim hiện khá thấp. Nếu bạn thấy mệt hoặc chóng mặt, nên gặp bác sĩ để được tư vấn.";
        }

        String summary = "Gợi ý tham khảo hôm nay: " + String.join(", ", summaryParts) + ".";
        return new HealthAnalysisResult(summary, tags, shouldEat, shouldLimit, warning);
    }

    private static void add(@NonNull List<String> values, @NonNull String value) {
        String trimmed = value.trim();
        if (!trimmed.isEmpty() && !values.contains(trimmed)) {
            values.add(trimmed);
        }
    }

    @NonNull
    private static String normalize(@NonNull String input) {
        String ascii = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        return ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
