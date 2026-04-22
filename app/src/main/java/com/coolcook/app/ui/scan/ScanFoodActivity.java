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
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.UploadRequest;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.coolcook.app.BuildConfig;
import com.coolcook.app.R;
import com.coolcook.app.ui.chatbot.data.GeminiRepository;
import com.coolcook.app.ui.chatbot.model.ChatMessage;
import com.coolcook.app.ui.home.HomeActivity;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScanFoodActivity extends AppCompatActivity {

    private static final long PRESS_IN_DURATION = 200L;
    private static final long PRESS_OUT_DURATION = 220L;
    private static final float PRESS_SCALE = 0.95f;
    private static final long MODE_TRANSITION_DURATION = 240L;
    private static final @ColorInt int TAB_ACTIVE_COLOR = Color.parseColor("#FABD00");
    private static final @ColorInt int TAB_INACTIVE_COLOR = Color.parseColor("#99D4C5AB");

    private static final int MAX_RECOGNITION_IMAGE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_JOURNAL_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final int JOURNAL_UPLOAD_MAX_DIMENSION_PX = 1440;
    private static final int JOURNAL_UPLOAD_QUALITY = 84;
    private static final int JOURNAL_LIST_LIMIT = 80;

    private static final String FIRESTORE_USERS_COLLECTION = "users";
    private static final String FIRESTORE_JOURNAL_COLLECTION = "journal_entries";

    private View root;
    private View topBar;
    private View footerContainer;
    private View cameraPreviewCard;
    private View previewInnerFrame;
    private View detectionBox;
    private View tabIndicator;
    private View journalOverlay;
    private TextView txtScanStatus;
    private TextView txtJournalEmptyState;
    private TextView tabNhanDien;
    private TextView tabNhatKy;
    private TextView iconFlash;
    private View btnBack;
    private View btnFlash;
    private View btnGallery;
    private View btnShutter;
    private View btnFlipCamera;
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
    private final List<ChatMessage> recognitionHistory = new ArrayList<>();

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private ListenerRegistration journalListener;
    private JournalMomentAdapter journalMomentAdapter;

    public static Intent createIntent(Context context) {
        return new Intent(context, ScanFoodActivity.class);
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

        updateUiForMode(false);
        updateFlashUi(false);
        updateScanStatus("Hướng camera vào món ăn rồi bấm nút chụp");
        ensureCameraPermissionAndStart();
    }

    @Override
    protected void onStart() {
        super.onStart();
        startJournalListener();
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
        stopJournalListener();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopJournalListener();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    @Override
    public void onBackPressed() {
        navigateBackHome();
    }

    private void initViews() {
        root = findViewById(R.id.scanRoot);
        topBar = findViewById(R.id.topBar);
        footerContainer = findViewById(R.id.footerContainer);
        cameraPreviewCard = findViewById(R.id.cameraPreviewCard);
        previewInnerFrame = findViewById(R.id.previewInnerFrame);
        detectionBox = findViewById(R.id.detectionBox);
        tabIndicator = findViewById(R.id.tabIndicator);
        journalOverlay = findViewById(R.id.journalOverlay);
        txtScanStatus = findViewById(R.id.txtScanStatus);
        txtJournalEmptyState = findViewById(R.id.txtJournalEmptyState);
        tabNhanDien = findViewById(R.id.tabNhanDien);
        tabNhatKy = findViewById(R.id.tabNhatKy);
        iconFlash = findViewById(R.id.iconFlash);
        btnBack = findViewById(R.id.btnBack);
        btnFlash = findViewById(R.id.btnFlash);
        btnGallery = findViewById(R.id.btnGallery);
        btnShutter = findViewById(R.id.btnShutter);
        btnFlipCamera = findViewById(R.id.btnFlipCamera);
        previewView = findViewById(R.id.previewView);
        rvJournalMoments = findViewById(R.id.rvJournalMoments);
    }

    private void setupJournalList() {
        if (rvJournalMoments == null) {
            return;
        }
        journalMomentAdapter = new JournalMomentAdapter();
        rvJournalMoments.setLayoutManager(new GridLayoutManager(this, 2));
        rvJournalMoments.setAdapter(journalMomentAdapter);
        updateJournalEmptyState(0, "Đang tải nhật ký món ăn...");
    }

    private void startJournalListener() {
        stopJournalListener();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (journalMomentAdapter != null) {
                journalMomentAdapter.submitMoments(new ArrayList<>());
            }
            updateJournalEmptyState(0, "Đăng nhập để lưu và xem nhật ký món ăn.");
            return;
        }

        journalListener = firestore.collection(FIRESTORE_USERS_COLLECTION)
                .document(user.getUid())
                .collection(FIRESTORE_JOURNAL_COLLECTION)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(JOURNAL_LIST_LIMIT)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        updateJournalEmptyState(0, "Không tải được nhật ký. Vui lòng thử lại.");
                        return;
                    }
                    if (snapshot == null || snapshot.isEmpty()) {
                        if (journalMomentAdapter != null) {
                            journalMomentAdapter.submitMoments(new ArrayList<>());
                        }
                        updateJournalEmptyState(0,
                                "Chưa có khoảnh khắc nào. Bấm chụp để lưu món ăn đầu tiên.");
                        return;
                    }

                    List<JournalMoment> items = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        JournalMoment moment = JournalMoment.fromSnapshot(doc);
                        if (!TextUtils.isEmpty(moment.getImageUrl())) {
                            items.add(moment);
                        }
                    }
                    if (journalMomentAdapter != null) {
                        journalMomentAdapter.submitMoments(items);
                    }
                    updateJournalEmptyState(items.size(), "");
                });
    }

    private void stopJournalListener() {
        if (journalListener != null) {
            journalListener.remove();
            journalListener = null;
        }
    }

    private void updateJournalEmptyState(int count, @NonNull String emptyText) {
        if (txtJournalEmptyState == null) {
            return;
        }
        boolean showEmpty = count <= 0;
        txtJournalEmptyState.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        if (showEmpty && !TextUtils.isEmpty(emptyText)) {
            txtJournalEmptyState.setText(emptyText);
        }
    }

    private void applyInsets() {
        if (root == null || topBar == null || footerContainer == null) {
            return;
        }

        final int topBarStart = topBar.getPaddingStart();
        final int topBarTop = topBar.getPaddingTop();
        final int topBarEnd = topBar.getPaddingEnd();
        final int topBarBottom = topBar.getPaddingBottom();

        final int footerStart = footerContainer.getPaddingStart();
        final int footerTop = footerContainer.getPaddingTop();
        final int footerEnd = footerContainer.getPaddingEnd();
        final int footerBottom = footerContainer.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            topBar.setPaddingRelative(
                    topBarStart + systemBars.left,
                    topBarTop + systemBars.top,
                    topBarEnd + systemBars.right,
                    topBarBottom);

            footerContainer.setPaddingRelative(
                    footerStart + systemBars.left,
                    footerTop,
                    footerEnd + systemBars.right,
                    footerBottom + systemBars.bottom);

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
                                : "Camera sẵn sàng, bạn có thể lưu khoảnh khắc");
                        startCameraIfNeeded();
                    } else {
                        isCameraReady = false;
                        updateScanStatus("Bạn cần cấp quyền Camera để dùng tính năng này");
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
        if (cameraPermissionLauncher != null) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void startCameraIfNeeded() {
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
                Toast.makeText(this, "Không mở được camera", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
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
                    : "Camera sẵn sàng, bấm chụp để lưu nhật ký");
        } catch (Exception bindError) {
            isCameraReady = false;
            updateScanStatus("Không bind được camera, đang thử camera khác...");
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

        final boolean routeRecognition = isRecognitionMode;
        if (routeRecognition) {
            processImageUriForRecognition(imageUri, "gallery");
        } else {
            processImageUriForJournal(imageUri, "gallery");
        }
    }

    private void setupPressScaleFeedback() {
        applyPressScale(btnBack, btnFlash, btnGallery, btnShutter, btnFlipCamera, tabNhanDien, tabNhatKy);
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

        if (cameraPreviewCard != null) {
            cameraPreviewCard.animate().cancel();
            cameraPreviewCard.animate()
                    .alpha(isRecognitionMode ? 1f : 0.94f)
                    .setDuration(animated ? MODE_TRANSITION_DURATION : 0L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        if (previewInnerFrame != null) {
            previewInnerFrame.animate().cancel();
            previewInnerFrame.animate()
                    .alpha(isRecognitionMode ? 1f : 0.62f)
                    .setDuration(animated ? MODE_TRANSITION_DURATION : 0L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        if (journalOverlay != null) {
            journalOverlay.setVisibility(isRecognitionMode ? View.GONE : View.VISIBLE);
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
                detectionBox.animate()
                        .alpha(0f)
                        .setDuration(animated ? MODE_TRANSITION_DURATION : 0L)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(() -> detectionBox.setVisibility(View.INVISIBLE))
                        .start();
                if (!isJournalSaveInProgress) {
                    updateScanStatus("Bấm chụp để lưu món ăn vào nhật ký");
                }
            }
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

        if (showToast) {
            Toast.makeText(this, isFlashOn ? "Flash: Bật" : "Flash: Tắt", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyTorchState() {
        if (camera == null || camera.getCameraInfo() == null || camera.getCameraControl() == null) {
            return;
        }
        boolean hasTorch = camera.getCameraInfo().hasFlashUnit();
        if (!hasTorch) {
            isFlashOn = false;
            updateFlashUi(false);
            if (btnFlash != null) {
                btnFlash.setEnabled(false);
                btnFlash.setAlpha(0.5f);
            }
            return;
        }

        if (btnFlash != null) {
            btnFlash.setEnabled(true);
            btnFlash.setAlpha(1f);
        }
        camera.getCameraControl().enableTorch(isFlashOn);
        updateFlashUi(false);
    }

    private void setupClickListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> navigateBackHome());
        }

        if (btnFlash != null) {
            btnFlash.setOnClickListener(v -> {
                if (!isCameraReady || camera == null) {
                    Toast.makeText(this, "Camera chưa sẵn sàng", Toast.LENGTH_SHORT).show();
                    return;
                }
                isFlashOn = !isFlashOn;
                camera.getCameraControl().enableTorch(isFlashOn);
                updateFlashUi(true);
            });
        }

        if (btnGallery != null) {
            btnGallery.setOnClickListener(v -> openGalleryPicker());
        }

        if (btnShutter != null) {
            btnShutter.setOnClickListener(v -> performShutterAction());
        }

        if (btnFlipCamera != null) {
            btnFlipCamera.setOnClickListener(v -> {
                if (isBusyProcessing()) {
                    Toast.makeText(this, "Đang xử lý ảnh, vui lòng chờ", Toast.LENGTH_SHORT).show();
                    return;
                }
                isUsingFrontCamera = !isUsingFrontCamera;
                startCameraIfNeeded();
                String cameraLabel = isUsingFrontCamera ? "Camera trước" : "Camera sau";
                Toast.makeText(this, "Đã chuyển sang " + cameraLabel, Toast.LENGTH_SHORT).show();
            });
        }
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

        final boolean routeRecognition = isRecognitionMode;
        updateScanStatus(routeRecognition ? "Đang chụp để nhận diện..." : "Đang chụp để lưu nhật ký...");

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
                        Toast.makeText(ScanFoodActivity.this, "Không chụp được ảnh", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void animateShutterFeedback() {
        if (btnShutter == null) {
            return;
        }
        btnShutter.animate().cancel();
        btnShutter.animate()
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(180L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> btnShutter.animate()
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
                        updateScanStatus("Ảnh quá lớn, vui lòng chọn ảnh nhỏ hơn");
                        Toast.makeText(this, "Ảnh vượt quá 8MB", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    processImageBytesForJournal(raw, sourceLabel);
                });
            } catch (IOException ioException) {
                runOnUiThread(() -> {
                    updateScanStatus("Không đọc được ảnh, vui lòng thử lại");
                    Toast.makeText(this, "Không thể đọc ảnh đã chọn", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void processImageBytesForRecognition(@NonNull byte[] imageBytes, @Nullable String mimeType,
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
        recognitionHistory.add(new ChatMessage(ChatMessage.ROLE_USER,
                "Ảnh từ " + sourceLabel + ". Hãy nhận diện và hướng dẫn nấu."));
        recognitionHistory.add(new ChatMessage(ChatMessage.ROLE_AI, compactResult));
        while (recognitionHistory.size() > 10) {
            recognitionHistory.remove(0);
        }
    }

    private void processImageBytesForJournal(@NonNull byte[] imageBytes, @NonNull String sourceLabel) {
        if (isJournalSaveInProgress || isRecognitionInProgress) {
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            updateScanStatus("Bạn cần đăng nhập để lưu nhật ký món ăn");
            Toast.makeText(this, "Vui lòng đăng nhập để lưu nhật ký", Toast.LENGTH_SHORT).show();
            return;
        }

        isJournalSaveInProgress = true;
        setProcessingUiEnabled(false);
        updateScanStatus("Đang tối ưu ảnh để lưu nhật ký...");

        new Thread(() -> {
            byte[] preparedBytes = prepareJournalImageForUpload(imageBytes);
            if (preparedBytes == null || preparedBytes.length == 0) {
                runOnUiThread(() -> finishJournalSaveWithError("Không chuẩn bị được ảnh để lưu."));
                return;
            }
            ImageSize imageSize = resolveImageSize(preparedBytes);
            runOnUiThread(() -> uploadJournalToCloudinary(preparedBytes, sourceLabel, imageSize));
        }).start();
    }

    private void uploadJournalToCloudinary(@NonNull byte[] imageBytes, @NonNull String sourceLabel,
            @NonNull ImageSize imageSize) {
        if (!ensureCloudinaryConfigured()) {
            finishJournalSaveWithError("Thiếu cấu hình Cloudinary để lưu nhật ký.");
            return;
        }

        updateScanStatus("Đang tải ảnh nhật ký lên cloud...");

        String uploadPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET.trim();
        UploadRequest uploadRequest = MediaManager.get().upload(imageBytes)
                .option("resource_type", "image");

        if (!TextUtils.isEmpty(uploadPreset)) {
            uploadRequest.option("upload_preset", uploadPreset);
        } else {
            uploadRequest.option("folder", "coolcook/journal");
        }

        uploadRequest.callback(new UploadCallback() {
            @Override
            public void onStart(String requestId) {
                runOnUiThread(() -> updateScanStatus("Đang bắt đầu lưu nhật ký..."));
            }

            @Override
            public void onProgress(String requestId, long bytes, long totalBytes) {
                runOnUiThread(() -> {
                    if (totalBytes <= 0L) {
                        updateScanStatus("Đang tải ảnh nhật ký lên cloud...");
                        return;
                    }
                    int progress = (int) ((bytes * 100L) / totalBytes);
                    progress = Math.max(0, Math.min(progress, 100));
                    updateScanStatus("Đang lưu nhật ký... " + progress + "%");
                });
            }

            @Override
            public void onSuccess(String requestId, Map resultData) {
                String secureUrl = resultData == null ? "" : String.valueOf(resultData.get("secure_url"));
                runOnUiThread(() -> {
                    if (TextUtils.isEmpty(secureUrl)) {
                        finishJournalSaveWithError("Cloud không trả về URL ảnh hợp lệ.");
                        return;
                    }
                    saveJournalEntryToFirestore(secureUrl, sourceLabel, imageSize);
                });
            }

            @Override
            public void onError(String requestId, ErrorInfo error) {
                runOnUiThread(() -> {
                    String message = error == null || TextUtils.isEmpty(error.getDescription())
                            ? "Tải ảnh nhật ký thất bại"
                            : error.getDescription();
                    finishJournalSaveWithError(message);
                });
            }

            @Override
            public void onReschedule(String requestId, ErrorInfo error) {
                runOnUiThread(() -> updateScanStatus("Đang lên lịch tải lại nhật ký..."));
            }
        }).dispatch();
    }

    private void saveJournalEntryToFirestore(@NonNull String secureUrl, @NonNull String sourceLabel,
            @NonNull ImageSize imageSize) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finishJournalSaveWithError("Bạn cần đăng nhập để lưu nhật ký.");
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("imageUrl", secureUrl);
        payload.put("thumbUrl", buildCloudinaryThumbUrl(secureUrl, 480));
        payload.put("source", sourceLabel);
        payload.put("cameraFacing", "camera".equals(sourceLabel) ? (isUsingFrontCamera ? "front" : "back") : "none");
        payload.put("width", imageSize.width);
        payload.put("height", imageSize.height);
        payload.put("createdAt", new Date());
        payload.put("updatedAt", new Date());

        firestore.collection(FIRESTORE_USERS_COLLECTION)
                .document(user.getUid())
                .collection(FIRESTORE_JOURNAL_COLLECTION)
                .add(payload)
                .addOnSuccessListener(ref -> {
                    isJournalSaveInProgress = false;
                    setProcessingUiEnabled(true);
                    updateScanStatus("Đã lưu món ăn vào nhật ký");
                    Toast.makeText(this, "Đã lưu vào nhật ký", Toast.LENGTH_SHORT).show();
                    updateJournalEmptyState(
                            journalMomentAdapter == null ? 1 : journalMomentAdapter.getItemCount(),
                            "");
                })
                .addOnFailureListener(error -> finishJournalSaveWithError("Lưu Firestore thất bại. Vui lòng thử lại."));
    }

    private void finishJournalSaveWithError(@NonNull String errorMessage) {
        isJournalSaveInProgress = false;
        setProcessingUiEnabled(true);
        updateScanStatus(errorMessage);
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
    }

    private boolean ensureCloudinaryConfigured() {
        String cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME.trim();
        String apiKey = BuildConfig.CLOUDINARY_API_KEY.trim();
        String apiSecret = BuildConfig.CLOUDINARY_API_SECRET.trim();
        String uploadPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET.trim();

        boolean hasSignedCredentials = !TextUtils.isEmpty(apiKey) && !TextUtils.isEmpty(apiSecret);
        boolean hasUnsignedPreset = !TextUtils.isEmpty(uploadPreset);
        if (TextUtils.isEmpty(cloudName) || (!hasSignedCredentials && !hasUnsignedPreset)) {
            return false;
        }

        try {
            MediaManager.get();
            return true;
        } catch (IllegalStateException error) {
            HashMap<String, Object> config = new HashMap<>();
            config.put("cloud_name", cloudName);
            config.put("secure", true);
            if (!TextUtils.isEmpty(apiKey)) {
                config.put("api_key", apiKey);
            }
            if (!TextUtils.isEmpty(apiSecret)) {
                config.put("api_secret", apiSecret);
            }
            MediaManager.init(getApplicationContext(), config);
            return true;
        }
    }

    @Nullable
    private byte[] prepareJournalImageForUpload(@NonNull byte[] sourceBytes) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.length, bounds);

            int sourceWidth = Math.max(1, bounds.outWidth);
            int sourceHeight = Math.max(1, bounds.outHeight);
            int maxSide = Math.max(sourceWidth, sourceHeight);

            int inSampleSize = 1;
            while (maxSide / inSampleSize > JOURNAL_UPLOAD_MAX_DIMENSION_PX * 2) {
                inSampleSize *= 2;
            }

            BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
            decodeOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
            decodeOptions.inSampleSize = Math.max(1, inSampleSize);

            Bitmap decoded = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.length, decodeOptions);
            if (decoded == null) {
                return sourceBytes;
            }

            Bitmap scaled = decoded;
            int currentWidth = decoded.getWidth();
            int currentHeight = decoded.getHeight();
            int currentMax = Math.max(currentWidth, currentHeight);

            if (currentMax > JOURNAL_UPLOAD_MAX_DIMENSION_PX) {
                float ratio = JOURNAL_UPLOAD_MAX_DIMENSION_PX / (float) currentMax;
                int targetWidth = Math.max(1, Math.round(currentWidth * ratio));
                int targetHeight = Math.max(1, Math.round(currentHeight * ratio));
                scaled = Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true);
                if (scaled != decoded) {
                    decoded.recycle();
                }
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            boolean compressed = scaled.compress(Bitmap.CompressFormat.JPEG, JOURNAL_UPLOAD_QUALITY, outputStream);
            scaled.recycle();
            if (!compressed) {
                return null;
            }
            return outputStream.toByteArray();
        } catch (Exception error) {
            return null;
        }
    }

    @NonNull
    private ImageSize resolveImageSize(@NonNull byte[] bytes) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        return new ImageSize(Math.max(0, options.outWidth), Math.max(0, options.outHeight));
    }

    @NonNull
    private String buildCloudinaryThumbUrl(@NonNull String rawUrl, int targetSize) {
        if (TextUtils.isEmpty(rawUrl)) {
            return "";
        }
        String marker = "/image/upload/";
        int markerIndex = rawUrl.indexOf(marker);
        if (markerIndex < 0) {
            return rawUrl;
        }
        String prefix = rawUrl.substring(0, markerIndex + marker.length());
        String suffix = rawUrl.substring(markerIndex + marker.length());
        String transform = "c_fill,w_" + targetSize + ",h_" + targetSize + ",q_auto,f_auto/";
        return prefix + transform + suffix;
    }

    private void setProcessingUiEnabled(boolean enabled) {
        if (btnShutter != null) {
            btnShutter.setEnabled(enabled);
            btnShutter.setAlpha(enabled ? 1f : 0.72f);
        }
        if (btnGallery != null) {
            btnGallery.setEnabled(enabled);
            btnGallery.setAlpha(enabled ? 1f : 0.72f);
        }
        if (btnFlipCamera != null) {
            btnFlipCamera.setEnabled(enabled);
            btnFlipCamera.setAlpha(enabled ? 1f : 0.72f);
        }
        if (tabNhanDien != null) {
            tabNhanDien.setEnabled(enabled);
        }
        if (tabNhatKy != null) {
            tabNhatKy.setEnabled(enabled);
        }
    }

    private boolean isBusyProcessing() {
        return isRecognitionInProgress || isJournalSaveInProgress;
    }

    private void updateScanStatus(@NonNull String status) {
        if (txtScanStatus != null) {
            txtScanStatus.setText(status);
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
        if (imageProxy.getFormat() != ImageFormat.YUV_420_888 || imageProxy.getPlanes().length < 3) {
            return null;
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

    private static class ImageSize {
        final int width;
        final int height;

        ImageSize(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
}
