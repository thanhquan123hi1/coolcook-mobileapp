package com.coolcook.app.feature.chatbot.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.coolcook.app.R;
import com.coolcook.app.core.util.MarkdownRenderer;
import com.coolcook.app.feature.chatbot.model.ChatMessage;

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
        CharSequence content = MarkdownRenderer.toSpannable(message.getContent());
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
