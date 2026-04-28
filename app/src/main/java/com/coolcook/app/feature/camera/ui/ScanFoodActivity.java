package com.coolcook.app.feature.camera.ui;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Environment;
import android.os.Looper;
import android.provider.MediaStore;
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
import androidx.viewpager2.widget.ViewPager2;

import com.coolcook.app.R;
import com.coolcook.app.feature.auth.ui.AuthActivity;
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
import com.coolcook.app.feature.social.ui.FriendInviteActivity;
import com.coolcook.app.feature.social.ui.JournalHistoryGridActivity;
import com.coolcook.app.feature.social.ui.adapter.JournalLocketPagerAdapter;
import com.coolcook.app.feature.journal.data.JournalRepository;
import com.coolcook.app.feature.home.ui.HomeActivity;
import com.coolcook.app.feature.search.model.FoodItem;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
    private enum JournalCameraUiState {
        LIVE_CAMERA,
        CAPTURE_PREVIEW
    }

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
    private static final long CAPTURE_DEBOUNCE_MS = 900L;
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
    private View topCenterAction;
    private View footerContainer;
    private View tabsContainer;
    private View txtHistoryTitle;
    private View txtJournalHistoryActivityPill;
    private View journalHistoryEmptyFrame;
    private View cameraPreviewCard;
    private View previewInnerFrame;
    private View detectionBox;
    private View scanResultContainer;
    private View btnSaveSelectedDish;
    private View tabIndicator;
    private View captureSaveOverlay;
    private View journalHistoryOverlay;
    private View journalHistoryBottomBar;
    private View btnJournalHistoryProfile;
    private View btnJournalHistoryFriends;
    private View btnJournalGrid;
    private View btnJournalMore;
    private View btnJournalHistoryShutter;
    private View btnJournalFriends;
    private View btnJournalHistoryInviteEmpty;
    private View journalPreviewFooterContainer;
    private View btnBack;
    private View btnFlash;
    private View btnGallery;
    private View btnShutter;
    private View btnFlipCamera;
    private View journalCameraPreviewInnerFrame;
    private View btnJournalCameraFlash;
    private View btnJournalCameraGallery;
    private View btnJournalCameraShutter;
    private View btnJournalCameraFlip;
    private View btnJournalCameraProfile;
    private View journalCameraFriendsPill;
    private View journalCameraTopBar;
    private View journalCameraControlRow;
    private View txtJournalCameraHistoryTitle;
    private View btnCaptureSaveCancel;
    private View btnCaptureSaveConfirm;
    private View btnPreviewDownload;
    private View journalPreviewTopBarInline;
    private View btnPreviewEffects;
    private ProgressBar captureSaveLoading;
    private TextView txtScanStatus;
    private TextView txtDetectedIngredientsEmpty;
    private TextView txtDishSuggestionsEmpty;
    private TextView txtSelectedDishHint;
    private TextView txtExtraIngredientsHint;
    private TextView txtCaptureUploadProgress;
    private TextView txtCaptureSaveError;
    private TextView txtTopCenterActionLabel;
    private TextView txtJournalCameraFriendsLabel;
    private TextView txtJournalHistoryFriendsCount;
    private TextView tabNhanDien;
    private TextView tabNhatKy;
    private TextView iconFlash;
    private TextView journalCameraIconFlash;
    private EditText edtCaptureCaption;
    private EditText edtManualExtraIngredient;
    private ImageView imgCapturePreview;
    private ImageView imgCapturedRecognition;
    private PreviewView recognitionPreviewView;
    private PreviewView journalCameraPreviewView;
    private PreviewView previewView;
    private ViewPager2 vpJournalHistory;
    private RecyclerView rvDishSuggestions;
    private ChipGroup groupDetectedIngredients;
    private ChipGroup groupSuggestedExtraIngredients;
    private ChipGroup groupSelectedExtraIngredients;
    private ChipGroup groupHealthFilters;

    private ActivityResultLauncher<String> galleryPickerLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private ActivityResultLauncher<String> storagePermissionLauncher;

    private Animation detectionPulseAnimation;
    private boolean isRecognitionMode = true;
    private boolean isFlashOn;
    private boolean isUsingFrontCamera;
    private boolean isCameraReady;
    private boolean isRecognitionInProgress;
    private boolean isCaptureInProgress;
    private long lastCaptureTimestamp;
    @NonNull
    private JournalCameraUiState currentJournalCameraUiState = JournalCameraUiState.LIVE_CAMERA;

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
    private final List<ScanDishItem> allSuggestedDishItems = new ArrayList<>();
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private ScanFoodJournalManager journalManager;
    private byte[] lastRecognitionImageBytes;
    private ScanDishItem selectedDishItem;
    private String selectedHealthFilter = ScanHealthFilters.FILTER_ALL;
    private ExecutorService recognitionExecutor;
    private BottomSheetDialog suggestionDialog;
    private JournalLocketPagerAdapter journalHistoryAdapter;
    private ListenerRegistration journalHistoryListener;
    private ListenerRegistration journalProfileListener;
    private JournalFeedItem currentJournalHistoryItem;
    private List<JournalFeedItem> journalHistoryItems = new ArrayList<>();
    private ScanDishSuggestionAdapter suggestionDialogAdapter;
    private ChipGroup suggestionIngredientGroup;
    private TextView txtSuggestionEmpty;
    @Nullable
    private PendingGallerySaveRequest pendingGallerySaveRequest;

    private static final class PendingGallerySaveRequest {
        final byte[] imageBytes;
        final String fileName;
        final ScanFoodJournalManager.ImageSaveCallback callback;

        PendingGallerySaveRequest(
                @NonNull byte[] imageBytes,
                @NonNull String fileName,
                @NonNull ScanFoodJournalManager.ImageSaveCallback callback) {
            this.imageBytes = imageBytes;
            this.fileName = fileName;
            this.callback = callback;
        }
    }

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
        friendInviteRepository.ensureFriendCodeIfMissing(FirebaseAuth.getInstance().getCurrentUser());
        refreshTopCenterFriendCode();
        setupRecognitionResultUi();
        setupJournalHistoryFeed();
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

                    @Override
                    public void saveJournalPreviewImageToGallery(
                            @NonNull byte[] imageBytes,
                            @NonNull String fileName,
                            @NonNull ScanFoodJournalManager.ImageSaveCallback callback) {
                        ScanFoodActivity.this.saveJournalPreviewImageToGallery(imageBytes, fileName, callback);
                    }
                },
                null,
                null,
                null,
                txtCaptureUploadProgress,
                txtCaptureSaveError,
                edtCaptureCaption,
                imgCapturePreview,
                journalPreviewFooterContainer,
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
        startJournalHistoryListener();
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
        refreshTopCenterFriendCode();
        refreshJournalFriendCode();
    }

    @Override
    protected void onPause() {
        stopDetectionPulse();
        super.onPause();
    }

    @Override
    protected void onStop() {
        stopJournalHistoryListener();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissSuggestionDialog();
        stopJournalHistoryListener();
        if (recognitionExecutor != null && !recognitionExecutor.isShutdown()) {
            recognitionExecutor.shutdownNow();
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
        }
    }

    private void dismissSuggestionDialog() {
        if (suggestionDialog != null && suggestionDialog.isShowing()) {
            suggestionDialog.dismiss();
        }
    }

    private void handleBackPressed() {
        if (isJournalHistoryVisible()) {
            hideJournalHistoryOverlay();
            return;
        }
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
        topCenterAction = findViewById(R.id.topCenterAction);
        footerContainer = findViewById(R.id.footerContainer);
        tabsContainer = findViewById(R.id.tabsContainer);
        txtJournalHistoryActivityPill = findViewById(R.id.txtJournalHistoryActivityPill);
        journalHistoryEmptyFrame = findViewById(R.id.journalHistoryEmptyFrame);
        cameraPreviewCard = findViewById(R.id.cameraPreviewCard);
        previewInnerFrame = findViewById(R.id.previewInnerFrame);
        detectionBox = findViewById(R.id.detectionBox);
        scanResultContainer = findViewById(R.id.scanResultContainer);
        btnSaveSelectedDish = findViewById(R.id.btnSaveSelectedDish);
        tabIndicator = findViewById(R.id.tabIndicator);
        captureSaveOverlay = findViewById(R.id.captureSaveOverlay);
        journalHistoryOverlay = findViewById(R.id.journalHistoryOverlay);
        journalHistoryBottomBar = findViewById(R.id.journalHistoryBottomBar);
        btnJournalHistoryProfile = findViewById(R.id.btnJournalHistoryProfile);
        btnJournalHistoryFriends = findViewById(R.id.btnJournalHistoryFriends);
        btnJournalGrid = findViewById(R.id.btnJournalGrid);
        btnJournalMore = findViewById(R.id.btnJournalMore);
        btnJournalHistoryShutter = findViewById(R.id.btnJournalHistoryShutter);
        btnJournalFriends = findViewById(R.id.btnJournalFriends);
        if (topCenterAction instanceof ViewGroup) {
            ViewGroup topCenterGroup = (ViewGroup) topCenterAction;
            if (topCenterGroup.getChildCount() > 1 && topCenterGroup.getChildAt(1) instanceof TextView) {
                txtTopCenterActionLabel = (TextView) topCenterGroup.getChildAt(1);
            }
        }
        journalPreviewFooterContainer = findViewById(R.id.journalPreviewFooterContainer);
        txtScanStatus = findViewById(R.id.txtScanStatus);
        txtDetectedIngredientsEmpty = findViewById(R.id.txtDetectedIngredientsEmpty);
        txtDishSuggestionsEmpty = findViewById(R.id.txtDishSuggestionsEmpty);
        txtSelectedDishHint = findViewById(R.id.txtSelectedDishHint);
        txtExtraIngredientsHint = findViewById(R.id.txtExtraIngredientsHint);
        txtCaptureUploadProgress = findViewById(R.id.txtPreviewUploadProgressLegacy);
        txtCaptureSaveError = findViewById(R.id.txtPreviewSaveErrorOld);
        txtJournalHistoryFriendsCount = findViewById(R.id.txtJournalHistoryFriendsCount);
        tabNhanDien = findViewById(R.id.tabNhanDien);
        tabNhatKy = findViewById(R.id.tabNhatKy);
        iconFlash = findViewById(R.id.iconFlash);
        btnBack = findViewById(R.id.btnBack);
        btnFlash = findViewById(R.id.btnFlash);
        btnGallery = findViewById(R.id.btnGallery);
        btnShutter = findViewById(R.id.btnShutter);
        btnFlipCamera = findViewById(R.id.btnFlipCamera);
        btnCaptureSaveCancel = findViewById(R.id.btnPreviewCancelOld);
        btnCaptureSaveConfirm = findViewById(R.id.btnPreviewSendOld);
        btnPreviewDownload = findViewById(R.id.btnPreviewDownloadInlineTop);
        journalPreviewTopBarInline = findViewById(R.id.journalPreviewTopBarInline);
        btnPreviewEffects = findViewById(R.id.btnPreviewEffectsOld);
        captureSaveLoading = findViewById(R.id.capturePreviewLoadingOldInline);
        edtCaptureCaption = findViewById(R.id.edtJournalPreviewCaptionOld);
        edtManualExtraIngredient = findViewById(R.id.edtManualExtraIngredient);
        imgCapturePreview = findViewById(R.id.imgJournalPreviewInlineOld);
        imgCapturedRecognition = findViewById(R.id.imgCapturedRecognition);
        recognitionPreviewView = findViewById(R.id.previewView);
        vpJournalHistory = findViewById(R.id.vpJournalHistory);
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
        if (edtManualExtraIngredient != null) {
            edtManualExtraIngredient.setVisibility(View.GONE);
        }
        if (groupSuggestedExtraIngredients != null) {
            groupSuggestedExtraIngredients.setVisibility(View.GONE);
        }
        if (groupSelectedExtraIngredients != null) {
            groupSelectedExtraIngredients.setVisibility(View.GONE);
        }
        if (txtExtraIngredientsHint != null) {
            txtExtraIngredientsHint.setVisibility(View.GONE);
        }
    }

    private void setupJournalHistoryFeed() {
        if (vpJournalHistory != null) {
            journalHistoryAdapter = new JournalLocketPagerAdapter(new JournalLocketPagerAdapter.Listener() {
                @Override
                public void onHistoryPageVisible(@Nullable JournalFeedItem item) {
                    currentJournalHistoryItem = item;
                }

                @Override
                public void onCameraPageBound(
                        @NonNull View pageRoot,
                        @NonNull PreviewView pagePreviewView,
                        @NonNull View pagePreviewInnerFrame,
                        @NonNull View pageFlashButton,
                        @NonNull TextView pageFlashIcon,
                        @NonNull View pageGalleryButton,
                        @NonNull View pageShutterButton,
                        @NonNull View pageFlipButton,
                        @NonNull View pageControlRow,
                        @NonNull View pageHistoryTitle) {
                    bindJournalCameraPageViews(
                            pageRoot,
                            pagePreviewView,
                            pagePreviewInnerFrame,
                            pageFlashButton,
                            pageFlashIcon,
                            pageGalleryButton,
                            pageShutterButton,
                            pageFlipButton,
                            pageControlRow,
                            pageHistoryTitle);
                }
            });
            vpJournalHistory.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
            vpJournalHistory.setOffscreenPageLimit(2);
            vpJournalHistory.setAdapter(journalHistoryAdapter);
            vpJournalHistory.setPageTransformer((page, position) -> {
                page.setAlpha(1f);
                View historyCard = page.findViewById(R.id.journalHistoryCard);
                if (historyCard != null) {
                    float lift = Math.max(0f, position);
                    historyCard.setTranslationY(dp(18f) * lift);
                    historyCard.setScaleX(1f - (0.03f * Math.abs(position)));
                    historyCard.setScaleY(1f - (0.015f * Math.abs(position)));
                }
            });
            vpJournalHistory.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                    super.onPageScrolled(position, positionOffset, positionOffsetPixels);
                    renderJournalPagerChrome(position > 0 || positionOffset > 0.02f, position > 0 ? 1f : positionOffset);
                }

                @Override
                public void onPageSelected(int position) {
                    super.onPageSelected(position);
                    currentJournalHistoryItem = journalHistoryAdapter == null
                            ? null
                            : journalHistoryAdapter.getHistoryItemForPage(position);
                    renderJournalPagerState(position);
                }
            });
            vpJournalHistory.post(() -> {
                configureJournalHistoryPagerRecycler();
            });
        }
        renderJournalPagerState(0);
    }

    private void bindJournalCameraPageViews(
            @NonNull View pageTopBar,
            @NonNull PreviewView pagePreviewView,
            @NonNull View pagePreviewInnerFrame,
            @NonNull View pageFlashButton,
            @NonNull TextView pageFlashIcon,
            @NonNull View pageGalleryButton,
            @NonNull View pageShutterButton,
            @NonNull View pageFlipButton,
            @NonNull View pageControlRow,
            @NonNull View pageHistoryTitle) {
        journalCameraTopBar = pageTopBar;
        journalCameraPreviewView = pagePreviewView;
        journalCameraPreviewInnerFrame = pagePreviewInnerFrame;
        btnJournalCameraFlash = pageFlashButton;
        journalCameraIconFlash = pageFlashIcon;
        btnJournalCameraGallery = pageGalleryButton;
        btnJournalCameraShutter = pageShutterButton;
        btnJournalCameraFlip = pageFlipButton;
        btnJournalCameraProfile = pageTopBar.findViewById(R.id.btnJournalCameraProfile);
        journalCameraFriendsPill = pageTopBar.findViewById(R.id.journalCameraFriendsPill);
        txtJournalCameraFriendsLabel = resolveJournalCameraFriendsLabel(pageTopBar);
        journalCameraControlRow = pageControlRow;
        txtJournalCameraHistoryTitle = pageHistoryTitle;

        if (btnJournalCameraProfile != null) {
            btnJournalCameraProfile.setOnClickListener(v -> switchMode(true));
        }
        if (journalCameraFriendsPill != null) {
            journalCameraFriendsPill.setOnClickListener(v -> openFriendInviteFromHeader());
        }
        btnJournalCameraFlash.setOnClickListener(v -> toggleFlash());
        btnJournalCameraGallery.setOnClickListener(v -> openGalleryPicker());
        btnJournalCameraShutter.setOnClickListener(v -> performShutterAction());
        btnJournalCameraFlip.setOnClickListener(v -> toggleCameraLens());
        applyPressScale(
                btnJournalCameraProfile,
                journalCameraFriendsPill,
                btnJournalCameraFlash,
                btnJournalCameraGallery,
                btnJournalCameraShutter,
                btnJournalCameraFlip);
        refreshJournalFriendCode();
        updateFlashUi(false);
        setTorchButtonsEnabled(camera != null && camera.getCameraInfo() != null && camera.getCameraInfo().hasFlashUnit());
        if (!isRecognitionMode) {
            updateActivePreviewView();
            startCameraIfNeeded();
        }
        applyJournalCameraUiState();
    }

    private void configureJournalHistoryPagerRecycler() {
        if (vpJournalHistory == null || vpJournalHistory.getChildCount() == 0) {
            return;
        }
        View child = vpJournalHistory.getChildAt(0);
        if (!(child instanceof RecyclerView)) {
            return;
        }
        RecyclerView pagerRecycler = (RecyclerView) child;
        pagerRecycler.setClipToPadding(false);
        pagerRecycler.setClipChildren(false);
        pagerRecycler.setOverScrollMode(View.OVER_SCROLL_NEVER);
    }

    private void renderJournalPagerState(int pagePosition) {
        if (currentJournalCameraUiState == JournalCameraUiState.CAPTURE_PREVIEW) {
            renderJournalPagerChrome(false, 0f);
            applyJournalCameraUiState();
            return;
        }
        boolean showingHistoryPage = pagePosition > 0;
        renderJournalPagerChrome(showingHistoryPage, showingHistoryPage ? 1f : 0f);
        if (journalHistoryEmptyFrame != null) {
            journalHistoryEmptyFrame.setVisibility(View.GONE);
        }
        if (txtScanStatus != null) {
            txtScanStatus.setVisibility(View.GONE);
        }
        if (previewView != null) {
            previewView.setVisibility(View.VISIBLE);
        }
        if (previewInnerFrame != null) {
            previewInnerFrame.setVisibility(View.VISIBLE);
        }
        if (btnFlash != null) {
            btnFlash.setVisibility(showingHistoryPage ? View.GONE : View.VISIBLE);
        }
        if (btnJournalCameraFlash != null) {
            btnJournalCameraFlash.setVisibility(showingHistoryPage ? View.GONE : View.VISIBLE);
        }
        if (btnJournalMore != null) {
            btnJournalMore.setAlpha(showingHistoryPage ? 1f : 0.55f);
        }
        applyJournalCameraUiState();
    }

    private void renderJournalPagerChrome(boolean visible, float alpha) {
        float boundedAlpha = Math.max(0f, Math.min(1f, alpha));
        View journalHistoryTopBar = findViewById(R.id.journalHistoryTopBar);
        if (journalHistoryTopBar != null) {
            journalHistoryTopBar.setVisibility(visible ? View.VISIBLE : View.GONE);
            journalHistoryTopBar.setAlpha(boundedAlpha);
        }
        if (journalHistoryBottomBar != null) {
            journalHistoryBottomBar.setVisibility(visible ? View.VISIBLE : View.GONE);
            journalHistoryBottomBar.setAlpha(boundedAlpha);
        }
    }

    private void startJournalHistoryListener() {
        if (journalFeedRepository == null) {
            return;
        }
        stopJournalHistoryListener();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (journalHistoryAdapter != null) {
                journalHistoryAdapter.submitItems(new ArrayList<>());
            }
            journalHistoryItems = new ArrayList<>();
            currentJournalHistoryItem = null;
            updateJournalFriendCount(0L);
            updateJournalHistoryEmptyState(true);
            return;
        }
        journalHistoryListener = journalFeedRepository.listenToFeed(user.getUid(), 60, new JournalFeedRepository.FeedCallback() {
            @Override
            public void onItems(@NonNull List<JournalFeedItem> items) {
                journalHistoryItems = new ArrayList<>(items);
                if (journalHistoryAdapter != null) {
                    journalHistoryAdapter.submitItems(items);
                }
                int currentPosition = vpJournalHistory == null ? 0 : vpJournalHistory.getCurrentItem();
                int maxPage = items.isEmpty() ? 0 : items.size();
                int boundedPosition = Math.min(currentPosition, maxPage);
                currentJournalHistoryItem = journalHistoryAdapter == null
                        ? null
                        : journalHistoryAdapter.getHistoryItemForPage(boundedPosition);
                if (vpJournalHistory != null && vpJournalHistory.getCurrentItem() != boundedPosition) {
                    vpJournalHistory.setCurrentItem(boundedPosition, false);
                }
                renderJournalPagerState(boundedPosition);
                updateJournalHistoryEmptyState(items.isEmpty());
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (journalHistoryAdapter != null) {
                    journalHistoryAdapter.submitItems(new ArrayList<>());
                }
                journalHistoryItems = new ArrayList<>();
                currentJournalHistoryItem = null;
                renderJournalPagerState(0);
                updateJournalHistoryEmptyState(true);
            }
        });

        journalProfileListener = journalFeedRepository.listenToUserProfile(user, new JournalFeedRepository.UserProfileCallback() {
            @Override
            public void onProfile(@NonNull JournalFeedRepository.UserProfile profile) {
                updateJournalFriendCount(profile.friendCount);
            }

            @Override
            public void onError(@NonNull Exception error) {
                updateJournalFriendCount(0L);
            }
        });
    }

    private void stopJournalHistoryListener() {
        if (journalHistoryListener != null) {
            journalHistoryListener.remove();
            journalHistoryListener = null;
        }
        if (journalProfileListener != null) {
            journalProfileListener.remove();
            journalProfileListener = null;
        }
    }

    private void updateJournalFriendCount(long friendCount) {
        if (txtJournalHistoryFriendsCount == null) {
            return;
        }
        txtJournalHistoryFriendsCount.setText(friendCount + " Bạn bè");
    }

    private void updateJournalHistoryEmptyState(boolean empty) {
        if (vpJournalHistory != null) {
            vpJournalHistory.setVisibility((!isRecognitionMode && !empty) ? View.VISIBLE : View.GONE);
        }
        if (journalHistoryEmptyFrame != null) {
            journalHistoryEmptyFrame.setVisibility((empty && !isRecognitionMode) ? View.VISIBLE : View.GONE);
        }
        if (txtJournalHistoryActivityPill != null) {
            txtJournalHistoryActivityPill.setVisibility(View.GONE);
        }
    }

    private boolean isJournalHistoryVisible() {
        return !isRecognitionMode
                && journalHistoryOverlay != null
                && journalHistoryOverlay.getVisibility() == View.VISIBLE
                && vpJournalHistory != null
                && vpJournalHistory.getCurrentItem() > 0;
    }

    private void renderJournalHistoryMeta(@Nullable JournalFeedItem item) {
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
        final View journalHistoryTopBar = findViewById(R.id.journalHistoryTopBar);
        final ViewGroup.MarginLayoutParams historyTopParams = journalHistoryTopBar == null
                ? null
                : (ViewGroup.MarginLayoutParams) journalHistoryTopBar.getLayoutParams();
        final int historyTopMargin = historyTopParams == null ? 0 : historyTopParams.topMargin;
        final ViewGroup.MarginLayoutParams historyBottomParams = journalHistoryBottomBar == null
                ? null
                : (ViewGroup.MarginLayoutParams) journalHistoryBottomBar.getLayoutParams();
        final int historyBottomMargin = historyBottomParams == null ? 0 : historyBottomParams.bottomMargin;

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

            if (journalHistoryTopBar != null && historyTopParams != null) {
                historyTopParams.topMargin = historyTopMargin + systemBars.top;
                journalHistoryTopBar.setLayoutParams(historyTopParams);
            }

            if (journalHistoryBottomBar != null && historyBottomParams != null) {
                historyBottomParams.bottomMargin = historyBottomMargin + systemBars.bottom;
                journalHistoryBottomBar.setLayoutParams(historyBottomParams);
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
                                ? "Sẵn sàng quét món ăn"
                                : "");
                        startCameraIfNeeded();
                    } else {
                        isCameraReady = false;
                        updateScanStatus("Bạn cần cấp quyền Camera để dùng tính năng này");
                        updateEntryStatus("Bạn cần cấp quyền Camera để lưu nhật ký.");
                        Toast.makeText(this, "Vui lòng cấp quyền camera", Toast.LENGTH_SHORT).show();
                    }
                });
        storagePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    PendingGallerySaveRequest request = pendingGallerySaveRequest;
                    pendingGallerySaveRequest = null;
                    if (request == null) {
                        return;
                    }
                    if (isGranted) {
                        persistJournalPreviewImageToGallery(
                                request.imageBytes,
                                request.fileName,
                                request.callback);
                    } else {
                        request.callback.onError("KhÃ´ng cÃ³ quyá»n lÆ°u áº£nh");
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
        previewView = !isRecognitionMode && journalCameraPreviewView != null
                ? journalCameraPreviewView
                : recognitionPreviewView;
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
                    ? "Sẵn sàng quét món ăn"
                    : "");
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
                btnCaptureSaveConfirm,
                btnJournalHistoryProfile,
                btnJournalHistoryFriends,
                btnJournalGrid,
                btnJournalMore,
                btnJournalHistoryShutter);
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
        if (!isRecognitionMode && vpJournalHistory != null) {
            vpJournalHistory.setCurrentItem(0, false);
        }
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
        if (isRecognitionMode) {
            hideJournalHistoryOverlay();
        } else if (journalHistoryOverlay != null) {
            journalHistoryOverlay.setVisibility(View.VISIBLE);
        }
        updateJournalBottomControls();

        if (recognitionSurface != null) {
            recognitionSurface.setVisibility(View.VISIBLE);
        }

        if (cameraPreviewCard != null) {
            cameraPreviewCard.setAlpha(1f);
        }
        if (previewInnerFrame != null) {
            previewInnerFrame.setAlpha(isRecognitionMode ? 1f : 0.92f);
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
        renderJournalCameraState();
        if (!isRecognitionMode) {
            if (vpJournalHistory != null) {
                vpJournalHistory.setVisibility(View.VISIBLE);
            }
            renderJournalPagerState(vpJournalHistory == null ? 0 : vpJournalHistory.getCurrentItem());
        }
    }

    private void updateJournalBottomControls() {
        ImageView galleryButton = findViewById(R.id.btnGallery);
        TextView flipButton = findViewById(R.id.btnFlipCamera);
        if (galleryButton != null) {
            int journalPadding = 0;
            int recognitionPadding = Math.round(dp(6f));
            galleryButton.setImageResource(isRecognitionMode ? R.drawable.ic_camera_gallery : R.drawable.ic_journal_grid);
            galleryButton.setPadding(
                    isRecognitionMode ? recognitionPadding : journalPadding,
                    isRecognitionMode ? recognitionPadding : journalPadding,
                    isRecognitionMode ? recognitionPadding : journalPadding,
                    isRecognitionMode ? recognitionPadding : journalPadding);
            galleryButton.setVisibility(View.VISIBLE);
            galleryButton.setEnabled(true);
        }
        if (flipButton != null) {
            flipButton.setText(isRecognitionMode ? "flip_camera_ios" : "more_horiz");
            flipButton.setTextSize(isRecognitionMode ? 30f : 28f);
        }
    }

    private void renderJournalCameraState() {
        if (tabsContainer != null) {
            tabsContainer.setVisibility(isRecognitionMode ? View.VISIBLE : View.GONE);
        }
        if (txtHistoryTitle != null) {
            txtHistoryTitle.setVisibility(isRecognitionMode ? View.VISIBLE : View.GONE);
        }
        if (txtJournalHistoryActivityPill != null) {
            txtJournalHistoryActivityPill.setVisibility(View.GONE);
        }
        if (txtScanStatus != null) {
            txtScanStatus.setVisibility(isRecognitionMode ? View.VISIBLE : View.GONE);
            if (isRecognitionMode) {
                txtScanStatus.setTextColor(Color.parseColor("#D4C5AB"));
                txtScanStatus.setTextSize(13f);
            }
        }
        if (topBar != null) {
            topBar.setVisibility(isRecognitionMode ? View.VISIBLE : View.GONE);
        }
        if (footerContainer != null) {
            footerContainer.setVisibility(isRecognitionMode ? View.VISIBLE : View.GONE);
        }
        if (cameraPreviewCard != null) {
            cameraPreviewCard.setVisibility(isRecognitionMode ? View.VISIBLE : View.GONE);
        }
        if (journalHistoryOverlay != null) {
            journalHistoryOverlay.setVisibility(isRecognitionMode ? View.GONE : View.VISIBLE);
        }
    }

    private void setJournalPreviewUiVisible(boolean visible) {
        currentJournalCameraUiState = visible
                ? JournalCameraUiState.CAPTURE_PREVIEW
                : JournalCameraUiState.LIVE_CAMERA;
        updateCameraPreviewConstraintsForState(visible);
        if (captureSaveOverlay != null) {
            captureSaveOverlay.setVisibility(View.GONE);
        }
        if (journalPreviewFooterContainer != null) {
            journalPreviewFooterContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (journalPreviewTopBarInline != null) {
            journalPreviewTopBarInline.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (imgCapturePreview != null) {
            imgCapturePreview.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (edtCaptureCaption != null) {
            edtCaptureCaption.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (cameraPreviewCard != null && !isRecognitionMode) {
            cameraPreviewCard.setVisibility(View.VISIBLE);
        }
        if (vpJournalHistory != null) {
            vpJournalHistory.setUserInputEnabled(!visible);
            if (visible && vpJournalHistory.getCurrentItem() != 0) {
                vpJournalHistory.setCurrentItem(0, false);
            }
        }
        applyJournalCameraUiState();
        if (!visible) {
            updateUiForMode(false);
        }
    }

    private void applyJournalCameraUiState() {
        boolean previewVisible = currentJournalCameraUiState == JournalCameraUiState.CAPTURE_PREVIEW;
        if (isRecognitionMode) {
            return;
        }
        if (captureSaveOverlay != null) {
            captureSaveOverlay.setVisibility(View.GONE);
        }
        if (topBar != null) {
            topBar.setVisibility(View.GONE);
        }
        if (footerContainer != null) {
            footerContainer.setVisibility(View.GONE);
        }
        if (journalHistoryOverlay != null) {
            journalHistoryOverlay.setVisibility(View.VISIBLE);
        }
        if (journalCameraTopBar != null) {
            journalCameraTopBar.setVisibility(previewVisible ? View.GONE : View.VISIBLE);
        }
        if (journalCameraControlRow != null) {
            journalCameraControlRow.setVisibility(previewVisible ? View.GONE : View.VISIBLE);
        }
        if (txtJournalCameraHistoryTitle != null) {
            txtJournalCameraHistoryTitle.setVisibility(previewVisible ? View.GONE : View.VISIBLE);
        }
        if (btnJournalCameraFlash != null) {
            btnJournalCameraFlash.setVisibility(previewVisible ? View.GONE : View.VISIBLE);
        }
        if (btnJournalCameraGallery != null) {
            btnJournalCameraGallery.setVisibility(previewVisible ? View.GONE : View.VISIBLE);
        }
        if (btnJournalCameraShutter != null) {
            btnJournalCameraShutter.setVisibility(previewVisible ? View.GONE : View.VISIBLE);
        }
        if (btnJournalCameraFlip != null) {
            btnJournalCameraFlip.setVisibility(previewVisible ? View.GONE : View.VISIBLE);
        }
        if (journalHistoryBottomBar != null) {
            journalHistoryBottomBar.setVisibility(previewVisible ? View.GONE : journalHistoryBottomBar.getVisibility());
            if (previewVisible) {
                journalHistoryBottomBar.setAlpha(0f);
            }
        }
        View journalHistoryTopBar = findViewById(R.id.journalHistoryTopBar);
        if (journalHistoryTopBar != null) {
            journalHistoryTopBar.setVisibility(previewVisible ? View.GONE : journalHistoryTopBar.getVisibility());
            if (previewVisible) {
                journalHistoryTopBar.setAlpha(0f);
            }
        }
        if (previewView != null) {
            previewView.setVisibility(previewVisible ? View.GONE : View.VISIBLE);
        }
        if (previewInnerFrame != null) {
            previewInnerFrame.setVisibility(previewVisible ? View.GONE : View.VISIBLE);
        }
        if (detectionBox != null) {
            detectionBox.setVisibility(previewVisible ? View.GONE : View.INVISIBLE);
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

        if (!isRecognitionMode && !previewVisible) {
            int topMargin = getResources().getDimensionPixelSize(R.dimen.journal_history_card_margin_top);
            constraintSet.connect(R.id.cameraPreviewCard, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, topMargin);
            constraintSet.connect(R.id.cameraPreviewCard, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 0);
            constraintSet.setVerticalBias(R.id.cameraPreviewCard, 0f);
            constraintSet.applyTo(rootLayout);
            return;
        }

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
        if (journalCameraIconFlash != null) {
            journalCameraIconFlash.setText(isFlashOn ? "flash_on" : "flash_off");
            journalCameraIconFlash.setTextColor(Color.WHITE);
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
        if (btnJournalCameraFlash != null) {
            btnJournalCameraFlash.setEnabled(enabled);
            btnJournalCameraFlash.setAlpha(enabled ? 1f : 0.5f);
        }
    }

    private void setupClickListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> navigateBackHome());
        }
        if (topCenterAction != null) {
            topCenterAction.setOnClickListener(v -> openFriendInviteFromHeader());
        }
        if (btnFlash != null) {
            btnFlash.setOnClickListener(v -> toggleFlash());
        }
        if (btnGallery != null) {
            btnGallery.setOnClickListener(v -> {
                if (isRecognitionMode) {
                    openGalleryPicker();
                    return;
                }
                startActivity(JournalHistoryGridActivity.createIntent(this));
            });
        }
        if (btnShutter != null) {
            btnShutter.setOnClickListener(v -> {
                if (!isRecognitionMode && isJournalHistoryVisible()) {
                    hideJournalHistoryOverlay();
                    return;
                }
                performShutterAction();
            });
        }
        if (btnFlipCamera != null) {
            btnFlipCamera.setOnClickListener(v -> {
                if (isRecognitionMode) {
                    toggleCameraLens();
                    return;
                }
                showJournalHistoryActions();
            });
        }
        if (btnJournalGrid != null) {
            btnJournalGrid.setOnClickListener(v -> startActivity(JournalHistoryGridActivity.createIntent(this)));
        }
        if (btnJournalFriends != null) {
            btnJournalFriends.setOnClickListener(v -> openFriendInviteFromHeader());
        }
        if (btnJournalHistoryShutter != null) {
            btnJournalHistoryShutter.setOnClickListener(v -> {
                if (isRecognitionMode) {
                    return;
                }
                if (vpJournalHistory != null && vpJournalHistory.getCurrentItem() > 0) {
                    vpJournalHistory.setCurrentItem(0, true);
                    return;
                }
                performShutterAction();
            });
        }
        if (btnJournalMore != null) {
            btnJournalMore.setOnClickListener(v -> {
                if (!isRecognitionMode && vpJournalHistory != null && vpJournalHistory.getCurrentItem() > 0) {
                    showJournalHistoryActions();
                }
            });
        }
        if (btnJournalHistoryFriends != null) {
            btnJournalHistoryFriends.setOnClickListener(v -> {
                if (!isRecognitionMode && journalManager != null) {
                    journalManager.openFriendInviteSheet();
                }
            });
        }
        if (btnJournalHistoryProfile != null) {
            btnJournalHistoryProfile.setOnClickListener(v -> hideJournalHistoryOverlay());
        }
        if (btnJournalHistoryInviteEmpty != null) {
            btnJournalHistoryInviteEmpty.setOnClickListener(v -> {
                if (journalManager != null) {
                    journalManager.openFriendInviteSheet();
                }
            });
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
        if (btnPreviewDownload != null) {
            btnPreviewDownload.setOnClickListener(v -> {
                if (journalManager != null) {
                    journalManager.downloadPendingJournalImage(v);
                }
            });
        }
        View btnAddManualExtraIngredient = findViewById(R.id.btnAddManualExtraIngredient);
        if (btnAddManualExtraIngredient != null) {
            btnAddManualExtraIngredient.setVisibility(View.GONE);
        }
    }

    private void showJournalHistoryOverlay() {
        if (isRecognitionMode || currentJournalCameraUiState == JournalCameraUiState.CAPTURE_PREVIEW) {
            return;
        }
        if (journalHistoryOverlay != null) {
            journalHistoryOverlay.setVisibility(View.VISIBLE);
        }
        if (vpJournalHistory != null) {
            vpJournalHistory.setVisibility(View.VISIBLE);
            vpJournalHistory.setCurrentItem(journalHistoryItems.isEmpty() ? 0 : 1, true);
        }
        currentJournalHistoryItem = journalHistoryItems.isEmpty() ? null : journalHistoryItems.get(0);
        renderJournalPagerState(vpJournalHistory == null ? 0 : vpJournalHistory.getCurrentItem());
    }

    private void openFriendInviteFromHeader() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            FriendInviteActivity.savePendingInvite(this, FriendInviteActivity.PENDING_OPEN_SELF_TOKEN);
            startActivity(AuthActivity.createIntent(this, AuthActivity.MODE_LOGIN));
            return;
        }
        startActivity(new Intent(this, FriendInviteActivity.class));
        overridePendingTransition(R.anim.dialog_bounce_in, 0);
    }

    private void refreshTopCenterFriendCode() {
        if (txtTopCenterActionLabel == null || friendInviteRepository == null) {
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            txtTopCenterActionLabel.setText("Đăng nhập");
            return;
        }
        txtTopCenterActionLabel.setText("Đang lấy mã...");
        friendInviteRepository.ensureFriendCode(user, new FriendInviteRepository.EnsureFriendCodeCallback() {
            @Override
            public void onSuccess(@NonNull String friendCode) {
                runOnUiThread(() -> txtTopCenterActionLabel.setText(friendCode));
            }

            @Override
            public void onError(@NonNull String message) {
                runOnUiThread(() -> txtTopCenterActionLabel.setText("Mã kết bạn"));
            }
        });
    }

    @Nullable
    private TextView resolveJournalCameraFriendsLabel(@NonNull View root) {
        View friendsPill = root.findViewById(R.id.journalCameraFriendsPill);
        if (!(friendsPill instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) friendsPill;
        if (group.getChildCount() > 1 && group.getChildAt(1) instanceof TextView) {
            return (TextView) group.getChildAt(1);
        }
        return null;
    }

    private void refreshJournalFriendCode() {
        if (txtJournalCameraFriendsLabel == null || friendInviteRepository == null) {
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            txtJournalCameraFriendsLabel.setText("Đăng nhập");
            return;
        }
        txtJournalCameraFriendsLabel.setText("Đang lấy mã...");
        friendInviteRepository.ensureFriendCode(user, new FriendInviteRepository.EnsureFriendCodeCallback() {
            @Override
            public void onSuccess(@NonNull String friendCode) {
                runOnUiThread(() -> txtJournalCameraFriendsLabel.setText(friendCode));
            }

            @Override
            public void onError(@NonNull String message) {
                runOnUiThread(() -> txtJournalCameraFriendsLabel.setText("Mã kết bạn"));
            }
        });
    }

    private void hideJournalHistoryOverlay() {
        if (currentJournalCameraUiState == JournalCameraUiState.CAPTURE_PREVIEW) {
            return;
        }
        if (vpJournalHistory != null) {
            vpJournalHistory.setCurrentItem(0, true);
        }
        renderJournalPagerState(0);
    }

    private void showJournalHistoryActions() {
        JournalFeedItem item = currentJournalHistoryItem;
        if (item == null) {
            Toast.makeText(this, "Chưa có ảnh nhật ký", Toast.LENGTH_SHORT).show();
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = getLayoutInflater().inflate(R.layout.bottom_sheet_journal_history_actions, null, false);
        dialog.setContentView(sheet);

        View btnShare = sheet.findViewById(R.id.btnJournalShare);
        View btnDownload = sheet.findViewById(R.id.btnJournalDownload);
        TextView btnDelete = sheet.findViewById(R.id.btnJournalDelete);
        View btnCancel = sheet.findViewById(R.id.btnJournalCancel);

        if (btnShare != null) {
            btnShare.setOnClickListener(v -> {
                dialog.dismiss();
                shareJournalHistoryItem(item);
            });
        }
        if (btnDownload != null) {
            btnDownload.setOnClickListener(v -> {
                dialog.dismiss();
                downloadJournalHistoryItem(item);
            });
        }
        if (btnDelete != null) {
            if (!item.isOwnMoment()) {
                btnDelete.setVisibility(View.GONE);
            } else {
                btnDelete.setOnClickListener(v -> {
                    dialog.dismiss();
                    confirmDeleteJournalHistoryItem(item);
                });
            }
        }
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    private void shareJournalHistoryItem(@NonNull JournalFeedItem item) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, item.getImageUrl());
        startActivity(Intent.createChooser(intent, "Chia sẻ nhật ký"));
    }

    private void downloadJournalHistoryItem(@NonNull JournalFeedItem item) {
        String imageUrl = item.getImageUrl();
        if (TextUtils.isEmpty(imageUrl)) {
            Toast.makeText(this, "Không có ảnh để tải", Toast.LENGTH_SHORT).show();
            return;
        }
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(imageUrl));
        request.setTitle("CoolCook journal");
        request.setDescription("Đang tải ảnh nhật ký");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "coolcook-journal-" + item.getMomentId() + ".jpg");
        DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager != null) {
            downloadManager.enqueue(request);
            Toast.makeText(this, "Đang tải ảnh về Downloads", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDeleteJournalHistoryItem(@NonNull JournalFeedItem item) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Xóa nhật ký?")
                .setMessage("Ảnh này sẽ bị xóa khỏi feed Nhật ký.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) -> deleteJournalHistoryItem(item))
                .show();
    }

    private void deleteJournalHistoryItem(@NonNull JournalFeedItem item) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !item.isOwnMoment()) {
            Toast.makeText(this, "Chỉ có thể xóa ảnh của bạn", Toast.LENGTH_SHORT).show();
            return;
        }
        journalFeedRepository.deleteOwnMoment(user.getUid(), item.getMomentId(), new JournalFeedRepository.DeleteCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> Toast.makeText(ScanFoodActivity.this, "Đã xóa nhật ký", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(@NonNull Exception error) {
                runOnUiThread(() -> Toast.makeText(ScanFoodActivity.this, "Không xóa được nhật ký", Toast.LENGTH_SHORT).show());
            }
        });
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

        if (!isRecognitionMode && currentJournalCameraUiState == JournalCameraUiState.CAPTURE_PREVIEW) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastCaptureTimestamp < CAPTURE_DEBOUNCE_MS) {
            return;
        }
        lastCaptureTimestamp = now;
        isCaptureInProgress = true;
        setProcessingUiEnabled(false);

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
                                isCaptureInProgress = false;
                                setProcessingUiEnabled(true);
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
                            runOnUiThread(() -> {
                                isCaptureInProgress = false;
                                processImageBytesForRecognition(normalizedBytes, "image/jpeg", "camera");
                            });
                        } else {
                            runOnUiThread(() -> {
                                isCaptureInProgress = false;
                                setProcessingUiEnabled(true);
                                processImageBytesForEntry(normalizedBytes, "camera");
                            });
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        super.onError(exception);
                        runOnUiThread(() -> {
                            isCaptureInProgress = false;
                            setProcessingUiEnabled(true);
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
        boolean showingJournalCameraPage = !isRecognitionMode
                && vpJournalHistory != null
                && vpJournalHistory.getCurrentItem() == 0;
        View activeShutter = isRecognitionMode
                ? btnShutter
                : (showingJournalCameraPage ? btnJournalCameraShutter : btnJournalHistoryShutter);
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
                    "Ch??a nh???n di???n ???????c th???c ph???m, h??y th??? ch???p r?? h??n"));
            return;
        }

        List<String> uncoveredIngredients = scanFoodLocalMatcher.findUncoveredIngredients(effectiveIngredients);
        final boolean forceAiForUnknownIngredients = !uncoveredIngredients.isEmpty();
        boolean hasCompleteLocalCoverage = !forceAiForUnknownIngredients;
        List<ScanDishItem> resolvedLocalSuggestions = scanFoodLocalMatcher.suggestDishes(
                effectiveIngredients,
                SUGGESTION_LIMIT,
                selectedHealthFilter);
        List<ScanDishItem> relaxedLocalSuggestions = scanFoodLocalMatcher.suggestDishesRelaxed(
                effectiveIngredients,
                SUGGESTION_LIMIT,
                selectedHealthFilter);

        if (hasCompleteLocalCoverage && resolvedLocalSuggestions.isEmpty()) {
            resolvedLocalSuggestions = new ArrayList<>(relaxedLocalSuggestions);
        }
        final List<ScanDishItem> localSuggestions = resolvedLocalSuggestions;
        final List<ScanDishItem> fallbackLocalSuggestions = relaxedLocalSuggestions;
        final boolean hasLocalCombinationMatch = hasLocalCombinationMatch(effectiveIngredients, localSuggestions);

        if (hasCompleteLocalCoverage && hasLocalCombinationMatch && !localSuggestions.isEmpty()) {
            runOnUiThread(() -> finishRecognitionSuccess(localSuggestions, sourceLabel));
            return;
        }

        geminiRepository.requestStructuredResponse(
                DISH_SUGGESTION_SYSTEM_PROMPT,
                buildDishSuggestionPrompt(effectiveIngredients, localSuggestions, uncoveredIngredients),
                null,
                null,
                new GeminiRepository.StreamCallback() {
                    @Override
                    public void onStart() {
                        runOnUiThread(() -> updateScanStatus("??ang g???i ?? m??n ??n..."));
                    }

                    @Override
                    public void onChunk(@NonNull String accumulatedText) {
                        runOnUiThread(() -> updateScanStatus("??ang g???i ?? m??n ??n..."));
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
                                    parseSuggestedDishes(finalText),
                                    forceAiForUnknownIngredients);
                            runOnUiThread(() -> {
                                if (combinedDishItems.isEmpty() && !fallbackLocalSuggestions.isEmpty()) {
                                    finishRecognitionSuccess(fallbackLocalSuggestions, sourceLabel);
                                    return;
                                }
                                if (combinedDishItems.isEmpty()) {
                                    requestFallbackAiDishSuggestions(effectiveIngredients, sourceLabel);
                                    return;
                                }
                                if (hasMissingAiRecipes(combinedDishItems)) {
                                    requestRecipesForAiDishes(effectiveIngredients, combinedDishItems, sourceLabel);
                                } else {
                                    finishRecognitionSuccess(combinedDishItems, sourceLabel);
                                }
                            });
                        });
                    }

                    @Override
                    public void onError(@NonNull String friendlyError) {
                        runOnUiThread(() -> {
                            if (!localSuggestions.isEmpty()) {
                                finishRecognitionSuccess(localSuggestions, sourceLabel);
                                return;
                            }
                            if (!fallbackLocalSuggestions.isEmpty()) {
                                finishRecognitionSuccess(fallbackLocalSuggestions, sourceLabel);
                                return;
                            }
                            requestFallbackAiDishSuggestions(effectiveIngredients, sourceLabel);
                        });
                    }
                });
    }

    private void requestDishSuggestions(@NonNull String sourceLabel) {
        requestDishSuggestions(new ArrayList<>(currentDetectedIngredients), sourceLabel);
    }

    @NonNull
    private List<DetectedIngredient> buildEffectiveIngredients(@NonNull List<DetectedIngredient> detectedIngredients) {
        return new ArrayList<>(detectedIngredients);
    }

    private boolean hasMissingAiRecipes(@NonNull List<ScanDishItem> dishItems) {
        for (ScanDishItem item : dishItems) {
            if (!item.isLocal() && item.getRecipe().trim().isEmpty()) {
                return true;
            }
        }
        return false;
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
            @NonNull List<SuggestedDish> aiSuggestions,
            boolean forceAiForUnknownIngredients) {
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
        if (items.isEmpty() && !localSuggestions.isEmpty() && !forceAiForUnknownIngredients) {
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
        if (availableCount >= 3 && matchedCount < 2) {
            return false;
        }
        if (availableCount == 2 && matchedCount < 1) {
            return false;
        }

        if (sanitizedMissingIngredients.size() > Math.max(3, matchedCount + 1)) {
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

    private void requestFallbackAiDishSuggestions(
            @NonNull List<DetectedIngredient> ingredients,
            @NonNull String sourceLabel) {
        geminiRepository.requestStructuredResponse(
                DISH_SUGGESTION_SYSTEM_PROMPT,
                buildFallbackDishSuggestionPrompt(ingredients),
                null,
                null,
                new GeminiRepository.StreamCallback() {
                    @Override
                    public void onStart() {
                        runOnUiThread(() -> updateScanStatus("Đang mở rộng gợi ý món ăn..."));
                    }

                    @Override
                    public void onChunk(@NonNull String accumulatedText) {
                        runOnUiThread(() -> updateScanStatus("Đang mở rộng gợi ý món ăn..."));
                    }

                    @Override
                    public void onCompleted(@NonNull String finalText) {
                        ExecutorService executor = recognitionExecutor == null
                                ? Executors.newSingleThreadExecutor()
                                : recognitionExecutor;
                        executor.execute(() -> {
                            List<ScanDishItem> fallbackItems = buildAiSuggestionItems(
                                    ingredients,
                                    new ArrayList<>(),
                                    parseSuggestedDishes(finalText),
                                    true);
                            runOnUiThread(() -> {
                                if (fallbackItems.isEmpty()) {
                                    finishRecognitionWithError("Chưa tạo được gợi ý món ăn phù hợp");
                                    return;
                                }
                                if (hasMissingAiRecipes(fallbackItems)) {
                                    requestRecipesForAiDishes(ingredients, fallbackItems, sourceLabel);
                                } else {
                                    finishRecognitionSuccess(fallbackItems, sourceLabel);
                                }
                            });
                        });
                    }

                    @Override
                    public void onError(@NonNull String friendlyError) {
                        runOnUiThread(() -> finishRecognitionWithError("AI đang bận, vui lòng thử lại"));
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
    private String buildFallbackDishSuggestionPrompt(@NonNull List<DetectedIngredient> ingredients) {
        JSONArray ingredientArray = new JSONArray();
        for (DetectedIngredient ingredient : ingredients) {
            ingredientArray.put(ingredient.getName());
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("Nguyên liệu đang có: ").append(ingredientArray).append('.').append("\n");
        prompt.append("Hãy gợi ý đúng 3 món khả thi nhất từ bộ nguyên liệu này.\n");
        prompt.append("Không được trả về dishes rỗng trừ khi danh sách nguyên liệu trống.\n");
        prompt.append("Được phép thêm một vài nguyên liệu phụ phổ biến nếu cần hoàn thiện món.\n");
        prompt.append("matchedIngredients phải lấy từ chính danh sách nguyên liệu đang có.\n");
        prompt.append("Mỗi món phải có reason ngắn, healthTags, confidence và recipe tiếng Việt rõ ràng.\n");
        prompt.append("Nếu không gọi đúng tên món hoàn chỉnh, hãy gợi ý món gần nhất, thực tế nhất có thể nấu được.\n");
        prompt.append("Trả về JSON hợp lệ theo schema:\n");
        prompt.append('{').append("\n");
        prompt.append("  \"dishes\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"name\": \"...\",\n");
        prompt.append("      \"matchedIngredients\": [\"...\"],\n");
        prompt.append("      \"missingIngredients\": [\"...\"],\n");
        prompt.append("      \"reason\": \"...\",\n");
        prompt.append("      \"healthTags\": [\"...\"],\n");
        prompt.append("      \"recipe\": \"### ...\",\n");
        prompt.append("      \"confidence\": 0.0\n");
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
            FrameLayout bottomSheet = suggestionDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Color.TRANSPARENT);
                bottomSheet.setPadding(0, 0, 0, 0);
            }
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
        chip.setTextColor(Color.parseColor("#4F392D"));
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#FFFFF7F9")));
        chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#4AF1C7D2")));
        chip.setChipStrokeWidth(dp(1f));
        chip.setChipMinHeight(dp(40f));
        chip.setChipStartPadding(dp(14f));
        chip.setChipEndPadding(dp(14f));
        chip.setTextStartPadding(dp(4f));
        chip.setCloseIconStartPadding(dp(6f));
        chip.setCloseIconEndPadding(dp(2f));
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
    }

    private void openDishIngredientRemovalHint() {
        Toast.makeText(this, "Nhấn dấu X để xóa nguyên liệu đang được AI sử dụng.", Toast.LENGTH_SHORT).show();
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
        if (currentDetectedIngredients.isEmpty()) {
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
            @NonNull List<ScanDishItem> localSuggestions,
            @NonNull List<String> uncoveredIngredients) {
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
        prompt.append("Danh sách nguyên liệu hợp lệ để đưa vào matchedIngredients: ").append(allowedIngredientArray).append('.').append("\n");
        prompt.append("Món local tham khảo trong app: ").append(localDishArray).append('.').append("\n");
        if (!uncoveredIngredients.isEmpty()) {
            prompt.append("Nguyên liệu chưa có trong foods.json: ")
                    .append(new JSONArray(uncoveredIngredients))
                    .append('.').append("\n");
            prompt.append("Khi có nguyên liệu ngoài foods.json, bắt buộc suy luận bằng AI từ tập nguyên liệu hiện có và trả về đúng 3 món khác nhau, phù hợp nhất.\n");
        }
        prompt.append("Hãy gợi ý ĐÚNG 3 món phù hợp nhất có thể nấu từ tập nguyên liệu này.\n");
        prompt.append("Mỗi món được gợi ý phải bám sát tập nguyên liệu đang có, không được đổi sang một món khác chỉ vì thiếu nguyên liệu.\n");
        prompt.append("matchedIngredients BẮT BUỘC phải copy đúng nguyên văn từng tên từ danh sách nguyên liệu hợp lệ. Không được đổi tên, viết lại tên, hay tự thêm tên mới.\n");
        prompt.append("missingIngredients chỉ được phép là gia vị hoặc nguyên liệu phụ nhỏ. Nếu thiếu nguyên liệu chính của món thì KHÔNG được đề xuất món đó.\n");
        prompt.append("Nếu người dùng có đồng thời gà và bò thì ưu tiên món có cả gà và bò; nếu local không có thì mới được đề xuất món mới, nhưng vẫn phải giữ đúng các nguyên liệu chính đang có.\n");
        prompt.append("Món được gợi ý phải bám sát những gì xuất hiện trong ảnh, không được đổi sang món không liên quan.\n");
        prompt.append("Nếu ảnh cho thấy 1 món hoàn chỉnh hoặc 1 thực phẩm rõ ràng, ưu tiên giữ đúng tên món/thực phẩm đó.\n");
        prompt.append("Ví dụ nếu nhận diện là bánh mì thì không được gợi ý cơm gà chiên hay món khác không liên quan.\n");
        prompt.append("Chỉ khi không thể gọi đúng tên món đang thấy mới được suy luận 1 món gần nhất từ tập nguyên liệu, và món đó phải có matchedIngredients bao phủ phần lớn nguyên liệu đang có.\n");
        prompt.append("Nếu không có món khớp hoàn toàn, vẫn phải gợi ý 3 món gần nhất, thực tế nhất có thể nấu từ bộ nguyên liệu hiện có.\n");
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
                try {
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
                } catch (Exception ignoredItem) {
                    // Bỏ qua món lỗi riêng lẻ để các món hợp lệ phía sau vẫn được giữ lại.
                }
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
                JSONArray matchedArray = item.optJSONArray("matchedIngredients");
                if (matchedArray == null || matchedArray.length() == 0) {
                    matchedArray = item.optJSONArray("usedIngredients");
                }
                dishes.add(new SuggestedDish(
                        name,
                        toStringList(matchedArray),
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

    private void saveJournalPreviewImageToGallery(
            @NonNull byte[] imageBytes,
            @NonNull String fileName,
            @NonNull ScanFoodJournalManager.ImageSaveCallback callback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !hasLegacyWriteStoragePermission()) {
            pendingGallerySaveRequest = new PendingGallerySaveRequest(
                    imageBytes.clone(),
                    fileName,
                    callback);
            if (storagePermissionLauncher != null) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            } else {
                callback.onError("KhÃ´ng thá»ƒ xin quyá»n lÆ°u áº£nh");
            }
            return;
        }
        persistJournalPreviewImageToGallery(imageBytes, fileName, callback);
    }

    private boolean hasLegacyWriteStoragePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void persistJournalPreviewImageToGallery(
            @NonNull byte[] imageBytes,
            @NonNull String fileName,
            @NonNull ScanFoodJournalManager.ImageSaveCallback callback) {
        new Thread(() -> {
            Bitmap bitmap = ScanFoodImageUtils.decodePreviewBitmap(imageBytes);
            if (bitmap == null) {
                runOnUiThread(() -> callback.onError("KhÃ´ng táº£i Ä‘Æ°á»£c áº£nh Ä‘á»ƒ lÆ°u"));
                return;
            }

            Uri savedUri = null;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CoolCook");
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);

                    savedUri = getContentResolver().insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            values);
                    if (savedUri == null) {
                        throw new IOException("MediaStore insert returned null");
                    }

                    try (OutputStream outputStream = getContentResolver().openOutputStream(savedUri, "w")) {
                        if (outputStream == null || !bitmap.compress(Bitmap.CompressFormat.JPEG, 96, outputStream)) {
                            throw new IOException("Bitmap compression failed");
                        }
                    }

                    ContentValues completedValues = new ContentValues();
                    completedValues.put(MediaStore.Images.Media.IS_PENDING, 0);
                    getContentResolver().update(savedUri, completedValues, null, null);
                } else {
                    File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                    File coolCookDir = new File(picturesDir, "CoolCook");
                    if (!coolCookDir.exists() && !coolCookDir.mkdirs()) {
                        throw new IOException("Cannot create output directory");
                    }

                    File outputFile = new File(coolCookDir, fileName);
                    try (OutputStream outputStream = new FileOutputStream(outputFile)) {
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 96, outputStream)) {
                            throw new IOException("Bitmap compression failed");
                        }
                    }
                    MediaScannerConnection.scanFile(
                            this,
                            new String[]{outputFile.getAbsolutePath()},
                            new String[]{"image/jpeg"},
                            null);
                }

                runOnUiThread(callback::onSuccess);
            } catch (Exception error) {
                if (savedUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    getContentResolver().delete(savedUri, null, null);
                }
                runOnUiThread(() -> callback.onError("LÆ°u áº£nh tháº¥t báº¡i"));
            } finally {
                bitmap.recycle();
            }
        }, "coolcook-journal-download").start();
    }

    private void setProcessingUiEnabled(boolean enabled) {
        setViewEnabled(btnShutter, enabled);
        setViewEnabled(btnGallery, enabled);
        setViewEnabled(btnFlipCamera, enabled);
        setViewEnabled(btnJournalCameraGallery, enabled);
        setViewEnabled(btnJournalCameraShutter, enabled);
        setViewEnabled(btnJournalCameraFlip, enabled);
        setViewEnabled(btnJournalHistoryShutter, enabled);
        setViewEnabled(btnJournalGrid, enabled);
        setViewEnabled(btnJournalMore, enabled);
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
        return isCaptureInProgress
                || isRecognitionInProgress
                || (journalManager != null && journalManager.isJournalSaveInProgress());
    }

    private void updateScanStatus(@NonNull String status) {
        if (txtScanStatus != null) {
            txtScanStatus.setText(status);
        }
    }

    private void updateEntryStatus(@NonNull String status) {
        if (txtScanStatus != null && !isRecognitionMode && txtScanStatus.getVisibility() == View.VISIBLE) {
            txtScanStatus.setText(status);
        }
    }

    private void navigateBackHome() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (recognitionExecutor != null) {
            recognitionExecutor.shutdownNow();
        }
        if (isTaskRoot()) {
            Intent intent = new Intent(this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }
        finish();
        overridePendingTransition(0, R.anim.slide_out_right_scale);
    }
}






