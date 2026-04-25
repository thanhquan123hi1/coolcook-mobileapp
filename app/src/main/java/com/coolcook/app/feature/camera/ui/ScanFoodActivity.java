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
import android.view.MotionEvent;
import android.view.View;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONObject;

public class ScanFoodActivity extends AppCompatActivity {
    private static final int SUGGESTION_LIMIT = 3;

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
    private View btnBack;
    private View btnFlash;
    private View btnGallery;
    private View btnShutter;
    private View btnFlipCamera;
    private View btnCaptureSaveCancel;
    private View btnCaptureSaveConfirm;
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
        txtScanStatus = findViewById(R.id.txtScanStatus);
        txtDetectedIngredientsEmpty = findViewById(R.id.txtDetectedIngredientsEmpty);
        txtDishSuggestionsEmpty = findViewById(R.id.txtDishSuggestionsEmpty);
        txtSelectedDishHint = findViewById(R.id.txtSelectedDishHint);
        txtExtraIngredientsHint = findViewById(R.id.txtExtraIngredientsHint);
        txtCaptureUploadProgress = findViewById(R.id.txtCaptureUploadProgress);
        txtCaptureSaveError = findViewById(R.id.txtCaptureSaveError);
        tabNhanDien = findViewById(R.id.tabNhanDien);
        tabNhatKy = findViewById(R.id.tabNhatKy);
        iconFlash = findViewById(R.id.iconFlash);
        btnBack = findViewById(R.id.btnBack);
        btnFlash = findViewById(R.id.btnFlash);
        btnGallery = findViewById(R.id.btnGallery);
        btnShutter = findViewById(R.id.btnShutter);
        btnFlipCamera = findViewById(R.id.btnFlipCamera);
        btnCaptureSaveCancel = findViewById(R.id.btnCaptureSaveCancel);
        btnCaptureSaveConfirm = findViewById(R.id.btnCaptureSaveConfirm);
        captureSaveLoading = findViewById(R.id.captureSaveLoading);
        edtCaptureCaption = findViewById(R.id.edtCaptureCaption);
        edtManualExtraIngredient = findViewById(R.id.edtManualExtraIngredient);
        imgCapturePreview = findViewById(R.id.imgCapturePreview);
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
                saved ? "Da luu mon vao ho so" : "Mon nay da co trong danh sach da luu",
                Toast.LENGTH_SHORT).show();
    }

    private void openDishRecipe(@NonNull ScanDishItem item) {
        if (item.getRecipe().trim().isEmpty()) {
            Toast.makeText(this, "Mon nay chua co cong thuc chi tiet.", Toast.LENGTH_SHORT).show();
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
        final int topBarStart = topBar == null ? 0 : topBar.getPaddingStart();
        final int topBarTop = topBar == null ? 0 : topBar.getPaddingTop();
        final int topBarEnd = topBar == null ? 0 : topBar.getPaddingEnd();
        final int topBarBottom = topBar == null ? 0 : topBar.getPaddingBottom();

        final int footerStart = footerContainer == null ? 0 : footerContainer.getPaddingStart();
        final int footerTop = footerContainer == null ? 0 : footerContainer.getPaddingTop();
        final int footerEnd = footerContainer == null ? 0 : footerContainer.getPaddingEnd();
        final int footerBottom = footerContainer == null ? 0 : footerContainer.getPaddingBottom();

        final int overlayStart = captureSaveOverlay == null ? 0 : captureSaveOverlay.getPaddingStart();
        final int overlayTop = captureSaveOverlay == null ? 0 : captureSaveOverlay.getPaddingTop();
        final int overlayEnd = captureSaveOverlay == null ? 0 : captureSaveOverlay.getPaddingEnd();
        final int overlayBottom = captureSaveOverlay == null ? 0 : captureSaveOverlay.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            if (topBar != null) {
                topBar.setPaddingRelative(
                        topBarStart + systemBars.left,
                        topBarTop + systemBars.top,
                        topBarEnd + systemBars.right,
                        topBarBottom);
            }

            if (footerContainer != null) {
                footerContainer.setPaddingRelative(
                        footerStart + systemBars.left,
                        footerTop,
                        footerEnd + systemBars.right,
                        footerBottom + systemBars.bottom);
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
                        try {
                            bytes = ScanFoodImageUtils.imageProxyToJpeg(image);
                        } finally {
                            image.close();
                        }
                        if (bytes == null || bytes.length == 0) {
                            runOnUiThread(() -> {
                                updateScanStatus("Khong doc duoc anh chup, vui long thu lai");
                                updateEntryStatus("Khong doc duoc anh vua chup.");
                                Toast.makeText(
                                        ScanFoodActivity.this,
                                        "Khong doc duoc anh vua chup",
                                        Toast.LENGTH_SHORT).show();
                            });
                            return;
                        }

                        if (routeRecognition) {
                            processImageBytesForRecognition(bytes, "image/jpeg", "camera");
                        } else {
                            runOnUiThread(() -> processImageBytesForEntry(bytes, "camera"));
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        super.onError(exception);
                        runOnUiThread(() -> {
                            updateScanStatus("Chup that bai, vui long thu lai");
                            updateEntryStatus("Chup that bai, vui long thu lai.");
                            Toast.makeText(
                                    ScanFoodActivity.this,
                                    "Khong chup duoc anh",
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
                        updateScanStatus("Anh qua lon, vui long chon anh nho hon");
                        Toast.makeText(this, "Anh vuot qua gioi han nhan dien", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                runOnUiThread(() -> processImageBytesForRecognition(raw, mimeType, sourceLabel));
            } catch (IOException ioException) {
                runOnUiThread(() -> {
                    updateScanStatus("Khong doc duoc anh, vui long thu lai");
                    Toast.makeText(this, "Khong the doc anh da chon", Toast.LENGTH_SHORT).show();
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
        updateScanStatus("Dang nhan dien thuc pham...");
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
                        "Phan tich anh nay va nhan dien tung thuc pham nhin thay ro trong anh.",
                        safeMimeType,
                        imageBase64,
                        new GeminiRepository.StreamCallback() {
                            @Override
                            public void onStart() {
                                runOnUiThread(() -> updateScanStatus("Dang nhan dien thuc pham..."));
                            }

                            @Override
                            public void onChunk(@NonNull String accumulatedText) {
                                runOnUiThread(() -> updateScanStatus("Dang nhan dien thuc pham..."));
                            }

                            @Override
                            public void onCompleted(@NonNull String finalText) {
                                executor.execute(() -> handleDetectedIngredients(finalText, sourceLabel));
                            }

                            @Override
                            public void onError(@NonNull String friendlyError) {
                                runOnUiThread(() -> finishRecognitionWithError("AI dang ban, vui long thu lai"));
                            }
                        });
            } catch (IOException ioException) {
                runOnUiThread(() -> finishRecognitionWithError(
                        "Chua nhan dien duoc thuc pham, hay thu chup ro hon"));
            }
        });
    }

    private void handleDetectedIngredients(@NonNull String finalText, @NonNull String sourceLabel) {
        List<DetectedIngredient> rawIngredients = parseDetectedIngredients(finalText);
        List<DetectedIngredient> refinedIngredients = refineDetectedIngredients(rawIngredients);
        if (refinedIngredients.isEmpty()) {
            runOnUiThread(() -> finishRecognitionWithError(
                    "Chua nhan dien duoc thuc pham, hay thu chup ro hon"));
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
                    "Chua nhan dien duoc thuc pham, hay thu chup ro hon"));
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
                        runOnUiThread(() -> updateScanStatus("Dang goi y mon an..."));
                    }

                    @Override
                    public void onChunk(@NonNull String accumulatedText) {
                        runOnUiThread(() -> updateScanStatus("Dang goi y mon an..."));
                    }

                    @Override
                    public void onCompleted(@NonNull String finalText) {
                        ExecutorService executor = recognitionExecutor == null
                                ? Executors.newSingleThreadExecutor()
                                : recognitionExecutor;
                        executor.execute(() -> {
                            List<ScanDishItem> combinedDishItems = buildAiSuggestionItems(
                                    localSuggestions,
                                    parseSuggestedDishes(finalText));
                            runOnUiThread(() -> {
                                if (combinedDishItems.isEmpty()) {
                                    finishRecognitionWithError("Chua tim thay mon phu hop");
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
                                finishRecognitionWithError("AI dang ban, vui long thu lai");
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
                        "tu them",
                        "Nguoi dung bo sung"));
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
            @NonNull List<ScanDishItem> localSuggestions,
            @NonNull List<SuggestedDish> aiSuggestions) {
        List<ScanDishItem> items = new ArrayList<>();
        List<String> seenStableIds = new ArrayList<>();

        for (SuggestedDish suggestedDish : aiSuggestions) {
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
                        suggestedDish.getUsedIngredients(),
                        suggestedDish.getMissingIngredients(),
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
                        suggestedDish.getUsedIngredients(),
                        suggestedDish.getMissingIngredients(),
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
                        runOnUiThread(() -> updateScanStatus("Dang bo sung cong thuc mon an..."));
                    }

                    @Override
                    public void onChunk(@NonNull String accumulatedText) {
                        runOnUiThread(() -> updateScanStatus("Dang bo sung cong thuc mon an..."));
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
        prompt.append("Nguyen lieu da nhan dien: ").append(ingredientArray).append('.').append("\n");
        prompt.append("Can tao cong thuc cho cac mon sau: ").append(dishArray).append('.').append("\n");
        prompt.append("Cong thuc phai theo dung format recipe local cua CoolCook trong foods.json.\n");
        prompt.append("Bat buoc dung cac heading sau theo dung thu tu:\n");
        prompt.append("### Ten mon\n");
        prompt.append("**Khau phan:**\n");
        prompt.append("**Nguyen lieu:**\n");
        prompt.append("**Cac buoc thuc hien:**\n");
        prompt.append("**Meo toi uu:**\n");
        prompt.append("Cong thuc phai thuc te, tiếng Việt rõ ràng, uu tien tan dung usedIngredients va chi them missingIngredients khi can.\n");
        prompt.append("Chi tra ve JSON hop le theo schema sau:\n");
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
            updateScanStatus("Chua tim thay mon phu hop");
        } else {
            updateScanStatus("Mo popup goi y mon an");
            showSuggestionDialog();
        }
        if ("cap nhat".equalsIgnoreCase(sourceLabel)) {
            Toast.makeText(this, "Da cap nhat goi y 1 mon theo tap nguyen lieu hien tai.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Da nhan dien nguyen lieu tu anh " + sourceLabel, Toast.LENGTH_SHORT).show();
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
                txtSuggestionIngredientsLabel.setText("Nguyen lieu AI su dung");
            }
            RecyclerView dialogRecyclerView = dialogView.findViewById(R.id.rvSuggestionDishes);
            dialogRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            dialogRecyclerView.setAdapter(suggestionDialogAdapter);
            View closeButton = dialogView.findViewById(R.id.btnSuggestionDialogClose);
            closeButton.setOnClickListener(v -> dismissSuggestionDialog());
            suggestionDialog.setOnDismissListener(dialog -> updateScanStatus(
                    "Huong camera vao thuc pham roi bam chup"));
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
            txtDetectedIngredientsEmpty.setText("Dang nhan dien nguyen lieu...");
        }
        if (txtDishSuggestionsEmpty != null) {
            txtDishSuggestionsEmpty.setText("Dang goi y mon co the nau...");
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
                txtDetectedIngredientsEmpty.setText("Chua co nguyen lieu nhan dien duoc.");
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
                txtExtraIngredientsHint.setText("Dang khoi tao danh sach nguyen lieu goi y...");
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
                    ? "Khong co nguyen lieu goi y them. Ban co the tu nhap de AI ket hop."
                    : "Chon them nguyen lieu de AI ket hop thanh 1 mon phu hop hon.");
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
        Toast.makeText(this, "Nhan dau X de xoa nguyen lieu dang duoc AI su dung.", Toast.LENGTH_SHORT).show();
    }

    private void addManualExtraIngredient() {
        if (edtManualExtraIngredient == null) {
            return;
        }
        String rawValue = edtManualExtraIngredient.getText() == null
                ? ""
                : edtManualExtraIngredient.getText().toString().trim();
        if (rawValue.isEmpty()) {
            Toast.makeText(this, "Nhap them mot nguyen lieu truoc.", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "Nguyen lieu nay da co trong danh sach nhan dien.", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        for (String existing : currentExtraIngredients) {
            if (scanFoodLocalMatcher.createStableId(existing, false).equals(stableId)) {
                Toast.makeText(this, "Nguyen lieu bo sung nay da duoc chon.", Toast.LENGTH_SHORT).show();
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
            updateScanStatus("Ban co the chup hoac them nguyen lieu de AI goi y 1 mon.");
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
            updateScanStatus("Da xoa het nhan dien truoc do. Ban co the chup lai.");
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
                txtSuggestionEmpty.setText("Chua co mon goi y phu hop.");
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
            Toast.makeText(this, "Ban can chon mot mon truoc khi luu.", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean saved = scanSavedDishStore != null && scanSavedDishStore.save(selectedDishItem);
        Toast.makeText(
                this,
                saved ? "Da luu mon da chon vao danh sach nhan dien." : "Mon nay da co trong danh sach nhan dien.",
                Toast.LENGTH_SHORT).show();
    }

    private void updateSelectedDishState() {
        if (txtSelectedDishHint != null) {
            txtSelectedDishHint.setText(selectedDishItem == null
                    ? "Chua chon mon nao de luu"
                    : "Da chon: " + selectedDishItem.getName());
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
            txtDishSuggestionsEmpty.setText("Khong co mon phu hop voi bo loc suc khoe dang chon.");
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

        StringBuilder prompt = new StringBuilder();
        prompt.append("Nguyen lieu da nhan dien: ").append(ingredientArray).append('.').append("\n");
        prompt.append("Nguyen lieu bo sung nguoi dung chon: ").append(new JSONArray(currentExtraIngredients)).append('.').append("\n");
        prompt.append("Mon local tham khao trong app: ").append(localDishArray).append('.').append("\n");
        prompt.append("Hay goi y DUNG 3 mon phu hop nhat co the nau tu tap nguyen lieu nay.\n");
        prompt.append("Moi mon duoc goi y phai co gang tan dung day du to hop nguyen lieu dang co, khong duoc bo qua nguyen lieu chinh nhu ga hoac bo.\n");
        prompt.append("Neu nguoi dung co dong thoi ga va bo thi uu tien mon co ca ga va bo; neu local khong co thi moi duoc de xuat mon moi.\n");
        prompt.append("Mon duoc goi y phai bam sat nhung gi xuat hien trong anh, khong duoc doi sang mon khong lien quan.\n");
        prompt.append("Neu anh cho thay 1 mon hoan chinh hoac 1 thuc pham ro rang, uu tien giu dung ten mon/thuc pham do.\n");
        prompt.append("Vi du neu nhan dien la banh mi thi khong duoc goi y com ga chien hay mon khac khong lien quan.\n");
        prompt.append("Chi khi khong the goi dung ten mon dang thay moi duoc suy luan 1 mon gan nhat tu tap nguyen lieu.\n");
        prompt.append("Sap xep 3 mon theo muc do phu hop giam dan, mon dau tien la phu hop nhat.\n");
        prompt.append("Neu ten mon trung mot mon local trong app, giu dung ten mon local do.\n");
        prompt.append("Bat buoc tra ve day du cong thuc theo dung format foods.json voi cac heading:\n");
        prompt.append("### Ten mon | **Khau phan:** | **Nguyen lieu:** | **Cac buoc thuc hien:** | **Meo toi uu:**\n");
        prompt.append("Chi tra ve JSON hop le theo dung schema sau:\n");
        prompt.append('{').append("\n");
        prompt.append("  \"dishes\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"name\": \"...\",\n");
        prompt.append("      \"matchedIngredients\": [\"...\", \"...\"],\n");
        prompt.append("      \"missingIngredients\": [\"...\"],\n");
        prompt.append("      \"reason\": \"Vi sao mon nay la ket hop phu hop nhat\",\n");
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
                        item.optString("visibleAmount", "khong ro").trim(),
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
        overridePendingTransition(R.anim.slide_in_left_scale, R.anim.slide_out_right_scale);
    }
}

