package com.coolcook.app.feature.profile.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.coolcook.app.R;
import com.coolcook.app.core.theme.ThemeManager;
import com.coolcook.app.feature.camera.data.ScanSavedDishStore;
import com.coolcook.app.feature.camera.ui.ScanDishRecipeBottomSheet;
import com.coolcook.app.feature.journal.data.JournalRepository;
import com.coolcook.app.feature.journal.model.JournalEntry;
import com.coolcook.app.feature.profile.data.HealthAnalyzer;
import com.coolcook.app.feature.profile.data.HealthProfileRepository;
import com.coolcook.app.feature.profile.model.HealthAnalysisResult;
import com.coolcook.app.feature.profile.model.HealthProfile;
import com.coolcook.app.feature.recommendation.data.HealthFoodRecommendationRepository;
import com.coolcook.app.feature.recommendation.model.HealthRecommendedFood;
import com.coolcook.app.feature.search.ui.FoodDetailActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class HealthTrackingActivity extends AppCompatActivity {

    private TextInputLayout tilWeight;
    private TextInputLayout tilSystolic;
    private TextInputLayout tilDiastolic;
    private TextInputLayout tilHeartRate;
    private TextInputLayout tilGoal;
    private TextInputEditText edtWeight;
    private TextInputEditText edtSystolic;
    private TextInputEditText edtDiastolic;
    private TextInputEditText edtHeartRate;
    private TextInputEditText edtGoal;
    private MaterialButton btnSaveHealthProfile;
    private TextView txtHealthStatus;
    private TextView txtHealthSummary;
    private TextView txtHealthWarning;
    private TextView txtHealthShouldEat;
    private TextView txtHealthShouldLimit;
    private TextView txtHealthEmpty;
    private LinearLayout recommendationsContainer;

    private HealthProfileRepository healthProfileRepository;
    private HealthFoodRecommendationRepository recommendationRepository;
    private ScanSavedDishStore scanSavedDishStore;
    private JournalRepository journalRepository;

    @NonNull
    public static Intent createIntent(@NonNull Context context) {
        return new Intent(context, HealthTrackingActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_tracking);

        bindViews();
        applyInsets();
        bindActions();

        healthProfileRepository = new HealthProfileRepository(FirebaseFirestore.getInstance());
        recommendationRepository = new HealthFoodRecommendationRepository(this);
        scanSavedDishStore = new ScanSavedDishStore(this);
        journalRepository = new JournalRepository(FirebaseFirestore.getInstance());

        loadHealthProfileAndRecommendations();
    }

    private void bindViews() {
        tilWeight = findViewById(R.id.tilProfileWeight);
        tilSystolic = findViewById(R.id.tilProfileSystolic);
        tilDiastolic = findViewById(R.id.tilProfileDiastolic);
        tilHeartRate = findViewById(R.id.tilProfileHeartRate);
        tilGoal = findViewById(R.id.tilProfileGoal);
        edtWeight = findViewById(R.id.edtProfileWeight);
        edtSystolic = findViewById(R.id.edtProfileSystolic);
        edtDiastolic = findViewById(R.id.edtProfileDiastolic);
        edtHeartRate = findViewById(R.id.edtProfileHeartRate);
        edtGoal = findViewById(R.id.edtProfileGoal);
        btnSaveHealthProfile = findViewById(R.id.btnSaveHealthProfile);
        txtHealthStatus = findViewById(R.id.txtProfileHealthStatus);
        txtHealthSummary = findViewById(R.id.txtProfileHealthSummary);
        txtHealthWarning = findViewById(R.id.txtProfileHealthWarning);
        txtHealthShouldEat = findViewById(R.id.txtProfileHealthShouldEat);
        txtHealthShouldLimit = findViewById(R.id.txtProfileHealthShouldLimit);
        txtHealthEmpty = findViewById(R.id.txtProfileHealthRecommendationsEmpty);
        recommendationsContainer = findViewById(R.id.layoutProfileHealthRecommendationsList);
    }

    private void bindActions() {
        View backButton = findViewById(R.id.btnHealthTrackingBack);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
        if (btnSaveHealthProfile != null) {
            btnSaveHealthProfile.setOnClickListener(v -> saveHealthProfile());
        }
    }

    private void applyInsets() {
        View root = findViewById(R.id.healthTrackingRoot);
        View header = findViewById(R.id.healthTrackingHeader);
        View scroll = findViewById(R.id.healthTrackingScroll);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (header != null) {
                header.setPadding(
                        header.getPaddingLeft(),
                        systemBars.top + dpToPx(8),
                        header.getPaddingRight(),
                        header.getPaddingBottom());
            }
            if (scroll != null) {
                scroll.setPadding(
                        scroll.getPaddingLeft(),
                        scroll.getPaddingTop(),
                        scroll.getPaddingRight(),
                        systemBars.bottom);
            }
            return insets;
        });
    }

    private void loadHealthProfileAndRecommendations() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            renderLoggedOutHealthState();
            return;
        }

        String userId = user.getUid();
        healthProfileRepository.loadHealthProfile(userId, (profile, error) -> {
            if (!isActivityAliveForUi()) {
                return;
            }

            bindHealthProfile(profile);
            if (!TextUtils.isEmpty(error)) {
                setHealthStatus(error);
            } else if (profile == null) {
                setHealthStatus("Bạn chưa có hồ sơ sức khỏe. Hãy nhập chỉ số để nhận gợi ý tham khảo.");
            } else {
                setHealthStatus("Đã đồng bộ hồ sơ sức khỏe gần nhất.");
            }
        });

        recommendationRepository.loadRecommendations(userId, (profile, analysis, recommendations, friendlyError) -> {
            if (!isActivityAliveForUi()) {
                return;
            }
            renderRecommendations(profile, analysis, recommendations, friendlyError);
        });
    }

    private void bindHealthProfile(@Nullable HealthProfile profile) {
        if (profile == null) {
            setEditTextValue(edtWeight, "");
            setEditTextValue(edtSystolic, "");
            setEditTextValue(edtDiastolic, "");
            setEditTextValue(edtHeartRate, "");
            setEditTextValue(edtGoal, "");
            return;
        }

        setEditTextValue(edtWeight, formatWeight(profile.getWeightKg()));
        setEditTextValue(edtSystolic, String.valueOf(profile.getSystolicBp()));
        setEditTextValue(edtDiastolic, String.valueOf(profile.getDiastolicBp()));
        setEditTextValue(edtHeartRate, String.valueOf(profile.getHeartRateBpm()));
        setEditTextValue(edtGoal, profile.getGoal());
    }

    private void renderRecommendations(
            @Nullable HealthProfile profile,
            @Nullable HealthAnalysisResult analysis,
            @NonNull List<HealthRecommendedFood> recommendations,
            @Nullable String friendlyError) {
        if (txtHealthSummary == null || recommendationsContainer == null || txtHealthEmpty == null) {
            return;
        }

        if (!TextUtils.isEmpty(friendlyError) && profile == null) {
            txtHealthSummary.setText("Bạn chưa có hồ sơ sức khỏe. Hãy lưu chỉ số để nhận gợi ý tham khảo.");
            txtHealthShouldEat.setText("");
            txtHealthShouldLimit.setText("");
            txtHealthWarning.setVisibility(View.GONE);
            recommendationsContainer.removeAllViews();
            txtHealthEmpty.setVisibility(View.VISIBLE);
            txtHealthEmpty.setText("Biểu mẫu nhập sức khỏe đã sẵn sàng.");
            return;
        }

        if (analysis == null) {
            txtHealthSummary.setText("Bạn chưa có hồ sơ sức khỏe. Hãy lưu chỉ số để nhận gợi ý tham khảo.");
            txtHealthShouldEat.setText("");
            txtHealthShouldLimit.setText("");
            txtHealthWarning.setVisibility(View.GONE);
            recommendationsContainer.removeAllViews();
            txtHealthEmpty.setVisibility(View.VISIBLE);
            txtHealthEmpty.setText("Gợi ý sẽ xuất hiện sau khi bạn lưu hồ sơ sức khỏe.");
            return;
        }

        txtHealthSummary.setText(analysis.getSummary());
        txtHealthShouldEat.setText("Nên ưu tiên: " + joinOrFallback(analysis.getShouldEat(), "bữa ăn cân bằng."));
        txtHealthShouldLimit.setText("Nên hạn chế: " + joinOrFallback(analysis.getShouldLimit(), "món quá nhiều dầu và muối."));

        if (analysis.getWarning().isEmpty()) {
            txtHealthWarning.setVisibility(View.GONE);
        } else {
            txtHealthWarning.setVisibility(View.VISIBLE);
            txtHealthWarning.setText(analysis.getWarning());
        }

        recommendationsContainer.removeAllViews();
        if (recommendations.isEmpty()) {
            txtHealthEmpty.setVisibility(View.VISIBLE);
            txtHealthEmpty.setText("Chưa có món phù hợp từ dữ liệu hiện tại. Bạn có thể thử lưu lại hồ sơ hoặc kiểm tra kết nối.");
            return;
        }

        txtHealthEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (HealthRecommendedFood recommendation : recommendations) {
            View itemView = inflater.inflate(R.layout.item_profile_health_recommendation, recommendationsContainer, false);
            bindRecommendationItem(itemView, recommendation);
            recommendationsContainer.addView(itemView);
        }
    }

    private void bindRecommendationItem(@NonNull View itemView, @NonNull HealthRecommendedFood recommendation) {
        android.widget.ImageView imageView = itemView.findViewById(R.id.imgHealthRecommendation);
        TextView nameView = itemView.findViewById(R.id.txtHealthRecommendationName);
        TextView reasonView = itemView.findViewById(R.id.txtHealthRecommendationReason);
        ChipGroup chipGroup = itemView.findViewById(R.id.groupHealthRecommendationTags);
        MaterialButton btnRecipe = itemView.findViewById(R.id.btnHealthRecommendationRecipe);
        MaterialButton btnSave = itemView.findViewById(R.id.btnHealthRecommendationSave);
        MaterialButton btnJournal = itemView.findViewById(R.id.btnHealthRecommendationJournal);

        imageView.setImageResource(recommendation.resolveImageResId(this));
        nameView.setText(recommendation.getName());
        reasonView.setText(recommendation.getReason());
        bindTags(chipGroup, recommendation.getSuitableFor());

        btnRecipe.setOnClickListener(v -> openRecommendationRecipe(recommendation));
        btnSave.setOnClickListener(v -> saveRecommendationDish(recommendation));
        btnJournal.setOnClickListener(v -> addRecommendationToJournal(recommendation));
    }

    private void bindTags(@NonNull ChipGroup chipGroup, @NonNull List<String> tags) {
        chipGroup.removeAllViews();
        for (String tag : tags) {
            if (TextUtils.isEmpty(tag)) {
                continue;
            }
            Chip chip = new Chip(this);
            chip.setText(tag);
            chip.setCheckable(false);
            chip.setClickable(false);
            chip.setChipBackgroundColorResource(R.color.surface_container_low);
            chip.setTextColor(getColor(R.color.textSecondary));
            chipGroup.addView(chip);
        }
    }

    private void openRecommendationRecipe(@NonNull HealthRecommendedFood recommendation) {
        if (recommendation.isLocal()) {
            Intent intent = FoodDetailActivity.createIntent(this, recommendation.getFoodId());
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right_scale, R.anim.slide_out_left_scale);
            return;
        }
        ScanDishRecipeBottomSheet.show(this, recommendation.toScanDishItem());
    }

    private void saveRecommendationDish(@NonNull HealthRecommendedFood recommendation) {
        boolean saved = scanSavedDishStore.save(recommendation.toScanDishItem());
        Toast.makeText(
                this,
                saved ? "Đã lưu món vào danh sách đã lưu." : "Món này đã có trong danh sách đã lưu.",
                Toast.LENGTH_SHORT).show();
    }

    private void addRecommendationToJournal(@NonNull HealthRecommendedFood recommendation) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Bạn cần đăng nhập để lưu nhật ký món ăn.", Toast.LENGTH_SHORT).show();
            return;
        }

        JournalEntry entry = JournalEntry.createFoodEntry(
                user.getUid(),
                java.time.LocalDate.now(),
                recommendation.getFoodId(),
                recommendation.getName(),
                "",
                recommendation.getImageName(),
                inferMealType(),
                recommendation.getReason(),
                "health",
                null);

        journalRepository.saveEntry(user.getUid(), entry, error -> runOnUiThread(() -> Toast.makeText(
                this,
                error == null
                        ? "Đã thêm món vào nhật ký món ăn."
                        : "Không thể lưu nhật ký món ăn lúc này.",
                Toast.LENGTH_SHORT).show()));
    }

    private void saveHealthProfile() {
        clearHealthErrors();

        Double weight = parseDouble(edtWeight);
        Integer systolic = parseInt(edtSystolic);
        Integer diastolic = parseInt(edtDiastolic);
        Integer heartRate = parseInt(edtHeartRate);
        String goal = edtGoal == null || edtGoal.getText() == null ? "" : edtGoal.getText().toString().trim();

        boolean valid = true;
        if (weight == null) {
            tilWeight.setError("Vui lòng nhập cân nặng.");
            valid = false;
        }
        if (systolic == null) {
            tilSystolic.setError("Nhập tâm thu.");
            valid = false;
        }
        if (diastolic == null) {
            tilDiastolic.setError("Nhập tâm trương.");
            valid = false;
        }
        if (heartRate == null) {
            tilHeartRate.setError("Nhập nhịp tim.");
            valid = false;
        }
        if (!valid) {
            setHealthStatus("Vui lòng kiểm tra lại chỉ số sức khỏe.");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            setHealthStatus("Bạn cần đăng nhập để lưu hồ sơ sức khỏe.");
            Toast.makeText(this, "Bạn cần đăng nhập để lưu hồ sơ sức khỏe.", Toast.LENGTH_SHORT).show();
            return;
        }

        HealthProfile draftProfile = new HealthProfile(
                weight,
                systolic,
                diastolic,
                heartRate,
                goal,
                new ArrayList<>(),
                null);
        HealthAnalysisResult analysis = HealthAnalyzer.analyze(draftProfile);
        HealthProfile finalProfile = new HealthProfile(
                weight,
                systolic,
                diastolic,
                heartRate,
                goal,
                analysis.getTags(),
                null);

        healthProfileRepository.saveHealthProfile(user.getUid(), finalProfile, (success, friendlyError) -> {
            if (!isActivityAliveForUi()) {
                return;
            }
            if (!success) {
                setHealthStatus(friendlyError == null
                        ? "Không thể lưu hồ sơ sức khỏe."
                        : friendlyError);
                if (friendlyError != null) {
                    attachValidationError(friendlyError);
                }
                return;
            }

            Toast.makeText(this, "Đã lưu hồ sơ sức khỏe.", Toast.LENGTH_SHORT).show();
            setHealthStatus("Đã lưu hồ sơ sức khỏe. CoolCook đang cập nhật gợi ý hôm nay.");
            loadHealthProfileAndRecommendations();
        });
    }

    private void attachValidationError(@NonNull String friendlyError) {
        String normalized = friendlyError.toLowerCase(Locale.ROOT);
        if (normalized.contains("cân nặng")) {
            tilWeight.setError(friendlyError);
        } else if (normalized.contains("tâm thu")) {
            tilSystolic.setError(friendlyError);
        } else if (normalized.contains("tâm trương")) {
            tilDiastolic.setError(friendlyError);
        } else if (normalized.contains("nhịp tim")) {
            tilHeartRate.setError(friendlyError);
        }
    }

    private void clearHealthErrors() {
        if (tilWeight != null) tilWeight.setError(null);
        if (tilSystolic != null) tilSystolic.setError(null);
        if (tilDiastolic != null) tilDiastolic.setError(null);
        if (tilHeartRate != null) tilHeartRate.setError(null);
        if (tilGoal != null) tilGoal.setError(null);
    }

    private void renderLoggedOutHealthState() {
        bindHealthProfile(null);
        if (txtHealthSummary != null) {
            txtHealthSummary.setText("Bạn chưa đăng nhập nên chưa thể đồng bộ hồ sơ sức khỏe.");
        }
        if (txtHealthEmpty != null) {
            txtHealthEmpty.setVisibility(View.VISIBLE);
            txtHealthEmpty.setText("Bạn có thể đăng nhập rồi lưu chỉ số để nhận gợi ý tham khảo.");
        }
    }

    private void setHealthStatus(@NonNull String message) {
        if (txtHealthStatus != null) {
            txtHealthStatus.setText(message);
        }
    }

    private boolean isActivityAliveForUi() {
        return !isFinishing() && !isDestroyed();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Nullable
    private static Double parseDouble(@Nullable TextInputEditText editText) {
        if (editText == null || editText.getText() == null) {
            return null;
        }
        String value = editText.getText().toString().trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nullable
    private static Integer parseInt(@Nullable TextInputEditText editText) {
        if (editText == null || editText.getText() == null) {
            return null;
        }
        String value = editText.getText().toString().trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void setEditTextValue(@Nullable TextInputEditText editText, @NonNull String value) {
        if (editText == null) {
            return;
        }
        String current = editText.getText() == null ? "" : editText.getText().toString();
        if (!value.contentEquals(current)) {
            editText.setText(value);
        }
    }

    @NonNull
    private static String formatWeight(double weight) {
        if (weight <= 0d) {
            return "";
        }
        return weight == Math.rint(weight)
                ? String.valueOf((int) weight)
                : String.format(Locale.US, "%.1f", weight);
    }

    @NonNull
    private static String joinOrFallback(@NonNull List<String> values, @NonNull String fallback) {
        if (values.isEmpty()) {
            return fallback;
        }
        return TextUtils.join(", ", values);
    }

    @NonNull
    private static String inferMealType() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 10) {
            return "breakfast";
        }
        if (hour < 15) {
            return "lunch";
        }
        if (hour < 21) {
            return "dinner";
        }
        return "snack";
    }
}
