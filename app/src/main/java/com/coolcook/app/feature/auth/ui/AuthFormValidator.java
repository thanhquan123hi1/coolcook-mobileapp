package com.coolcook.app.feature.auth.ui;

import android.text.TextUtils;
import android.util.Patterns;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.coolcook.app.R;
import com.google.android.material.textfield.TextInputLayout;

final class AuthFormValidator {

    private final AuthActivity activity;
    private final TextInputLayout loginEmailLayout;
    private final TextInputLayout loginPasswordLayout;
    private final TextInputLayout registerNameLayout;
    private final TextInputLayout registerEmailLayout;
    private final TextInputLayout registerPasswordLayout;
    private final TextInputLayout registerConfirmPasswordLayout;
    private final EditText loginEmail;
    private final EditText loginPassword;
    private final EditText registerName;
    private final EditText registerEmail;
    private final EditText registerPassword;
    private final EditText registerConfirmPassword;

    AuthFormValidator(
            @NonNull AuthActivity activity,
            @NonNull TextInputLayout loginEmailLayout,
            @NonNull TextInputLayout loginPasswordLayout,
            @NonNull TextInputLayout registerNameLayout,
            @NonNull TextInputLayout registerEmailLayout,
            @NonNull TextInputLayout registerPasswordLayout,
            @NonNull TextInputLayout registerConfirmPasswordLayout,
            @NonNull EditText loginEmail,
            @NonNull EditText loginPassword,
            @NonNull EditText registerName,
            @NonNull EditText registerEmail,
            @NonNull EditText registerPassword,
            @NonNull EditText registerConfirmPassword) {
        this.activity = activity;
        this.loginEmailLayout = loginEmailLayout;
        this.loginPasswordLayout = loginPasswordLayout;
        this.registerNameLayout = registerNameLayout;
        this.registerEmailLayout = registerEmailLayout;
        this.registerPasswordLayout = registerPasswordLayout;
        this.registerConfirmPasswordLayout = registerConfirmPasswordLayout;
        this.loginEmail = loginEmail;
        this.loginPassword = loginPassword;
        this.registerName = registerName;
        this.registerEmail = registerEmail;
        this.registerPassword = registerPassword;
        this.registerConfirmPassword = registerConfirmPassword;
    }

    boolean validateLoginForm() {
        clearFieldError(loginEmailLayout);
        clearFieldError(loginPasswordLayout);

        String email = getTrimmedText(loginEmail);
        String password = getText(loginPassword);

        if (TextUtils.isEmpty(email)) {
            showFieldError(loginEmailLayout, loginEmail, activity.getString(R.string.auth_error_email_required));
            return false;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showFieldError(loginEmailLayout, loginEmail, activity.getString(R.string.auth_error_email_invalid));
            return false;
        }

        if (TextUtils.isEmpty(password)) {
            showFieldError(loginPasswordLayout, loginPassword, activity.getString(R.string.auth_error_password_required));
            return false;
        }

        if (password.length() < 6) {
            showFieldError(loginPasswordLayout, loginPassword, activity.getString(R.string.auth_error_password_min));
            return false;
        }

        return true;
    }

    boolean validateRegisterForm() {
        clearFieldError(registerNameLayout);
        clearFieldError(registerEmailLayout);
        clearFieldError(registerPasswordLayout);
        clearFieldError(registerConfirmPasswordLayout);

        String name = getTrimmedText(registerName);
        String email = getTrimmedText(registerEmail);
        String password = getText(registerPassword);
        String confirmPassword = getText(registerConfirmPassword);

        if (TextUtils.isEmpty(name)) {
            showFieldError(registerNameLayout, registerName, activity.getString(R.string.auth_error_name_required));
            return false;
        }

        if (TextUtils.isEmpty(email)) {
            showFieldError(registerEmailLayout, registerEmail, activity.getString(R.string.auth_error_email_required));
            return false;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showFieldError(registerEmailLayout, registerEmail, activity.getString(R.string.auth_error_email_invalid));
            return false;
        }

        if (TextUtils.isEmpty(password)) {
            showFieldError(registerPasswordLayout, registerPassword, activity.getString(R.string.auth_error_password_required));
            return false;
        }

        if (password.length() < 6) {
            showFieldError(registerPasswordLayout, registerPassword, activity.getString(R.string.auth_error_password_min));
            return false;
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            showFieldError(
                    registerConfirmPasswordLayout,
                    registerConfirmPassword,
                    activity.getString(R.string.auth_error_confirm_password_required));
            return false;
        }

        if (!password.equals(confirmPassword)) {
            showFieldError(
                    registerConfirmPasswordLayout,
                    registerConfirmPassword,
                    activity.getString(R.string.auth_error_confirm_password_mismatch));
            return false;
        }

        return true;
    }

    void clearAllErrors() {
        clearFieldError(loginEmailLayout);
        clearFieldError(loginPasswordLayout);
        clearFieldError(registerNameLayout);
        clearFieldError(registerEmailLayout);
        clearFieldError(registerPasswordLayout);
        clearFieldError(registerConfirmPasswordLayout);
    }

    void clearFieldError(@NonNull TextInputLayout layout) {
        layout.setError(null);
        layout.setErrorEnabled(false);
    }

    void showFieldError(@NonNull TextInputLayout layout, @NonNull EditText input, @NonNull String message) {
        layout.setErrorEnabled(true);
        layout.setError(message);
        input.requestFocus();
        input.setSelection(input.getText() == null ? 0 : input.getText().length());
    }

    @NonNull
    String getText(@NonNull EditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    @NonNull
    String getTrimmedText(@NonNull EditText input) {
        return getText(input).trim();
    }
}
