package com.coolcook.app.ui.journal;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.coolcook.app.ui.journal.data.JournalRepository;
import com.coolcook.app.ui.journal.model.JournalDay;
import com.coolcook.app.ui.journal.model.JournalEntry;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class JournalViewModel extends ViewModel {

    private static final int DAYS_PER_WEEK = 7;
    private static final int VISUAL_PATTERN_COUNT = 7;

    private final JournalRepository repository;
    private final DateTimeFormatter monthFormatter =
            DateTimeFormatter.ofPattern("'Tháng' M, uuuu", Locale.forLanguageTag("vi-VN"));

    private final MutableLiveData<YearMonth> currentMonth = new MutableLiveData<>(YearMonth.now());
    private final MutableLiveData<String> monthLabel = new MutableLiveData<>("");
    private final MutableLiveData<List<JournalDay>> calendarDays = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<JournalEntry>> selectedDayEntries = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> uiMessage = new MutableLiveData<>("");

    private String userId = "";

    public JournalViewModel() {
        this.repository = new JournalRepository(FirebaseFirestore.getInstance());
        updateMonthLabel(nonNullMonth());
    }

    public LiveData<YearMonth> getCurrentMonth() {
        return currentMonth;
    }

    public LiveData<String> getMonthLabel() {
        return monthLabel;
    }

    public LiveData<List<JournalDay>> getCalendarDays() {
        return calendarDays;
    }

    public LiveData<List<JournalEntry>> getSelectedDayEntries() {
        return selectedDayEntries;
    }

    public LiveData<Boolean> isLoading() {
        return loading;
    }

    public LiveData<String> getUiMessage() {
        return uiMessage;
    }

    public void setUserId(@Nullable String userId) {
        this.userId = userId == null ? "" : userId.trim();
    }

    public void setCurrentMonth(@NonNull YearMonth month) {
        currentMonth.setValue(month);
        updateMonthLabel(month);
        refreshMonth();
    }

    public void goToPreviousMonth() {
        YearMonth previous = nonNullMonth().minusMonths(1);
        currentMonth.setValue(previous);
        updateMonthLabel(previous);
        refreshMonth();
    }

    public void goToNextMonth() {
        YearMonth next = nonNullMonth().plusMonths(1);
        currentMonth.setValue(next);
        updateMonthLabel(next);
        refreshMonth();
    }

    public void refreshMonth() {
        YearMonth month = nonNullMonth();
        updateMonthLabel(month);

        if (TextUtils.isEmpty(userId)) {
            calendarDays.setValue(buildCalendarDays(month, new ArrayList<>()));
            uiMessage.setValue("Bạn cần đăng nhập để xem nhật ký món ăn.");
            return;
        }

        loading.setValue(true);
        repository.loadEntriesForMonth(userId, month, (entries, error) -> {
            loading.postValue(false);
            if (error != null) {
                calendarDays.postValue(buildCalendarDays(month, new ArrayList<>()));
                uiMessage.postValue("Không thể tải dữ liệu nhật ký tháng này.");
                return;
            }

            calendarDays.postValue(buildCalendarDays(month, entries));
            uiMessage.postValue("");
        });
    }

    public void loadEntriesOfDate(@NonNull LocalDate date) {
        if (TextUtils.isEmpty(userId)) {
            selectedDayEntries.setValue(new ArrayList<>());
            uiMessage.setValue("Bạn cần đăng nhập để xem nhật ký món ăn.");
            return;
        }

        loading.setValue(true);
        repository.loadEntriesForDate(userId, date, (entries, error) -> {
            loading.postValue(false);
            if (error != null) {
                selectedDayEntries.postValue(new ArrayList<>());
                uiMessage.postValue("Không thể tải ảnh của ngày đã chọn.");
                return;
            }

            selectedDayEntries.postValue(entries);
            uiMessage.postValue(entries.isEmpty() ? "Ngày này chưa có ảnh món ăn." : "");
        });
    }

    @NonNull
    private List<JournalDay> buildCalendarDays(
            @NonNull YearMonth month,
            @NonNull List<JournalEntry> entries) {

        Map<LocalDate, List<JournalEntry>> groupedByDate = new HashMap<>();
        for (JournalEntry entry : entries) {
            LocalDate date = entry.getDate();
            if (date.getYear() != month.getYear() || date.getMonthValue() != month.getMonthValue()) {
                continue;
            }
            List<JournalEntry> bucket = groupedByDate.get(date);
            if (bucket == null) {
                bucket = new ArrayList<>();
                groupedByDate.put(date, bucket);
            }
            bucket.add(entry);
        }

        groupedByDate.values().forEach(dayEntries -> dayEntries.sort(
                Comparator.comparing(JournalViewModel::capturedAtMillis).reversed()));

        int firstDayOffset = resolveFirstDayOffset(month);
        int daysInMonth = month.lengthOfMonth();

        List<JournalDay> cells = new ArrayList<>();
        int patternSeed = (month.getMonthValue() * 3) % VISUAL_PATTERN_COUNT;

        for (int i = 0; i < firstDayOffset; i++) {
            cells.add(JournalDay.empty((patternSeed + i) % VISUAL_PATTERN_COUNT));
        }

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = month.atDay(day);
            List<JournalEntry> dayEntries = groupedByDate.get(date);
            if (dayEntries == null) {
                dayEntries = new ArrayList<>();
            }
            int visualPattern = (patternSeed + day) % VISUAL_PATTERN_COUNT;
            cells.add(JournalDay.fromEntries(date, dayEntries, visualPattern));
        }

        int trailingCount = (DAYS_PER_WEEK - (cells.size() % DAYS_PER_WEEK)) % DAYS_PER_WEEK;
        for (int i = 0; i < trailingCount; i++) {
            cells.add(JournalDay.empty((patternSeed + firstDayOffset + daysInMonth + i) % VISUAL_PATTERN_COUNT));
        }

        return cells;
    }

    private void updateMonthLabel(@NonNull YearMonth month) {
        monthLabel.setValue(month.format(monthFormatter));
    }

    @NonNull
    private YearMonth nonNullMonth() {
        YearMonth month = currentMonth.getValue();
        if (month == null) {
            month = YearMonth.now();
            currentMonth.setValue(month);
        }
        return month;
    }

    private static long capturedAtMillis(@NonNull JournalEntry entry) {
        if (entry.getCapturedAt() == null) {
            return 0L;
        }
        return entry.getCapturedAt().getTime();
    }

    private int resolveFirstDayOffset(@NonNull YearMonth month) {
        return month.atDay(1).getDayOfWeek().getValue() - 1;
    }
}
