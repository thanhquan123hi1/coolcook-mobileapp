package com.coolcook.app.ui.chatbot.adapter;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.coolcook.app.R;
import com.coolcook.app.ui.chatbot.model.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder> {

    private final List<ChatMessage> items = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public void submitMessages(@NonNull List<ChatMessage> messages) {
        items.clear();
        items.addAll(messages);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        ChatMessage message = items.get(position);
        CharSequence content = renderPrettyMessage(message.getContent());
        String time = timeFormat.format(message.getCreatedAt());

        if (message.isUser()) {
            holder.layoutUserRow.setVisibility(View.VISIBLE);
            holder.layoutAiRow.setVisibility(View.GONE);
            holder.txtUserMessage.setText(content);
            holder.txtUserTime.setText(time);
        } else {
            holder.layoutAiRow.setVisibility(View.VISIBLE);
            holder.layoutUserRow.setVisibility(View.GONE);
            holder.txtAiMessage.setText(content);
            holder.txtAiTime.setText(time);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    private static CharSequence renderPrettyMessage(@NonNull String raw) {
        if (TextUtils.isEmpty(raw)) {
            return "";
        }

        String[] lines = raw.replace("\r\n", "\n").split("\n");
        SpannableStringBuilder builder = new SpannableStringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                appendWithInlineBold(builder, "• " + trimmed.substring(2));
            } else if (trimmed.startsWith("### ")) {
                appendHeading(builder, trimmed.substring(4).trim());
            } else if (trimmed.startsWith("## ")) {
                appendHeading(builder, trimmed.substring(3).trim());
            } else if (trimmed.startsWith("# ")) {
                appendHeading(builder, trimmed.substring(2).trim());
            } else {
                appendWithInlineBold(builder, line);
            }

            if (i < lines.length - 1) {
                builder.append('\n');
            }
        }

        return builder;
    }

    private static void appendHeading(@NonNull SpannableStringBuilder builder, @NonNull String heading) {
        int start = builder.length();
        builder.append(heading);
        builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void appendWithInlineBold(
            @NonNull SpannableStringBuilder builder,
            @NonNull String line) {
        int cursor = 0;
        while (cursor < line.length()) {
            int open = line.indexOf("**", cursor);
            if (open < 0) {
                builder.append(line.substring(cursor));
                return;
            }

            if (open > cursor) {
                builder.append(line.substring(cursor, open));
            }

            int close = line.indexOf("**", open + 2);
            if (close < 0) {
                builder.append(line.substring(open));
                return;
            }

            int startBold = builder.length();
            builder.append(line.substring(open + 2, close));
            builder.setSpan(new StyleSpan(Typeface.BOLD), startBold, builder.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            cursor = close + 2;
        }
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {

        final LinearLayout layoutAiRow;
        final LinearLayout layoutUserRow;
        final TextView txtAiMessage;
        final TextView txtAiTime;
        final TextView txtUserMessage;
        final TextView txtUserTime;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutAiRow = itemView.findViewById(R.id.layoutAiRow);
            layoutUserRow = itemView.findViewById(R.id.layoutUserRow);
            txtAiMessage = itemView.findViewById(R.id.txtAiMessage);
            txtAiTime = itemView.findViewById(R.id.txtAiTime);
            txtUserMessage = itemView.findViewById(R.id.txtUserMessage);
            txtUserTime = itemView.findViewById(R.id.txtUserTime);
        }
    }
}
