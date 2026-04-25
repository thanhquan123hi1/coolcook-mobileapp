package com.coolcook.app.feature.chatbot.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.coolcook.app.R;
import com.coolcook.app.feature.chatbot.model.ChatSession;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChatSessionAdapter extends RecyclerView.Adapter<ChatSessionAdapter.SessionViewHolder> {

    public interface SessionActionListener {
        void onSessionClick(@NonNull ChatSession session);

        void onSessionLongClick(@NonNull ChatSession session);
    }

    private final List<ChatSession> sessions = new ArrayList<>();
    private final SessionActionListener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
    private String activeSessionId = "";

    public ChatSessionAdapter(@NonNull SessionActionListener listener) {
        this.listener = listener;
    }

    public void submitSessions(@NonNull List<ChatSession> newSessions) {
        sessions.clear();
        sessions.addAll(newSessions);
        notifyDataSetChanged();
    }

    public void setActiveSessionId(@NonNull String sessionId) {
        activeSessionId = sessionId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_session, parent, false);
        return new SessionViewHolder(view);
    }

    private static String stripMarkdown(String text) {
        if (text == null) return "";
        return text
                .replaceAll("(?m)^#{1,6}\\s*", "")   // headings
                .replaceAll("\\*{1,3}([^*]+)\\*{1,3}", "$1") // bold/italic
                .replaceAll("_{1,3}([^_]+)_{1,3}", "$1")     // underscore bold/italic
                .replaceAll("`{1,3}[^`]*`{1,3}", "")          // inline code / code blocks
                .replaceAll("(?m)^[-*+]\\s+", "")             // unordered lists
                .replaceAll("(?m)^\\d+\\.\\s+", "")           // ordered lists
                .replaceAll("!?\\[[^]]*]\\([^)]*\\)", "")     // links / images
                .replaceAll(">+\\s?", "")                      // blockquotes
                .replaceAll("-{3,}|\\*{3,}|_{3,}", "")        // horizontal rules
                .replaceAll("\\s+", " ")
                .trim();
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        ChatSession session = sessions.get(position);
        holder.txtTitle.setText(session.getTitle());
        holder.txtPreview.setText(stripMarkdown(session.getLastPreview()));
        holder.txtTime.setText(session.getUpdatedAt() != null ? timeFormat.format(session.getUpdatedAt()) : "");

        boolean isActive = session.getId().equals(activeSessionId);
        Context context = holder.itemView.getContext();
        int bg = ContextCompat.getColor(context,
                isActive ? R.color.chatbot_session_card_active_bg : R.color.chatbot_session_card_bg);
        int stroke = ContextCompat.getColor(context,
                isActive ? R.color.chatbot_session_card_active_border : R.color.chatbot_session_card_border);
        holder.cardSession.setCardBackgroundColor(bg);
        holder.cardSession.setStrokeColor(stroke);

        holder.itemView.setOnClickListener(v -> listener.onSessionClick(session));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onSessionLongClick(session);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    static class SessionViewHolder extends RecyclerView.ViewHolder {

        final MaterialCardView cardSession;
        final TextView txtTitle;
        final TextView txtPreview;
        final TextView txtTime;

        SessionViewHolder(@NonNull View itemView) {
            super(itemView);
            cardSession = itemView.findViewById(R.id.cardSession);
            txtTitle = itemView.findViewById(R.id.txtSessionTitle);
            txtPreview = itemView.findViewById(R.id.txtSessionPreview);
            txtTime = itemView.findViewById(R.id.txtSessionTime);
        }
    }
}
