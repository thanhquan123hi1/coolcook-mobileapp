package com.coolcook.app.feature.journal.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JournalDay {

    private static final int MAX_VISIBLE_IMAGES = 3;

    @Nullable
    private final LocalDate date;
    private final boolean inCurrentMonth;
    private final int dayOfMonth;
    private final int totalEntryCount;
    private final int visualPatternIndex;
    @NonNull
    private final List<JournalEntry> latestEntries;

    private JournalDay(
            @Nullable LocalDate date,
            boolean inCurrentMonth,
            int dayOfMonth,
            int totalEntryCount,
            int visualPatternIndex,
            @NonNull List<JournalEntry> latestEntries) {
        this.date = date;
        this.inCurrentMonth = inCurrentMonth;
        this.dayOfMonth = dayOfMonth;
        this.totalEntryCount = totalEntryCount;
        this.visualPatternIndex = visualPatternIndex;
        this.latestEntries = latestEntries;
    }

    @NonNull
    public static JournalDay empty(int visualPatternIndex) {
        return new JournalDay(null, false, 0, 0, visualPatternIndex, new ArrayList<>());
    }

    @NonNull
    public static JournalDay fromEntries(
            @NonNull LocalDate date,
            @NonNull List<JournalEntry> sortedEntries,
            int visualPatternIndex) {

        List<JournalEntry> latest = new ArrayList<>();
        for (int i = 0; i < sortedEntries.size() && i < MAX_VISIBLE_IMAGES; i++) {
            latest.add(sortedEntries.get(i));
        }

        return new JournalDay(
                date,
                true,
                date.getDayOfMonth(),
                sortedEntries.size(),
                visualPatternIndex,
                Collections.unmodifiableList(latest));
    }

    @Nullable
    public LocalDate getDate() {
        return date;
    }

    public boolean isInCurrentMonth() {
        return inCurrentMonth;
    }

    public int getDayOfMonth() {
        return dayOfMonth;
    }

    public int getTotalEntryCount() {
        return totalEntryCount;
    }

    public int getVisualPatternIndex() {
        return visualPatternIndex;
    }

    @NonNull
    public List<JournalEntry> getLatestEntries() {
        return latestEntries;
    }

    public int getExtraCount() {
        return Math.max(0, totalEntryCount - MAX_VISIBLE_IMAGES);
    }
}
