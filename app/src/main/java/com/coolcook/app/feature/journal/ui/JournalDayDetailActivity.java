package com.coolcook.app.feature.journal.ui;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.coolcook.app.R;
import com.coolcook.app.feature.journal.model.JournalEntry;
import com.coolcook.app.feature.journal.ui.adapter.JournalDayDetailAdapter;
import com.coolcook.app.feature.camera.ui.ScanFoodActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public class JournalDayDetailActivity extends AppCompatActivity {

    private static final String EXTRA_DATE = "journal_extra_date";

    public static android.content.Intent createIntent(@NonNull android.content.Context context, @NonNull LocalDate date) {
        android.content.Intent intent = new android.content.Intent(context, JournalDayDetailActivity.class);
        intent.putExtra(EXTRA_DATE, date.format(DateTimeFormatter.ISO_LOCAL_DATE));
        return intent;
    }

    private final DateTimeFormatter titleFormatter = DateTimeFormatter.ofPattern("'Ngày' d 'tháng' M, uuuu", Locale.forLanguageTag("vi-VN"));

    private final DateTimeFormatter editDateFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.forLanguageTag("vi-VN"));

    private View root;
    private View topRow;
    private View btnBack;
    private TextView txtTitle;
    private RecyclerView rvPhotos;
    private TextView txtEmptyState;
    private View progressLoading;
    private FloatingActionButton fabAddPhoto;

    private JournalViewModel viewModel;
    private JournalDayDetailAdapter detailAdapter;
    private LocalDate selectedDate = LocalDate.now();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.journal_day_detail_screen);

        selectedDate = extractDateFromIntent();

        initViews();
        setupRecycler();
        setupActions();
        applyInsets();
        renderTitle();

        viewModel = new ViewModelProvider(this).get(JournalViewModel.class);
        observeViewModel();
        bindUser();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.loadEntriesOfDate(selectedDate);
        }
    }

    private void initViews() {
        root = findViewById(R.id.journalDayDetailRoot);
        topRow = findViewById(R.id.journalDayDetailTopRow);
        btnBack = findViewById(R.id.btnJournalDetailBack);
        txtTitle = findViewById(R.id.txtJournalDayDetailTitle);
        rvPhotos = findViewById(R.id.rvJournalDayDetail);
        txtEmptyState = findViewById(R.id.txtJournalDayDetailEmptyState);
        progressLoading = findViewById(R.id.journalDayDetailLoading);
        fabAddPhoto = findViewById(R.id.fabJournalDayAddPhoto);
    }

    private void setupRecycler() {
        detailAdapter = new JournalDayDetailAdapter(
                this::showPhotoDetailDialog,
                this::showDeleteConfirmation);

        rvPhotos.setLayoutManager(new GridLayoutManager(this, resolveSpanCount()));
        rvPhotos.setAdapter(detailAdapter);
        rvPhotos.setHasFixedSize(true);
    }

    private int resolveSpanCount() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        return widthDp >= 411 ? 3 : 2;
    }

    private void setupActions() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finishWithSlideBack());
        }

        if (fabAddPhoto != null) {
            fabAddPhoto.setOnClickListener(v -> playBounceThen(v, this::openScanInJournalMode));
        }
    }

    private void observeViewModel() {
        viewModel.getSelectedDayEntries().observe(this, entries -> {
            detailAdapter.submitEntries(entries);
            if (txtEmptyState != null) {
                txtEmptyState.setVisibility(entries == null || entries.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.isLoading().observe(this, isLoading -> {
            if (progressLoading != null) {
                progressLoading.setVisibility(Boolean.TRUE.equals(isLoading) ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.getUiMessage().observe(this, message -> {
            if (txtEmptyState == null) {
                return;
            }
            if (!TextUtils.isEmpty(message)) {
                txtEmptyState.setText(message);
            } else {
                txtEmptyState.setText(R.string.journal_empty_day);
            }
        });
    }

    private void bindUser() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = user == null ? "" : user.getUid();
        viewModel.setUserId(uid);
        viewModel.loadEntriesOfDate(selectedDate);
    }

    private void renderTitle() {
        if (txtTitle != null) {
            txtTitle.setText(selectedDate.format(titleFormatter));
        }
    }

    @NonNull
    private LocalDate extractDateFromIntent() {
        String rawDate = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_DATE);
        if (!TextUtils.isEmpty(rawDate)) {
            try {
                return LocalDate.parse(rawDate, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
                // Fallback to today when incoming extra is malformed.
            }
        }
        return LocalDate.now();
    }

    private void openScanInJournalMode() {
        startActivity(ScanFoodActivity.createJournalIntent(this));
        overridePendingTransition(R.anim.slide_in_left_scale, R.anim.slide_out_right_scale);
    }

    private void showDeleteConfirmation(@NonNull JournalEntry entry) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Xóa ảnh nhật ký?")
                .setMessage("Bạn có chắc muốn xóa ảnh này khỏi nhật ký không?")
                .setNegativeButton("Giữ lại", null)
                .setPositiveButton("Xóa", (dialog, which) -> deleteEntry(entry))
                .show();
    }

    private void deleteEntry(@NonNull JournalEntry entry) {
        viewModel.deleteEntry(entry, success -> runOnUiThread(() -> {
            if (success) {
                Toast.makeText(this, "Đã xóa ảnh nhật ký", Toast.LENGTH_SHORT).show();
                viewModel.loadEntriesOfDate(selectedDate);
            } else {
                Toast.makeText(this, "Không thể xóa ảnh. Vui lòng thử lại.", Toast.LENGTH_SHORT).show();
            }
        }));
    }

    private void showPhotoDetailDialog(@NonNull JournalEntry entry) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_journal_photo_detail);

        ImageView imgPhoto = dialog.findViewById(R.id.imgJournalPhotoFull);
        View btnClose = dialog.findViewById(R.id.btnJournalPhotoClose);
        TextInputEditText edtDate = dialog.findViewById(R.id.edtJournalPhotoDate);
        TextInputEditText edtCaption = dialog.findViewById(R.id.edtJournalPhotoCaption);
        MaterialButton btnSave = dialog.findViewById(R.id.btnJournalPhotoSave);

        final LocalDate[] editableDate = new LocalDate[]{entry.getDate()};
        Glide.with(this)
                .load(entry.getImageUrl())
                .placeholder(R.drawable.bg_journal_placeholder_cute)
                .error(R.drawable.bg_journal_placeholder_cute_alt)
                .into(imgPhoto);

        edtDate.setText(editableDate[0].format(editDateFormatter));
        edtCaption.setText(entry.getCaption());

        View.OnClickListener datePickerClick = v -> showPhotoDatePicker(editableDate[0], pickedDate -> {
            editableDate[0] = pickedDate;
            edtDate.setText(pickedDate.format(editDateFormatter));
        });
        edtDate.setOnClickListener(datePickerClick);
        edtDate.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                datePickerClick.onClick(v);
            }
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String caption = edtCaption.getText() == null ? "" : edtCaption.getText().toString();
            btnSave.setEnabled(false);
            viewModel.updateEntryMetadata(entry, editableDate[0], caption, success -> runOnUiThread(() -> {
                btnSave.setEnabled(true);
                if (success) {
                    Toast.makeText(this, "Đã lưu thay đổi ảnh nhật ký", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    viewModel.loadEntriesOfDate(selectedDate);
                } else {
                    Toast.makeText(this, "Không thể lưu thay đổi. Vui lòng thử lại.", Toast.LENGTH_SHORT).show();
                }
            }));
        });

        dialog.setOnShowListener(shownDialog -> {
            Window shownWindow = dialog.getWindow();
            if (shownWindow != null) {
                shownWindow.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                shownWindow.setBackgroundDrawableResource(android.R.color.transparent);
            }
        });
        dialog.show();
    }

    private interface DatePickCallback {
        void onDatePicked(@NonNull LocalDate pickedDate);
    }

    private void showPhotoDatePicker(@NonNull LocalDate currentDate, @NonNull DatePickCallback callback) {
        DatePickerDialog pickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> callback.onDatePicked(LocalDate.of(year, month + 1, dayOfMonth)),
                currentDate.getYear(),
                currentDate.getMonthValue() - 1,
                currentDate.getDayOfMonth());
        pickerDialog.show();
    }

    private void finishWithSlideBack() {
        finish();
        overridePendingTransition(R.anim.slide_in_left_scale, R.anim.slide_out_right_scale);
    }

    private void playBounceThen(@NonNull View target, @NonNull Runnable action) {
        target.setEnabled(false);
        target.animate().cancel();
        target.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(90L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> target.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(220L)
                        .setInterpolator(new OvershootInterpolator(0.95f))
                        .withEndAction(() -> {
                            target.setEnabled(true);
                            action.run();
                        })
                        .start())
                .start();
    }

    private void applyInsets() {
        if (root == null || topRow == null || fabAddPhoto == null) {
            return;
        }

        ViewGroup.MarginLayoutParams topRowParams = (ViewGroup.MarginLayoutParams) topRow.getLayoutParams();
        final int baseTopMargin = topRowParams.topMargin;

        ViewGroup.MarginLayoutParams fabParams = (ViewGroup.MarginLayoutParams) fabAddPhoto.getLayoutParams();
        final int baseFabBottom = fabParams.bottomMargin;
        final int baseFabEnd = fabParams.rightMargin;

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, 0, bars.right, 0);

            ViewGroup.MarginLayoutParams updatedTopRowParams = (ViewGroup.MarginLayoutParams) topRow.getLayoutParams();
            updatedTopRowParams.topMargin = baseTopMargin + bars.top;
            topRow.setLayoutParams(updatedTopRowParams);

            ViewGroup.MarginLayoutParams updatedFabParams = (ViewGroup.MarginLayoutParams) fabAddPhoto.getLayoutParams();
            updatedFabParams.bottomMargin = baseFabBottom + bars.bottom;
            updatedFabParams.rightMargin = baseFabEnd + bars.right;
            fabAddPhoto.setLayoutParams(updatedFabParams);

            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
