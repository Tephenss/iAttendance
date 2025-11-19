package com.example.iattendance;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class EmailVerificationActivity extends AppCompatActivity {

    private TextView emailDisplayText;
    private EditText verificationCodeEditText;
    private MaterialButton verifyButton;
    private TextView resendButton;
    private TextView cooldownText;
    private ImageView verificationStatusIcon;
    private EditText[] codeInputs;
    
    private String userId;
    private String userType;
    private String fullName;
    private String email;
    private DatabaseReference mDatabase;
    
    private int countdownSeconds = 1800; // 30 minutes (matching website)
    private boolean isTimerRunning = false;
    private String currentVerificationCode; // Store code locally for faster verification
    private int cooldownSeconds = 0; // Resend cooldown timer
    private static final long CODE_EXPIRATION_TIME = 30 * 60 * 1000; // 30 minutes in milliseconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_verification);
        
        // Get data from intent
        userId = getIntent().getStringExtra("userId");
        userType = getIntent().getStringExtra("userType");
        fullName = getIntent().getStringExtra("fullName");
        email = getIntent().getStringExtra("email");
        
        // Initialize Firebase
        mDatabase = FirebaseDatabase.getInstance().getReference();
        
        // Check if account is deleted before proceeding
        checkAccountStatus();
    }
    
    private void checkAccountStatus() {
        android.util.Log.d("EmailVerification", "=== CHECKING ACCOUNT STATUS ===");
        android.util.Log.d("EmailVerification", "User ID: " + userId);
        android.util.Log.d("EmailVerification", "User Type: " + userType);
        
        // Determine the correct path based on user type
        String userPath;
        if ("student".equals(userType)) {
            userPath = "attendance_system/students";
        } else if ("teacher".equals(userType)) {
            userPath = "attendance_system/teachers";
        } else if ("admin".equals(userType)) {
            userPath = "attendance_system/admins";
        } else {
            // Unknown user type, block access
            android.util.Log.e("EmailVerification", "Invalid user type: " + userType);
            showErrorAndReturn("Invalid user type");
            return;
        }
        
        android.util.Log.d("EmailVerification", "Searching in path: " + userPath);
        
        // Search for the user in Firebase
        mDatabase.child(userPath).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                android.util.Log.d("EmailVerification", "Data received: " + snapshot.getChildrenCount() + " records");
                boolean accountFound = false;
                boolean isDeleted = false;
                
                // Look through all records
                for (com.google.firebase.database.DataSnapshot userSnapshot : snapshot.getChildren()) {
                    android.util.Log.d("EmailVerification", "Checking record: " + userSnapshot.getKey());
                    com.google.firebase.database.DataSnapshot dataSnapshot = userSnapshot.child("data");
                    if (dataSnapshot.exists()) {
                        android.util.Log.d("EmailVerification", "Found 'data' child in record");
                        
                        // Check if this is the correct user
                        String[] idFields;
                        if ("student".equals(userType)) {
                            idFields = new String[]{"student_id", "id", "user_id", "username"};
                        } else if ("teacher".equals(userType)) {
                            idFields = new String[]{"teacher_id", "id", "user_id", "username"};
                        } else {
                            idFields = new String[]{"admin_id", "id", "user_id", "username"};
                        }
                        
                        boolean isCorrectUser = false;
                        
                        for (String idField : idFields) {
                            String foundId = dataSnapshot.child(idField).getValue(String.class);
                            android.util.Log.d("EmailVerification", "Checking ID field '" + idField + "': " + foundId);
                            if (foundId != null && foundId.equals(userId)) {
                                android.util.Log.d("EmailVerification", "✓ Found matching ID: " + foundId);
                                isCorrectUser = true;
                                accountFound = true;
                                break;
                            }
                        }
                        
                        if (isCorrectUser) {
                            // Check if account is deleted - check both data child and root level
                            Object isDeletedObj = dataSnapshot.child("is_deleted").getValue();
                            android.util.Log.d("EmailVerification", "Checking is_deleted in data child: " + isDeletedObj);
                            
                            if (isDeletedObj == null) {
                                // Also check at root level of user record
                                isDeletedObj = userSnapshot.child("is_deleted").getValue();
                                android.util.Log.d("EmailVerification", "Checking is_deleted in root: " + isDeletedObj);
                            }
                            
                            if (isDeletedObj != null) {
                                android.util.Log.d("EmailVerification", "is_deleted value found: " + isDeletedObj + " (type: " + isDeletedObj.getClass().getSimpleName() + ")");
                                if (isDeletedObj instanceof Boolean) {
                                    isDeleted = (Boolean) isDeletedObj;
                                } else if (isDeletedObj instanceof Number) {
                                    isDeleted = ((Number) isDeletedObj).intValue() == 1;
                                } else if (isDeletedObj instanceof String) {
                                    String deletedStr = ((String) isDeletedObj).trim();
                                    isDeleted = "1".equals(deletedStr) || "true".equalsIgnoreCase(deletedStr);
                                    // Also check if it's NOT "0" or "false" (defensive check)
                                    if (!isDeleted && !"0".equals(deletedStr) && !"false".equalsIgnoreCase(deletedStr)) {
                                        // If it's not a recognized value, treat as deleted for safety
                                        android.util.Log.w("EmailVerification", "Unknown is_deleted value: " + deletedStr + " - treating as deleted for safety");
                                        isDeleted = true;
                                    }
                                }
                                android.util.Log.d("EmailVerification", "is_deleted parsed as: " + isDeleted);
                            } else {
                                android.util.Log.d("EmailVerification", "is_deleted field not found - assuming account is active");
                            }
                            break;
                        }
                    } else {
                        android.util.Log.d("EmailVerification", "No 'data' child found in record");
                    }
                }
                
                android.util.Log.d("EmailVerification", "Account found: " + accountFound + ", Is deleted: " + isDeleted);
                
                if (!accountFound) {
                    android.util.Log.e("EmailVerification", "Account not found in database");
                    showErrorAndReturn("Account not found. Please contact support.");
                } else if (isDeleted) {
                    android.util.Log.e("EmailVerification", "✗ Account is deleted - BLOCKING ACCESS");
                    showErrorAndReturn("This account has been deleted. Please contact the administrator to restore your account.");
                } else {
                    // Account is valid, proceed with initialization
                    android.util.Log.d("EmailVerification", "✓ Account status verified - proceeding");
                    initializeActivity();
                }
            }
            
            @Override
            public void onCancelled(DatabaseError error) {
                android.util.Log.e("EmailVerification", "Error checking account status: " + error.getMessage());
                showErrorAndReturn("Error verifying account. Please try again.");
            }
        });
    }
    
    private void showErrorAndReturn(String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            
            // Clear session
            SessionManager sessionManager = new SessionManager(this);
            sessionManager.logout();
            
            // Return to login
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
    
    private void initializeActivity() {
        // Initialize views
        initViews();
        
        // Set up click listeners
        setupClickListeners();
        
        // Check if there's an existing valid verification code first
        checkExistingVerificationCode();
    }
    
    private void initViews() {
        emailDisplayText = findViewById(R.id.emailDisplayText);
        verificationCodeEditText = findViewById(R.id.verificationCodeEditText);
        verifyButton = findViewById(R.id.verifyButton);
        resendButton = findViewById(R.id.resendButton);
        cooldownText = findViewById(R.id.cooldownText);
        verificationStatusIcon = findViewById(R.id.verificationStatusIcon);

        // Initialize code input array
        codeInputs = new EditText[]{
            findViewById(R.id.codeInput1),
            findViewById(R.id.codeInput2),
            findViewById(R.id.codeInput3),
            findViewById(R.id.codeInput4),
            findViewById(R.id.codeInput5),
            findViewById(R.id.codeInput6)
        };
        
        // Set email display
        emailDisplayText.setText(email);
        
        // Initially disable resend button (cooldown active)
        resendButton.setEnabled(false);
        resendButton.setAlpha(0.6f);
        
        // Setup code input listeners
        setupCodeInputListeners();
    }
    
    private void setupClickListeners() {
        android.util.Log.d("EmailVerification", "Setting up click listeners");
        
        verifyButton.setOnClickListener(v -> {
            android.util.Log.d("EmailVerification", "Verify button clicked in listener");
            verifyCode();
        });
        
        resendButton.setOnClickListener(v -> {
            android.util.Log.d("EmailVerification", "Resend button clicked");
            resendCode();
        });
        
        android.util.Log.d("EmailVerification", "Click listeners set up successfully");
    }
    
    private void setupCodeInputListeners() {
        if (codeInputs == null) {
            android.util.Log.e("EmailVerification", "setupCodeInputListeners: codeInputs is null");
            return;
        }
        
        for (int i = 0; i < codeInputs.length; i++) {
            final int index = i;
            EditText input = codeInputs[i];
            
            if (input == null) {
                android.util.Log.e("EmailVerification", "setupCodeInputListeners: input " + i + " is null");
                continue;
            }
            
            input.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    android.util.Log.d("EmailVerification", "Input " + (index + 1) + " changed: '" + s + "'");
                    
                    if (s.length() == 1) {
                        // Move to next input
                        if (index < codeInputs.length - 1 && codeInputs[index + 1] != null) {
                            android.util.Log.d("EmailVerification", "Moving to next input: " + (index + 2));
                            codeInputs[index + 1].requestFocus();
                            // Select all text in next input for easy replacement
                            codeInputs[index + 1].setSelection(codeInputs[index + 1].getText().length());
                        } else {
                            // Last input, hide keyboard
                            android.util.Log.d("EmailVerification", "Last input filled, hiding keyboard");
                            input.clearFocus();
                            hideKeyboard();
                        }
                    } else if (s.length() > 1) {
                        // Handle paste or multiple characters
                        android.util.Log.d("EmailVerification", "Multiple characters detected, keeping only last");
                        String lastChar = s.toString().substring(s.length() - 1);
                        input.setText(lastChar);
                        input.setSelection(1);
                        
                        // Move to next input after paste
                        if (index < codeInputs.length - 1 && codeInputs[index + 1] != null) {
                            codeInputs[index + 1].requestFocus();
                        }
                    }
                    
                    updateVerificationCode();
                }
                
                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
            
            input.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == android.view.KeyEvent.KEYCODE_DEL && event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                    android.util.Log.d("EmailVerification", "Backspace pressed on input " + (index + 1));
                    
                    if (input.getText().toString().isEmpty()) {
                        // Current input is empty, move to previous and clear it
                        if (index > 0 && codeInputs[index - 1] != null) {
                            android.util.Log.d("EmailVerification", "Moving to previous input: " + index);
                            codeInputs[index - 1].requestFocus();
                            codeInputs[index - 1].setText("");
                            codeInputs[index - 1].setSelection(0);
                            return true;
                        }
                    } else {
                        // Current input has text, clear it
                        android.util.Log.d("EmailVerification", "Clearing current input: " + (index + 1));
                        input.setText("");
                        return true;
                    }
                }
                return false;
            });
            
            // Handle focus changes
            input.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    android.util.Log.d("EmailVerification", "Input " + (index + 1) + " gained focus");
                    // Select all text when focused for easy replacement
                    input.post(() -> input.setSelection(input.getText().length()));
                }
            });
        }
    }
    
    private void updateVerificationCode() {
        if (codeInputs == null || verificationCodeEditText == null) {
            android.util.Log.e("EmailVerification", "updateVerificationCode: null references");
            return;
        }
        
        StringBuilder code = new StringBuilder();
        for (EditText input : codeInputs) {
            if (input != null) {
                code.append(input.getText().toString());
            }
        }
        verificationCodeEditText.setText(code.toString());
    }
    
    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            android.view.View currentFocus = getCurrentFocus();
            if (currentFocus != null) {
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            } else {
                // Fallback: hide keyboard from any view
                imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
            }
        }
    }
    
    private void startCountdownTimer() {
        // Stop any existing timer
        isTimerRunning = false;
        
        // Start new timer
        isTimerRunning = true;
        new Thread(() -> {
            while (countdownSeconds > 0 && isTimerRunning) {
                runOnUiThread(() -> {
                    int minutes = countdownSeconds / 60;
                    int seconds = countdownSeconds % 60;
                    // Timer display removed as per website design
                });
                
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
                countdownSeconds--;
            }
            
            if (isTimerRunning) {
                runOnUiThread(() -> {
                    resendButton.setEnabled(true);
                    resendButton.setAlpha(1.0f); // Make button fully visible
                    android.util.Log.d("EmailVerification", "Timer expired, resend button enabled");
                });
            }
        }).start();
    }
    
    private void checkExistingVerificationCode() {
        android.util.Log.d("EmailVerification", "Checking for existing verification code");
        
        // Check if there's an existing code in Firebase
        mDatabase.child("verification_codes").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Check if it's a simple string (old format) or a map (new format with timestamp)
                    Object codeData = snapshot.getValue();
                    
                    String existingCode = null;
                    Long timestamp = null;
                    
                    if (codeData instanceof String) {
                        // Old format: just the code
                        existingCode = (String) codeData;
                        android.util.Log.d("EmailVerification", "Found old format code: " + existingCode);
                        // No timestamp, assume expired and generate new
                        generateAndSendVerificationCode();
                        return;
                    } else if (codeData instanceof java.util.Map) {
                        // New format: map with code and timestamp
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> codeMap = (java.util.Map<String, Object>) codeData;
                        existingCode = (String) codeMap.get("code");
                        Object timestampObj = codeMap.get("timestamp");
                        if (timestampObj instanceof Long) {
                            timestamp = (Long) timestampObj;
                        } else if (timestampObj instanceof Integer) {
                            timestamp = ((Integer) timestampObj).longValue();
                        }
                    }
                    
                    if (existingCode != null && timestamp != null) {
                        // Check if code is still valid (not expired)
                        long currentTime = System.currentTimeMillis();
                        long elapsedTime = currentTime - timestamp;
                        
                        android.util.Log.d("EmailVerification", "Existing code found. Elapsed time: " + (elapsedTime / 1000) + " seconds");
                        
                        if (elapsedTime < CODE_EXPIRATION_TIME) {
                            // Code is still valid, reuse it
                            android.util.Log.d("EmailVerification", "Reusing existing valid code");
                            currentVerificationCode = existingCode;
                            
                            // Calculate remaining time
                            long remainingTime = CODE_EXPIRATION_TIME - elapsedTime;
                            countdownSeconds = (int) (remainingTime / 1000);
                            
                            // Start countdown timer with remaining time
                            startCountdownTimer();
                            
                            // Start cooldown timer (60 seconds - matching website)
                            startCooldownTimer();
                            
                            // Don't send email again, just show message
                            Toast.makeText(EmailVerificationActivity.this, 
                                "Using existing verification code. Check your email.", 
                                Toast.LENGTH_LONG).show();
                            return;
                        } else {
                            // Code expired, generate new one
                            android.util.Log.d("EmailVerification", "Existing code expired, generating new code");
                        }
                    } else {
                        // Invalid format, generate new code
                        android.util.Log.d("EmailVerification", "Invalid code format, generating new code");
                    }
                }
                
                // No existing code or expired, generate new one
                generateAndSendVerificationCode();
            }
            
            @Override
            public void onCancelled(DatabaseError error) {
                android.util.Log.e("EmailVerification", "Error checking existing code: " + error.getMessage());
                // On error, generate new code
                generateAndSendVerificationCode();
            }
        });
    }
    
    private void generateAndSendVerificationCode() {
        android.util.Log.d("EmailVerification", "Generating and sending verification code");
        
        // Generate 6-digit verification code
        String verificationCode = String.format("%06d", (int) (Math.random() * 1000000));
        
        // Store code locally for faster verification
        currentVerificationCode = verificationCode;
        
        // Store verification code with timestamp in Firebase
        long timestamp = System.currentTimeMillis();
        java.util.Map<String, Object> codeData = new java.util.HashMap<>();
        codeData.put("code", verificationCode);
        codeData.put("timestamp", timestamp);
        
        mDatabase.child("verification_codes").child(userId).setValue(codeData);
        
        // Reset countdown timer to 30 minutes
        countdownSeconds = 1800;
        startCountdownTimer();
        
        // Start initial cooldown timer (60 seconds - matching website)
        startCooldownTimer();
        
        // Send verification code via email
        sendVerificationEmail(verificationCode);
    }
    
    private void sendVerificationEmail(String verificationCode) {
        if (email == null || email.isEmpty()) {
            Toast.makeText(this, "Email address not found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show loading message
        Toast.makeText(this, "Sending verification email...", Toast.LENGTH_SHORT).show();
        
        // Send email using EmailService
        EmailService.sendVerificationEmail(email, verificationCode, fullName, new EmailService.EmailCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(EmailVerificationActivity.this, 
                        "Verification code sent to " + email, Toast.LENGTH_LONG).show();
                });
            }
            
            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    Toast.makeText(EmailVerificationActivity.this, 
                        "Failed to send email: " + errorMessage, Toast.LENGTH_LONG).show();
                    // Show verification code as fallback
                    Toast.makeText(EmailVerificationActivity.this, 
                        "Verification code: " + verificationCode, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void verifyCode() {
        android.util.Log.d("EmailVerification", "Verify button clicked!");
        
        // Safety check for null references
        if (verificationCodeEditText == null) {
            android.util.Log.e("EmailVerification", "verificationCodeEditText is null!");
            Toast.makeText(this, "Error: Input field not initialized", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String enteredCode = verificationCodeEditText.getText().toString().trim();
        android.util.Log.d("EmailVerification", "Entered code: " + enteredCode);
        android.util.Log.d("EmailVerification", "Current code: " + currentVerificationCode);
        
        if (enteredCode.isEmpty()) {
            Toast.makeText(this, "Please enter verification code", Toast.LENGTH_SHORT).show();
            // Focus on first input with safety check
            if (codeInputs != null && codeInputs.length > 0 && codeInputs[0] != null) {
                codeInputs[0].requestFocus();
            }
            return;
        }
        
        if (enteredCode.length() != 6) {
            Toast.makeText(this, "Verification code must be 6 digits", Toast.LENGTH_SHORT).show();
            // Focus on first empty input with safety check
            if (codeInputs != null) {
                for (int i = 0; i < codeInputs.length; i++) {
                    if (codeInputs[i] != null && codeInputs[i].getText().toString().isEmpty()) {
                        codeInputs[i].requestFocus();
                        break;
                    }
                }
            }
            return;
        }
        
        // Show loading state
        verifyButton.setEnabled(false);
        verifyButton.setText("VERIFYING...");
        
        // Fast local verification first
        if (currentVerificationCode != null && currentVerificationCode.equals(enteredCode)) {
            android.util.Log.d("EmailVerification", "Local verification successful!");
            
            // Show success visual feedback
            showSuccessAnimation();
            
            Toast.makeText(this, "Email verified successfully!", Toast.LENGTH_SHORT).show();
            
            // Mark user as verified in session
            SessionManager sessionManager = new SessionManager(this);
            sessionManager.setVerified(true);
            
            // Clear verification code (async, don't wait)
            mDatabase.child("verification_codes").child(userId).removeValue();
            currentVerificationCode = null;
            
            // Navigate to appropriate dashboard after animation
            new android.os.Handler().postDelayed(() -> {
                navigateToDashboard();
            }, 1000);
            
            // Reset button state
            verifyButton.setEnabled(true);
            verifyButton.setText("VERIFY CODE");
            return;
        }
        
        // Fallback to Firebase verification if local verification fails
        android.util.Log.d("EmailVerification", "Local verification failed, checking Firebase...");
        
        // Check verification code in Firebase with timeout
        mDatabase.child("verification_codes").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                android.util.Log.d("EmailVerification", "Firebase data received");
                
                String storedCode = null;
                
                if (snapshot.exists()) {
                    Object codeData = snapshot.getValue();
                    
                    if (codeData instanceof String) {
                        // Old format: just the code
                        storedCode = (String) codeData;
                    } else if (codeData instanceof java.util.Map) {
                        // New format: map with code and timestamp
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> codeMap = (java.util.Map<String, Object>) codeData;
                        storedCode = (String) codeMap.get("code");
                    }
                }
                
                android.util.Log.d("EmailVerification", "Stored code: " + storedCode);
                
                if (storedCode != null && storedCode.equals(enteredCode)) {
                    // Verification successful
                    android.util.Log.d("EmailVerification", "Firebase verification successful!");
                    
                    // Show success visual feedback
                    showSuccessAnimation();
                    
                    Toast.makeText(EmailVerificationActivity.this, "Email verified successfully!", Toast.LENGTH_SHORT).show();
                    
                    // Mark user as verified in session
                    SessionManager sessionManager = new SessionManager(EmailVerificationActivity.this);
                    sessionManager.setVerified(true);
                    
                    // Clear verification code (async, don't wait)
                    mDatabase.child("verification_codes").child(userId).removeValue();
                    currentVerificationCode = null;
                    
                    // Navigate to appropriate dashboard after animation
                    new android.os.Handler().postDelayed(() -> {
                        navigateToDashboard();
                    }, 1000);
                } else {
                    android.util.Log.d("EmailVerification", "Invalid verification code");
                    showErrorAnimation();
                    Toast.makeText(EmailVerificationActivity.this, "Invalid verification code", Toast.LENGTH_SHORT).show();
                    clearCodeInputs();
                }
                
                // Reset button state
                verifyButton.setEnabled(true);
                verifyButton.setText("VERIFY CODE");
            }
            
            @Override
            public void onCancelled(DatabaseError error) {
                android.util.Log.e("EmailVerification", "Firebase error: " + error.getMessage());
                Toast.makeText(EmailVerificationActivity.this, "Error verifying code: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                
                // Reset button state
                verifyButton.setEnabled(true);
                verifyButton.setText("VERIFY CODE");
            }
        });
        
        // Add timeout to prevent infinite waiting
        new android.os.Handler().postDelayed(() -> {
            if (!verifyButton.isEnabled()) {
                android.util.Log.d("EmailVerification", "Verification timeout");
                verifyButton.setEnabled(true);
                verifyButton.setText("VERIFY CODE");
                Toast.makeText(EmailVerificationActivity.this, "Verification timeout. Please try again.", Toast.LENGTH_SHORT).show();
                clearCodeInputs();
            }
        }, 5000); // 5 second timeout (reduced from 10)
    }
    
    private void resendCode() {
        android.util.Log.d("EmailVerification", "=== RESEND BUTTON CLICKED ===");
        
        // Disable resend button immediately and make it semi-transparent
        resendButton.setEnabled(false);
        resendButton.setAlpha(0.6f);
        
        // INVALIDATE CURRENT CODE (matching website behavior)
        android.util.Log.d("EmailVerification", "Invalidating current verification code");
        currentVerificationCode = null; // Clear local cache
        mDatabase.child("verification_codes").child(userId).removeValue(); // Remove from Firebase
        
        // Generate and send new verification code (this will reset timer to 30 minutes and start timers)
        android.util.Log.d("EmailVerification", "Generating NEW verification code for resend");
        generateAndSendVerificationCode();
        
        // Show feedback
        Toast.makeText(this, "Resending verification code...", Toast.LENGTH_SHORT).show();
        
        android.util.Log.d("EmailVerification", "=== RESEND PROCESS COMPLETED ===");
    }
    
    private void startCooldownTimer() {
        cooldownSeconds = 60; // 60 second cooldown (matching website)
        android.util.Log.d("EmailVerification", "Starting cooldown timer: 60 seconds");
        
        new Thread(() -> {
            while (cooldownSeconds > 0) {
                runOnUiThread(() -> {
                    if (cooldownText != null) {
                        cooldownText.setText("Resend available in: " + cooldownSeconds + "s");
                        cooldownText.setVisibility(android.view.View.VISIBLE);
                    }
                    // Keep resend button disabled during cooldown
                    resendButton.setEnabled(false);
                    resendButton.setAlpha(0.6f);
                });
                
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
                cooldownSeconds--;
            }
            
            runOnUiThread(() -> {
                if (cooldownText != null) {
                    cooldownText.setVisibility(android.view.View.GONE);
                }
                resendButton.setEnabled(true);
                resendButton.setAlpha(1.0f);
                android.util.Log.d("EmailVerification", "Resend button re-enabled after 60 second cooldown");
            });
        }).start();
    }
    
    private void showSuccessAnimation() {
        // Change icon to success state
        verificationStatusIcon.setImageResource(android.R.drawable.ic_dialog_info);
        verificationStatusIcon.setColorFilter(android.graphics.Color.parseColor("#00b341"));
        
        // Add success animation to card
        findViewById(R.id.verificationCard).animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(200)
            .withEndAction(() -> {
                findViewById(R.id.verificationCard).animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(200);
            });
    }
    
    private void showErrorAnimation() {
        // Change icon to error state
        verificationStatusIcon.setColorFilter(android.graphics.Color.parseColor("#dc3545"));
        
        // Shake animation for error
        findViewById(R.id.verificationCard).animate()
            .translationX(-10)
            .setDuration(50)
            .withEndAction(() -> {
                findViewById(R.id.verificationCard).animate()
                    .translationX(10)
                    .setDuration(50)
                    .withEndAction(() -> {
                        findViewById(R.id.verificationCard).animate()
                            .translationX(0)
                            .setDuration(50);
                    });
            });
    }
    
    private void clearCodeInputs() {
        android.util.Log.d("EmailVerification", "Clearing all code inputs");
        
        if (codeInputs == null) {
            android.util.Log.e("EmailVerification", "clearCodeInputs: codeInputs is null");
            return;
        }
        
        for (int i = 0; i < codeInputs.length; i++) {
            if (codeInputs[i] != null) {
                codeInputs[i].setText("");
            }
        }
        
        // Focus on first input and select all text with safety check
        if (codeInputs.length > 0 && codeInputs[0] != null) {
            codeInputs[0].requestFocus();
            codeInputs[0].post(() -> {
                if (codeInputs[0] != null) {
                    codeInputs[0].setSelection(codeInputs[0].getText().length());
                }
            });
        }
    }
    
    private void navigateToDashboard() {
        // Navigate to loading screen first with smooth transition
        Intent loadingIntent = new Intent(this, LoadingScreenActivity.class);
        
        // Pass dashboard information
        loadingIntent.putExtra("next_activity", "dashboard");
        loadingIntent.putExtra("loading_message", "Preparing your dashboard...");
        loadingIntent.putExtra("userId", userId);
        loadingIntent.putExtra("userType", userType);
        loadingIntent.putExtra("fullName", fullName);
        loadingIntent.putExtra("email", email);
        
        startActivity(loadingIntent);
        overridePendingTransition(R.anim.scale_in, R.anim.scale_out);
        finish();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        android.util.Log.d("EmailVerification", "Activity resumed - NOT sending new code");
        // Don't send new code on resume, only on initial load
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        android.util.Log.d("EmailVerification", "Activity paused");
        // Keep timers running in background
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        isTimerRunning = false;
        android.util.Log.d("EmailVerification", "Activity destroyed");
    }
    
    @Override
    public void onBackPressed() {
        // When back button is pressed, clear session and go back to login
        android.util.Log.d("EmailVerification", "Back button pressed - clearing session and returning to login");
        
        // Clear the login session
        SessionManager sessionManager = new SessionManager(this);
        sessionManager.logout();
        
        // Navigate back to login
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
