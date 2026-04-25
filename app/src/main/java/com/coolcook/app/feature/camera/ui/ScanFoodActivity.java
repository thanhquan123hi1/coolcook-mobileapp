package com.coolcook.app.feature.camera.ui;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.coolcook.app.R;
import com.coolcook.app.feature.camera.data.ScanFoodLocalMatcher;
import com.coolcook.app.feature.camera.data.ScanHealthFilters;
import com.coolcook.app.feature.camera.data.ScanSavedDishStore;
import com.coolcook.app.feature.camera.model.DetectedIngredient;
import com.coolcook.app.feature.camera.model.ScanDishItem;
import com.coolcook.app.feature.camera.model.SuggestedDish;
import com.coolcook.app.feature.camera.ui.adapter.ScanDishSuggestionAdapter;
import com.coolcook.app.feature.chatbot.data.GeminiRepository;
import com.coolcook.app.feature.social.data.FriendInviteRepository;
import com.coolcook.app.feature.social.data.JournalFeedRepository;
import com.coolcook.app.feature.social.data.MediaUploadRepository;
import com.coolcook.app.feature.journal.data.JournalRepository;
import com.coolcook.app.feature.home.ui.HomeActivity;
import com.coolcook.app.feature.search.model.FoodItem;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONObject;

public class ScanFoodActivity extends AppCompatActivity {
    private static final String TAG = "ScanFoodActivity";
    private static final int SUGGESTION_LIMIT = 3;
    private static final List<String> MAIN_INGREDIENT_TOKENS = java.util.Arrays.asList(
            "ga",
            "bo",
            "heo",
            "lon",
            "tom",
            "ca",
            "muc",
            "cua",
            "trung",
            "com",
            "mi",
            "bun",
            "pho",
            "banh mi");

    private static final long PRESS_IN_DURATION = 200L;
    private static final long PRESS_OUT_DURATION = 220L;
    private static final float PRESS_SCALE = 0.95f;
    private static final long MODE_TRANSITION_DURATION = 240L;
    private static final @ColorInt int TAB_ACTIVE_COLOR = Color.parseColor("#FABD00");
    private static final @ColorInt int TAB_INACTIVE_COLOR = Color.parseColor("#D4C5AB");

    private static final int MAX_RECOGNITION_IMAGE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_JOURNAL_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final String EXTRA_START_MODE = "extra_start_mode";
    private static final String START_MODE_JOURNAL = "journal";
    private static final String INGREDIENT_DETECTION_PROMPT = "Bạn là AI nhận diện nguyên liệu thực phẩm cho app CoolCook.\n"
            + "Hãy phân tích ảnh người dùng gửi và nhận diện từng nguyên liệu/thực phẩm nhìn thấy trong ảnh.\n"
            + "Chỉ trả về JSON hợp lệ, không markdown, không giải thích ngoài JSON.\n\n"
            + "Schema:\n"
            + "{\n"
            + "  \"detectedIngredients\": [\n"
            + "    {\n"
            + "      \"name\": \"Tên nguyên liệu\",\n"
            + "      \"confidence\": 0.0,\n"
            + "      \"category\": \"meat | seafood | vegetable | fruit | grain | dairy | spice | other\",\n"
            + "      \"visibleAmount\": \"ít | vừa | nhiều | không rõ\",\n"
            + "      \"notes\": \"mô tả ngắn nếu cần\"\n"
            + "    }\n"
            + "  ]\n"
            + "}";
    private static final String DISH_SUGGESTION_SYSTEM_PROMPT = "Bạn là AI gợi ý món ăn cho app CoolCook. Chỉ trả về JSON hợp lệ.";
    private static final String AI_DISH_GENERATION_SYSTEM_PROMPT = "Bạn là AI tạo món ăn mới cho app CoolCook. "
            + "Mỗi món phải có công thức tiếng Việt rõ ràng, thực tế và phù hợp với nguyên liệu được cung cấp. "
            + "Chỉ trả về JSON hợp lệ.";

    private View root;
    private View recognitionSurface;
    private View topBar;
    private View footerContainer;
    private View cameraPreviewCard;
    private View previewInnerFrame;
    private View detectionBox;
    private View scanResultContainer;
    private View btnSaveSelectedDish;
    private View tabIndicator;
    private View captureSaveOverlay;
    private View journalPreviewFooterContainer;
    private View btnBack;
    private View btnFlash;
    private View btnGallery;
    private View btnShutter;
    private View btnFlipCamera;
    private View btnCaptureSaveCancel;
    private View btnCaptureSaveConfirm;
    private View btnPreviewDownload;
    private View btnPreviewEffects;
    private ProgressBar captureSaveLoading;
    private TextView txtScanStatus;
    private TextView txtDetectedIngredientsEmpty;
    private TextView txtDishSuggestionsEmpty;
    private TextView txtSelectedDishHint;
    private TextView txtExtraIngredientsHint;
    private TextView txtCaptureUploadProgress;
    private TextView txtCaptureSaveError;
    private TextView tabNhanDien;
    private TextView tabNhatKy;
    private TextView iconFlash;
    private EditText edtCaptureCaption;
    private EditText edtManualExtraIngredient;
    private ImageView imgCapturePreview;
    private ImageView imgCapturedRecognition;
    private PreviewView recognitionPreviewView;
    private PreviewView previewView;
    private RecyclerView rvDishSuggestions;
    private ChipGroup groupDetectedIngredients;
    private ChipGroup groupSuggestedExtraIngredients;
    private ChipGroup groupSelectedExtraIngredients;
    private ChipGroup groupHealthFilters;

    private ActivityResultLauncher<String> galleryPickerLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    private Animation detectionPulseAnimation;
    private boolean isRecognitionMode = true;
    private boolean isFlashOn;
    private boolean isUsingFrontCamera;
    private boolean isCameraReady;
    private boolean isRecognitionInProgress;

    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private Camera camera;

    private GeminiRepository geminiRepository;
    private ScanFoodLocalMatcher scanFoodLocalMatcher;
    private ScanSavedDishStore scanSavedDishStore;
    private ScanDishSuggestionAdapter scanDishSuggestionAdapter;
    private MediaUploadRepository mediaUploadRepository;
    private JournalRepository journalRepository;
    private JournalFeedRepository journalFeedRepository;
    private FriendInviteRepository friendInviteRepository;
    private final List<DetectedIngredient> currentDetectedIngredients = new ArrayList<>();
    private final List<String> currentExtraIngredients = new ArrayList<>();
    private final List<ScanDishItem> allSuggestedDishItems = new ArrayList<>();
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private ScanFoodJournalManager journalManager;
    private byte[] lastRecognitionImageBytes;
    private ScanDishItem selectedDishItem;
    private String selectedHealthFilter = ScanHealthFilters.FILTER_ALL;
    private ExecutorService recognitionExecutor;
    private BottomSheetDialog suggestionDialog;
    private ScanDishSuggestionAdapter suggestionDialogAdapter;
    private ChipGroup suggestionIngredientGroup;
    private TextView txtSuggestionEmpty;

    public static Intent createIntent(Context context) {
        return new Intent(context, ScanFoodActivity.class);
    }

    public static Intent createJournalIntent(Context context) {
        Intent intent = createIntent(context);
        intent.putExtra(EXTRA_START_MODE, START_MODE_JOURNAL);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().setNavigationBarContrastEnforced(false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setContentView(R.layout.activity_scan_food);

        initViews();
        applyInsets();
        setupAnimations();
        setupModeTabs();
        setupGalleryPicker();
        setupPermissionFlow();
        setupPressScaleFeedback();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPressed();
            }
        });
        geminiRepository = new GeminiRepository();
        scanFoodLocalMatcher = new ScanFoodLocalMatcher(getApplicationContext());
        scanSavedDishStore = new ScanSavedDishStore(getApplicationContext());
        mediaUploadRepository = new MediaUploadRepository(getApplicationContext());
        journalRepository = new JournalRepository(firestore);
        journalFeedRepository = new JournalFeedRepository(firestore);
        friendInviteRepository = new FriendInviteRepository(firestore);
        setupRecognitionResultUi();
        setupClickListeners();
        recognitionExecutor = Executors.newSingleThreadExecutor();
        journalManager = new ScanFoodJournalManager(
                this,
                new ScanFoodJournalManager.Host() {
                    @Override
                    public void setProcessingUiEnabled(boolean enabled) {
                        ScanFoodActivity.this.setProcessingUiEnabled(enabled);
                    }

                    @Override
                    public void updateJournalStatus(@NonNull String status) {
                        ScanFoodActivity.this.updateEntryStatus(status);
                    }

                    @Override
                    public void setJournalPreviewUiVisible(boolean visible) {
                        ScanFoodActivity.this.setJournalPreviewUiVisible(visible);
                    }
                },
                null,
                null,
                null,
                txtCaptureUploadProgress,
                txtCaptureSaveError,
                edtCaptureCaption,
                imgCapturePreview,
                captureSaveOverlay,
                btnCaptureSaveCancel,
                 btnCaptureSaveConfirm,
                captureSaveLoading,
                mediaUploadRepository,
                journalFeedRepository,
                friendInviteRepository);

        applyInitialModeFromIntent();
        updateUiForMode(false);
        updateFlashUi(false);
        updateScanStatus(isRecognitionMode
                ? "Hướng camera vào món ăn rồi bấm nút chụp"
                : "Chụp nhanh một khoảnh khắc để đăng.");
        updateEntryStatus("Chụp ảnh, thêm caption và bấm Lưu vào nhật ký.");
        ensureCameraPermissionAndStart();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isRecognitionMode) {
            startDetectionPulse();
        }
        if (hasCameraPermission()) {
            startCameraIfNeeded();
        }
    }

    @Override
    protected void onPause() {
        stopDetectionPulse();
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissSuggestionDialog();
        if (recognitionExecutor != null) {
            recognitionExecutor.shutdownNow();
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    private void dismissSuggestionDialog() {
        if (suggestionDialog != null && suggestionDialog.isShowing()) {
            suggestionDialog.dismiss();
        }
    }

    private void handleBackPressed() {
        if (journalManager != null && journalManager.isCapturePreviewVisible()) {
            journalManager.hideJournalCapturePreview(true);
            return;
        }
        navigateBackHome();
    }

    private void initViews() {
        root = findViewById(R.id.scanRoot);
        recognitionSurface = findViewById(R.id.recognitionSurface);
        topBar = findViewById(R.id.topBar);
        footerContainer = findViewById(R.id.footerContainer);
        cameraPreviewCard = findViewById(R.id.cameraPreviewCard);
        previewInnerFrame = findViewById(R.id.previewInnerFrame);
        detectionBox = findViewById(R.id.detectionBox);
        scanResultContainer = findViewById(R.id.scanResultContainer);
        btnSaveSelectedDish = findViewById(R.id.btnSaveSelectedDish);
        tabIndicator = findViewById(R.id.tabIndicator);
        captureSaveOverlay = findViewById(R.id.captureSaveOverlay);
        journalPreviewFooterContainer = findViewById(R.id.journalPreviewFooterContainer);
        txtScanStatus = findViewById(R.id.txtScanStatus);
        txtDetectedIngredientsEmpty = findViewById(R.id.txtDetectedIngredientsEmpty);
        txtDishSuggestionsEmpty = findViewById(R.id.txtDishSuggestionsEmpty);
        txtSelectedDishHint = findViewById(R.id.txtSelectedDishHint);
        txtExtraIngredientsHint = findViewById(R.id.txtExtraIngredientsHint);
        txtCaptureUploadProgress = findViewById(R.id.txtPreviewUploadProgress);
        txtCaptureSaveError = findViewById(R.id.txtPreviewSaveError);
        tabNhanDien = findViewById(R.id.tabNhanDien);
        tabNhatKy = findViewById(R.id.tabNhatKy);
        iconFlash = findViewById(R.id.iconFlash);
        btnBack = findViewById(R.id.btnBack);
        btnFlash = findViewById(R.id.btnFlash);
        btnGallery = findViewById(R.id.btnGallery);
        btnShutter = findViewById(R.id.btnShutter);
        btnFlipCamera = findViewById(R.id.btnFlipCamera);
        btnCaptureSaveCancel = findViewById(R.id.btnPreviewCancel);
        btnCaptureSaveConfirm = findViewById(R.id.btnPreviewSend);
        btnPreviewDownload = findViewById(R.id.btnPreviewDownload);
        btnPreviewEffects = findViewById(R.id.btnPreviewEffects);
        captureSaveLoading = findViewById(R.id.capturePreviewLoading);
        edtCaptureCaption = findViewById(R.id.edtJournalPreviewCaption);
        edtManualExtraIngredient = findViewById(R.id.edtManualExtraIngredient);
        imgCapturePreview = findViewById(R.id.imgJournalPreviewInline);
        imgCapturedRecognition = findViewById(R.id.imgCapturedRecognition);
        recognitionPreviewView = findViewById(R.id.previewView);
        rvDishSuggestions = findViewById(R.id.rvDishSuggestions);
        groupDetectedIngredients = findViewById(R.id.groupDetectedIngredients);
        groupSuggestedExtraIngredients = findViewById(R.id.groupSuggestedExtraIngredients);
        groupSelectedExtraIngredients = findViewById(R.id.groupSelectedExtraIngredients);
        groupHealthFilters = findViewById(R.id.groupHealthFilters);
        previewView = recognitionPreviewView;
    }

    private void setupRecognitionResultUi() {
        suggestionDialogAdapter = new ScanDishSuggestionAdapter(new ScanDishSuggestionAdapter.DishActionListener() {
            @Override
            public void onDishClicked(@NonNull ScanDishItem item) {
                openDishRecipe(item);
            }

            @Override
            public void onSaveDishClicked(@NonNull ScanDishItem item) {
                saveSuggestedDish(item);
            }

            @Override
            public void onAddToJournalClicked(@NonNull ScanDishItem item) {
                saveSuggestedDish(item);
            }
        }, true);
        scanDishSuggestionAdapter = suggestionDialogAdapter;
        selectedHealthFilter = ScanHealthFilters.FILTER_ALL;
        if (scanResultContainer != null) {
            scanResultContainer.setVisibility(View.GONE);
        }
        if (groupHealthFilters != null) {
            groupHealthFilters.setVisibility(View.GONE);
        }
        if (rvDishSuggestions != null) {
            rvDishSuggestions.setVisibility(View.GONE);
        }
        if (btnSaveSelectedDish != null) {
            btnSaveSelectedDish.setVisibility(View.GONE);
        }
        if (txtSelectedDishHint != null) {
            txtSelectedDishHint.setVisibility(View.GONE);
        }
        renderSelectedExtraIngredients();
        renderSuggestedExtraIngredients();
    }

    private void saveSuggestedDish(@NonNull ScanDishItem item) {
        boolean saved = scanSavedDishStore != null && scanSavedDishStore.save(item);
        Toast.makeText(
                this,
            saved ? "Đã lưu món vào hồ sơ" : "Món này đã có trong danh sách đã lưu",
                Toast.LENGTH_SHORT).show();
    }

    private void openDishRecipe(@NonNull ScanDishItem item) {
        if (item.getRecipe().trim().isEmpty()) {
            Toast.makeText(this, "Món này chưa có công thức chi tiết.", Toast.LENGTH_SHORT).show();
            return;
        }
        ScanDishRecipeBottomSheet.show(this, item);
    }

    @NonNull
    private Chip createSelectableChip(@NonNull String label, boolean checked) {
        Chip chip = new Chip(this);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setCheckable(true);
        chip.setChecked(checked);
        chip.setClickable(true);
        chip.setCheckable(true);
        chip.setTextColor(Color.WHITE);
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#221F1F1F")));
        chip.setChipStrokeWidth(dp(1f));
        chip.setChipMinHeight(dp(34f));
        chip.setTypeface(getResources().getFont(R.font.be_vietnam_pro_medium));
        chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#334B4B4B")));
        return chip;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private void applyInitialModeFromIntent() {
        Intent intent = getIntent();
        if (intent == null) {
            isRecognitionMode = true;
            return;
        }

        String startMode = intent.getStringExtra(EXTRA_START_MODE);
        isRecognitionMode = !START_MODE_JOURNAL.equalsIgnoreCase(startMode);
    }

    private void applyInsets() {
        final ViewGroup.MarginLayoutParams topBarLayoutParams = topBar != null
                ? (ViewGroup.MarginLayoutParams) topBar.getLayoutParams()
                : null;
        final int topBarMarginStart = topBarLayoutParams == null ? 0 : topBarLayoutParams.getMarginStart();
        final int topBarMarginTop = topBarLayoutParams == null ? 0 : topBarLayoutParams.topMargin;
        final int topBarMarginEnd = topBarLayoutParams == null ? 0 : topBarLayoutParams.getMarginEnd();
        final int topBarMarginBottom = topBarLayoutParams == null ? 0 : topBarLayoutParams.bottomMargin;

        final int footerStart = footerContainer == null ? 0 : footerContainer.getPaddingStart();
        final int footerTop = footerContainer == null ? 0 : footerContainer.getPaddingTop();
        final int footerEnd = footerContainer == null ? 0 : footerContainer.getPaddingEnd();
        final int footerBottom = footerContainer == null ? 0 : footerContainer.getPaddingBottom();

        final int previewFooterStart = journalPreviewFooterContainer == null ? 0 : journalPreviewFooterContainer.getPaddingStart();
        final int previewFooterTop = journalPreviewFooterContainer == null ? 0 : journalPreviewFooterContainer.getPaddingTop();
        final int previewFooterEnd = journalPreviewFooterContainer == null ? 0 : journalPreviewFooterContainer.getPaddingEnd();
        final int previewFooterBottom = journalPreviewFooterContainer == null ? 0 : journalPreviewFooterContainer.getPaddingBottom();

        final int overlayStart = captureSaveOverlay == null ? 0 : captureSaveOverlay.getPaddingStart();
        final int overlayTop = captureSaveOverlay == null ? 0 : captureSaveOverlay.getPaddingTop();
        final int overlayEnd = captureSaveOverlay == null ? 0 : captureSaveOverlay.getPaddingEnd();
        final int overlayBottom = captureSaveOverlay == null ? 0 : captureSaveOverlay.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            if (topBar != null && topBarLayoutParams != null) {
                topBarLayoutParams.setMarginStart(topBarMarginStart + systemBars.left);
                topBarLayoutParams.topMargin = topBarMarginTop + systemBars.top;
                topBarLayoutParams.setMarginEnd(topBarMarginEnd + systemBars.right);
                topBarLayoutParams.bottomMargin = topBarMarginBottom;
                topBar.setLayoutParams(topBarLayoutParams);
            }

            if (footerContainer != null) {
                footerContainer.setPaddingRelative(
                        footerStart + systemBars.left,
                        footerTop,
                        footerEnd + systemBars.right,
                        footerBottom + systemBars.bottom);
            }

            if (journalPreviewFooterContainer != null) {
                journalPreviewFooterContainer.setPaddingRelative(
                        previewFooterStart + systemBars.left,
                        previewFooterTop,
                        previewFooterEnd + systemBars.right,
                        previewFooterBottom + systemBars.bottom);
            }

            if (captureSaveOverlay != null) {
                captureSaveOverlay.setPaddingRelative(
                        overlayStart + systemBars.left,
                        overlayTop + systemBars.top,
                        overlayEnd + systemBars.right,
                        overlayBottom + systemBars.bottom);
            }

            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void setupAnimations() {
        detectionPulseAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse_detection);
        startDetectionPulse();
    }

    private void setupModeTabs() {
        if (tabNhanDien != null) {
            tabNhanDien.setOnClickListener(v -> switchMode(true));
        }
        if (tabNhatKy != null) {
            tabNhatKy.setOnClickListener(v -> switchMode(false));
        }
        moveIndicatorTo(tabNhanDien, false);
    }

    private void setupGalleryPicker() {
        galleryPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::onGalleryImageSelected);
    }

    private void setupPermissionFlow() {
        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        updateScanStatus(isRecognitionMode
                                ? "Camera sẵn sàng, bạn có thể quét món ăn"
                                : "Camera sẵn sàng, bạn có thể đăng moment");
                        updateEntryStatus("Camera sẵn sàng. Chụp ảnh để lưu vào nhật ký.");
                        startCameraIfNeeded();
                    } else {
                        isCameraReady = false;
                        updateScanStatus("Bạn cần cấp quyền Camera để dùng tính năng này");
                        updateEntryStatus("Bạn cần cấp quyền Camera để lưu nhật ký.");
                        Toast.makeText(this, "Vui lòng cấp quyền camera", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void ensureCameraPermissionAndStart() {
        if (hasCameraPermission()) {
            startCameraIfNeeded();
            return;
        }
        updateScanStatus("Đang xin quyền camera...");
        updateEntryStatus("Đang xin quyền camera...");
        if (cameraPermissionLauncher != null) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startCameraIfNeeded() {
        updateActivePreviewView();
        if (previewView == null || !hasCameraPermission()) {
            return;
        }

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (Exception exception) {
                isCameraReady = false;
                updateScanStatus("Không thể khởi tạo camera, vui lòng thử lại");
                updateEntryStatus("Không thể khởi tạo camera.");
                Toast.makeText(this, "Không mở được camera", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void updateActivePreviewView() {
        previewView = recognitionPreviewView;
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null || previewView == null) {
            return;
        }

        cameraProvider.unbindAll();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        CameraSelector selector = new CameraSelector.Builder()
                .requireLensFacing(isUsingFrontCamera
                        ? CameraSelector.LENS_FACING_FRONT
                        : CameraSelector.LENS_FACING_BACK)
                .build();

        try {
            camera = cameraProvider.bindToLifecycle(this, selector, preview, imageCapture);
            isCameraReady = true;
            applyTorchState();
            updateScanStatus(isRecognitionMode
                    ? "Camera sẵn sàng, bạn có thể quét món ăn"
                    : "Camera sẵn sàng, bấm chụp để đăng.");
            updateEntryStatus("Camera sẵn sàng. Chụp ảnh để lưu vào nhật ký.");
        } catch (Exception bindError) {
            isCameraReady = false;
            updateScanStatus("Không bind được camera, đang thử camera khác...");
            updateEntryStatus("Không bind được camera.");
            if (!isUsingFrontCamera) {
                isUsingFrontCamera = true;
                bindCameraUseCases();
                return;
            }
            Toast.makeText(this, "Thiết bị không hỗ trợ camera", Toast.LENGTH_SHORT).show();
        }
    }

    private void onGalleryImageSelected(@Nullable Uri imageUri) {
        if (imageUri == null) {
            return;
        }
        if (isBusyProcessing()) {
            Toast.makeText(this, "Đang xử lý, vui lòng chờ", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isRecognitionMode) {
            processImageUriForRecognition(imageUri, "gallery");
        } else {
            processImageUriForJournal(imageUri, "gallery");
        }
    }

    private void setupPressScaleFeedback() {
        applyPressScale(
                btnBack,
                btnFlash,
                btnGallery,
                btnShutter,
                btnFlipCamera,
                tabNhanDien,
                tabNhatKy,
                btnCaptureSaveCancel,
                btnCaptureSaveConfirm);
    }

    private void applyPressScale(View... targets) {
        for (View target : targets) {
            if (target == null) {
                continue;
            }
            target.setOnTouchListener((v, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        animatePressState(v, true);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        animatePressState(v, false);
                        break;
                    default:
                        break;
                }
                return false;
            });
        }
    }

    private void animatePressState(View target, boolean pressed) {
        float targetScale = pressed ? PRESS_SCALE : 1f;
        target.animate().cancel();
        target.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .setDuration(pressed ? PRESS_IN_DURATION : PRESS_OUT_DURATION)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void startDetectionPulse() {
        if (detectionBox == null || detectionPulseAnimation == null || !isRecognitionMode) {
            return;
        }
        detectionBox.clearAnimation();
        detectionBox.startAnimation(detectionPulseAnimation);
    }

    private void stopDetectionPulse() {
        if (detectionBox == null) {
            return;
        }
        detectionBox.clearAnimation();
        detectionBox.setScaleX(1f);
        detectionBox.setScaleY(1f);
    }

    private void switchMode(boolean recognitionMode) {
        if (isRecognitionMode == recognitionMode) {
            return;
        }
        if (isBusyProcessing()) {
            Toast.makeText(this, "Đang xử lý ảnh, vui lòng chờ xong rồi đổi tab", Toast.LENGTH_SHORT).show();
            return;
        }
        isRecognitionMode = recognitionMode;
        updateUiForMode(true);
        startCameraIfNeeded();
    }

    private void updateUiForMode(boolean animated) {
        animateTabColor(tabNhanDien, isRecognitionMode ? TAB_ACTIVE_COLOR : TAB_INACTIVE_COLOR, animated);
        animateTabColor(tabNhatKy, isRecognitionMode ? TAB_INACTIVE_COLOR : TAB_ACTIVE_COLOR, animated);

        if (tabNhanDien != null) {
            tabNhanDien.animate().cancel();
            tabNhanDien.animate()
                    .alpha(isRecognitionMode ? 1f : 0.75f)
                    .setDuration(animated ? MODE_TRANSITION_DURATION : 0L)
                    .start();
        }
        if (tabNhatKy != null) {
            tabNhatKy.animate().cancel();
            tabNhatKy.animate()
                    .alpha(isRecognitionMode ? 0.72f : 1f)
                    .setDuration(animated ? MODE_TRANSITION_DURATION : 0L)
                    .start();
        }

        moveIndicatorTo(isRecognitionMode ? tabNhanDien : tabNhatKy, animated);
        updateActivePreviewView();

        if (recognitionSurface != null) {
            recognitionSurface.setVisibility(View.VISIBLE);
        }

        if (cameraPreviewCard != null) {
            cameraPreviewCard.setAlpha(1f);
        }
        if (previewInnerFrame != null) {
            previewInnerFrame.setAlpha(isRecognitionMode ? 1f : 0.62f);
        }
        if (!isRecognitionMode && scanResultContainer != null) {
            scanResultContainer.setVisibility(View.GONE);
        }

        if (detectionBox != null) {
            detectionBox.animate().cancel();
            if (isRecognitionMode) {
                detectionBox.setVisibility(View.VISIBLE);
                detectionBox.animate()
                        .alpha(1f)
                        .setDuration(animated ? MODE_TRANSITION_DURATION : 0L)
                        .setInterpolator(new DecelerateInterpolator())
                        .withStartAction(this::startDetectionPulse)
                        .start();
                if (!isRecognitionInProgress) {
                    updateScanStatus("Hướng camera vào món ăn rồi bấm nút chụp");
                }
            } else {
                stopDetectionPulse();
                detectionBox.setVisibility(View.INVISIBLE);
                if (!isBusyProcessing()) {
                    updateEntryStatus("Chụp ảnh, thêm caption và bấm Lưu vào nhật ký.");
                }
            }
        }

        if (isRecognitionMode && journalManager != null && journalManager.isCapturePreviewVisible()) {
            journalManager.hideJournalCapturePreview(true);
        }
    }

    private void setJournalPreviewUiVisible(boolean visible) {
        updateCameraPreviewConstraintsForState(visible);
        if (captureSaveOverlay != null) {
            captureSaveOverlay.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (journalPreviewFooterContainer != null) {
            journalPreviewFooterContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (imgCapturePreview != null) {
            imgCapturePreview.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (edtCaptureCaption != null) {
            edtCaptureCaption.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (topBar != null) {
            topBar.setVisibility(visible ? View.GONE : View.VISIBLE);
        }
        if (footerContainer != null) {
            footerContainer.setVisibility(visible ? View.GONE : View.VISIBLE);
        }
        if (btnFlash != null) {
            btnFlash.setVisibility(visible ? View.GONE : View.VISIBLE);
        }
        if (previewView != null) {
            previewView.setVisibility(visible ? View.INVISIBLE : View.VISIBLE);
        }
        if (previewInnerFrame != null) {
            previewInnerFrame.setVisibility(visible ? View.GONE : View.VISIBLE);
        }
        if (detectionBox != null) {
            detectionBox.setVisibility(visible ? View.GONE : (isRecognitionMode ? View.VISIBLE : View.INVISIBLE));
        }
        if (!visible) {
            updateUiForMode(false);
        }
    }

    private void updateCameraPreviewConstraintsForState(boolean previewVisible) {
        if (!(recognitionSurface instanceof ConstraintLayout) || cameraPreviewCard == null) {
            return;
        }

        ConstraintLayout rootLayout = (ConstraintLayout) recognitionSurface;
        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(rootLayout);
        constraintSet.clear(R.id.cameraPreviewCard, ConstraintSet.TOP);
        constraintSet.clear(R.id.cameraPreviewCard, ConstraintSet.BOTTOM);

        int topMargin = getResources().getDimensionPixelSize(
                previewVisible
                        ? R.dimen.camera_screen_preview_journal_top_gap
                        : R.dimen.camera_screen_preview_top_gap);

        if (previewVisible) {
            constraintSet.connect(R.id.cameraPreviewCard, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, topMargin);
            constraintSet.connect(R.id.cameraPreviewCard, ConstraintSet.BOTTOM, R.id.journalPreviewFooterContainer, ConstraintSet.TOP, 0);
        } else {
            constraintSet.connect(R.id.cameraPreviewCard, ConstraintSet.TOP, R.id.topBar, ConstraintSet.BOTTOM, topMargin);
            constraintSet.connect(R.id.cameraPreviewCard, ConstraintSet.BOTTOM, R.id.footerContainer, ConstraintSet.TOP, 0);
        }
        constraintSet.setVerticalBias(R.id.cameraPreviewCard, 0f);
        constraintSet.applyTo(rootLayout);
    }

    private void moveIndicatorTo(View target, boolean animated) {
        if (target == null || tabIndicator == null) {
            return;
        }

        tabIndicator.post(() -> {
            float targetX = target.getX() + (target.getWidth() - tabIndicator.getWidth()) / 2f;
            if (!animated) {
                tabIndicator.setX(targetX);
                return;
            }
            tabIndicator.animate().cancel();
            tabIndicator.animate()
                    .x(targetX)
                    .setDuration(MODE_TRANSITION_DURATION)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        });
    }

    private void animateTabColor(TextView target, @ColorInt int targetColor, boolean animated) {
        if (target == null) {
            return;
        }

        if (!animated) {
            target.setTextColor(targetColor);
            return;
        }

        int currentColor = target.getCurrentTextColor();
        if (currentColor == targetColor) {
            target.setTextColor(targetColor);
            return;
        }

        ValueAnimator colorAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), currentColor, targetColor);
        colorAnimator.setDuration(MODE_TRANSITION_DURATION);
        colorAnimator.setInterpolator(new DecelerateInterpolator());
        colorAnimator.addUpdateListener(animation -> target.setTextColor((int) animation.getAnimatedValue()));
        colorAnimator.start();
    }

    private void updateFlashUi(boolean showToast) {
        if (iconFlash != null) {
            iconFlash.setText(isFlashOn ? "flash_on" : "flash_off");
            iconFlash.setTextColor(Color.WHITE);
        }

        if (showToast) {
            Toast.makeText(this, isFlashOn ? "Flash: Bật" : "Flash: Tắt", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyTorchState() {
        boolean hasTorch = camera != null
                && camera.getCameraInfo() != null
                && camera.getCameraControl() != null
                && camera.getCameraInfo().hasFlashUnit();

        if (!hasTorch) {
            isFlashOn = false;
            updateFlashUi(false);
            setTorchButtonsEnabled(false);
            return;
        }

        setTorchButtonsEnabled(true);
        camera.getCameraControl().enableTorch(isFlashOn);
        updateFlashUi(false);
    }

    private void setTorchButtonsEnabled(boolean enabled) {
        if (btnFlash != null) {
            btnFlash.setEnabled(enabled);
            btnFlash.setAlpha(enabled ? 1f : 0.5f);
        }
    }

    private void setupClickListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> navigateBackHome());
        }
        if (btnFlash != null) {
            btnFlash.setOnClickListener(v -> toggleFlash());
        }
        if (btnGallery != null) {
            btnGallery.setOnClickListener(v -> openGalleryPicker());
        }
        if (btnShutter != null) {
            btnShutter.setOnClickListener(v -> performShutterAction());
        }
        if (btnFlipCamera != null) {
            btnFlipCamera.setOnClickListener(v -> toggleCameraLens());
        }
        if (btnCaptureSaveCancel != null) {
            btnCaptureSaveCancel.setOnClickListener(v -> {
                if (journalManager != null) {
                    journalManager.hideJournalCapturePreview(true);
                }
            });
        }
        if (btnCaptureSaveConfirm != null) {
            btnCaptureSaveConfirm.setOnClickListener(v -> savePendingEntry());
        }
        if (btnPreviewDownload != null) {
            btnPreviewDownload.setOnClickListener(v -> Toast.makeText(
                    this,
                    "TODO: lưu ảnh vào thiết bị",
                    Toast.LENGTH_SHORT).show());
        }
        if (btnPreviewEffects != null) {
            btnPreviewEffects.setOnClickListener(v -> Toast.makeText(
                    this,
                    "TODO: hiệu ứng đang được phát triển",
                    Toast.LENGTH_SHORT).show());
        }
        if (btnSaveSelectedDish != null) {
            btnSaveSelectedDish.setOnClickListener(v -> saveSelectedDish());
        }
        View btnAddManualExtraIngredient = findViewById(R.id.btnAddManualExtraIngredient);
        if (btnAddManualExtraIngredient != null) {
            btnAddManualExtraIngredient.setOnClickListener(v -> addManualExtraIngredient());
        }
    }

    private void toggleFlash() {
        if (!isCameraReady || camera == null) {
            Toast.makeText(this, "Camera chưa sẵn sàng", Toast.LENGTH_SHORT).show();
            return;
        }
        isFlashOn = !isFlashOn;
        camera.getCameraControl().enableTorch(isFlashOn);
        updateFlashUi(true);
    }

    private void toggleCameraLens() {
        if (isBusyProcessing()) {
            Toast.makeText(this, "Đang xử lý ảnh, vui lòng chờ", Toast.LENGTH_SHORT).show();
            return;
        }
        isUsingFrontCamera = !isUsingFrontCamera;
        startCameraIfNeeded();
        String cameraLabel = isUsingFrontCamera ? "Camera trước" : "Camera sau";
        Toast.makeText(this, "Đã chuyển sang " + cameraLabel, Toast.LENGTH_SHORT).show();
    }

    private void performShutterAction() {
        if (isBusyProcessing()) {
            Toast.makeText(this, "Đang xử lý ảnh trước đó", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isCameraReady || imageCapture == null) {
            ensureCameraPermissionAndStart();
            Toast.makeText(this, "Camera chưa sẵn sàng", Toast.LENGTH_SHORT).show();
            return;
        }

        animateShutterFeedback();
        updateScanStatus(isRecognitionMode
                ? "Đang chụp để nhận diện..."
                : "Đang chụp để đăng nhật ký...");
        updateEntryStatus("Đang chụp...");

        final boolean routeRecognition = isRecognitionMode;
        imageCapture.takePicture(
                recognitionExecutor == null ? ContextCompat.getMainExecutor(this) : command -> recognitionExecutor.execute(command),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        super.onCaptureSuccess(image);
                        byte[] bytes;
                        int imageRotationDegrees = image.getImageInfo() == null
                                ? 0
                                : image.getImageInfo().getRotationDegrees();
                        int displayRotation = getDisplay() == null ? -1 : getDisplay().getRotation();
                        String lensFacingLabel = isUsingFrontCamera ? "front" : "back";
                        try {
                            bytes = ScanFoodImageUtils.imageProxyToJpeg(image);
                        } finally {
                            image.close();
                        }
                        if (bytes == null || bytes.length == 0) {
                            runOnUiThread(() -> {
                                updateScanStatus("Không đọc được ảnh chụp, vui lòng thử lại");
                                updateEntryStatus("Không đọc được ảnh vừa chụp.");
                                Toast.makeText(
                                        ScanFoodActivity.this,
                                    "Không đọc được ảnh vừa chụp",
                                        Toast.LENGTH_SHORT).show();
                            });
                            return;
                        }

                        bytes = ScanFoodImageUtils.normalizeCapturedJpeg(
                                bytes,
                                imageRotationDegrees,
                                isUsingFrontCamera,
                                lensFacingLabel,
                                displayRotation);
                        final byte[] normalizedBytes = bytes;
                        Log.d(TAG, "captureSuccess:"
                                + " lensFacing=" + lensFacingLabel
                                + ", displayRotation=" + displayRotation
                                + ", imageRotationDegrees=" + imageRotationDegrees
                                + ", normalizedBytes=" + normalizedBytes.length);

                        if (routeRecognition) {
                            processImageBytesForRecognition(normalizedBytes, "image/jpeg", "camera");
                        } else {
                            runOnUiThread(() -> processImageBytesForEntry(normalizedBytes, "camera"));
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        super.onError(exception);
                        runOnUiThread(() -> {
                                updateScanStatus("Chụp thất bại, vui lòng thử lại");
                                updateEntryStatus("Chụp thất bại, vui lòng thử lại.");
                            Toast.makeText(
                                    ScanFoodActivity.this,
                                    "Không chụp được ảnh",
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    private void animateShutterFeedback() {
        View activeShutter = btnShutter;
        if (activeShutter == null) {
            return;
        }
        activeShutter.animate().cancel();
        activeShutter.animate()
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(180L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> activeShutter.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200L)
                        .setInterpolator(new DecelerateInterpolator())
                        .start())
                .start();
    }

    private void openGalleryPicker() {
        if (isBusyProcessing()) {
            Toast.makeText(this, "Đang nhận diện thực phẩm...", Toast.LENGTH_SHORT).show();
            return;
        }
        if (galleryPickerLauncher == null) {
            return;
        }
        galleryPickerLauncher.launch("image/*");
    }

    private void processImageUriForRecognition(@NonNull Uri uri, @NonNull String sourceLabel) {
        ExecutorService executor = recognitionExecutor == null ? Executors.newSingleThreadExecutor() : recognitionExecutor;
        executor.execute(() -> {
            try {
                String mimeType = GeminiRepository.detectMimeType(getContentResolver(), uri);
                byte[] raw = ScanFoodImageUtils.readOptimizedRecognitionBytes(
                        getContentResolver(),
                        uri,
                        MAX_RECOGNITION_IMAGE_BYTES);
                if (raw.length > MAX_RECOGNITION_IMAGE_BYTES) {
                    runOnUiThread(() -> {
                        updateScanStatus("Ảnh quá lớn, vui lòng chọn ảnh nhỏ hơn");
                        Toast.makeText(this, "Ảnh vượt quá giới hạn nhận diện", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                runOnUiThread(() -> processImageBytesForRecognition(raw, mimeType, sourceLabel));
            } catch (IOException ioException) {
                runOnUiThread(() -> {
                    updateScanStatus("Không đọc được ảnh, vui lòng thử lại");
                    Toast.makeText(this, "Không thể đọc ảnh đã chọn", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void processImageUriForJournal(@NonNull Uri uri, @NonNull String sourceLabel) {
        new Thread(() -> {
            try {
                byte[] raw = ScanFoodImageUtils.readImageBytes(
                        getContentResolver(),
                        uri,
                        MAX_JOURNAL_IMAGE_BYTES);
                runOnUiThread(() -> {
                    if (raw.length > MAX_JOURNAL_IMAGE_BYTES) {
                        updateEntryStatus("Ảnh quá lớn, vui lòng chọn ảnh nhỏ hơn.");
                        Toast.makeText(this, "Ảnh vượt quá 8MB", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    processImageBytesForEntry(raw, sourceLabel);
                });
            } catch (IOException ioException) {
                runOnUiThread(() -> {
                    updateEntryStatus("Không đọc được ảnh, vui lòng thử lại.");
                    Toast.makeText(this, "Không thể đọc ảnh đã chọn", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void processImageBytesForRecognition(
            @NonNull byte[] imageBytes,
            @Nullable String mimeType,
            @NonNull String sourceLabel) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(() -> processImageBytesForRecognition(imageBytes, mimeType, sourceLabel));
            return;
        }
        if (isBusyProcessing()) {
            return;
        }
        isRecognitionInProgress = true;
        setProcessingUiEnabled(false);
        updateScanStatus("Đang nhận diện thực phẩm...");
        selectedDishItem = null;
        dismissSuggestionDialog();
        renderDetectedIngredients(currentDetectedIngredients);
        applyDishFilter();
        updateRecognitionPreviewState();

        ExecutorService executor = recognitionExecutor == null ? Executors.newSingleThreadExecutor() : recognitionExecutor;
        executor.execute(() -> {
            try {
                byte[] optimizedBytes = ScanFoodImageUtils.optimizeForRecognition(
                        imageBytes,
                        MAX_RECOGNITION_IMAGE_BYTES);
                lastRecognitionImageBytes = optimizedBytes.clone();
                String safeMimeType = TextUtils.isEmpty(mimeType) ? "image/jpeg" : mimeType;
                String imageBase64 = Base64.encodeToString(optimizedBytes, Base64.NO_WRAP);

                geminiRepository.requestStructuredResponse(
                        INGREDIENT_DETECTION_PROMPT,
                        "Phân tích ảnh này và nhận diện từng thực phẩm nhìn thấy rõ trong ảnh.",
                        safeMimeType,
                        imageBase64,
                        new GeminiRepository.StreamCallback() {
                            @Override
                            public void onStart() {
                                runOnUiThread(() -> updateScanStatus("Đang nhận diện thực phẩm..."));
                            }

                            @Override
                            public void onChunk(@NonNull String accumulatedText) {
                                runOnUiThread(() -> updateScanStatus("Đang nhận diện thực phẩm..."));
                            }

                            @Override
                            public void onCompleted(@NonNull String finalText) {
                                executor.execute(() -> handleDetectedIngredients(finalText, sourceLabel));
                            }

                            @Override
                            public void onError(@NonNull String friendlyError) {
                                runOnUiThread(() -> finishRecognitionWithError("AI đang bận, vui lòng thử lại"));
                            }
                        });
            } catch (IOException ioException) {
                runOnUiThread(() -> finishRecognitionWithError(
                        "Chưa nhận diện được thực phẩm, hãy thử chụp rõ hơn"));
            }
        });
    }

    private void handleDetectedIngredients(@NonNull String finalText, @NonNull String sourceLabel) {
        List<DetectedIngredient> rawIngredients = parseDetectedIngredients(finalText);
        List<DetectedIngredient> refinedIngredients = refineDetectedIngredients(rawIngredients);
        if (refinedIngredients.isEmpty()) {
            runOnUiThread(() -> finishRecognitionWithError(
                    "Chưa nhận diện được thực phẩm, hãy thử chụp rõ hơn"));
            return;
        }

        List<DetectedIngredient> mergedIngredients = mergeDetectedIngredients(currentDetectedIngredients, refinedIngredients);
        currentDetectedIngredients.clear();
        currentDetectedIngredients.addAll(mergedIngredients);
        requestDishSuggestions(new ArrayList<>(currentDetectedIngredients), sourceLabel);
    }

    private void requestDishSuggestions(@NonNull List<DetectedIngredient> ingredients, @NonNull String sourceLabel) {
        List<DetectedIngredient> effectiveIngredients = buildEffectiveIngredients(ingredients);
        if (effectiveIngredients.isEmpty()) {
            runOnUiThread(() -> finishRecognitionWithError(
                    "Chưa nhận diện được thực phẩm, hãy thử chụp rõ hơn"));
            return;
        }

        boolean hasCompleteLocalCoverage = scanFoodLocalMatcher.hasCompleteLocalCoverage(effectiveIngredients);
        List<ScanDishItem> resolvedLocalSuggestions = scanFoodLocalMatcher.suggestDishes(
                effectiveIngredients,
                SUGGESTION_LIMIT,
                selectedHealthFilter);

        if (hasCompleteLocalCoverage && resolvedLocalSuggestions.isEmpty()) {
            resolvedLocalSuggestions = scanFoodLocalMatcher.suggestDishesRelaxed(
                    effectiveIngredients,
                    SUGGESTION_LIMIT,
                    selectedHealthFilter);
        }
        final List<ScanDishItem> localSuggestions = resolvedLocalSuggestions;
        final boolean hasLocalCombinationMatch = hasLocalCombinationMatch(effectiveIngredients, localSuggestions);

        if (hasCompleteLocalCoverage && hasLocalCombinationMatch && !localSuggestions.isEmpty()) {
            runOnUiThread(() -> finishRecognitionSuccess(localSuggestions, sourceLabel));
            return;
        }

        geminiRepository.requestStructuredResponse(
                DISH_SUGGESTION_SYSTEM_PROMPT,
                buildDishSuggestionPrompt(effectiveIngredients, localSuggestions),
                null,
                null,
                new GeminiRepository.StreamCallback() {
                    @Override
                    public void onStart() {
                        runOnUiThread(() -> updateScanStatus("Đang gợi ý món ăn..."));
                    }

                    @Override
                    public void onChunk(@NonNull String accumulatedText) {
                        runOnUiThread(() -> updateScanStatus("Đang gợi ý món ăn..."));
                    }

                    @Override
                    public void onCompleted(@NonNull String finalText) {
                        ExecutorService executor = recognitionExecutor == null
                                ? Executors.newSingleThreadExecutor()
                                : recognitionExecutor;
                        executor.execute(() -> {
                            List<ScanDishItem> combinedDishItems = buildAiSuggestionItems(
                                    effectiveIngredients,
                                    localSuggestions,
                                    parseSuggestedDishes(finalText));
                            runOnUiThread(() -> {
                                if (combinedDishItems.isEmpty()) {
                                    finishRecognitionWithError("Chưa tìm thấy món phù hợp");
                                } else {
                                    finishRecognitionSuccess(combinedDishItems, sourceLabel);
                                }
                            });
                        });
                    }

                    @Override
                    public void onError(@NonNull String friendlyError) {
                        runOnUiThread(() -> {
                            if (hasCompleteLocalCoverage && hasLocalCombinationMatch && !localSuggestions.isEmpty()) {
                                finishRecognitionSuccess(localSuggestions, sourceLabel);
                            } else {
                                finishRecognitionWithError("AI đang bận, vui lòng thử lại");
                            }
                        });
                    }
                });
    }

    private void requestDishSuggestions(@NonNull String sourceLabel) {
        requestDishSuggestions(new ArrayList<>(currentDetectedIngredients), sourceLabel);
    }

    @NonNull
    private List<DetectedIngredient> buildEffectiveIngredients(@NonNull List<DetectedIngredient> detectedIngredients) {
        List<DetectedIngredient> effectiveIngredients = new ArrayList<>(detectedIngredients);
        for (String extraIngredient : currentExtraIngredients) {
            String normalizedName = scanFoodLocalMatcher.normalizeIngredientName(extraIngredient);
            if (normalizedName.isEmpty()) {
                continue;
            }
            boolean exists = false;
            for (DetectedIngredient ingredient : effectiveIngredients) {
                if (scanFoodLocalMatcher.createStableId(ingredient.getName(), false)
                        .equals(scanFoodLocalMatcher.createStableId(normalizedName, false))) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                effectiveIngredients.add(new DetectedIngredient(
                        normalizedName,
                        1d,
                        "other",
                        "tự thêm",
                        "Người dùng bổ sung"));
            }
        }
        return effectiveIngredients;
    }

    private boolean hasLocalCombinationMatch(
            @NonNull List<DetectedIngredient> effectiveIngredients,
            @NonNull List<ScanDishItem> localSuggestions) {
        if (effectiveIngredients.isEmpty() || localSuggestions.isEmpty()) {
            return false;
        }

        List<String> requiredIngredientIds = new ArrayList<>();
        for (DetectedIngredient ingredient : effectiveIngredients) {
            String stableId = scanFoodLocalMatcher.createStableId(ingredient.getName(), false);
            if (!requiredIngredientIds.contains(stableId)) {
                requiredIngredientIds.add(stableId);
            }
        }

        for (ScanDishItem item : localSuggestions) {
            List<String> usedIngredientIds = new ArrayList<>();
            for (String usedIngredient : item.getUsedIngredients()) {
                String stableId = scanFoodLocalMatcher.createStableId(usedIngredient, false);
                if (!usedIngredientIds.contains(stableId)) {
                    usedIngredientIds.add(stableId);
                }
            }

            boolean coversAllIngredients = true;
            for (String requiredId : requiredIngredientIds) {
                if (!usedIngredientIds.contains(requiredId)) {
                    coversAllIngredients = false;
                    break;
                }
            }
            if (coversAllIngredients) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private List<ScanDishItem> buildAiSuggestionItems(
            @NonNull List<DetectedIngredient> effectiveIngredients,
            @NonNull List<ScanDishItem> localSuggestions,
            @NonNull List<SuggestedDish> aiSuggestions) {
        List<ScanDishItem> items = new ArrayList<>();
        List<String> seenStableIds = new ArrayList<>();

        for (SuggestedDish suggestedDish : aiSuggestions) {
            List<String> sanitizedMatchedIngredients = sanitizeMatchedIngredients(
                    effectiveIngredients,
                    suggestedDish.getUsedIngredients());
            List<String> sanitizedMissingIngredients = sanitizeMissingIngredients(
                    effectiveIngredients,
                    suggestedDish.getMissingIngredients());
            if (!isAiSuggestionRelevant(
                    effectiveIngredients,
                    suggestedDish,
                    sanitizedMatchedIngredients,
                    sanitizedMissingIngredients)) {
                continue;
            }

            FoodItem localFood = scanFoodLocalMatcher.findDishByName(suggestedDish.getName());
            if (localFood != null) {
                String stableId = scanFoodLocalMatcher.createStableId(localFood.getId(), true);
                if (seenStableIds.contains(stableId)) {
                    continue;
                }
                seenStableIds.add(stableId);
                items.add(new ScanDishItem(
                        stableId,
                        localFood.getName(),
                        localFood,
                        sanitizedMatchedIngredients,
                        sanitizedMissingIngredients,
                        suggestedDish.getHealthTags().isEmpty() ? localFood.getSuitableFor() : suggestedDish.getHealthTags(),
                        suggestedDish.getReason(),
                        localFood.getRecipe(),
                        suggestedDish.getConfidence()));
                if (items.size() >= SUGGESTION_LIMIT) {
                    break;
                }
                continue;
            }
            if (!suggestedDish.getRecipe().trim().isEmpty()) {
                String stableId = scanFoodLocalMatcher.createStableId(suggestedDish.getName(), false);
                if (seenStableIds.contains(stableId)) {
                    continue;
                }
                seenStableIds.add(stableId);
                items.add(new ScanDishItem(
                        stableId,
                        suggestedDish.getName(),
                        null,
                        sanitizedMatchedIngredients,
                        sanitizedMissingIngredients,
                        suggestedDish.getHealthTags(),
                        suggestedDish.getReason(),
                        suggestedDish.getRecipe(),
                        suggestedDish.getConfidence()));
                if (items.size() >= SUGGESTION_LIMIT) {
                    break;
                }
            }
        }
        if (items.isEmpty() && !localSuggestions.isEmpty()) {
            return new ArrayList<>(localSuggestions);
        }
        return items;
    }

    private boolean isAiSuggestionRelevant(
            @NonNull List<DetectedIngredient> effectiveIngredients,
            @NonNull SuggestedDish suggestedDish,
            @NonNull List<String> sanitizedMatchedIngredients,
            @NonNull List<String> sanitizedMissingIngredients) {
        if (effectiveIngredients.isEmpty() || sanitizedMatchedIngredients.isEmpty()) {
            return false;
        }

        int availableCount = effectiveIngredients.size();
        int matchedCount = sanitizedMatchedIngredients.size();
        if (availableCount >= 2 && matchedCount < 2) {
            return false;
        }

        double coverage = matchedCount / (double) Math.max(1, availableCount);
        if (availableCount >= 3 && coverage < 0.5d) {
            return false;
        }

        if (sanitizedMissingIngredients.size() > Math.max(2, matchedCount)) {
            return false;
        }

        return !suggestsUnavailableMainIngredient(
                effectiveIngredients,
                suggestedDish.getName(),
                sanitizedMatchedIngredients);
    }

    @NonNull
    private List<String> sanitizeMatchedIngredients(
            @NonNull List<DetectedIngredient> effectiveIngredients,
            @NonNull List<String> rawMatchedIngredients) {
        List<String> sanitized = new ArrayList<>();
        for (String rawIngredient : rawMatchedIngredients) {
            String canonicalName = findBestAvailableIngredientName(effectiveIngredients, rawIngredient);
            if (!canonicalName.isEmpty() && !sanitized.contains(canonicalName)) {
                sanitized.add(canonicalName);
            }
        }
        return sanitized;
    }

    @NonNull
    private List<String> sanitizeMissingIngredients(
            @NonNull List<DetectedIngredient> effectiveIngredients,
            @NonNull List<String> rawMissingIngredients) {
        List<String> sanitized = new ArrayList<>();
        for (String rawIngredient : rawMissingIngredients) {
            String value = rawIngredient == null ? "" : rawIngredient.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (!findBestAvailableIngredientName(effectiveIngredients, value).isEmpty()) {
                continue;
            }
            if (!sanitized.contains(value)) {
                sanitized.add(value);
            }
        }
        return sanitized;
    }

    @NonNull
    private String findBestAvailableIngredientName(
            @NonNull List<DetectedIngredient> effectiveIngredients,
            @NonNull String rawIngredient) {
        String normalizedRaw = normalizeLooseIngredient(rawIngredient);
        if (normalizedRaw.isEmpty()) {
            return "";
        }

        for (DetectedIngredient ingredient : effectiveIngredients) {
            String candidate = ingredient.getName().trim();
            String normalizedCandidate = normalizeLooseIngredient(candidate);
            if (normalizedCandidate.equals(normalizedRaw)
                    || normalizedCandidate.contains(normalizedRaw)
                    || normalizedRaw.contains(normalizedCandidate)) {
                return candidate;
            }
        }
        return "";
    }

    private boolean suggestsUnavailableMainIngredient(
            @NonNull List<DetectedIngredient> effectiveIngredients,
            @NonNull String dishName,
            @NonNull List<String> sanitizedMatchedIngredients) {
        List<String> availableMainTokens = new ArrayList<>();
        for (DetectedIngredient ingredient : effectiveIngredients) {
            String normalized = normalizeLooseIngredient(ingredient.getName());
            for (String token : MAIN_INGREDIENT_TOKENS) {
                if (normalized.contains(token) && !availableMainTokens.contains(token)) {
                    availableMainTokens.add(token);
                }
            }
        }

        List<String> matchedMainTokens = new ArrayList<>();
        for (String ingredientName : sanitizedMatchedIngredients) {
            String normalized = normalizeLooseIngredient(ingredientName);
            for (String token : MAIN_INGREDIENT_TOKENS) {
                if (normalized.contains(token) && !matchedMainTokens.contains(token)) {
                    matchedMainTokens.add(token);
                }
            }
        }

        String normalizedDishName = normalizeLooseIngredient(dishName);
        for (String token : MAIN_INGREDIENT_TOKENS) {
            if (!normalizedDishName.contains(token)) {
                continue;
            }
            if (!availableMainTokens.contains(token) && !matchedMainTokens.contains(token)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private String normalizeLooseIngredient(@NonNull String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replaceAll("[^\\p{L}0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void requestRecipesForAiDishes(
            @NonNull List<DetectedIngredient> ingredients,
            @NonNull List<ScanDishItem> dishItems,
            @NonNull String sourceLabel) {
        List<ScanDishItem> missingRecipes = new ArrayList<>();
        for (ScanDishItem item : dishItems) {
            if (!item.isLocal() && item.getRecipe().trim().isEmpty()) {
                missingRecipes.add(item);
            }
        }
        if (missingRecipes.isEmpty()) {
            finishRecognitionSuccess(dishItems, sourceLabel);
            return;
        }

        geminiRepository.requestStructuredResponse(
                AI_DISH_GENERATION_SYSTEM_PROMPT,
                buildRecipeGenerationPrompt(ingredients, missingRecipes),
                null,
                null,
                new GeminiRepository.StreamCallback() {
                    @Override
                    public void onStart() {
                        runOnUiThread(() -> updateScanStatus("Đang bổ sung công thức món ăn..."));
                    }

                    @Override
                    public void onChunk(@NonNull String accumulatedText) {
                        runOnUiThread(() -> updateScanStatus("Đang bổ sung công thức món ăn..."));
                    }

                    @Override
                    public void onCompleted(@NonNull String finalText) {
                        ExecutorService executor = recognitionExecutor == null
                                ? Executors.newSingleThreadExecutor()
                                : recognitionExecutor;
                        executor.execute(() -> {
                            List<ScanDishItem> merged = mergeGeneratedRecipes(
                                    dishItems,
                                    parseGeneratedDishes(finalText));
                            runOnUiThread(() -> finishRecognitionSuccess(merged, sourceLabel));
                        });
                    }

                    @Override
                    public void onError(@NonNull String friendlyError) {
                        runOnUiThread(() -> finishRecognitionSuccess(dishItems, sourceLabel));
                    }
                });
    }

    @NonNull
    private String buildRecipeGenerationPrompt(
            @NonNull List<DetectedIngredient> ingredients,
            @NonNull List<ScanDishItem> dishItems) {
        JSONArray ingredientArray = new JSONArray();
        for (DetectedIngredient ingredient : ingredients) {
            ingredientArray.put(ingredient.getName());
        }

        JSONArray dishArray = new JSONArray();
        for (ScanDishItem item : dishItems) {
            JSONObject dishObject = new JSONObject();
            try {
                dishObject.put("name", item.getName());
                dishObject.put("usedIngredients", new JSONArray(item.getUsedIngredients()));
                dishObject.put("missingIngredients", new JSONArray(item.getMissingIngredients()));
                dishObject.put("healthTags", new JSONArray(item.getHealthTags()));
                dishObject.put("reason", item.getReason());
            } catch (Exception ignored) {
            }
            dishArray.put(dishObject);
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("Nguyên liệu đã nhận diện: ").append(ingredientArray).append('.').append("\n");
        prompt.append("Cần tạo công thức cho các món sau: ").append(dishArray).append('.').append("\n");
        prompt.append("Công thức phải theo đúng format recipe local của CoolCook trong foods.json.\n");
        prompt.append("Bắt buộc dùng các heading sau theo đúng thứ tự:\n");
        prompt.append("### Tên món\n");
        prompt.append("**Khẩu phần:**\n");
        prompt.append("**Nguyên liệu:**\n");
        prompt.append("**Các bước thực hiện:**\n");
        prompt.append("**Mẹo tối ưu:**\n");
        prompt.append("Công thức phải thực tế, tiếng Việt rõ ràng, ưu tiên tận dụng usedIngredients và chỉ thêm missingIngredients khi cần.\n");
        prompt.append("Chỉ trả về JSON hợp lệ theo schema sau:\n");
        prompt.append('{').append("\n");
        prompt.append("  \"generatedDishes\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"name\": \"...\",\n");
        prompt.append("      \"healthTags\": [\"...\"],\n");
        prompt.append("      \"reason\": \"...\",\n");
        prompt.append("      \"recipe\": \"### ...\"\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append('}');
        return prompt.toString();
    }

    @NonNull
    private List<ScanDishItem> mergeGeneratedRecipes(
            @NonNull List<ScanDishItem> sourceItems,
            @NonNull List<GeneratedDishPayload> generatedDishes) {
        Map<String, GeneratedDishPayload> payloadByStableId = new LinkedHashMap<>();
        for (GeneratedDishPayload payload : generatedDishes) {
            payloadByStableId.put(
                    scanFoodLocalMatcher.createStableId(payload.name, false),
                    payload);
        }

        List<ScanDishItem> mergedItems = new ArrayList<>();
        for (ScanDishItem item : sourceItems) {
            if (item.isLocal() || !item.getRecipe().trim().isEmpty()) {
                mergedItems.add(item);
                continue;
            }

            GeneratedDishPayload payload = payloadByStableId.get(item.getStableId());
            if (payload == null) {
                mergedItems.add(item);
                continue;
            }

            mergedItems.add(new ScanDishItem(
                    item.getStableId(),
                    item.getName(),
                    item.getLocalFood(),
                    item.getUsedIngredients(),
                    item.getMissingIngredients(),
                    payload.healthTags.isEmpty() ? item.getHealthTags() : payload.healthTags,
                    payload.reason.trim().isEmpty() ? item.getReason() : payload.reason,
                    payload.recipe.trim(),
                    item.getConfidence()));
        }
        return rankAndLimitDishItems(mergedItems);
    }

    private void finishRecognitionSuccess(@NonNull List<ScanDishItem> dishItems, @NonNull String sourceLabel) {
        isRecognitionInProgress = false;
        setProcessingUiEnabled(true);
        allSuggestedDishItems.clear();
        allSuggestedDishItems.addAll(rankAndLimitDishItems(dishItems));
        selectedDishItem = null;
        renderDetectedIngredients(currentDetectedIngredients);
        applyDishFilter();
        updateRecognitionPreviewState();
        if (allSuggestedDishItems.isEmpty()) {
            updateScanStatus("Chưa tìm thấy món phù hợp");
        } else {
            updateScanStatus("Mở popup gợi ý món ăn");
            showSuggestionDialog();
        }
        if ("cap nhat".equalsIgnoreCase(sourceLabel)) {
            Toast.makeText(this, "Đã cập nhật gợi ý 1 món theo tập nguyên liệu hiện tại.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Đã nhận diện nguyên liệu từ ảnh " + sourceLabel, Toast.LENGTH_SHORT).show();
        }
    }

    private void finishRecognitionWithError(@NonNull String message) {
        isRecognitionInProgress = false;
        setProcessingUiEnabled(true);
        renderDetectedIngredients(currentDetectedIngredients);
        renderSuggestedDishes(allSuggestedDishItems);
        dismissSuggestionDialog();
        updateRecognitionPreviewState();
        updateScanStatus(message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showSuggestionDialog() {
        if (suggestionDialog == null) {
            suggestionDialog = new BottomSheetDialog(this);
            View dialogView = getLayoutInflater().inflate(R.layout.bottom_sheet_scan_suggestions, null, false);
            suggestionDialog.setContentView(dialogView);
            suggestionDialog.setCancelable(false);
            suggestionDialog.setCanceledOnTouchOutside(false);
            suggestionIngredientGroup = dialogView.findViewById(R.id.groupSuggestionIngredients);
            txtSuggestionEmpty = dialogView.findViewById(R.id.txtSuggestionEmpty);
            TextView txtSuggestionIngredientsLabel = dialogView.findViewById(R.id.txtSuggestionIngredientsLabel);
            if (txtSuggestionIngredientsLabel != null) {
                txtSuggestionIngredientsLabel.setText("Nguyên liệu AI sử dụng");
            }
            RecyclerView dialogRecyclerView = dialogView.findViewById(R.id.rvSuggestionDishes);
            dialogRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            dialogRecyclerView.setAdapter(suggestionDialogAdapter);
            View closeButton = dialogView.findViewById(R.id.btnSuggestionDialogClose);
            closeButton.setOnClickListener(v -> dismissSuggestionDialog());
                suggestionDialog.setOnDismissListener(dialog -> updateScanStatus(
                    "Hướng camera vào thực phẩm rồi bấm chụp"));
        }

        renderSuggestionIngredientChips();
        applyDishFilter();
        if (!suggestionDialog.isShowing()) {
            suggestionDialog.show();
            BottomSheetBehavior<FrameLayout> behavior = suggestionDialog.getBehavior();
            behavior.setHideable(false);
            behavior.setSkipCollapsed(true);
            behavior.setDraggable(false);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    @NonNull
    private List<ScanDishItem> rankAndLimitDishItems(@NonNull List<ScanDishItem> dishItems) {
        List<ScanDishItem> rankedItems = new ArrayList<>(dishItems);
        rankedItems.sort(Comparator
                .comparingInt((ScanDishItem item) -> item.getUsedIngredients().size()).reversed()
                .thenComparing(ScanDishItem::isLocal, Comparator.reverseOrder())
                .thenComparing(item -> ScanHealthFilters.matches(item.getHealthTags(), selectedHealthFilter), Comparator.reverseOrder())
                .thenComparingDouble(ScanDishItem::getConfidence).reversed()
                .thenComparing(ScanDishItem::getName, String.CASE_INSENSITIVE_ORDER));
        if (rankedItems.size() <= SUGGESTION_LIMIT) {
            return rankedItems;
        }
        return new ArrayList<>(rankedItems.subList(0, SUGGESTION_LIMIT));
    }

    private void prepareRecognitionResultUiForLoading(@NonNull byte[] imageBytes) {
        if (scanResultContainer != null) {
            scanResultContainer.setVisibility(View.VISIBLE);
        }
        if (imgCapturedRecognition != null) {
            imgCapturedRecognition.setImageBitmap(BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length));
        }
        renderDetectedIngredients(currentDetectedIngredients);
        applyDishFilter();
        updateSelectedDishState();
        updateRecognitionPreviewState();
        if (txtDetectedIngredientsEmpty != null) {
            txtDetectedIngredientsEmpty.setText("Đang nhận diện nguyên liệu...");
        }
        if (txtDishSuggestionsEmpty != null) {
            txtDishSuggestionsEmpty.setText("Đang gợi ý món có thể nấu...");
        }
    }

    private void renderDetectedIngredients(@NonNull List<DetectedIngredient> ingredients) {
        if (groupDetectedIngredients == null) {
            return;
        }
        groupDetectedIngredients.removeAllViews();
        for (DetectedIngredient ingredient : ingredients) {
            groupDetectedIngredients.addView(createIngredientChip(ingredient));
        }
        renderSuggestedExtraIngredients();
        renderSelectedExtraIngredients();
        if (txtDetectedIngredientsEmpty != null) {
            txtDetectedIngredientsEmpty.setVisibility(ingredients.isEmpty() ? View.VISIBLE : View.GONE);
            if (ingredients.isEmpty()) {
                txtDetectedIngredientsEmpty.setText("Chưa có nguyên liệu nhận diện được.");
            }
        }
    }

    private void renderSuggestionIngredientChips() {
        if (suggestionIngredientGroup == null) {
            return;
        }
        suggestionIngredientGroup.removeAllViews();
        for (DetectedIngredient ingredient : buildEffectiveIngredients(currentDetectedIngredients)) {
            suggestionIngredientGroup.addView(createIngredientChip(ingredient));
        }
    }

    private void renderSuggestedExtraIngredients() {
        if (groupSuggestedExtraIngredients == null) {
            return;
        }
        groupSuggestedExtraIngredients.removeAllViews();
        if (scanFoodLocalMatcher == null) {
            if (txtExtraIngredientsHint != null) {
                txtExtraIngredientsHint.setText("Đang khởi tạo danh sách nguyên liệu gợi ý...");
            }
            return;
        }
        List<String> suggestions = scanFoodLocalMatcher.suggestComplementaryIngredients(
                currentDetectedIngredients,
                currentExtraIngredients,
                8);
        for (String suggestion : suggestions) {
            groupSuggestedExtraIngredients.addView(createSuggestedExtraChip(suggestion));
        }
        if (txtExtraIngredientsHint != null) {
                txtExtraIngredientsHint.setText(suggestions.isEmpty()
                    ? "Không có nguyên liệu gợi ý thêm. Bạn có thể tự nhập để AI kết hợp."
                    : "Chọn thêm nguyên liệu để AI kết hợp thành 1 món phù hợp hơn.");
        }
    }

    private void renderSelectedExtraIngredients() {
        if (groupSelectedExtraIngredients == null) {
            return;
        }
        groupSelectedExtraIngredients.removeAllViews();
        for (String ingredient : currentExtraIngredients) {
            groupSelectedExtraIngredients.addView(createSelectedExtraChip(ingredient));
        }
    }

    @NonNull
    private Chip createIngredientChip(@NonNull DetectedIngredient ingredient) {
        Chip chip = new Chip(this);
        chip.setText(ingredient.getName());
        chip.setCheckable(false);
        chip.setClickable(true);
        chip.setCloseIconVisible(true);
        chip.setCloseIconTint(android.content.res.ColorStateList.valueOf(Color.parseColor("#7A5C45")));
        chip.setOnCloseIconClickListener(v -> removeIngredientFromSelection(ingredient.getName()));
        chip.setOnClickListener(v -> openDishIngredientRemovalHint());
        chip.setTypeface(getResources().getFont(R.font.be_vietnam_pro_medium));
        chip.setTextColor(Color.parseColor("#4D3B2E"));
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#F7EEE5")));
        chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#E4D6C6")));
        chip.setChipStrokeWidth(dp(1f));
        chip.setChipMinHeight(dp(34f));
        chip.setEnsureMinTouchTargetSize(false);
        return chip;
    }

    private void removeIngredientFromSelection(@NonNull String ingredientName) {
        String stableId = scanFoodLocalMatcher.createStableId(ingredientName, false);
        for (DetectedIngredient ingredient : currentDetectedIngredients) {
            if (scanFoodLocalMatcher.createStableId(ingredient.getName(), false).equals(stableId)) {
                removeDetectedIngredient(ingredient);
                return;
            }
        }
        removeExtraIngredient(ingredientName);
    }

    @NonNull
    private Chip createSuggestedExtraChip(@NonNull String ingredientName) {
        Chip chip = new Chip(this);
        chip.setText("+ " + ingredientName);
        chip.setCheckable(false);
        chip.setClickable(true);
        chip.setTypeface(getResources().getFont(R.font.be_vietnam_pro_medium));
        chip.setTextColor(Color.parseColor("#8F5A14"));
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#FFF3E0")));
        chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#E7C79F")));
        chip.setChipStrokeWidth(dp(1f));
        chip.setChipMinHeight(dp(34f));
        chip.setEnsureMinTouchTargetSize(false);
        chip.setOnClickListener(v -> addExtraIngredient(ingredientName));
        return chip;
    }

    @NonNull
    private Chip createSelectedExtraChip(@NonNull String ingredientName) {
        Chip chip = new Chip(this);
        chip.setText(ingredientName);
        chip.setCheckable(false);
        chip.setClickable(false);
        chip.setCloseIconVisible(true);
        chip.setCloseIconTint(android.content.res.ColorStateList.valueOf(Color.parseColor("#7A5C45")));
        chip.setOnCloseIconClickListener(v -> removeExtraIngredient(ingredientName));
        chip.setTypeface(getResources().getFont(R.font.be_vietnam_pro_medium));
        chip.setTextColor(Color.parseColor("#4D3B2E"));
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#FCEFD8")));
        chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#E7C79F")));
        chip.setChipStrokeWidth(dp(1f));
        chip.setChipMinHeight(dp(34f));
        chip.setEnsureMinTouchTargetSize(false);
        return chip;
    }

    private void openDishIngredientRemovalHint() {
        Toast.makeText(this, "Nhấn dấu X để xóa nguyên liệu đang được AI sử dụng.", Toast.LENGTH_SHORT).show();
    }

    private void addManualExtraIngredient() {
        if (edtManualExtraIngredient == null) {
            return;
        }
        String rawValue = edtManualExtraIngredient.getText() == null
                ? ""
                : edtManualExtraIngredient.getText().toString().trim();
        if (rawValue.isEmpty()) {
            Toast.makeText(this, "Nhập thêm một nguyên liệu trước.", Toast.LENGTH_SHORT).show();
            return;
        }
        edtManualExtraIngredient.setText("");
        addExtraIngredient(rawValue);
    }

    private void addExtraIngredient(@NonNull String ingredientName) {
        String normalizedName = scanFoodLocalMatcher.normalizeIngredientName(ingredientName);
        if (normalizedName.isEmpty()) {
            return;
        }
        String stableId = scanFoodLocalMatcher.createStableId(normalizedName, false);
        for (DetectedIngredient ingredient : currentDetectedIngredients) {
            if (scanFoodLocalMatcher.createStableId(ingredient.getName(), false).equals(stableId)) {
                Toast.makeText(this, "Nguyên liệu này đã có trong danh sách nhận diện.", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        for (String existing : currentExtraIngredients) {
            if (scanFoodLocalMatcher.createStableId(existing, false).equals(stableId)) {
                Toast.makeText(this, "Nguyên liệu bổ sung này đã được chọn.", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        currentExtraIngredients.add(normalizedName);
        renderSelectedExtraIngredients();
        renderSuggestedExtraIngredients();
        renderSuggestionIngredientChips();
        requestDishSuggestions("cap nhat");
    }

    private void removeExtraIngredient(@NonNull String ingredientName) {
        String stableId = scanFoodLocalMatcher.createStableId(ingredientName, false);
        List<String> updated = new ArrayList<>();
        for (String existing : currentExtraIngredients) {
            if (!scanFoodLocalMatcher.createStableId(existing, false).equals(stableId)) {
                updated.add(existing);
            }
        }
        currentExtraIngredients.clear();
        currentExtraIngredients.addAll(updated);
        renderSelectedExtraIngredients();
        renderSuggestedExtraIngredients();
        renderSuggestionIngredientChips();
        if (currentDetectedIngredients.isEmpty() && currentExtraIngredients.isEmpty()) {
            allSuggestedDishItems.clear();
            renderSuggestedDishes(allSuggestedDishItems);
            dismissSuggestionDialog();
            updateScanStatus("Bạn có thể chụp hoặc thêm nguyên liệu để AI gợi ý 1 món.");
            return;
        }
        requestDishSuggestions("cap nhat");
    }

    private void removeDetectedIngredient(@NonNull DetectedIngredient ingredient) {
        String stableId = scanFoodLocalMatcher.createStableId(ingredient.getName(), false);
        List<DetectedIngredient> updated = new ArrayList<>();
        for (DetectedIngredient item : currentDetectedIngredients) {
            String currentStableId = scanFoodLocalMatcher.createStableId(item.getName(), false);
            if (!currentStableId.equals(stableId)) {
                updated.add(item);
            }
        }
        currentDetectedIngredients.clear();
        currentDetectedIngredients.addAll(updated);
        renderDetectedIngredients(currentDetectedIngredients);
        renderSuggestionIngredientChips();
        if (currentDetectedIngredients.isEmpty() && currentExtraIngredients.isEmpty()) {
            allSuggestedDishItems.clear();
            selectedDishItem = null;
            renderSuggestedDishes(allSuggestedDishItems);
            dismissSuggestionDialog();
            updateSelectedDishState();
            updateScanStatus("Đã xóa hết nhận diện trước đó. Bạn có thể chụp lại.");
            return;
        }
        requestDishSuggestions("cap nhat");
    }

    private void renderSuggestedDishes(@NonNull List<ScanDishItem> dishItems) {
        if (scanDishSuggestionAdapter != null) {
            scanDishSuggestionAdapter.submitItems(dishItems);
        }
        if (txtDishSuggestionsEmpty != null) {
            txtDishSuggestionsEmpty.setVisibility(View.GONE);
        }
        if (txtSuggestionEmpty != null) {
            txtSuggestionEmpty.setVisibility(dishItems.isEmpty() ? View.VISIBLE : View.GONE);
            if (dishItems.isEmpty()) {
                txtSuggestionEmpty.setText("Chưa có món gợi ý phù hợp.");
            }
        }
    }

    private void onDishSelected(@NonNull ScanDishItem item) {
        selectedDishItem = item;
        if (scanDishSuggestionAdapter != null) {
            scanDishSuggestionAdapter.setSelectedDishId(item.getStableId());
        }
        updateSelectedDishState();
    }

    private void saveSelectedDish() {
        if (selectedDishItem == null) {
            Toast.makeText(this, "Bạn cần chọn một món trước khi lưu.", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean saved = scanSavedDishStore != null && scanSavedDishStore.save(selectedDishItem);
        Toast.makeText(
                this,
                saved ? "Đã lưu món đã chọn vào danh sách nhận diện." : "Món này đã có trong danh sách nhận diện.",
                Toast.LENGTH_SHORT).show();
    }

    private void updateSelectedDishState() {
        if (txtSelectedDishHint != null) {
            txtSelectedDishHint.setText(selectedDishItem == null
                    ? "Chưa chọn món nào để lưu"
                    : "Đã chọn: " + selectedDishItem.getName());
        }
        if (btnSaveSelectedDish != null) {
            btnSaveSelectedDish.setEnabled(selectedDishItem != null);
            btnSaveSelectedDish.setAlpha(selectedDishItem == null ? 0.55f : 1f);
        }
        if (scanDishSuggestionAdapter != null) {
            scanDishSuggestionAdapter.setSelectedDishId(selectedDishItem == null ? "" : selectedDishItem.getStableId());
        }
    }

    private void syncHealthFilterSelection() {
        if (groupHealthFilters == null) {
            return;
        }
        for (int index = 0; index < groupHealthFilters.getChildCount(); index++) {
            View child = groupHealthFilters.getChildAt(index);
            if (!(child instanceof Chip)) {
                continue;
            }
            Chip chip = (Chip) child;
            boolean selected = selectedHealthFilter.contentEquals(chip.getText());
            chip.setChecked(selected);
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(
                    Color.parseColor(selected ? "#335C4B24" : "#221F1F1F")));
            chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(
                    Color.parseColor(selected ? "#FABD00" : "#334B4B4B")));
            chip.setTextColor(Color.parseColor(selected ? "#FFF4DA" : "#F8F5EE"));
        }
    }

    private void applyDishFilter() {
        List<ScanDishItem> filteredItems = new ArrayList<>();
        for (ScanDishItem item : allSuggestedDishItems) {
            if (ScanHealthFilters.matches(item.getHealthTags(), selectedHealthFilter)) {
                filteredItems.add(item);
            }
        }

        boolean selectedStillVisible = false;
        if (selectedDishItem != null) {
            for (ScanDishItem item : filteredItems) {
                if (item.getStableId().equals(selectedDishItem.getStableId())) {
                    selectedStillVisible = true;
                    break;
                }
            }
        }
        if (!selectedStillVisible) {
            selectedDishItem = null;
        }

        renderSuggestedDishes(filteredItems);
        if (txtDishSuggestionsEmpty != null && !allSuggestedDishItems.isEmpty() && filteredItems.isEmpty()) {
            txtDishSuggestionsEmpty.setText("Không có món phù hợp với bộ lọc sức khỏe đang chọn.");
        }
        updateSelectedDishState();
    }

    private void updateRecognitionPreviewState() {
        boolean hasResults = hasRecognitionResults();
        if (previewInnerFrame != null) {
            previewInnerFrame.setAlpha(hasResults ? 0.18f : 1f);
        }
        if (detectionBox != null) {
            detectionBox.clearAnimation();
            detectionBox.setVisibility(hasResults ? View.INVISIBLE : View.VISIBLE);
            if (!hasResults && isRecognitionMode) {
                startDetectionPulse();
            }
        }
    }

    private boolean hasRecognitionResults() {
        return lastRecognitionImageBytes != null
                || !currentDetectedIngredients.isEmpty()
                || !allSuggestedDishItems.isEmpty()
                || isRecognitionInProgress;
    }

    @NonNull
    private List<DetectedIngredient> refineDetectedIngredients(@NonNull List<DetectedIngredient> ingredients) {
        List<DetectedIngredient> refined = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (DetectedIngredient ingredient : ingredients) {
            String normalizedName = scanFoodLocalMatcher.normalizeIngredientName(ingredient.getName());
            if (normalizedName.isEmpty()) {
                continue;
            }
            String stableId = scanFoodLocalMatcher.createStableId(normalizedName, false);
            if (seen.contains(stableId)) {
                continue;
            }
            seen.add(stableId);
            refined.add(new DetectedIngredient(
                    normalizedName,
                    ingredient.getConfidence(),
                    ingredient.getCategory(),
                    ingredient.getVisibleAmount(),
                    ingredient.getNotes()));
        }
        return refined;
    }

    @NonNull
    private List<DetectedIngredient> mergeDetectedIngredients(
            @NonNull List<DetectedIngredient> currentIngredients,
            @NonNull List<DetectedIngredient> newIngredients) {
        List<DetectedIngredient> merged = new ArrayList<>(currentIngredients);
        List<String> seen = new ArrayList<>();
        for (DetectedIngredient ingredient : merged) {
            seen.add(scanFoodLocalMatcher.createStableId(ingredient.getName(), false));
        }
        for (DetectedIngredient ingredient : newIngredients) {
            String stableId = scanFoodLocalMatcher.createStableId(ingredient.getName(), false);
            if (seen.contains(stableId)) {
                continue;
            }
            seen.add(stableId);
            merged.add(ingredient);
        }
        return merged;
    }

    @NonNull
    private String buildDishSuggestionPrompt(
            @NonNull List<DetectedIngredient> ingredients,
            @NonNull List<ScanDishItem> localSuggestions) {
        JSONArray ingredientArray = new JSONArray();
        for (DetectedIngredient ingredient : ingredients) {
            ingredientArray.put(ingredient.getName());
        }

        JSONArray localDishArray = new JSONArray();
        for (ScanDishItem item : localSuggestions) {
            localDishArray.put(item.getName());
        }

        JSONArray allowedIngredientArray = new JSONArray();
        for (DetectedIngredient ingredient : ingredients) {
            allowedIngredientArray.put(ingredient.getName());
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("Nguyên liệu đã nhận diện: ").append(ingredientArray).append('.').append("\n");
        prompt.append("Nguyên liệu bổ sung người dùng chọn: ").append(new JSONArray(currentExtraIngredients)).append('.').append("\n");
        prompt.append("Danh sách nguyên liệu hợp lệ để đưa vào matchedIngredients: ").append(allowedIngredientArray).append('.').append("\n");
        prompt.append("Món local tham khảo trong app: ").append(localDishArray).append('.').append("\n");
        prompt.append("Hãy gợi ý ĐÚNG 3 món phù hợp nhất có thể nấu từ tập nguyên liệu này.\n");
        prompt.append("Mỗi món được gợi ý phải bám sát tập nguyên liệu đang có, không được đổi sang một món khác chỉ vì thiếu nguyên liệu.\n");
        prompt.append("matchedIngredients BẮT BUỘC phải copy đúng nguyên văn từng tên từ danh sách nguyên liệu hợp lệ. Không được đổi tên, viết lại tên, hay tự thêm tên mới.\n");
        prompt.append("missingIngredients chỉ được phép là gia vị hoặc nguyên liệu phụ nhỏ. Nếu thiếu nguyên liệu chính của món thì KHÔNG được đề xuất món đó.\n");
        prompt.append("Nếu người dùng có đồng thời gà và bò thì ưu tiên món có cả gà và bò; nếu local không có thì mới được đề xuất món mới, nhưng vẫn phải giữ đúng các nguyên liệu chính đang có.\n");
        prompt.append("Món được gợi ý phải bám sát những gì xuất hiện trong ảnh, không được đổi sang món không liên quan.\n");
        prompt.append("Nếu ảnh cho thấy 1 món hoàn chỉnh hoặc 1 thực phẩm rõ ràng, ưu tiên giữ đúng tên món/thực phẩm đó.\n");
        prompt.append("Ví dụ nếu nhận diện là bánh mì thì không được gợi ý cơm gà chiên hay món khác không liên quan.\n");
        prompt.append("Chỉ khi không thể gọi đúng tên món đang thấy mới được suy luận 1 món gần nhất từ tập nguyên liệu, và món đó phải có matchedIngredients bao phủ phần lớn nguyên liệu đang có.\n");
        prompt.append("Nếu không tìm được món phù hợp, trả về dishes rỗng []. Không được cố gắng trả về đủ 3 món bằng cách đoán món lệch nguyên liệu.\n");
        prompt.append("Sắp xếp 3 món theo mức độ phù hợp giảm dần, món đầu tiên là phù hợp nhất.\n");
        prompt.append("Nếu tên món trùng một món local trong app, giữ đúng tên món local đó.\n");
        prompt.append("Bắt buộc trả về đầy đủ công thức theo đúng format foods.json với các heading:\n");
        prompt.append("### Tên món | **Khẩu phần:** | **Nguyên liệu:** | **Các bước thực hiện:** | **Mẹo tối ưu:**\n");
        prompt.append("Quy tắc hợp lệ:\n");
        prompt.append("- Nếu input có từ 2 nguyên liệu trở lên, mỗi món gợi ý phải dùng ít nhất 2 matchedIngredients.\n");
        prompt.append("- Không đề xuất món nếu matchedIngredients ít hơn missingIngredients.\n");
        prompt.append("- Không được đề xuất món mà nguyên liệu chính không nằm trong matchedIngredients.\n");
        prompt.append("- Nếu một tên trong matchedIngredients không nằm trong danh sách hợp lệ thì output đó là sai.\n");
        prompt.append("Chi tra ve JSON hop le theo dung schema sau:\n");
        prompt.append('{').append("\n");
        prompt.append("  \"dishes\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"name\": \"...\",\n");
        prompt.append("      \"matchedIngredients\": [\"...\", \"...\"],\n");
        prompt.append("      \"missingIngredients\": [\"...\"],\n");
        prompt.append("      \"reason\": \"Vì sao món này là kết hợp phù hợp nhất\",\n");
        prompt.append("      \"healthTags\": [\"it dau\", \"tang co\", \"de tieu\"],\n");
        prompt.append("      \"recipe\": \"### ...\",\n");
        prompt.append("      \"confidence\": 0.0\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append('}');
        return prompt.toString();
    }

    @NonNull
    private List<DetectedIngredient> parseDetectedIngredients(@NonNull String rawText) {
        List<DetectedIngredient> ingredients = new ArrayList<>();
        try {
            JSONObject rootObject = new JSONObject(extractJsonPayload(rawText));
            JSONArray array = rootObject.optJSONArray("detectedIngredients");
            if (array == null) {
                return ingredients;
            }
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.optJSONObject(index);
                if (item == null) {
                    continue;
                }
                String name = item.optString("name", "").trim();
                if (name.isEmpty()) {
                    continue;
                }
                ingredients.add(new DetectedIngredient(
                        name,
                        item.optDouble("confidence", 0d),
                        item.optString("category", "other").trim(),
                        item.optString("visibleAmount", "không rõ").trim(),
                        item.optString("notes", "").trim()));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return ingredients;
    }

    @NonNull
    private List<SuggestedDish> parseSuggestedDishes(@NonNull String rawText) {
        List<SuggestedDish> dishes = new ArrayList<>();
        try {
            JSONObject rootObject = new JSONObject(extractJsonPayload(rawText));
            JSONArray array = rootObject.optJSONArray("dishes");
            if (array == null) {
                return dishes;
            }
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.optJSONObject(index);
                if (item == null) {
                    continue;
                }
                String name = item.optString("name", "").trim();
                if (name.isEmpty()) {
                    continue;
                }
                dishes.add(new SuggestedDish(
                        name,
                        toStringList(item.optJSONArray("matchedIngredients")),
                        toStringList(item.optJSONArray("missingIngredients")),
                        toStringList(item.optJSONArray("healthTags")),
                        item.optString("reason", "").trim(),
                        item.optString("recipe", "").trim(),
                        item.optDouble("confidence", 0d)));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return dishes;
    }

    @NonNull
    private List<GeneratedDishPayload> parseGeneratedDishes(@NonNull String rawText) {
        List<GeneratedDishPayload> dishes = new ArrayList<>();
        try {
            JSONObject rootObject = new JSONObject(extractJsonPayload(rawText));
            JSONArray array = rootObject.optJSONArray("generatedDishes");
            if (array == null) {
                return dishes;
            }
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.optJSONObject(index);
                if (item == null) {
                    continue;
                }
                String name = item.optString("name", "").trim();
                if (name.isEmpty()) {
                    continue;
                }
                dishes.add(new GeneratedDishPayload(
                        name,
                        toStringList(item.optJSONArray("healthTags")),
                        item.optString("reason", "").trim(),
                        item.optString("recipe", "").trim()));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return dishes;
    }

    @NonNull
    private List<String> toStringList(@Nullable JSONArray jsonArray) {
        List<String> values = new ArrayList<>();
        if (jsonArray == null) {
            return values;
        }
        for (int index = 0; index < jsonArray.length(); index++) {
            String value = jsonArray.optString(index, "").trim();
            if (!value.isEmpty() && !values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }

    @NonNull
    private String extractJsonPayload(@NonNull String rawText) {
        String trimmed = rawText.trim();
        if (trimmed.startsWith("```")) {
            int firstLineBreak = trimmed.indexOf("\n");
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineBreak >= 0 && lastFence > firstLineBreak) {
                trimmed = trimmed.substring(firstLineBreak + 1, lastFence).trim();
            }
        }

        int objectStart = trimmed.indexOf('{');
        int objectEnd = trimmed.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1);
        }
        return trimmed;
    }

    private static final class GeneratedDishPayload {
        @NonNull
        final String name;
        @NonNull
        final List<String> healthTags;
        @NonNull
        final String reason;
        @NonNull
        final String recipe;

        GeneratedDishPayload(
                @NonNull String name,
                @NonNull List<String> healthTags,
                @NonNull String reason,
                @NonNull String recipe) {
            this.name = name;
            this.healthTags = healthTags;
            this.reason = reason;
            this.recipe = recipe;
        }
    }

    private void processImageBytesForEntry(@NonNull byte[] imageBytes, @NonNull String sourceLabel) {
        if (journalManager != null) {
            journalManager.processImageBytesForJournal(imageBytes, sourceLabel, isBusyProcessing());
        }
    }

    private void savePendingEntry() {
        if (journalManager != null) {
            journalManager.publishPendingJournalMoment(isUsingFrontCamera);
        }
    }

    private void setProcessingUiEnabled(boolean enabled) {
        setViewEnabled(btnShutter, enabled);
        setViewEnabled(btnGallery, enabled);
        setViewEnabled(btnFlipCamera, enabled);
        setViewEnabled(tabNhanDien, enabled);
        setViewEnabled(tabNhatKy, enabled);
        setViewEnabled(btnPreviewDownload, enabled);
        setViewEnabled(btnPreviewEffects, enabled);
    }

    private void setViewEnabled(@Nullable View view, boolean enabled) {
        if (view == null) {
            return;
        }
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.72f);
    }

    private boolean isBusyProcessing() {
        return isRecognitionInProgress
                || (journalManager != null && journalManager.isJournalSaveInProgress());
    }

    private void updateScanStatus(@NonNull String status) {
        if (txtScanStatus != null) {
            txtScanStatus.setText(status);
        }
    }

    private void updateEntryStatus(@NonNull String status) {
        if (txtScanStatus != null && !isRecognitionMode) {
            txtScanStatus.setText(status);
        }
    }

    private void navigateBackHome() {
        if (isTaskRoot()) {
            Intent intent = new Intent(this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }
        finish();
        overridePendingTransition(0, R.anim.slide_out_right_scale);
    }
}

