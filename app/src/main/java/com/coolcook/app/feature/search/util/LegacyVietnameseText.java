package com.coolcook.app.feature.search.util;

import androidx.annotation.NonNull;

import com.coolcook.app.feature.profile.data.HealthAnalyzer;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LegacyVietnameseText {

    private static final Map<String, String> DIRECT_REPLACEMENTS = buildReplacementMap();

    private LegacyVietnameseText() {
    }

    @NonNull
    public static String repair(@NonNull String raw) {
        String value = raw;
        for (Map.Entry<String, String> entry : DIRECT_REPLACEMENTS.entrySet()) {
            value = value.replace(entry.getKey(), entry.getValue());
        }
        return value;
    }

    @NonNull
    public static String repairHealthTag(@NonNull String raw) {
        String normalized = HealthAnalyzer.normalize(raw);
        if (normalized.contains("an nhe bung")) {
            return "ăn nhẹ bụng";
        }
        if (normalized.contains("an thanh dam")) {
            return "ăn thanh đạm";
        }
        if (normalized.contains("de tieu")) {
            return "dễ tiêu";
        }
        if (normalized.contains("han che dau mo")) {
            return "hạn chế dầu mỡ";
        }
        if (normalized.contains("kiem soat can nang")) {
            return "kiểm soát cân nặng";
        }
        if (normalized.contains("mo nhiem mau")
                || normalized.contains("m nhi m m u")
                || (normalized.contains("nhiem") && normalized.contains("mau"))) {
            return "mỡ nhiễm máu";
        }
        if (normalized.contains("da day") || normalized.contains("d d y")) {
            return "dạ dày";
        }
        if (normalized.contains("nguoi can nhieu nang luong") || normalized.contains("can nhieu nang luong")) {
            return "người cần nhiều năng lượng";
        }
        if (normalized.contains("tang co bap")) {
            return "tăng cơ bắp";
        }
        if (normalized.contains("thieu sat")) {
            return "thiếu sắt";
        }
        if (normalized.contains("tieu duong")) {
            return "tiểu đường";
        }
        if (normalized.contains("nguoi moi om day")) {
            return "người mới ốm dậy";
        }
        if (normalized.contains("nguoi can hoi suc")) {
            return "người cần hồi sức";
        }
        if (normalized.contains("tre nho")) {
            return "trẻ nhỏ";
        }
        return repair(raw);
    }

    @NonNull
    private static Map<String, String> buildReplacementMap() {
        Map<String, String> replacements = new LinkedHashMap<>();

        replacements.put("### Com ", "### Cơm ");
        replacements.put("### Bun ", "### Bún ");
        replacements.put("### Goi cuon", "### Gỏi cuốn");
        replacements.put("### Bo ", "### Bò ");
        replacements.put("### Ca ", "### Cá ");
        replacements.put("### Ga ", "### Gà ");
        replacements.put("### Pho ", "### Phở ");
        replacements.put("### Chao ", "### Cháo ");
        replacements.put("### Dau hu", "### Đậu hũ ");

        replacements.put("**Kh???u ph???n:**", "**Khẩu phần:**");
        replacements.put("**Kh?u ph?n:**", "**Khẩu phần:**");
        replacements.put("**Nguy??n li???u:**", "**Nguyên liệu:**");
        replacements.put("**Nguyï¿½n li?u:**", "**Nguyên liệu:**");
        replacements.put("**C??c b?????c th???c hi???n:**", "**Các bước thực hiện:**");
        replacements.put("**Cï¿½c bu?c th?c hi?n:**", "**Các bước thực hiện:**");
        replacements.put("**M???o t???i ??u:**", "**Mẹo tối ưu:**");
        replacements.put("**M?o t?i uu:**", "**Mẹo tối ưu:**");

        replacements.put("Ngu?i", "Người");
        replacements.put("ngu?i", "người");
        replacements.put("Nu?c", "Nước");
        replacements.put("nu?c", "nước");
        replacements.put("M?m", "Mắm");
        replacements.put("m?m", "mắm");
        replacements.put("Mu?ng", "Muỗng");
        replacements.put("mu?ng", "muỗng");
        replacements.put("cï¿½ phï¿½", "cà phê");
        replacements.put("ph?t", "phút");
        replacements.put("l?a", "lửa");
        replacements.put("v?a", "vừa");
        replacements.put("Th?t", "Thịt");
        replacements.put("th?t", "thịt");
        replacements.put("N?m", "Nêm");
        replacements.put("n?m", "nêm");
        replacements.put("cu?i", "cuối");
        replacements.put("nh?", "nhỏ");
        replacements.put("D?u", "Dầu");
        replacements.put("d?u", "dầu");
        replacements.put("T?i", "Tỏi");
        replacements.put("t?i", "tỏi");
        replacements.put("b?m", "băm");
        replacements.put("bam", "băm");
        replacements.put("Tiï¿½u", "Tiêu");
        replacements.put("tiï¿½u", "tiêu");
        replacements.put("th?c", "thực");
        replacements.put("hi?n", "hiện");
        replacements.put("bu?c", "bước");
        replacements.put("ph?n", "phần");
        replacements.put("li?u", "liệu");
        replacements.put("h?p", "hấp");
        replacements.put("u?p", "ướp");
        replacements.put("n?u", "nếu");
        replacements.put("c?n", "cần");
        replacements.put("d?m", "đậm");
        replacements.put("r?i", "rồi");
        replacements.put("nhi?u", "nhiều");
        replacements.put("m?i", "mỗi");
        replacements.put("l?n", "lần");
        replacements.put("l?i", "lại");
        replacements.put("tru?c", "trước");
        replacements.put("ho?c", "hoặc");
        replacements.put("Lu?c", "Luộc");
        replacements.put("lu?c", "luộc");
        replacements.put("?p", "áp");
        replacements.put("ch?o", "chảo");
        replacements.put("g?ng", "gừng");
        replacements.put("su?n", "sườn");
        replacements.put("c?t l?t", "cốt lết");
        replacements.put("ng?t", "ngọt");
        replacements.put("s?ng", "sống");
        replacements.put("h?i", "hồi");
        replacements.put("h?p n?ng", "hấp nóng");
        replacements.put("dia", "đĩa");
        replacements.put("d?a", "đĩa");

        replacements.put("Com ", "Cơm ");
        replacements.put("com ", "cơm ");
        replacements.put("Bun ", "Bún ");
        replacements.put("bun ", "bún ");
        replacements.put("Goi ", "Gỏi ");
        replacements.put("goi ", "gỏi ");
        replacements.put("cu?n", "cuốn");
        replacements.put("Bo ", "Bò ");
        replacements.put("bo ", "bò ");
        replacements.put("Ga ", "Gà ");
        replacements.put("ga ", "gà ");
        replacements.put("Ca ", "Cá ");
        replacements.put("ca ", "cá ");
        replacements.put("T?m", "Tôm");
        replacements.put("t?m", "tôm");
        replacements.put("tom", "tôm");
        replacements.put("Pho ", "Phở ");
        replacements.put("pho ", "phở ");
        replacements.put("Dau hu", "Đậu hũ");
        replacements.put("dau hu", "đậu hũ");
        replacements.put("G?ng", "Gừng");
        replacements.put("g?o", "gạo");
        replacements.put("s?t", "sắt");
        replacements.put("du?ng", "đường");
        replacements.put("ti?u", "tiểu");
        replacements.put("ki?m", "kiểm");
        replacements.put("so?t", "soát");

        return replacements;
    }
}
