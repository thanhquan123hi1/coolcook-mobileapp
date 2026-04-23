package com.coolcook.app.ui.scan;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
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
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.coolcook.app.R;
import com.coolcook.app.ui.auth.AuthActivity;
import com.coolcook.app.ui.chatbot.data.GeminiRepository;
import com.coolcook.app.ui.chatbot.model.ChatMessage;
import com.coolcook.app.ui.home.HomeActivity;
import com.coolcook.app.ui.scan.data.FriendInviteRepository;
import com.coolcook.app.ui.scan.data.JournalFeedRepository;
import com.coolcook.app.ui.scan.data.MediaUploadRepository;
import com.coolcook.app.ui.scan.model.FriendInvite;
import com.coolcook.app.ui.scan.model.JournalFeedItem;
import com.coolcook.app.ui.scan.model.MediaUploadResult;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ScanFoodActivity extends AppCompatActivity {

    private static final long PRESS_IN_DURATION = 200L;
    private static final long PRESS_OUT_DURATION = 220L;
    private static final float PRESS_SCALE = 0.95f;
    private static final long MODE_TRANSITION_DURATION = 240L;
    private static final @ColorInt int TAB_ACTIVE_COLOR = Color.parseColor("#FABD00");
    private static final @ColorInt int TAB_INACTIVE_COLOR = Color.parseColor("#99D4C5AB");

    private static final int MAX_RECOGNITION_IMAGE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_JOURNAL_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final int JOURNAL_LIST_LIMIT = 30;

    private static final String EXTRA_START_MODE = "extra_start_mode";
    private static final String START_MODE_JOURNAL = "journal";

    private View root;
    private View recognitionSurface;
    private NestedScrollView journalSurface;
    private View topBar;
    private View footerContainer;
    private View cameraPreviewCard;
    private View previewInnerFrame;
    private View detectionBox;
    private View tabIndicator;
    private View journalTopBar;
    private View journalFooterContainer;
    private View journalCameraPreviewCard;
    private View journalCaptureOverlay;
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
    private PreviewView recognitionPreviewView;
    private PreviewView journalPreviewView;
    private PreviewView previewView;
    private RecyclerView rvJournalMoments;

    private ActivityResultLauncher<String> galleryPickerLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    private Animation detectionPulseAnimation;
    private boolean isRecognitionMode = true;
    private boolean isFlashOn;
    private boolean isUsingFrontCamera;
    private boolean isCameraReady;
    private boolean isRecognitionInProgress;
    private boolean isJournalSaveInProgress;

    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private Camera camera;

    private GeminiRepository geminiRepository;
    private MediaUploadRepository mediaUploadRepository;
    private JournalFeedRepository journalFeedRepository;
    private FriendInviteRepository friendInviteRepository;
    private final List<ChatMessage> recognitionHistory = new ArrayList<>();
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private ListenerRegistration journalFeedListener;
    private ListenerRegistration journalProfileListener;
    private JournalFeedAdapter journalFeedAdapter;

    private byte[] pendingJournalImageBytes;
    private String pendingJournalSourceLabel = "camera";

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
        setupJournalList();
        setupClickListeners();

        geminiRepository = new GeminiRepository();
        mediaUploadRepository = new MediaUploadRepository(getApplicationContext());
        journalFeedRepository = new JournalFeedRepository(firestore);
        friendInviteRepository = new FriendInviteRepository(firestore);

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
        if (journalCaptureOverlay != null && journalCaptureOverlay.getVisibility() == View.VISIBLE) {
            hideJournalCapturePreview(true);
            return;
        }
        navigateBackHome();
    }

    private void initViews() {
        root = findViewById(R.id.scanRoot);
        recognitionSurface = findViewById(R.id.recognitionSurface);
        journalSurface = findViewById(R.id.journalSurface);
        topBar = findViewById(R.id.topBar);
        footerContainer = findViewById(R.id.footerContainer);
        cameraPreviewCard = findViewById(R.id.cameraPreviewCard);
        previewInnerFrame = findViewById(R.id.previewInnerFrame);
        detectionBox = findViewById(R.id.detectionBox);
        tabIndicator = findViewById(R.id.tabIndicator);
        journalTopBar = findViewById(R.id.journalTopBar);
        journalFooterContainer = findViewById(R.id.journalFooterContainer);
        journalCameraPreviewCard = findViewById(R.id.journalCameraPreviewCard);
        journalCaptureOverlay = findViewById(R.id.journalCaptureOverlay);
        txtScanStatus = findViewById(R.id.txtScanStatus);
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
        recognitionPreviewView = findViewById(R.id.previewView);
        journalPreviewView = findViewById(R.id.journalPreviewView);
        rvJournalMoments = findViewById(R.id.rvJournalMoments);
        previewView = recognitionPreviewView;
    }

    private void setupJournalList() {
        if (rvJournalMoments == null) {
            return;
        }

        journalFeedAdapter = new JournalFeedAdapter();
        rvJournalMoments.setLayoutManager(new LinearLayoutManager(this));
        rvJournalMoments.setAdapter(journalFeedAdapter);
        rvJournalMoments.setNestedScrollingEnabled(false);
        updateJournalEmptyState(0, "Chưa có hoạt động nào!");
    }

    private void startJournalRealtimeListeners() {
        stopJournalRealtimeListeners();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (journalFeedAdapter != null) {
                journalFeedAdapter.submitItems(new ArrayList<>());
            }
            updateJournalEmptyState(0, "Đăng nhập để xem feed của bạn bè.");
            updateJournalStatus("Vui lòng đăng nhập để đăng moment.");
            if (txtJournalFriendCount != null) {
                txtJournalFriendCount.setText("Tất cả bạn bè");
            }
            return;
        }

        journalFeedListener = journalFeedRepository.listenToFeed(user.getUid(), JOURNAL_LIST_LIMIT,
                new JournalFeedRepository.FeedCallback() {
                    @Override
                    public void onItems(@NonNull List<JournalFeedItem> items) {
                        if (journalFeedAdapter != null) {
                            journalFeedAdapter.submitItems(items);
                        }
                        updateJournalEmptyState(items.size(), "Chưa có hoạt động nào!");
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        updateJournalEmptyState(0, "Không tải được feed. Vui lòng thử lại.");
                        updateJournalStatus("Không tải được feed.");
                    }
                });

        journalProfileListener = journalFeedRepository.listenToUserProfile(user,
                new JournalFeedRepository.UserProfileCallback() {
                    @Override
                    public void onProfile(@NonNull JournalFeedRepository.UserProfile profile) {
                        if (txtJournalFriendCount == null) {
                            return;
                        }
                        if (profile.friendCount <= 0L) {
                            txtJournalFriendCount.setText("Tất cả bạn bè");
                        } else {
                            txtJournalFriendCount.setText(profile.friendCount + " Bạn bè");
                        }
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        if (txtJournalFriendCount != null) {
                            txtJournalFriendCount.setText("Bạn bè");
                        }
                    }
                });
    }

    private void stopJournalRealtimeListeners() {
        if (journalFeedListener != null) {
            journalFeedListener.remove();
            journalFeedListener = null;
        }
        if (journalProfileListener != null) {
            journalProfileListener.remove();
            journalProfileListener = null;
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

    private void updateJournalEmptyState(int count, @NonNull String emptyText) {
        if (txtJournalEmptyState == null) {
            return;
        }
        boolean showEmpty = count <= 0;
        txtJournalEmptyState.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        if (showEmpty) {
            txtJournalEmptyState.setText(emptyText);
        }
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
        if (journalSurface != null) {
            journalSurface.setVisibility(isRecognitionMode ? View.GONE : View.VISIBLE);
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
                if (!isJournalSaveInProgress) {
                    updateJournalStatus("Chụp nhanh rồi kéo xuống để xem feed.");
                }
                startJournalRealtimeListeners();
            }
        }

        if (isRecognitionMode && journalCaptureOverlay != null && journalCaptureOverlay.getVisibility() == View.VISIBLE) {
            hideJournalCapturePreview(true);
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
            btnJournalPostCancel.setOnClickListener(v -> hideJournalCapturePreview(true));
        }
        if (btnJournalPostPublish != null) {
            btnJournalPostPublish.setOnClickListener(v -> publishPendingJournalMoment());
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
                        byte[] bytes = imageProxyToJpeg(image);
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
                byte[] raw = readImageBytes(uri, MAX_RECOGNITION_IMAGE_BYTES);
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
                byte[] raw = readImageBytes(uri, MAX_JOURNAL_IMAGE_BYTES);
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
        if (isRecognitionInProgress || isJournalSaveInProgress) {
            return;
        }
        if (imageBytes.length > MAX_RECOGNITION_IMAGE_BYTES) {
            updateScanStatus("Ảnh quá lớn, vui lòng chụp/chọn ảnh khác");
            return;
        }

        isRecognitionInProgress = true;
        setProcessingUiEnabled(false);
        updateScanStatus("Đang nhận diện món ăn...");
        startDetectionPulse();

        String safeMimeType = TextUtils.isEmpty(mimeType) ? "image/jpeg" : mimeType;
        String imageBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
        String userPrompt = "Nhận diện món ăn trong ảnh và hướng dẫn nấu chi tiết.";

        geminiRepository.streamChatResponse(
                new ArrayList<>(recognitionHistory),
                userPrompt,
                safeMimeType,
                imageBase64,
                new GeminiRepository.StreamCallback() {
                    @Override
                    public void onStart() {
                        runOnUiThread(() -> updateScanStatus("Đang phân tích món ăn..."));
                    }

                    @Override
                    public void onChunk(@NonNull String accumulatedText) {
                        runOnUiThread(() -> updateScanStatus("Đang tạo hướng dẫn nấu..."));
                    }

                    @Override
                    public void onCompleted(@NonNull String finalText) {
                        runOnUiThread(() -> {
                            isRecognitionInProgress = false;
                            setProcessingUiEnabled(true);

                            String cooked = TextUtils.isEmpty(finalText)
                                    ? "Chưa nhận được kết quả rõ ràng."
                                    : finalText.trim();
                            appendRecognitionHistory(sourceLabel, cooked);

                            if (isRecognitionMode) {
                                updateScanStatus("Nhận diện xong. Bạn có thể quét món khác.");
                            } else {
                                updateScanStatus("Nhận diện xong. Chuyển tab Nhận diện để xem tiếp.");
                            }
                            Toast.makeText(ScanFoodActivity.this, "Đã nhận diện xong", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(@NonNull String friendlyError) {
                        runOnUiThread(() -> {
                            isRecognitionInProgress = false;
                            setProcessingUiEnabled(true);
                            updateScanStatus("Nhận diện thất bại, vui lòng thử lại");
                            Toast.makeText(
                                    ScanFoodActivity.this,
                                    TextUtils.isEmpty(friendlyError) ? "Lỗi nhận diện" : friendlyError,
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    private void appendRecognitionHistory(@NonNull String sourceLabel, @NonNull String resultText) {
        String compactResult = resultText.length() > 360 ? resultText.substring(0, 360) : resultText;
        recognitionHistory.add(new ChatMessage(
                ChatMessage.ROLE_USER,
                "Ảnh từ " + sourceLabel + ". Hãy nhận diện và hướng dẫn nấu."));
        recognitionHistory.add(new ChatMessage(ChatMessage.ROLE_AI, compactResult));
        while (recognitionHistory.size() > 10) {
            recognitionHistory.remove(0);
        }
    }

    private void processImageBytesForJournal(@NonNull byte[] imageBytes, @NonNull String sourceLabel) {
        if (isRecognitionInProgress || isJournalSaveInProgress) {
            return;
        }
        showJournalCapturePreview(imageBytes, sourceLabel);
    }

    private void showJournalCapturePreview(@NonNull byte[] imageBytes, @NonNull String sourceLabel) {
        if (imgJournalCapturedPreview == null || journalCaptureOverlay == null) {
            return;
        }

        Bitmap bitmap = decodePreviewBitmap(imageBytes);
        if (bitmap == null) {
            updateJournalStatus("Không mở được ảnh vừa chụp.");
            Toast.makeText(this, "Không hiển thị được ảnh", Toast.LENGTH_SHORT).show();
            return;
        }

        pendingJournalImageBytes = imageBytes;
        pendingJournalSourceLabel = sourceLabel;
        imgJournalCapturedPreview.setImageBitmap(bitmap);
        if (edtJournalCaption != null) {
            edtJournalCaption.setText("");
        }
        setJournalPostLoading(false, "");
        showJournalPostError("");
        journalCaptureOverlay.setVisibility(View.VISIBLE);
        updateJournalStatus("Thêm caption rồi bấm Đăng.");
    }

    private void hideJournalCapturePreview(boolean clearPendingState) {
        if (journalCaptureOverlay != null) {
            journalCaptureOverlay.setVisibility(View.GONE);
        }
        if (imgJournalCapturedPreview != null) {
            imgJournalCapturedPreview.setImageDrawable(null);
        }
        if (edtJournalCaption != null) {
            edtJournalCaption.setText("");
        }
        setJournalPostLoading(false, "");
        showJournalPostError("");
        if (clearPendingState) {
            pendingJournalImageBytes = null;
            pendingJournalSourceLabel = "camera";
        }
        updateJournalStatus("Sẵn sàng chụp moment mới.");
    }

    @Nullable
    private Bitmap decodePreviewBitmap(@NonNull byte[] imageBytes) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, bounds);

            int maxSide = Math.max(bounds.outWidth, bounds.outHeight);
            int inSampleSize = 1;
            while (maxSide / inSampleSize > 1600) {
                inSampleSize *= 2;
            }

            BitmapFactory.Options decode = new BitmapFactory.Options();
            decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
            decode.inSampleSize = Math.max(1, inSampleSize);
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, decode);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void publishPendingJournalMoment() {
        if (pendingJournalImageBytes == null || pendingJournalImageBytes.length == 0) {
            showJournalPostError("Không có ảnh để đăng.");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Vui lòng đăng nhập để đăng nhật ký", Toast.LENGTH_SHORT).show();
            startActivity(AuthActivity.createIntent(this, AuthActivity.MODE_LOGIN));
            return;
        }

        String caption = edtJournalCaption == null ? "" : String.valueOf(edtJournalCaption.getText()).trim();
        String cameraFacing = "camera".equals(pendingJournalSourceLabel)
                ? (isUsingFrontCamera ? "front" : "back")
                : "none";

        isJournalSaveInProgress = true;
        setProcessingUiEnabled(false);
        setJournalPostLoading(true, "Đang tối ưu ảnh...");
        showJournalPostError("");
        updateJournalStatus("Đang đăng moment...");

        mediaUploadRepository.uploadJournalImage(pendingJournalImageBytes,
                new MediaUploadRepository.UploadCallbackListener() {
                    @Override
                    public void onPreparing() {
                        runOnUiThread(() -> setJournalPostLoading(true, "Đang tối ưu ảnh..."));
                    }

                    @Override
                    public void onProgress(int progress) {
                        runOnUiThread(() -> setJournalPostLoading(
                                true,
                                progress <= 0
                                        ? "Đang tải ảnh lên Cloudinary..."
                                        : "Đang tải ảnh lên Cloudinary... " + progress + "%"));
                    }

                    @Override
                    public void onSuccess(@NonNull MediaUploadResult result) {
                        runOnUiThread(() -> setJournalPostLoading(true, "Đang cập nhật Firestore..."));
                        journalFeedRepository.publishMoment(
                                user,
                                result,
                                caption,
                                pendingJournalSourceLabel,
                                cameraFacing,
                                new JournalFeedRepository.PublishCallback() {
                                    @Override
                                    public void onSuccess(@NonNull JournalFeedItem item) {
                                        runOnUiThread(() -> {
                                            isJournalSaveInProgress = false;
                                            setProcessingUiEnabled(true);
                                            if (journalFeedAdapter != null) {
                                                journalFeedAdapter.prependIfMissing(item);
                                                updateJournalEmptyState(journalFeedAdapter.getItemCount(), "Chưa có hoạt động nào!");
                                            }
                                            hideJournalCapturePreview(true);
                                            updateJournalStatus("Đã đăng moment mới.");
                                            Toast.makeText(
                                                    ScanFoodActivity.this,
                                                    "Đã đăng moment thành công",
                                                    Toast.LENGTH_SHORT).show();
                                        });
                                    }

                                    @Override
                                    public void onError(@NonNull Exception error) {
                                        runOnUiThread(() -> finishJournalSaveWithError(
                                                "Lưu Firestore thất bại. Vui lòng thử lại."));
                                    }
                                });
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        runOnUiThread(() -> finishJournalSaveWithError(message));
                    }
                });
    }

    private void setJournalPostLoading(boolean loading, @NonNull String statusText) {
        if (journalPostLoading != null) {
            journalPostLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (txtJournalUploadProgress != null) {
            txtJournalUploadProgress.setText(statusText);
            txtJournalUploadProgress.setVisibility(TextUtils.isEmpty(statusText) ? View.GONE : View.VISIBLE);
        }
        if (btnJournalPostCancel != null) {
            btnJournalPostCancel.setEnabled(!loading);
            btnJournalPostCancel.setAlpha(loading ? 0.65f : 1f);
        }
        if (btnJournalPostPublish != null) {
            btnJournalPostPublish.setEnabled(!loading);
            btnJournalPostPublish.setAlpha(loading ? 0.65f : 1f);
        }
    }

    private void showJournalPostError(@NonNull String message) {
        if (txtJournalPostError == null) {
            return;
        }
        if (TextUtils.isEmpty(message)) {
            txtJournalPostError.setVisibility(View.GONE);
            txtJournalPostError.setText("");
            return;
        }
        txtJournalPostError.setVisibility(View.VISIBLE);
        txtJournalPostError.setText(message);
    }

    private void finishJournalSaveWithError(@NonNull String errorMessage) {
        isJournalSaveInProgress = false;
        setProcessingUiEnabled(true);
        setJournalPostLoading(false, "");
        showJournalPostError(errorMessage);
        updateJournalStatus(errorMessage);
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
    }

    private void openFriendInviteSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_friend_invite, null, false);
        dialog.setContentView(sheet);

        TextView txtTitle = sheet.findViewById(R.id.txtFriendSheetTitle);
        TextView txtSubtitle = sheet.findViewById(R.id.txtFriendSheetSubtitle);
        View btnCreateInviteLink = sheet.findViewById(R.id.btnCreateInviteLink);
        View btnClose = sheet.findViewById(R.id.btnFriendSheetClose);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (txtTitle != null) {
            txtTitle.setText(txtJournalFriendCount == null ? "Bạn bè" : txtJournalFriendCount.getText());
        }
        if (txtSubtitle != null && user == null) {
            txtSubtitle.setText("Đăng nhập để tạo link mời bạn vào journal feed.");
        }

        if (btnCreateInviteLink != null) {
            btnCreateInviteLink.setOnClickListener(v -> {
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser == null) {
                    dialog.dismiss();
                    startActivity(AuthActivity.createIntent(this, AuthActivity.MODE_LOGIN));
                    return;
                }

                v.setEnabled(false);
                v.setAlpha(0.7f);
                friendInviteRepository.createInvite(currentUser, new FriendInviteRepository.CreateInviteCallback() {
                    @Override
                    public void onSuccess(@NonNull FriendInvite invite) {
                        runOnUiThread(() -> {
                            dialog.dismiss();
                            shareInvite(invite);
                        });
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        runOnUiThread(() -> {
                            v.setEnabled(true);
                            v.setAlpha(1f);
                            Toast.makeText(ScanFoodActivity.this, message, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            });
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    private void shareInvite(@NonNull FriendInvite invite) {
        String shareText = "Kết bạn với mình trên CoolCook nhé.\n"
                + invite.buildDeepLink()
                + "\nNếu app link web đã được cấu hình trên domain, bạn cũng có thể thử:\n"
                + invite.buildWebLink();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(intent, "Chia sẻ lời mời"));
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
        return isRecognitionInProgress || isJournalSaveInProgress;
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

    @NonNull
    private byte[] readImageBytes(@NonNull Uri uri, int maxBytes) throws IOException {
        ContentResolver resolver = getContentResolver();
        try (InputStream inputStream = resolver.openInputStream(uri);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (inputStream == null) {
                throw new IOException("Cannot open selected image");
            }
            byte[] buffer = new byte[8 * 1024];
            int read;
            int total = 0;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    return new byte[maxBytes + 1];
                }
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    @Nullable
    private byte[] imageProxyToJpeg(@NonNull ImageProxy imageProxy) {
        ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
        if (imageProxy.getFormat() == ImageFormat.JPEG && planes.length > 0) {
            return readPlaneBytes(planes[0]);
        }

        if (imageProxy.getFormat() != ImageFormat.YUV_420_888 || planes.length < 3) {
            return planes.length > 0 ? readPlaneBytes(planes[0]) : null;
        }

        byte[] nv21 = yuv420888ToNv21(imageProxy);
        if (nv21 == null) {
            return null;
        }

        YuvImage yuvImage = new YuvImage(
                nv21,
                ImageFormat.NV21,
                imageProxy.getWidth(),
                imageProxy.getHeight(),
                null);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        boolean compressed = yuvImage.compressToJpeg(
                new Rect(0, 0, imageProxy.getWidth(), imageProxy.getHeight()),
                88,
                outputStream);

        if (!compressed) {
            return null;
        }

        return outputStream.toByteArray();
    }

    @Nullable
    private byte[] readPlaneBytes(@NonNull ImageProxy.PlaneProxy plane) {
        ByteBuffer buffer = plane.getBuffer();
        ByteBuffer copy = buffer.duplicate();
        copy.rewind();
        if (!copy.hasRemaining()) {
            return null;
        }

        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }

    @Nullable
    private byte[] yuv420888ToNv21(@NonNull ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);

        int chromaRowStride = planes[2].getRowStride();
        int chromaPixelStride = planes[2].getPixelStride();
        int width = image.getWidth();
        int height = image.getHeight();
        int offset = ySize;

        byte[] vBytes = new byte[vBuffer.remaining()];
        vBuffer.get(vBytes);
        byte[] uBytes = new byte[uBuffer.remaining()];
        uBuffer.get(uBytes);

        int chromaHeight = height / 2;
        int chromaWidth = width / 2;

        for (int row = 0; row < chromaHeight; row++) {
            int rowStart = row * chromaRowStride;
            for (int col = 0; col < chromaWidth; col++) {
                int index = rowStart + col * chromaPixelStride;
                if (index >= vBytes.length || index >= uBytes.length || offset + 1 >= nv21.length) {
                    break;
                }
                nv21[offset++] = vBytes[index];
                nv21[offset++] = uBytes[index];
            }
        }

        return nv21;
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
