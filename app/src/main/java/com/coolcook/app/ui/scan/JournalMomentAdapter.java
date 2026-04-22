package com.coolcook.app.ui.scan;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.coolcook.app.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class JournalMomentAdapter extends RecyclerView.Adapter<JournalMomentAdapter.MomentViewHolder> {

    private final List<JournalMoment> moments = new ArrayList<>();
    private final SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm - dd/MM", Locale.forLanguageTag("vi-VN"));

    @NonNull
    @Override
    public MomentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_scan_journal_moment, parent, false);
        return new MomentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MomentViewHolder holder, int position) {
        JournalMoment item = moments.get(position);
        String imageUrl = item.getThumbUrl().isEmpty() ? item.getImageUrl() : item.getThumbUrl();

        Glide.with(holder.itemView)
                .load(imageUrl)
                .placeholder(R.drawable.img_scan_food_salad)
                .error(R.drawable.img_scan_food_salad)
                .into(holder.imgMoment);

        if (item.getCreatedAt() != null) {
            holder.txtTime.setText(timeFormatter.format(item.getCreatedAt()));
        } else {
            holder.txtTime.setText("Vừa xong");
        }

        String source = item.getSource().equalsIgnoreCase("gallery") ? "Từ thư viện" : "Từ camera";
        String facing = item.getCameraFacing().equalsIgnoreCase("front") ? " trước" : " sau";
        holder.txtSource.setText(item.getSource().equalsIgnoreCase("gallery") ? source : source + facing);
    }

    @Override
    public int getItemCount() {
        return moments.size();
    }

    public void submitMoments(@NonNull List<JournalMoment> newMoments) {
        moments.clear();
        moments.addAll(newMoments);
        notifyDataSetChanged();
    }

    static class MomentViewHolder extends RecyclerView.ViewHolder {
        final ImageView imgMoment;
        final TextView txtTime;
        final TextView txtSource;

        MomentViewHolder(@NonNull View itemView) {
            super(itemView);
            imgMoment = itemView.findViewById(R.id.imgJournalMoment);
            txtTime = itemView.findViewById(R.id.txtJournalMomentTime);
            txtSource = itemView.findViewById(R.id.txtJournalMomentSource);
        }
    }
}
