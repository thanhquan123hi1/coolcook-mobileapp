package com.coolcook.app.feature.auth.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.Guideline;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.coolcook.app.R;
import com.coolcook.app.feature.home.ui.HomeActivity;
import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.GoogleAuthProvider;

import java.util.Arrays;
import java.util.Locale;

public class AuthActivity extends AppCompatActivity {

    public static final String EXTRA_AUTH_MODE = "extra_auth_mode";
    public static final String MODE_LOGIN = "login";
    public static final String MODE_REGISTER = "register";

    private FirebaseAuth firebaseAuth;
    private GoogleSignInClient googleSignInClient;
    private CallbackManager facebookCallbackManager;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    private NestedScrollView authScroll;
    private View loginPanel;
    private View registerPanel;
    private View authLoadingOverlay;

    private View btnBack;
    private View btnSubmitLogin;
    private View btnSubmitRegister;
    private View btnGoogleLogin;
    private View btnFacebookLogin;
    private View btnGoogleRegister;
    private View btnFacebookRegister;
    private View txtSwitchToRegister;
    private View txtSwitchToLogin;

    private AppCompatCheckBox chkRemember;

    private TextInputLayout loginEmailLayout;
    private TextInputLayout loginPasswordLayout;
    private TextInputLayout registerNameLayout;
    private TextInputLayout registerEmailLayout;
    private TextInputLayout registerPasswordLayout;
    private TextInputLayout registerConfirmPasswordLayout;

    private EditText loginEmail;
    private EditText loginPassword;
    private EditText registerEmail;
    private EditText registerPassword;
    private EditText registerConfirmPassword;
    private EditText registerName;

    private boolean showingRegister;
    private boolean isTransitioning;
    private boolean isLoading;

    private String googleWebClientId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_auth);

        bindViews();
        setupFirebaseAuth();
        setupGoogleSignIn();
        setupFacebookSignIn();

        applyInsets();
        applyResponsiveTweaks();
        setupFieldInteractions();
        setupCheckboxAnimation();
        setupActions();

        String initialMode = getIntent().getStringExtra(EXTRA_AUTH_MODE);
        showingRegister = MODE_REGISTER.equals(initialMode);
        applyInitialState(showingRegister);
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkCurrentUser();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (facebookCallbackManager != null) {
            facebookCallbackManager.onActivityResult(requestCode, resultCode, data);
        }
    }

    public static Intent createIntent(AppCompatActivity activity, String mode) {
        Intent intent = new Intent(activity, AuthActivity.class);
        intent.putExtra(EXTRA_AUTH_MODE, mode);
        return intent;
    }

    private void bindViews() {
        authScroll = findViewById(R.id.authScroll);
        loginPanel = findViewById(R.id.loginPanel);
        registerPanel = findViewById(R.id.registerPanel);
        authLoadingOverlay = findViewById(R.id.authLoadingOverlay);

        btnBack = findViewById(R.id.btnBack);
        btnSubmitLogin = findViewById(R.id.btnSubmitLogin);
        btnSubmitRegister = findViewById(R.id.btnSubmitRegister);
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin);
        btnFacebookLogin = findViewById(R.id.btnFacebookLogin);
        btnGoogleRegister = findViewById(R.id.btnGoogleRegister);
        btnFacebookRegister = findViewById(R.id.btnFacebookRegister);
        txtSwitchToRegister = findViewById(R.id.txtSwitchToRegister);
        txtSwitchToLogin = findViewById(R.id.txtSwitchToLogin);

        chkRemember = findViewById(R.id.chkRemember);

        loginEmailLayout = findViewById(R.id.loginEmailLayout);
        loginPasswordLayout = findViewById(R.id.loginPasswordLayout);
        registerNameLayout = findViewById(R.id.registerNameLayout);
        registerEmailLayout = findViewById(R.id.registerEmailLayout);
        registerPasswordLayout = findViewById(R.id.registerPasswordLayout);
        registerConfirmPasswordLayout = findViewById(R.id.registerConfirmPasswordLayout);

        loginEmail = findViewById(R.id.loginEmail);
        loginPassword = findViewById(R.id.loginPassword);
        registerName = findViewById(R.id.registerName);
        registerEmail = findViewById(R.id.registerEmail);
        registerPassword = findViewById(R.id.registerPassword);
        registerConfirmPassword = findViewById(R.id.registerConfirmPassword);
    }

    private void setupFirebaseAuth() {
        firebaseAuth = FirebaseAuth.getInstance();
    }

    private void setupGoogleSignIn() {
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Intent data = result.getData();
                    if (data == null) {
                        setLoading(false);
                        Toast.makeText(this, R.string.auth_error_google_cancelled, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(data)
                                .getResult(ApiException.class);
                        if (account == null || account.getIdToken() == null) {
                            setLoading(false);
                            Toast.makeText(this, R.string.auth_error_google_failed, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        firebaseAuthWithGoogle(account.getIdToken());
                    } catch (ApiException ex) {
                        setLoading(false);
                        Toast.makeText(this, R.string.auth_error_google_failed, Toast.LENGTH_SHORT).show();
                    }
                });

        int webClientResId = getResources().getIdentifier(
                "default_web_client_id",
                "string",
                getPackageName());

        if (webClientResId != 0) {
            googleWebClientId = getString(webClientResId);
            GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(googleWebClientId)
                    .requestEmail()
                    .build();
            googleSignInClient = GoogleSignIn.getClient(this, options);
        }
    }

    private void setupFacebookSignIn() {
        facebookCallbackManager = CallbackManager.Factory.create();
        LoginManager.getInstance().registerCallback(facebookCallbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                handleFacebookAccessToken(loginResult.getAccessToken());
            }

            @Override
            public void onCancel() {
                setLoading(false);
                Toast.makeText(AuthActivity.this, R.string.auth_error_facebook_cancelled, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull FacebookException error) {
                setLoading(false);
                Toast.makeText(AuthActivity.this, R.string.auth_error_facebook_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupFieldInteractions() {
        clearAllErrors();
        setupClearErrorOnInput(loginEmail, loginEmailLayout);
        setupClearErrorOnInput(loginPassword, loginPasswordLayout);
        setupClearErrorOnInput(registerName, registerNameLayout);
        setupClearErrorOnInput(registerEmail, registerEmailLayout);
        setupClearErrorOnInput(registerPassword, registerPasswordLayout);
        setupClearErrorOnInput(registerConfirmPassword, registerConfirmPasswordLayout);
    }

    private void setupClearErrorOnInput(EditText input, TextInputLayout layout) {
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No-op.
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearFieldError(layout);
            }

            @Override
            public void afterTextChanged(Editable s) {
                // No-op.
            }
        });
    }

    private void setupCheckboxAnimation() {
        chkRemember.setOnCheckedChangeListener((buttonView, isChecked) -> {
            buttonView.animate().cancel();
            buttonView.setPivotX(buttonView.getWidth() * 0.14f);
            buttonView.setPivotY(buttonView.getHeight() * 0.5f);
            buttonView.setScaleX(0.94f);
            buttonView.setScaleY(0.94f);
            buttonView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(isChecked ? 180L : 140L)
                    .setInterpolator(isChecked
                            ? new OvershootInterpolator(0.55f)
                            : new DecelerateInterpolator())
                    .start();
        });
    }

    private void setupActions() {
        btnBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.slide_in_left_scale, R.anim.slide_out_right_scale);
        });

        txtSwitchToRegister.setOnClickListener(v -> switchMode(true));
        txtSwitchToLogin.setOnClickListener(v -> switchMode(false));

        btnSubmitLogin.setOnClickListener(v -> loginUser());
        btnSubmitRegister.setOnClickListener(v -> registerUser());

        btnGoogleLogin.setOnClickListener(v -> signInWithGoogle());
        btnGoogleRegister.setOnClickListener(v -> signInWithGoogle());

        btnFacebookLogin.setOnClickListener(v -> signInWithFacebook());
        btnFacebookRegister.setOnClickListener(v -> signInWithFacebook());
    }

    private boolean validateLoginForm() {
        clearFieldError(loginEmailLayout);
        clearFieldError(loginPasswordLayout);

        String email = getTrimmedText(loginEmail);
        String password = getText(loginPassword);

        if (TextUtils.isEmpty(email)) {
            showFieldError(loginEmailLayout, loginEmail, getString(R.string.auth_error_email_required));
            return false;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showFieldError(loginEmailLayout, loginEmail, getString(R.string.auth_error_email_invalid));
            return false;
        }

        if (TextUtils.isEmpty(password)) {
            showFieldError(loginPasswordLayout, loginPassword, getString(R.string.auth_error_password_required));
            return false;
        }

        if (password.length() < 6) {
            showFieldError(loginPasswordLayout, loginPassword, getString(R.string.auth_error_password_min));
            return false;
        }

        return true;
    }

    private boolean validateRegisterForm() {
        clearFieldError(registerNameLayout);
        clearFieldError(registerEmailLayout);
        clearFieldError(registerPasswordLayout);
        clearFieldError(registerConfirmPasswordLayout);

        String name = getTrimmedText(registerName);
        String email = getTrimmedText(registerEmail);
        String password = getText(registerPassword);
        String confirmPassword = getText(registerConfirmPassword);

        if (TextUtils.isEmpty(name)) {
            showFieldError(registerNameLayout, registerName, getString(R.string.auth_error_name_required));
            return false;
        }

        if (TextUtils.isEmpty(email)) {
            showFieldError(registerEmailLayout, registerEmail, getString(R.string.auth_error_email_required));
            return false;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showFieldError(registerEmailLayout, registerEmail, getString(R.string.auth_error_email_invalid));
            return false;
        }

        if (TextUtils.isEmpty(password)) {
            showFieldError(registerPasswordLayout, registerPassword, getString(R.string.auth_error_password_required));
            return false;
        }

        if (password.length() < 6) {
            showFieldError(registerPasswordLayout, registerPassword, getString(R.string.auth_error_password_min));
            return false;
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            showFieldError(registerConfirmPasswordLayout, registerConfirmPassword, getString(R.string.auth_error_confirm_password_required));
            return false;
        }

        if (!password.equals(confirmPassword)) {
            showFieldError(registerConfirmPasswordLayout, registerConfirmPassword, getString(R.string.auth_error_confirm_password_mismatch));
            return false;
        }

        return true;
    }

    private void registerUser() {
        if (isLoading || !validateRegisterForm()) {
            return;
        }

        String name = getTrimmedText(registerName);
        String email = getTrimmedText(registerEmail);
        String password = getText(registerPassword);

        setLoading(true);
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && firebaseAuth.getCurrentUser() != null) {
                        com.google.firebase.auth.UserProfileChangeRequest profileUpdates =
                                new com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                        .setDisplayName(name)
                                        .build();
                        firebaseAuth.getCurrentUser().updateProfile(profileUpdates)
                                .addOnCompleteListener(profileTask -> {
                                    setLoading(false);
                                    Toast.makeText(this, R.string.auth_success_register, Toast.LENGTH_SHORT).show();
                                    navigateToHome();
                                });
                    } else {
                        setLoading(false);
                        showAuthFailure(task.getException(), true);
                    }
                });
    }

    private void loginUser() {
        if (isLoading || !validateLoginForm()) {
            return;
        }

        String email = getTrimmedText(loginEmail);
        String password = getText(loginPassword);

        setLoading(true);
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        navigateToHome();
                    } else {
                        showAuthFailure(task.getException(), false);
                    }
                });
    }

    private void signInWithGoogle() {
        if (isLoading) {
            return;
        }
        if (googleSignInClient == null || TextUtils.isEmpty(googleWebClientId)) {
            Toast.makeText(this, R.string.auth_error_google_config_missing, Toast.LENGTH_LONG).show();
            return;
        }

        setLoading(true);
        googleSignInLauncher.launch(googleSignInClient.getSignInIntent());
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        navigateToHome();
                    } else {
                        showAuthFailure(task.getException(), false);
                    }
                });
    }

    private void signInWithFacebook() {
        if (isLoading) {
            return;
        }

        String facebookAppId = getString(R.string.facebook_app_id).trim();
        String facebookClientToken = getString(R.string.facebook_client_token).trim();
        String facebookProtocolScheme = getString(R.string.fb_login_protocol_scheme).trim();

        String normalizedAppId = facebookAppId.toLowerCase(Locale.ROOT).startsWith("fb")
                ? facebookAppId.substring(2)
                : facebookAppId;
        String expectedScheme = "fb" + normalizedAppId;

        boolean missingAppId = TextUtils.isEmpty(normalizedAppId) || normalizedAppId.startsWith("YOUR_");
        boolean missingClientToken = TextUtils.isEmpty(facebookClientToken) || facebookClientToken.startsWith("YOUR_");
        boolean invalidScheme = TextUtils.isEmpty(facebookProtocolScheme)
                || facebookProtocolScheme.startsWith("fbYOUR_")
                || !facebookProtocolScheme.equals(expectedScheme);

        if (missingAppId || missingClientToken || invalidScheme) {
            Toast.makeText(this, R.string.auth_error_facebook_config_missing, Toast.LENGTH_LONG).show();
            return;
        }

        setLoading(true);
        LoginManager.getInstance().logInWithReadPermissions(this, Arrays.asList("email", "public_profile"));
    }

    private void handleFacebookAccessToken(AccessToken token) {
        AuthCredential credential = FacebookAuthProvider.getCredential(token.getToken());
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        navigateToHome();
                    } else {
                        showAuthFailure(task.getException(), false);
                    }
                });
    }

    private void navigateToHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void checkCurrentUser() {
        if (firebaseAuth.getCurrentUser() != null) {
            navigateToHome();
        }
    }

    private void setLoading(boolean loading) {
        isLoading = loading;
        authLoadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);

        setViewEnabled(btnBack, !loading);
        setViewEnabled(btnSubmitLogin, !loading);
        setViewEnabled(btnSubmitRegister, !loading);
        setViewEnabled(btnGoogleLogin, !loading);
        setViewEnabled(btnFacebookLogin, !loading);
        setViewEnabled(btnGoogleRegister, !loading);
        setViewEnabled(btnFacebookRegister, !loading);
        setViewEnabled(txtSwitchToRegister, !loading);
        setViewEnabled(txtSwitchToLogin, !loading);
        setViewEnabled(chkRemember, !loading);

        setFieldEnabled(loginEmail, !loading);
        setFieldEnabled(loginPassword, !loading);
        setFieldEnabled(registerName, !loading);
        setFieldEnabled(registerEmail, !loading);
        setFieldEnabled(registerPassword, !loading);
        setFieldEnabled(registerConfirmPassword, !loading);
    }

    private void setFieldEnabled(EditText input, boolean enabled) {
        input.setEnabled(enabled);
        input.setFocusable(enabled);
        input.setFocusableInTouchMode(enabled);
        input.setClickable(enabled);
        input.setLongClickable(enabled);
        input.setAlpha(enabled ? 1f : 0.8f);
    }

    private void setViewEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setClickable(enabled);
        view.setAlpha(enabled ? 1f : 0.6f);
    }

    private void showAuthFailure(Exception exception, boolean registerFlow) {
        String message = getReadableAuthError(exception, registerFlow);

        if (registerFlow && exception instanceof FirebaseAuthUserCollisionException) {
            showFieldError(registerEmailLayout, registerEmail, message);
        } else if (!registerFlow && (exception instanceof FirebaseAuthInvalidCredentialsException
                || exception instanceof FirebaseAuthInvalidUserException)) {
            showFieldError(loginPasswordLayout, loginPassword, message);
        }

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String getReadableAuthError(Exception exception, boolean registerFlow) {
        if (exception instanceof FirebaseAuthUserCollisionException) {
            return getString(R.string.auth_error_user_exists);
        }

        if (exception instanceof FirebaseAuthInvalidCredentialsException
                || exception instanceof FirebaseAuthInvalidUserException) {
            return getString(R.string.auth_error_invalid_credentials);
        }

        if (exception instanceof FirebaseNetworkException) {
            return getString(R.string.auth_error_network);
        }

        if (exception != null) {
            android.util.Log.e("AuthActivity", "Auth Error: ", exception);
            return exception.getMessage() != null
                    ? exception.getMessage()
                    : getString(registerFlow
                            ? R.string.auth_error_register_failed
                            : R.string.auth_error_login_failed);
        }

        return getString(registerFlow
                ? R.string.auth_error_register_failed
                : R.string.auth_error_login_failed);
    }

    private void applyInsets() {
        View root = findViewById(R.id.authRoot);
        View header = findViewById(R.id.authHeader);
        MaterialCardView card = findViewById(R.id.authCard);

        final int baseHeaderTop = dp(12);
        final int baseCardBottom = dp(8);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            header.setPadding(
                    header.getPaddingLeft(),
                    baseHeaderTop + systemBars.top,
                    header.getPaddingRight(),
                    header.getPaddingBottom());

            card.setPadding(
                    card.getPaddingLeft(),
                    card.getPaddingTop(),
                    card.getPaddingRight(),
                    baseCardBottom + systemBars.bottom);

            return insets;
        });
    }

    private void applyResponsiveTweaks() {
        float density = getResources().getDisplayMetrics().density;
        float heightDp = getResources().getDisplayMetrics().heightPixels / density;

        if (heightDp <= 700f) {
            setGuidelinePercent(R.id.guideCardTop, 0.24f);
            setGuidelinePercent(R.id.guideImageBottom, 0.50f);
            View content = findViewById(R.id.authContentContainer);
            content.setPadding(dp(24), dp(20), dp(24), dp(12));
        } else if (heightDp >= 880f) {
            setGuidelinePercent(R.id.guideCardTop, 0.32f);
            setGuidelinePercent(R.id.guideImageBottom, 0.57f);
        }
    }

    private void setGuidelinePercent(@IdRes int guidelineId, float percent) {
        Guideline guideline = findViewById(guidelineId);
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) guideline.getLayoutParams();
        params.guidePercent = percent;
        guideline.setLayoutParams(params);
    }

    private void clearAllErrors() {
        clearFieldError(loginEmailLayout);
        clearFieldError(loginPasswordLayout);
        clearFieldError(registerNameLayout);
        clearFieldError(registerEmailLayout);
        clearFieldError(registerPasswordLayout);
        clearFieldError(registerConfirmPasswordLayout);
    }

    private void clearFieldError(TextInputLayout layout) {
        layout.setError(null);
        layout.setErrorEnabled(false);
    }

    private void showFieldError(TextInputLayout layout, EditText input, String message) {
        layout.setErrorEnabled(true);
        layout.setError(message);
        input.requestFocus();
        input.setSelection(input.getText() == null ? 0 : input.getText().length());
    }

    private String getText(EditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    private String getTrimmedText(EditText input) {
        return getText(input).trim();
    }

    private void applyInitialState(boolean register) {
        clearAllErrors();
        authScroll.scrollTo(0, 0);
        if (register) {
            registerPanel.setVisibility(View.VISIBLE);
            registerPanel.setAlpha(1f);
            loginPanel.setVisibility(View.GONE);
        } else {
            loginPanel.setVisibility(View.VISIBLE);
            loginPanel.setAlpha(1f);
            registerPanel.setVisibility(View.GONE);
        }
    }

    private void switchMode(boolean targetRegister) {
        if (isLoading || isTransitioning || targetRegister == showingRegister) {
            return;
        }

        clearAllErrors();
        authScroll.scrollTo(0, 0);
        isTransitioning = true;

        final View outgoing = showingRegister ? registerPanel : loginPanel;
        final View incoming = targetRegister ? registerPanel : loginPanel;

        outgoing.animate().cancel();
        incoming.animate().cancel();

        outgoing.animate()
                .alpha(0f)
                .scaleX(0.97f)
                .scaleY(0.97f)
                .translationY(dp(12))
                .setDuration(140)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    outgoing.setVisibility(View.GONE);
                    outgoing.setAlpha(1f);
                    outgoing.setScaleX(1f);
                    outgoing.setScaleY(1f);
                    outgoing.setTranslationY(0f);

                    incoming.setVisibility(View.VISIBLE);
                    incoming.setAlpha(0f);
                    incoming.setScaleX(0.98f);
                    incoming.setScaleY(0.98f);
                    incoming.setTranslationY(dp(18));

                    incoming.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .scaleX(1.01f)
                            .scaleY(1.01f)
                            .setDuration(180)
                            .setInterpolator(new OvershootInterpolator(0.45f))
                            .withEndAction(() -> incoming.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(70)
                                    .setInterpolator(new DecelerateInterpolator())
                                    .withEndAction(() -> {
                                        showingRegister = targetRegister;
                                        isTransitioning = false;
                                    })
                                    .start())
                            .start();
                })
                .start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
