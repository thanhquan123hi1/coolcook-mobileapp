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
import android.text.TextUtils;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.GestureDetector;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
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
import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
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
import com.coolcook.app.feature.social.model.JournalFeedItem;
import com.coolcook.app.feature.home.ui.HomeActivity;
import com.coolcook.app.feature.search.model.FoodItem;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class ScanFoodActivity extends AppCompatActivity {

    private static final long PRESS_IN_DURATION = 200L;
    private static final long PRESS_OUT_DURATION = 220L;
    private static final float PRESS_SCALE = 0.95f;
    private static final long MODE_TRANSITION_DURATION = 240L;
    private static final @ColorInt int TAB_ACTIVE_COLOR = Color.parseColor("#FABD00");
    private static final @ColorInt int TAB_INACTIVE_COLOR = Color.parseColor("#99D4C5AB");

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
    private View journalModeContainer;
    private NestedScrollView journalSurface;
    private View topBar;
    private View footerContainer;
    private View cameraPreviewCard;
    private View previewInnerFrame;
    private View detectionBox;
    private View scanResultContainer;
    private View btnSaveSelectedDish;
    private View tabIndicator;
    private View journalTopBar;
    private View journalFooterContainer;
    private View journalCameraPreviewCard;
    private View journalCaptureOverlay;
    private View journalCameraShell;
    private View journalFeedContent;
    private View btnBack;
    private View btnFlash;
    private View btnGallery;
    private View btnShutter;
    private View btnFlipCamera;
    private View btnJournalProfile;
    private View btnJournalFriends;
    private View btnJournalChat;
    private View btnJournalFlash;
    private View btnJournalGallery;
    private View btnJournalShutter;
    private View btnJournalFlipCamera;
    private View btnJournalPostCancel;
    private View btnJournalPostPublish;
    private ProgressBar journalPostLoading;
    private TextView txtScanStatus;
    private TextView txtDetectedIngredientsEmpty;
    private TextView txtDishSuggestionsEmpty;
    private TextView txtSelectedDishHint;
    private TextView txtJournalStatus;
    private TextView txtJournalEmptyState;
    private TextView txtJournalFriendCount;
    private TextView txtJournalUploadProgress;
    private TextView txtJournalPostError;
    private TextView txtJournalModeLabel;
    private TextView tabNhanDien;
    private TextView tabNhatKy;
    private TextView iconFlash;
    private TextView iconJournalFlash;
    private EditText edtJournalCaption;
    private ImageView imgJournalCapturedPreview;
    private ImageView imgCapturedRecognition;
    private PreviewView recognitionPreviewView;
    private PreviewView journalPreviewView;
    private PreviewView previewView;
    private RecyclerView rvJournalMoments;
    private RecyclerView rvDishSuggestions;
    private ChipGroup groupDetectedIngredients;
    private ChipGroup groupHealthFilters;

    private ActivityResultLauncher<String> galleryPickerLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    private Animation detectionPulseAnimation;
    private boolean isRecognitionMode = true;
    private boolean isFlashOn;
    private boolean isUsingFrontCamera;
    private boolean isCameraReady;
    private boolean isRecognitionInProgress;
    private boolean isJournalFeedOpen;
    private boolean isJournalFeedDragging;

    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private Camera camera;

    private GeminiRepository geminiRepository;
    private ScanFoodLocalMatcher scanFoodLocalMatcher;
    private ScanSavedDishStore scanSavedDishStore;
    private ScanDishSuggestionAdapter scanDishSuggestionAdapter;
    private MediaUploadRepository mediaUploadRepository;
    private JournalFeedRepository journalFeedRepository;
    private FriendInviteRepository friendInviteRepository;
    private final List<DetectedIngredient> currentDetectedIngredients = new ArrayList<>();
    private final List<ScanDishItem> allSuggestedDishItems = new ArrayList<>();
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private ScanFoodJournalManager journalManager;
    private GestureDetectorCompat journalGestureDetector;
    private int journalTouchSlop;
    private float journalFeedClosedTranslation;
    private float journalFeedOpenTranslation;
    private float journalDragStartY;
    private float journalDragStartTranslation;
    private byte[] lastRecognitionImageBytes;
    private ScanDishItem selectedDishItem;
    private String selectedHealthFilter = ScanHealthFilters.FILTER_ALL;

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
        setupRecognitionResultUi();
        setupJournalList();
        setupJournalFeedSheet();
        setupClickListeners();

        geminiRepository = new GeminiRepository();
        scanFoodLocalMatcher = new ScanFoodLocalMatcher(getApplicationContext());
        scanSavedDishStore = new ScanSavedDishStore(getApplicationContext());
        mediaUploadRepository = new MediaUploadRepository(getApplicationContext());
        journalFeedRepository = new JournalFeedRepository(firestore);
        friendInviteRepository = new FriendInviteRepository(firestore);
        journalManager = new ScanFoodJournalManager(
                this,
                new ScanFoodJournalManager.Host() {
                    @Override
                    public void setProcessingUiEnabled(boolean enabled) {
                        ScanFoodActivity.this.setProcessingUiEnabled(enabled);
                    }

                    @Override
                    public void updateJournalStatus(@NonNull String status) {
                        ScanFoodActivity.this.updateJournalStatus(status);
                    }
                },
                rvJournalMoments,
                txtJournalEmptyState,
                txtJournalFriendCount,
                txtJournalUploadProgress,
                txtJournalPostError,
                edtJournalCaption,
                imgJournalCapturedPreview,
                journalCaptureOverlay,
                btnJournalPostCancel,
                btnJournalPostPublish,
                journalPostLoading,
                mediaUploadRepository,
                journalFeedRepository,
                friendInviteRepository);

        applyInitialModeFromIntent();
        updateUiForMode(false);
        updateFlashUi(false);
        updateScanStatus(isRecognitionMode
                ? "Hướng camera vào món ăn rồi bấm nút chụp"
                : "Chụp nhanh một khoảnh khắc để đăng.");
        updateJournalStatus("Vuốt xuống để xem feed của bạn và bạn bè.");
        ensureCameraPermissionAndStart();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!isRecognitionMode) {
            startJournalRealtimeListeners();
        }
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
        stopJournalRealtimeListeners();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopJournalRealtimeListeners();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    @Override
    public void onBackPressed() {
        if (journalManager != null && journalManager.isCapturePreviewVisible()) {
            journalManager.hideJournalCapturePreview(true);
            return;
        }
        if (!isRecognitionMode && isJournalFeedOpen) {
            animateJournalFeed(false);
            return;
        }
        navigateBackHome();
    }

    private void initViews() {
        root = findViewById(R.id.scanRoot);
        recognitionSurface = findViewById(R.id.recognitionSurface);
        journalModeContainer = findViewById(R.id.journalModeContainer);
        journalSurface = findViewById(R.id.journalSurface);
        topBar = findViewById(R.id.topBar);
        footerContainer = findViewById(R.id.footerContainer);
        cameraPreviewCard = findViewById(R.id.cameraPreviewCard);
        previewInnerFrame = findViewById(R.id.previewInnerFrame);
        detectionBox = findViewById(R.id.detectionBox);
        scanResultContainer = findViewById(R.id.scanResultContainer);
        btnSaveSelectedDish = findViewById(R.id.btnSaveSelectedDish);
        tabIndicator = findViewById(R.id.tabIndicator);
        journalTopBar = findViewById(R.id.journalTopBar);
        journalFooterContainer = findViewById(R.id.journalFooterContainer);
        journalCameraPreviewCard = findViewById(R.id.journalCameraPreviewCard);
        journalCaptureOverlay = findViewById(R.id.journalCaptureOverlay);
        journalCameraShell = findViewById(R.id.journalCameraShell);
        journalFeedContent = findViewById(R.id.journalFeedContent);
        txtScanStatus = findViewById(R.id.txtScanStatus);
        txtDetectedIngredientsEmpty = findViewById(R.id.txtDetectedIngredientsEmpty);
        txtDishSuggestionsEmpty = findViewById(R.id.txtDishSuggestionsEmpty);
        txtSelectedDishHint = findViewById(R.id.txtSelectedDishHint);
        txtJournalStatus = findViewById(R.id.txtJournalStatus);
        txtJournalEmptyState = findViewById(R.id.txtJournalEmptyState);
        txtJournalFriendCount = findViewById(R.id.txtJournalFriendCount);
        txtJournalUploadProgress = findViewById(R.id.txtJournalUploadProgress);
        txtJournalPostError = findViewById(R.id.txtJournalPostError);
        txtJournalModeLabel = findViewById(R.id.txtJournalModeLabel);
        tabNhanDien = findViewById(R.id.tabNhanDien);
        tabNhatKy = findViewById(R.id.tabNhatKy);
        iconFlash = findViewById(R.id.iconFlash);
        iconJournalFlash = findViewById(R.id.iconJournalFlash);
        btnBack = findViewById(R.id.btnBack);
        btnFlash = findViewById(R.id.btnFlash);
        btnGallery = findViewById(R.id.btnGallery);
        btnShutter = findViewById(R.id.btnShutter);
        btnFlipCamera = findViewById(R.id.btnFlipCamera);
        btnJournalProfile = findViewById(R.id.btnJournalProfile);
        btnJournalFriends = findViewById(R.id.btnJournalFriends);
        btnJournalChat = findViewById(R.id.btnJournalChat);
        btnJournalFlash = findViewById(R.id.btnJournalFlash);
        btnJournalGallery = findViewById(R.id.btnJournalGallery);
        btnJournalShutter = findViewById(R.id.btnJournalShutter);
        btnJournalFlipCamera = findViewById(R.id.btnJournalFlipCamera);
        btnJournalPostCancel = findViewById(R.id.btnJournalPostCancel);
        btnJournalPostPublish = findViewById(R.id.btnJournalPostPublish);
        journalPostLoading = findViewById(R.id.journalPostLoading);
        edtJournalCaption = findViewById(R.id.edtJournalCaption);
        imgJournalCapturedPreview = findViewById(R.id.imgJournalCapturedPreview);
        imgCapturedRecognition = findViewById(R.id.imgCapturedRecognition);
        recognitionPreviewView = findViewById(R.id.previewView);
        journalPreviewView = findViewById(R.id.journalPreviewView);
        rvJournalMoments = findViewById(R.id.rvJournalMoments);
        rvDishSuggestions = findViewById(R.id.rvDishSuggestions);
        groupDetectedIngredients = findViewById(R.id.groupDetectedIngredients);
        groupHealthFilters = findViewById(R.id.groupHealthFilters);
        previewView = recognitionPreviewView;
    }

    private void setupRecognitionResultUi() {
        if (rvDishSuggestions != null) {
            scanDishSuggestionAdapter = new ScanDishSuggestionAdapter(this::onDishSelected);
            rvDishSuggestions.setLayoutManager(new LinearLayoutManager(this));
            rvDishSuggestions.setNestedScrollingEnabled(false);
            rvDishSuggestions.setAdapter(scanDishSuggestionAdapter);
        }

        if (groupHealthFilters != null) {
            groupHealthFilters.removeAllViews();
            for (String filter : ScanHealthFilters.defaultFilters()) {
                Chip chip = createSelectableChip(filter, ScanHealthFilters.FILTER_ALL.equals(filter));
                chip.setOnClickListener(v -> {
                    selectedHealthFilter = filter;
                    syncHealthFilterSelection();
                    applyDishFilter();
                });
                groupHealthFilters.addView(chip);
            }
        }

        syncHealthFilterSelection();
        renderDetectedIngredients(new ArrayList<>());
        renderSuggestedDishes(new ArrayList<>());
        updateSelectedDishState();
        if (scanResultContainer != null) {
            scanResultContainer.setVisibility(View.GONE);
        }
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

    private void setupJournalList() {
        if (rvJournalMoments == null) {
            return;
        }

        if (journalManager != null) {
            journalManager.setupJournalList();
        }
    }

    private void setupJournalFeedSheet() {
        journalTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        journalGestureDetector = new GestureDetectorCompat(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(
                    @NonNull MotionEvent e1,
                    @NonNull MotionEvent e2,
                    float velocityX,
                    float velocityY) {
                if (Math.abs(velocityY) <= Math.abs(velocityX) || Math.abs(velocityY) < 1200f) {
                    return false;
                }
                if (velocityY > 0f && !isJournalFeedOpen) {
                    animateJournalFeed(true);
                    return true;
                }
                if (velocityY < 0f && isJournalFeedOpen && canCloseJournalFeed()) {
                    animateJournalFeed(false);
                    return true;
                }
                return false;
            }
        });

        View.OnTouchListener dragListener = this::handleJournalFeedTouch;
        if (journalCameraShell != null) {
            journalCameraShell.setOnTouchListener(dragListener);
        }
        if (journalSurface != null) {
            journalSurface.setOnTouchListener(dragListener);
            journalSurface.post(this::configureJournalFeedSheet);
            journalSurface.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                    configureJournalFeedSheet();
                }
            });
        }
        if (journalCameraPreviewCard != null) {
            journalCameraPreviewCard.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                    configureJournalFeedSheet();
                }
            });
        }
    }

    private boolean handleJournalFeedTouch(View view, MotionEvent event) {
        if (isRecognitionMode || journalSurface == null) {
            return false;
        }

        if (journalGestureDetector != null) {
            journalGestureDetector.onTouchEvent(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                journalDragStartY = event.getRawY();
                journalDragStartTranslation = journalSurface.getTranslationY();
                isJournalFeedDragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float deltaY = event.getRawY() - journalDragStartY;
                if (!isJournalFeedDragging) {
                    boolean opening = !isJournalFeedOpen && deltaY > journalTouchSlop;
                    boolean closing = isJournalFeedOpen && deltaY < -journalTouchSlop && canCloseJournalFeed();
                    if (!opening && !closing) {
                        return false;
                    }
                    if (opening && journalSurface != null) {
                        journalSurface.bringToFront();
                    }
                    isJournalFeedDragging = true;
                }
                float nextTranslation = clamp(
                        journalDragStartTranslation - deltaY,
                        journalFeedOpenTranslation,
                        journalFeedClosedTranslation);
                setJournalFeedTranslation(nextTranslation);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isJournalFeedDragging) {
                    settleJournalFeed();
                    isJournalFeedDragging = false;
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    private void configureJournalFeedSheet() {
        if (journalSurface == null || journalTopBar == null || journalCameraPreviewCard == null) {
            return;
        }
        journalSurface.post(() -> {
            float openTranslation = journalTopBar.getBottom() + dp(12f);
            float closedTranslation = journalCameraPreviewCard.getBottom() + dp(12f);
            journalFeedOpenTranslation = Math.max(0f, openTranslation);
            journalFeedClosedTranslation = Math.max(journalFeedOpenTranslation, closedTranslation);
            setJournalFeedTranslation(isJournalFeedOpen ? journalFeedOpenTranslation : journalFeedClosedTranslation);
        });
    }

    private void settleJournalFeed() {
        float midpoint = journalFeedOpenTranslation
                + ((journalFeedClosedTranslation - journalFeedOpenTranslation) * 0.5f);
        animateJournalFeed(journalSurface != null && journalSurface.getTranslationY() < midpoint);
    }

    private void animateJournalFeed(boolean open) {
        if (journalSurface == null) {
            return;
        }
        isJournalFeedOpen = open;
        if (open && journalSurface != null) {
            journalSurface.bringToFront();
        }
        journalSurface.animate().cancel();
        journalSurface.animate()
                .translationY(open ? journalFeedOpenTranslation : journalFeedClosedTranslation)
                .setDuration(320L)
                .setInterpolator(open ? new OvershootInterpolator(0.7f) : new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (!open && journalCameraShell != null) {
                        journalCameraShell.bringToFront();
                    }
                    updateJournalFeedVisualState(journalSurface.getTranslationY());
                })
                .start();
    }

    private void setJournalFeedTranslation(float translationY) {
        if (journalSurface == null) {
            return;
        }
        journalSurface.setTranslationY(translationY);
        updateJournalFeedVisualState(translationY);
    }

    private void updateJournalFeedVisualState(float translationY) {
        float travel = Math.max(1f, journalFeedClosedTranslation - journalFeedOpenTranslation);
        float progress = 1f - ((translationY - journalFeedOpenTranslation) / travel);
        progress = clamp(progress, 0f, 1f);

        if (journalFooterContainer != null) {
            journalFooterContainer.setAlpha(1f - progress);
            journalFooterContainer.setTranslationY(progress * dp(24f));
        }
        if (journalCameraPreviewCard != null) {
            journalCameraPreviewCard.setScaleX(1f - (0.03f * progress));
            journalCameraPreviewCard.setScaleY(1f - (0.03f * progress));
            journalCameraPreviewCard.setAlpha(1f - (0.12f * progress));
        }
        if (journalFeedContent != null) {
            journalFeedContent.setAlpha(0.92f + (0.08f * progress));
        }
        if (journalSurface != null) {
            journalSurface.setNestedScrollingEnabled(progress > 0.98f);
        }
    }

    private boolean canCloseJournalFeed() {
        return journalSurface != null && !journalSurface.canScrollVertically(-1);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private void startJournalRealtimeListeners() {
        if (journalManager != null) {
            journalManager.startRealtimeListeners();
        }
    }

    private void stopJournalRealtimeListeners() {
        if (journalManager != null) {
            journalManager.stopRealtimeListeners();
        }
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

        final int journalTopStart = journalTopBar == null ? 0 : journalTopBar.getPaddingStart();
        final int journalTopTop = journalTopBar == null ? 0 : journalTopBar.getPaddingTop();
        final int journalTopEnd = journalTopBar == null ? 0 : journalTopBar.getPaddingEnd();
        final int journalTopBottom = journalTopBar == null ? 0 : journalTopBar.getPaddingBottom();

        final int journalFooterStart = journalFooterContainer == null ? 0 : journalFooterContainer.getPaddingStart();
        final int journalFooterTop = journalFooterContainer == null ? 0 : journalFooterContainer.getPaddingTop();
        final int journalFooterEnd = journalFooterContainer == null ? 0 : journalFooterContainer.getPaddingEnd();
        final int journalFooterBottom = journalFooterContainer == null ? 0 : journalFooterContainer.getPaddingBottom();

        final int overlayStart = journalCaptureOverlay == null ? 0 : journalCaptureOverlay.getPaddingStart();
        final int overlayTop = journalCaptureOverlay == null ? 0 : journalCaptureOverlay.getPaddingTop();
        final int overlayEnd = journalCaptureOverlay == null ? 0 : journalCaptureOverlay.getPaddingEnd();
        final int overlayBottom = journalCaptureOverlay == null ? 0 : journalCaptureOverlay.getPaddingBottom();

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

            if (journalTopBar != null) {
                journalTopBar.setPaddingRelative(
                        journalTopStart + systemBars.left,
                        journalTopTop + systemBars.top,
                        journalTopEnd + systemBars.right,
                        journalTopBottom);
            }

            if (journalFooterContainer != null) {
                journalFooterContainer.setPaddingRelative(
                        journalFooterStart + systemBars.left,
                        journalFooterTop,
                        journalFooterEnd + systemBars.right,
                        journalFooterBottom + systemBars.bottom);
            }

            if (journalCaptureOverlay != null) {
                journalCaptureOverlay.setPaddingRelative(
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
                        updateJournalStatus("Camera sẵn sàng. Chụp rồi kéo xuống để xem feed.");
                        startCameraIfNeeded();
                    } else {
                        isCameraReady = false;
                        updateScanStatus("Bạn cần cấp quyền Camera để dùng tính năng này");
                        updateJournalStatus("Bạn cần cấp quyền Camera để đăng moment.");
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
        updateJournalStatus("Đang xin quyền camera...");
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
                updateJournalStatus("Không thể khởi tạo camera.");
                Toast.makeText(this, "Không mở được camera", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void updateActivePreviewView() {
        previewView = isRecognitionMode ? recognitionPreviewView : journalPreviewView;
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
            updateJournalStatus("Camera sẵn sàng. Vuốt xuống để xem feed.");
        } catch (Exception bindError) {
            isCameraReady = false;
            updateScanStatus("Không bind được camera, đang thử camera khác...");
            updateJournalStatus("Không bind được camera.");
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
                btnJournalProfile,
                btnJournalFriends,
                btnJournalChat,
                btnJournalFlash,
                btnJournalGallery,
                btnJournalShutter,
                btnJournalFlipCamera,
                btnJournalPostCancel,
                btnJournalPostPublish,
                txtJournalModeLabel);
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
            recognitionSurface.setVisibility(isRecognitionMode ? View.VISIBLE : View.GONE);
        }
        if (journalModeContainer != null) {
            journalModeContainer.setVisibility(isRecognitionMode ? View.GONE : View.VISIBLE);
        }
        if (journalFooterContainer != null) {
            journalFooterContainer.setVisibility(isRecognitionMode ? View.GONE : View.VISIBLE);
        }

        if (cameraPreviewCard != null) {
            cameraPreviewCard.setAlpha(isRecognitionMode ? 1f : 0.94f);
        }
        if (previewInnerFrame != null) {
            previewInnerFrame.setAlpha(isRecognitionMode ? 1f : 0.62f);
        }
        if (journalCameraPreviewCard != null) {
            journalCameraPreviewCard.setAlpha(isRecognitionMode ? 0f : 1f);
        }

        if (detectionBox != null) {
            detectionBox.animate().cancel();
            if (isRecognitionMode) {
                isJournalFeedOpen = false;
                configureJournalFeedSheet();
                if (journalCameraShell != null) {
                    journalCameraShell.bringToFront();
                }
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
                stopJournalRealtimeListeners();
            } else {
                stopDetectionPulse();
                detectionBox.setVisibility(View.INVISIBLE);
                isJournalFeedOpen = false;
                configureJournalFeedSheet();
                if (journalCameraShell != null) {
                    journalCameraShell.bringToFront();
                }
                if (!isBusyProcessing()) {
                    updateJournalStatus("Chụp nhanh rồi kéo xuống để xem feed.");
                }
                startJournalRealtimeListeners();
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
            iconFlash.setTextColor(isFlashOn ? TAB_ACTIVE_COLOR : TAB_INACTIVE_COLOR);
        }

        if (iconJournalFlash != null) {
            iconJournalFlash.setText(isFlashOn ? "flash_on" : "flash_off");
            iconJournalFlash.setTextColor(isFlashOn ? TAB_ACTIVE_COLOR : Color.WHITE);
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
        if (btnJournalFlash != null) {
            btnJournalFlash.setEnabled(enabled);
            btnJournalFlash.setAlpha(enabled ? 1f : 0.5f);
        }
    }

    private void setupClickListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> navigateBackHome());
        }
        if (btnJournalProfile != null) {
            btnJournalProfile.setOnClickListener(v -> navigateBackHome());
        }
        if (btnFlash != null) {
            btnFlash.setOnClickListener(v -> toggleFlash());
        }
        if (btnJournalFlash != null) {
            btnJournalFlash.setOnClickListener(v -> toggleFlash());
        }
        if (btnGallery != null) {
            btnGallery.setOnClickListener(v -> openGalleryPicker());
        }
        if (btnJournalGallery != null) {
            btnJournalGallery.setOnClickListener(v -> openGalleryPicker());
        }
        if (btnShutter != null) {
            btnShutter.setOnClickListener(v -> performShutterAction());
        }
        if (btnJournalShutter != null) {
            btnJournalShutter.setOnClickListener(v -> performShutterAction());
        }
        if (btnFlipCamera != null) {
            btnFlipCamera.setOnClickListener(v -> toggleCameraLens());
        }
        if (btnJournalFlipCamera != null) {
            btnJournalFlipCamera.setOnClickListener(v -> toggleCameraLens());
        }
        if (btnJournalFriends != null) {
            btnJournalFriends.setOnClickListener(v -> openFriendInviteSheet());
        }
        if (btnJournalChat != null) {
            btnJournalChat.setOnClickListener(v -> openFriendInviteSheet());
        }
        if (txtJournalModeLabel != null) {
            txtJournalModeLabel.setOnClickListener(v -> switchMode(true));
        }
        if (btnJournalPostCancel != null) {
            btnJournalPostCancel.setOnClickListener(v -> {
                if (journalManager != null) {
                    journalManager.hideJournalCapturePreview(true);
                }
            });
        }
        if (btnJournalPostPublish != null) {
            btnJournalPostPublish.setOnClickListener(v -> publishPendingJournalMoment());
        }
        if (btnSaveSelectedDish != null) {
            btnSaveSelectedDish.setOnClickListener(v -> saveSelectedDish());
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
        updateJournalStatus("Đang chụp...");

        final boolean routeRecognition = isRecognitionMode;
        imageCapture.takePicture(
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        super.onCaptureSuccess(image);
                        byte[] bytes = ScanFoodImageUtils.imageProxyToJpeg(image);
                        image.close();
                        if (bytes == null || bytes.length == 0) {
                            updateScanStatus("Không đọc được ảnh chụp, vui lòng thử lại");
                            updateJournalStatus("Không đọc được ảnh vừa chụp.");
                            return;
                        }

                        if (routeRecognition) {
                            processImageBytesForRecognition(bytes, "image/jpeg", "camera");
                        } else {
                            processImageBytesForJournal(bytes, "camera");
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        super.onError(exception);
                        updateScanStatus("Chụp thất bại, vui lòng thử lại");
                        updateJournalStatus("Chụp thất bại, vui lòng thử lại.");
                        Toast.makeText(ScanFoodActivity.this, "Không chụp được ảnh", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void animateShutterFeedback() {
        View activeShutter = isRecognitionMode ? btnShutter : btnJournalShutter;
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
        if (galleryPickerLauncher == null) {
            return;
        }
        galleryPickerLauncher.launch("image/*");
    }

    private void processImageUriForRecognition(@NonNull Uri uri, @NonNull String sourceLabel) {
        new Thread(() -> {
            try {
                String mimeType = GeminiRepository.detectMimeType(getContentResolver(), uri);
                byte[] raw = ScanFoodImageUtils.readImageBytes(
                        getContentResolver(),
                        uri,
                        MAX_RECOGNITION_IMAGE_BYTES);
                runOnUiThread(() -> {
                    if (raw.length > MAX_RECOGNITION_IMAGE_BYTES) {
                        updateScanStatus("Ảnh quá lớn, vui lòng chọn ảnh dưới 4MB");
                        Toast.makeText(this, "Ảnh vượt quá 4MB", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    processImageBytesForRecognition(raw, mimeType, sourceLabel);
                });
            } catch (IOException ioException) {
                runOnUiThread(() -> {
                    updateScanStatus("Không đọc được ảnh, vui lòng thử lại");
                    Toast.makeText(this, "Không thể đọc ảnh đã chọn", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
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
                        updateJournalStatus("Ảnh quá lớn, vui lòng chọn ảnh nhỏ hơn.");
                        Toast.makeText(this, "Ảnh vượt quá 8MB", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    processImageBytesForJournal(raw, sourceLabel);
                });
            } catch (IOException ioException) {
                runOnUiThread(() -> {
                    updateJournalStatus("Không đọc được ảnh, vui lòng thử lại.");
                    Toast.makeText(this, "Không thể đọc ảnh đã chọn", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void processImageBytesForRecognition(
            @NonNull byte[] imageBytes,
            @Nullable String mimeType,
            @NonNull String sourceLabel) {
        if (isBusyProcessing()) {
            return;
        }
        if (imageBytes.length > MAX_RECOGNITION_IMAGE_BYTES) {
            updateScanStatus("Anh qua lon, vui long chup/chon anh khac");
            return;
        }

        isRecognitionInProgress = true;
        setProcessingUiEnabled(false);
        updateScanStatus("Dang nhan dien nguyen lieu...");
        lastRecognitionImageBytes = imageBytes.clone();
        selectedDishItem = null;
        prepareRecognitionResultUiForLoading(imageBytes);

        String safeMimeType = TextUtils.isEmpty(mimeType) ? "image/jpeg" : mimeType;
        String imageBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

        geminiRepository.requestStructuredResponse(
                INGREDIENT_DETECTION_PROMPT,
                "Phan tich anh nay va nhan dien tung nguyen lieu hoac thuc pham nhin thay ro trong anh.",
                safeMimeType,
                imageBase64,
                new GeminiRepository.StreamCallback() {
                    @Override
                    public void onStart() {
                        runOnUiThread(() -> updateScanStatus("Dang phan tich nguyen lieu trong anh..."));
                    }

                    @Override
                    public void onChunk(@NonNull String accumulatedText) {
                        runOnUiThread(() -> updateScanStatus("Dang nhan dien nguyen lieu..."));
                    }

                    @Override
                    public void onCompleted(@NonNull String finalText) {
                        runOnUiThread(() -> {
                            List<DetectedIngredient> detectedIngredients = refineDetectedIngredients(
                                    parseDetectedIngredients(finalText));
                            if (detectedIngredients.isEmpty()) {
                                finishRecognitionWithError("Chua nhan dien ro nguyen lieu trong anh, vui long thu lai voi anh ro hon.");
                                return;
                            }
                            List<DetectedIngredient> mergedIngredients = mergeDetectedIngredients(
                                    currentDetectedIngredients,
                                    detectedIngredients);
                            currentDetectedIngredients.clear();
                            currentDetectedIngredients.addAll(mergedIngredients);
                            renderDetectedIngredients(currentDetectedIngredients);
                            updateScanStatus("Da them nguyen lieu vao danh sach. Dang goi y mon co the nau...");
                            requestDishSuggestions(sourceLabel);
                        });
                    }

                    @Override
                    public void onError(@NonNull String friendlyError) {
                        runOnUiThread(() -> finishRecognitionWithError(
                                TextUtils.isEmpty(friendlyError)
                                        ? "Nhan dien nguyen lieu that bai, vui long thu lai."
                                        : friendlyError));
                    }
                });
    }

    private void requestDishSuggestions(@NonNull String sourceLabel) {
        List<ScanDishItem> localSuggestions = scanFoodLocalMatcher.suggestDishes(currentDetectedIngredients, 8);
        if (localSuggestions.isEmpty()) {
            finishRecognitionWithError("Da them nguyen lieu nhung chua co mon phu hop trong danh sach hien tai.");
            return;
        }
        finishRecognitionSuccess(localSuggestions, sourceLabel);
    }

    private void requestAiGeneratedDishesIfNeeded(
            @NonNull List<SuggestedDish> suggestedDishes,
            @NonNull String sourceLabel) {
        List<SuggestedDish> unmatchedDishes = new ArrayList<>();
        for (SuggestedDish suggestedDish : suggestedDishes) {
            if (scanFoodLocalMatcher.findDishByName(suggestedDish.getName()) == null) {
                unmatchedDishes.add(suggestedDish);
            }
        }

        if (unmatchedDishes.isEmpty()) {
            finishRecognitionSuccess(buildDishItems(suggestedDishes, new ArrayList<>()), sourceLabel);
            return;
        }

        geminiRepository.requestStructuredResponse(
                AI_DISH_GENERATION_SYSTEM_PROMPT,
                buildAiDishGenerationPrompt(unmatchedDishes),
                null,
                null,
                new GeminiRepository.StreamCallback() {
                    @Override
                    public void onStart() {
                        runOnUiThread(() -> updateScanStatus("Dang tao them mon AI cho cac mon chua co san..."));
                    }

                    @Override
                    public void onChunk(@NonNull String accumulatedText) {
                        runOnUiThread(() -> updateScanStatus("Dang hoan thien mon AI goi y..."));
                    }

                    @Override
                    public void onCompleted(@NonNull String finalText) {
                        runOnUiThread(() -> finishRecognitionSuccess(
                                buildDishItems(suggestedDishes, parseGeneratedDishes(finalText)),
                                sourceLabel));
                    }

                    @Override
                    public void onError(@NonNull String friendlyError) {
                        runOnUiThread(() -> finishRecognitionSuccess(
                                buildDishItems(suggestedDishes, new ArrayList<>()),
                                sourceLabel));
                    }
                });
    }

    @NonNull
    private List<ScanDishItem> buildDishItems(
            @NonNull List<SuggestedDish> suggestedDishes,
            @NonNull List<GeneratedDishPayload> generatedDishes) {
        List<ScanDishItem> dishItems = new ArrayList<>();
        for (SuggestedDish suggestedDish : suggestedDishes) {
            FoodItem localFood = scanFoodLocalMatcher.findDishByName(suggestedDish.getName());
            if (localFood != null) {
                dishItems.add(new ScanDishItem(
                        scanFoodLocalMatcher.createStableId(localFood.getId(), true),
                        localFood.getName(),
                        localFood,
                        suggestedDish.getUsedIngredients(),
                        suggestedDish.getMissingIngredients(),
                        suggestedDish.getHealthTags(),
                        suggestedDish.getReason(),
                        localFood.getRecipe(),
                        suggestedDish.getConfidence()));
                continue;
            }

            GeneratedDishPayload generatedDish = findGeneratedDish(generatedDishes, suggestedDish.getName());
            dishItems.add(new ScanDishItem(
                    scanFoodLocalMatcher.createStableId(suggestedDish.getName(), false),
                    suggestedDish.getName(),
                    null,
                    suggestedDish.getUsedIngredients(),
                    suggestedDish.getMissingIngredients(),
                    generatedDish == null || generatedDish.healthTags.isEmpty()
                            ? suggestedDish.getHealthTags()
                            : generatedDish.healthTags,
                    generatedDish == null || generatedDish.reason.isEmpty()
                            ? suggestedDish.getReason()
                            : generatedDish.reason,
                    generatedDish == null ? "" : generatedDish.recipe,
                    suggestedDish.getConfidence()));
        }
        return dishItems;
    }

    @Nullable
    private GeneratedDishPayload findGeneratedDish(
            @NonNull List<GeneratedDishPayload> generatedDishes,
            @NonNull String dishName) {
        String targetId = scanFoodLocalMatcher.createStableId(dishName, false);
        for (GeneratedDishPayload payload : generatedDishes) {
            if (targetId.equals(scanFoodLocalMatcher.createStableId(payload.name, false))) {
                return payload;
            }
        }
        return null;
    }

    private void finishRecognitionSuccess(@NonNull List<ScanDishItem> dishItems, @NonNull String sourceLabel) {
        isRecognitionInProgress = false;
        setProcessingUiEnabled(true);
        allSuggestedDishItems.clear();
        allSuggestedDishItems.addAll(dishItems);
        selectedDishItem = null;
        renderSuggestedDishes(dishItems);
        applyDishFilter();
        updateSelectedDishState();
        updateRecognitionPreviewState();
        if (dishItems.isEmpty()) {
            updateScanStatus("Da nhan dien nguyen lieu nhung chua thay mon phu hop.");
        } else if (isRecognitionMode) {
            updateScanStatus("Da cap nhat goi y mon. Ban co the chup them de bo sung nguyen lieu.");
        } else {
            updateScanStatus("Da goi y mon. Chuyen tab Nhan dien de xem tiep.");
        }
        Toast.makeText(this, "Da nhan dien nguyen lieu tu anh " + sourceLabel, Toast.LENGTH_SHORT).show();
    }

    private void finishRecognitionWithError(@NonNull String message) {
        isRecognitionInProgress = false;
        setProcessingUiEnabled(true);
        renderDetectedIngredients(currentDetectedIngredients);
        applyDishFilter();
        updateSelectedDishState();
        updateRecognitionPreviewState();
        updateScanStatus(message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
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
            Chip chip = new Chip(this);
            chip.setText(ingredient.getName());
            chip.setCheckable(false);
            chip.setClickable(false);
            chip.setTypeface(getResources().getFont(R.font.be_vietnam_pro_medium));
            chip.setTextColor(Color.WHITE);
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#223A3A3A")));
            chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#334B4B4B")));
            chip.setChipStrokeWidth(dp(1f));
            chip.setChipMinHeight(dp(34f));
            chip.setEnsureMinTouchTargetSize(false);
            groupDetectedIngredients.addView(chip);
        }
        if (txtDetectedIngredientsEmpty != null) {
            txtDetectedIngredientsEmpty.setVisibility(ingredients.isEmpty() ? View.VISIBLE : View.GONE);
            if (ingredients.isEmpty()) {
                txtDetectedIngredientsEmpty.setText("Chua co nguyen lieu nhan dien duoc.");
            }
        }
    }

    private void renderSuggestedDishes(@NonNull List<ScanDishItem> dishItems) {
        if (scanDishSuggestionAdapter != null) {
            scanDishSuggestionAdapter.submitItems(dishItems);
        }
        if (txtDishSuggestionsEmpty != null) {
            txtDishSuggestionsEmpty.setVisibility(dishItems.isEmpty() ? View.VISIBLE : View.GONE);
            if (dishItems.isEmpty() && !isRecognitionInProgress) {
                txtDishSuggestionsEmpty.setText("Chua co mon goi y phu hop.");
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
        return (scanResultContainer != null && scanResultContainer.getVisibility() == View.VISIBLE)
                && (lastRecognitionImageBytes != null
                || !currentDetectedIngredients.isEmpty()
                || !allSuggestedDishItems.isEmpty()
                || isRecognitionInProgress);
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
    private String buildDishSuggestionPrompt(@NonNull List<DetectedIngredient> ingredients) {
        JSONArray ingredientArray = new JSONArray();
        for (DetectedIngredient ingredient : ingredients) {
            ingredientArray.put(ingredient.getName());
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("Dua tren danh sach nguyen lieu sau: ").append(ingredientArray).append('.').append("\n");
        prompt.append("Hay goi y cac mon an co the nau, uu tien mon Viet Nam, phu hop voi du lieu foods.json neu co.").append("\n");
        prompt.append("Chi tra ve JSON hop le.").append("\n\n");
        prompt.append("Schema:").append("\n");
        prompt.append('{').append("\n");
        prompt.append("  \"suggestedDishes\": [").append("\n");
        prompt.append("    {").append("\n");
        prompt.append("      \"name\": \"Ten mon an\",").append("\n");
        prompt.append("      \"usedIngredients\": [\"nguyen lieu da dung\"],").append("\n");
        prompt.append("      \"missingIngredients\": [\"nguyen lieu con thieu neu co\"],").append("\n");
        prompt.append("      \"healthTags\": [\"tang co bap\", \"mo nhiem mau\", \"da day\", \"tieu duong\", \"nguoi an nhe bung\"],").append("\n");
        prompt.append("      \"reason\": \"Ly do ngan vi sao mon nay phu hop\",").append("\n");
        prompt.append("      \"confidence\": 0.0").append("\n");
        prompt.append("    }").append("\n");
        prompt.append("  ]").append("\n");
        prompt.append('}');
        return prompt.toString();
    }

    @NonNull
    private String buildAiDishGenerationPrompt(@NonNull List<SuggestedDish> unmatchedDishes) {
        JSONArray dishesArray = new JSONArray();
        for (SuggestedDish dish : unmatchedDishes) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("name", dish.getName());
                payload.put("usedIngredients", new JSONArray(dish.getUsedIngredients()));
                payload.put("missingIngredients", new JSONArray(dish.getMissingIngredients()));
                payload.put("healthTags", new JSONArray(dish.getHealthTags()));
                payload.put("reason", dish.getReason());
                dishesArray.put(payload);
            } catch (Exception ignored) {
                // Skip malformed payloads and continue building the remaining prompt.
            }
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("Dua tren cac mon goi y chua co trong foods.json sau: ").append(dishesArray).append('.').append("\n");
        prompt.append("Hay tao du lieu hien thi cho tung mon va uu tien mon Viet Nam.").append("\n");
        prompt.append("Chi tra ve JSON hop le.").append("\n\n");
        prompt.append("Schema:").append("\n");
        prompt.append('{').append("\n");
        prompt.append("  \"generatedDishes\": [").append("\n");
        prompt.append("    {").append("\n");
        prompt.append("      \"name\": \"Ten mon an\",").append("\n");
        prompt.append("      \"healthTags\": [\"tang co bap\", \"mo nhiem mau\", \"da day\", \"tieu duong\", \"nguoi an nhe bung\"],").append("\n");
        prompt.append("      \"reason\": \"Ly do ngan vi sao mon nay phu hop\",").append("\n");
        prompt.append("      \"recipe\": \"### Ten mon\\n**Khau phan:** ...\"").append("\n");
        prompt.append("    }").append("\n");
        prompt.append("  ]").append("\n");
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
            JSONArray array = rootObject.optJSONArray("suggestedDishes");
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
                        toStringList(item.optJSONArray("usedIngredients")),
                        toStringList(item.optJSONArray("missingIngredients")),
                        toStringList(item.optJSONArray("healthTags")),
                        item.optString("reason", "").trim(),
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

    private void processImageBytesForJournal(@NonNull byte[] imageBytes, @NonNull String sourceLabel) {
        if (journalManager != null) {
            journalManager.processImageBytesForJournal(imageBytes, sourceLabel, isBusyProcessing());
        }
    }

    private void publishPendingJournalMoment() {
        if (journalManager != null) {
            journalManager.publishPendingJournalMoment(isUsingFrontCamera);
        }
    }

    private void openFriendInviteSheet() {
        if (journalManager != null) {
            journalManager.openFriendInviteSheet();
        }
    }

    private void setProcessingUiEnabled(boolean enabled) {
        setViewEnabled(btnShutter, enabled);
        setViewEnabled(btnGallery, enabled);
        setViewEnabled(btnFlipCamera, enabled);
        setViewEnabled(btnJournalShutter, enabled);
        setViewEnabled(btnJournalGallery, enabled);
        setViewEnabled(btnJournalFlipCamera, enabled);
        setViewEnabled(tabNhanDien, enabled);
        setViewEnabled(tabNhatKy, enabled);
        setViewEnabled(txtJournalModeLabel, enabled);
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

    private void updateJournalStatus(@NonNull String status) {
        if (txtJournalStatus != null) {
            txtJournalStatus.setText(status);
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
