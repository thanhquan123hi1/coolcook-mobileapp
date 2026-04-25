package com.coolcook.app.core.util;

import android.graphics.Typeface;
import android.text.Spanned;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.widget.TextView;

import androidx.annotation.NonNull;

public final class MarkdownRenderer {

    private MarkdownRenderer() {}

    public static void render(@NonNull TextView view, @NonNull String markdown) {
        view.setText(toSpannable(markdown));
    }

    @NonNull
    public static CharSequence toSpannable(@NonNull String raw) {
        if (TextUtils.isEmpty(raw)) return "";

        String[] lines = raw.replace("\r\n", "\n").split("\n");
        SpannableStringBuilder builder = new SpannableStringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.startsWith("### ")) {
                appendHeading(builder, trimmed.substring(4).trim(), 1.1f);
            } else if (trimmed.startsWith("## ")) {
                appendHeading(builder, trimmed.substring(3).trim(), 1.2f);
            } else if (trimmed.startsWith("# ")) {
                appendHeading(builder, trimmed.substring(2).trim(), 1.35f);
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                appendInline(builder, "• " + trimmed.substring(2));
            } else if (trimmed.matches("\\d+\\.\\s.*")) {
                int dot = trimmed.indexOf(". ");
                appendInline(builder, trimmed.substring(0, dot + 2).replace(". ", ". ") + trimmed.substring(dot + 2));
            } else if (trimmed.equals("---") || trimmed.equals("***") || trimmed.equals("___")) {
                builder.append("────────────────────");
            } else {
                appendInline(builder, line);
            }

            if (i < lines.length - 1) {
                builder.append('\n');
            }
        }

        return builder;
    }

    private static void appendHeading(
            @NonNull SpannableStringBuilder b,
            @NonNull String text,
            float relativeSize) {
        int start = b.length();
        b.append(text);
        b.setSpan(new StyleSpan(Typeface.BOLD), start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.setSpan(new RelativeSizeSpan(relativeSize), start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void appendInline(@NonNull SpannableStringBuilder b, @NonNull String line) {
        int cursor = 0;
        while (cursor < line.length()) {
            int boldOpen = line.indexOf("**", cursor);
            int italicOpen = line.indexOf("_", cursor);

            int nextOpen = -1;
            boolean isBold = false;
            if (boldOpen >= 0 && (italicOpen < 0 || boldOpen <= italicOpen)) {
                nextOpen = boldOpen;
                isBold = true;
            } else if (italicOpen >= 0) {
                nextOpen = italicOpen;
            }

            if (nextOpen < 0) {
                b.append(line.substring(cursor));
                return;
            }

            if (nextOpen > cursor) {
                b.append(line.substring(cursor, nextOpen));
            }

            if (isBold) {
                int close = line.indexOf("**", nextOpen + 2);
                if (close < 0) {
                    b.append(line.substring(nextOpen));
                    return;
                }
                int start = b.length();
                b.append(line.substring(nextOpen + 2, close));
                b.setSpan(new StyleSpan(Typeface.BOLD), start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                cursor = close + 2;
            } else {
                int close = line.indexOf("_", nextOpen + 1);
                if (close < 0) {
                    b.append(line.substring(nextOpen));
                    return;
                }
                int start = b.length();
                b.append(line.substring(nextOpen + 1, close));
                b.setSpan(new StyleSpan(Typeface.ITALIC), start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                cursor = close + 1;
            }
        }
    }
}
