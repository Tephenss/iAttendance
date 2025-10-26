package com.example.iattendance;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import android.widget.ScrollView;
import android.view.ViewTreeObserver;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.animation.ValueAnimator;
import android.animation.ObjectAnimator;
import android.view.View;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    private EditText idEditText;
    private EditText passwordEditText;
    private ImageView togglePasswordVisibility;
    private MaterialButton loginButton;
    private View recoverButton;
    private ImageView infoButton;
    private ImageView logoImageView;
    private TextView welcomeText;
    private ScrollView scrollView;
    private FirebaseAuthHelper firebaseAuthHelper;
    private boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Check if user is already logged in
        SessionManager sessionManager = new SessionManager(this);
        if (sessionManager.isLoggedIn()) {
            // User is logged in, navigate to appropriate dashboard
            navigateToDashboard(sessionManager);
            return;
        }

        // Initialize views
        initViews();

        // Initialize Firebase Auth Helper
        firebaseAuthHelper = new FirebaseAuthHelper(this);

        // Set up click listeners
        setupClickListeners();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
    

    private void initViews() {
        idEditText = findViewById(R.id.idEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        togglePasswordVisibility = findViewById(R.id.togglePasswordVisibility);
        loginButton = findViewById(R.id.loginButton);
        recoverButton = findViewById(R.id.recoverButton);
        infoButton = findViewById(R.id.infoButton);
        logoImageView = findViewById(R.id.logoImageView);
        welcomeText = findViewById(R.id.welcomeText);
        scrollView = findViewById(R.id.scrollView);
        
        // Start logo animation
        startLogoAnimation();
        
        // Set welcome text with green "iAttendance"
        setWelcomeTextColor();
    }

    private void setupClickListeners() {
        loginButton.setOnClickListener(v -> performLogin());
        
        recoverButton.setOnClickListener(v -> {
            showRecoveryModal();
        });
        
        // Info button
        infoButton.setOnClickListener(v -> {
            showInfoModal();
        });
        
        // Password visibility toggle
        togglePasswordVisibility.setOnClickListener(v -> togglePasswordVisibility());
    }

    private void performLogin() {
        String id = idEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        // Validate inputs
        if (id.isEmpty()) {
            showError("ID is required");
            return;
        }

        if (password.isEmpty()) {
            showError("Password is required");
            return;
        }

        // Show loading state
        loginButton.setEnabled(false);
        loginButton.setText("LOGGING IN...");

        // For testing - try with test credentials first
        if (id.equals("test") && password.equals("test")) {
            Toast.makeText(this, "Testing Firebase connection...", Toast.LENGTH_SHORT).show();
        }

        // Use Firebase Database Authentication (search in your actual Firebase data)
        firebaseAuthHelper.signInWithIdAndPassword(id, password, new FirebaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String userType, String userId, String fullName, String email) {
                runOnUiThread(() -> {
                    loginButton.setEnabled(true);
                    loginButton.setText("LOGIN");

                    // Create login session
                    SessionManager sessionManager = new SessionManager(MainActivity.this);
                    sessionManager.createLoginSession(userId, userType, fullName, email);

                    // Navigate to email verification with smooth transition
                    Intent intent = new Intent(MainActivity.this, EmailVerificationActivity.class);
                    intent.putExtra("userId", userId);
                    intent.putExtra("userType", userType);
                    intent.putExtra("fullName", fullName);
                    intent.putExtra("email", email);
                    startActivity(intent);
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                    finish();
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    loginButton.setEnabled(true);
                    loginButton.setText("LOGIN");
                    showError(errorMessage);
                });
            }
        });
    }

    private void simulateLogin(String id, String password) {
        // Simulate network delay
        loginButton.postDelayed(() -> {
            // Reset button state
            loginButton.setEnabled(true);
            loginButton.setText("LOGIN");

            // For demo purposes, accept any login
            if (id.length() >= 3 && password.length() >= 3) {
                showSuccess("Login successful! Welcome to iAttendance");
                // TODO: Navigate to dashboard or verification screen
                // Intent intent = new Intent(this, DashboardActivity.class);
                // startActivity(intent);
                // finish();
            } else {
                showError("Invalid ID or password");
            }
        }, 2000);
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showSuccess(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
    

    private void showRecoveryModal() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        
        // Set custom layout
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_recovery, null);
        builder.setView(dialogView);
        
        // Get views from custom layout
        android.widget.TextView titleText = dialogView.findViewById(R.id.titleText);
        android.widget.TextView messageText = dialogView.findViewById(R.id.messageText);
        android.widget.TextView noteText = dialogView.findViewById(R.id.noteText);
        com.google.android.material.button.MaterialButton okButton = dialogView.findViewById(R.id.okButton);
        
        // Set text content
        titleText.setText(R.string.recovery_title);
        messageText.setText(R.string.recovery_message);
        noteText.setText(R.string.recovery_note);
        
        // Create dialog
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        
        // Set dialog properties
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        
        // Set button click listener
        okButton.setOnClickListener(v -> dialog.dismiss());
        
        // Show dialog
        dialog.show();
    }

    private void showInfoModal() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        
        // Set custom layout
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_info, null);
        builder.setView(dialogView);
        
        // Get views from custom layout
        android.widget.TextView titleText = dialogView.findViewById(R.id.titleText);
        android.widget.TextView messageText = dialogView.findViewById(R.id.messageText);
        android.widget.TextView noteText = dialogView.findViewById(R.id.noteText);
        com.google.android.material.button.MaterialButton okButton = dialogView.findViewById(R.id.okButton);
        
        // Set text content
        titleText.setText(R.string.info_title);
        messageText.setText(R.string.info_message);
        noteText.setText(R.string.info_note);
        
        // Create dialog
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        
        // Set dialog properties
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        
        // Set button click listener
        okButton.setOnClickListener(v -> dialog.dismiss());
        
        // Show dialog
        dialog.show();
    }

    private void startLogoAnimation() {
        if (logoImageView != null) {
            android.util.Log.d("MainActivity", "Starting logo animation");
            
            // Create floating animation using ObjectAnimator
            ObjectAnimator floatAnimator = ObjectAnimator.ofFloat(logoImageView, "translationY", 0f, -30f);
            floatAnimator.setDuration(2000);
            floatAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            floatAnimator.setRepeatMode(ObjectAnimator.REVERSE);
            floatAnimator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            
            // Create scale animation
            ObjectAnimator scaleAnimator = ObjectAnimator.ofFloat(logoImageView, "scaleX", 1.0f, 1.1f);
            scaleAnimator.setDuration(2000);
            scaleAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            scaleAnimator.setRepeatMode(ObjectAnimator.REVERSE);
            
            ObjectAnimator scaleYAnimator = ObjectAnimator.ofFloat(logoImageView, "scaleY", 1.0f, 1.1f);
            scaleYAnimator.setDuration(2000);
            scaleYAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            scaleYAnimator.setRepeatMode(ObjectAnimator.REVERSE);
            
            // Start animations
            floatAnimator.start();
            scaleAnimator.start();
            scaleYAnimator.start();
            
            android.util.Log.d("MainActivity", "Animations started");
        } else {
            android.util.Log.e("MainActivity", "Logo ImageView is null");
        }
    }

    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            passwordEditText.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            togglePasswordVisibility.setImageResource(R.drawable.ic_eye_off);
        } else {
            passwordEditText.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            togglePasswordVisibility.setImageResource(R.drawable.ic_eye);
        }
        isPasswordVisible = !isPasswordVisible;
        passwordEditText.setSelection(passwordEditText.getText().length());
    }

    private void setWelcomeTextColor() {
        if (welcomeText != null) {
            String fullText = getString(R.string.welcome_text);
            SpannableString spannableString = new SpannableString(fullText);

            // Find "iAttendance" in the text and make it green
            int startIndex = fullText.indexOf("iAttendance");
            if (startIndex != -1) {
                int endIndex = startIndex + "iAttendance".length();
                spannableString.setSpan(new ForegroundColorSpan(android.graphics.Color.parseColor("#00b341")),
                    startIndex, endIndex, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            welcomeText.setText(spannableString);
        }
    }

    private void navigateToDashboard(SessionManager sessionManager) {
        String userType = sessionManager.getUserType();
        String userId = sessionManager.getUserId();
        String fullName = sessionManager.getFullName();
        String email = sessionManager.getEmail();

        Intent intent;
        if ("student".equals(userType)) {
            intent = new Intent(this, StudentDashboardActivity.class);
        } else if ("teacher".equals(userType)) {
            intent = new Intent(this, TeacherDashboardActivity.class);
        } else if ("admin".equals(userType)) {
            intent = new Intent(this, AdminDashboardActivity.class);
        } else {
            // Fallback to login if user type is invalid
            return;
        }

        intent.putExtra("userId", userId);
        intent.putExtra("userType", userType);
        intent.putExtra("fullName", fullName);
        intent.putExtra("email", email);
        startActivity(intent);
        finish();
    }
}