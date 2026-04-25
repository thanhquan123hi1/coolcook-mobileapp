package com.coolcook.app.feature.journal.data;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.coolcook.app.feature.journal.model.JournalEntry;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class JournalRepository {

    public interface EntriesCallback {
        void onComplete(@NonNull List<JournalEntry> entries, @Nullable Exception error);
    }

    public interface SaveCallback {
        void onComplete(@Nullable Exception error);
    }

    private static final String USERS_COLLECTION = "users";
    private static final String JOURNAL_COLLECTION = "journal";
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final FirebaseFirestore firestore;

    public JournalRepository(@NonNull FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public void loadEntriesForMonth(
            @NonNull String userId,
            @NonNull YearMonth month,
            @NonNull EntriesCallback callback) {
        if (TextUtils.isEmpty(userId)) {
            callback.onComplete(new ArrayList<>(), new IllegalArgumentException("userId is required"));
            return;
        }

        String startDate = month.atDay(1).format(ISO_DATE);
        String endDate = month.atEndOfMonth().format(ISO_DATE);

        journalCollection(userId)
                .whereGreaterThanOrEqualTo("date", startDate)
                .whereLessThanOrEqualTo("date", endDate)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<JournalEntry> entries = new ArrayList<>();
                    snapshot.getDocuments().forEach(doc -> entries.add(JournalEntry.fromSnapshot(doc)));
                    sortNewestFirst(entries);
                    callback.onComplete(entries, null);
                })
                .addOnFailureListener(error -> callback.onComplete(new ArrayList<>(), error));
    }

    public void loadEntriesForDate(
            @NonNull String userId,
            @NonNull LocalDate date,
            @NonNull EntriesCallback callback) {
        if (TextUtils.isEmpty(userId)) {
            callback.onComplete(new ArrayList<>(), new IllegalArgumentException("userId is required"));
            return;
        }

        String dateKey = date.format(ISO_DATE);
        journalCollection(userId)
                .whereEqualTo("date", dateKey)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<JournalEntry> entries = new ArrayList<>();
                    snapshot.getDocuments().forEach(doc -> entries.add(JournalEntry.fromSnapshot(doc)));
                    sortNewestFirst(entries);
                    callback.onComplete(entries, null);
                })
                .addOnFailureListener(error -> callback.onComplete(new ArrayList<>(), error));
    }

    public void saveEntry(
            @NonNull String userId,
            @NonNull JournalEntry entry,
            @NonNull SaveCallback callback) {
        if (TextUtils.isEmpty(userId)) {
            callback.onComplete(new IllegalArgumentException("userId is required"));
            return;
        }

        DocumentReference docRef = TextUtils.isEmpty(entry.getId())
                ? journalCollection(userId).document()
                : journalCollection(userId).document(entry.getId());

        Map<String, Object> payload = entry.toFirestorePayload();
        payload.put("id", docRef.getId());
        payload.put("userId", userId);

        docRef.set(payload)
                .addOnSuccessListener(unused -> callback.onComplete(null))
                .addOnFailureListener(callback::onComplete);
    }

    public void deleteEntry(
            @NonNull String userId,
            @NonNull String entryId,
            @NonNull SaveCallback callback) {
        if (TextUtils.isEmpty(userId)) {
            callback.onComplete(new IllegalArgumentException("userId is required"));
            return;
        }
        if (TextUtils.isEmpty(entryId)) {
            callback.onComplete(new IllegalArgumentException("entryId is required"));
            return;
        }

        journalCollection(userId).document(entryId)
                .delete()
                .addOnSuccessListener(unused -> callback.onComplete(null))
                .addOnFailureListener(callback::onComplete);
    }

    private CollectionReference journalCollection(@NonNull String userId) {
        return firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(JOURNAL_COLLECTION);
    }

    private static void sortNewestFirst(@NonNull List<JournalEntry> entries) {
        entries.sort(Comparator
                .comparing(JournalRepository::captureMillis)
                .reversed()
                .thenComparing(JournalEntry::getId));
    }

    private static long captureMillis(@NonNull JournalEntry entry) {
        if (entry.getCapturedAt() == null) {
            return 0L;
        }
        return entry.getCapturedAt().getTime();
    }
}
