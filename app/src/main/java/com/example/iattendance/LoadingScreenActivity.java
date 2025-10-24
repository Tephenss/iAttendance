package com.example.iattendance;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

public class LoadingScreenActivity extends Activity {
    
    private ImageView logoImage;
    private TextView loadingText;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading_screen);
        
        initializeViews();
        startLogoAnimation();
        
        // Get navigation data from intent
        String nextActivity = getIntent().getStringExtra("next_activity");
        String loadingMessage = getIntent().getStringExtra("loading_message");
        
        // Update loading text if custom message provided
        if (loadingMessage != null && loadingText != null) {
            loadingText.setText(loadingMessage);
        }
        
        // Navigate based on next activity
        logoImage.postDelayed(() -> {
            Intent intent;
            if ("EmailVerificationActivity".equals(nextActivity)) {
                // Navigate to EmailVerificationActivity if specified
                intent = new Intent(this, EmailVerificationActivity.class);
                // Pass any extras from the original intent
                Bundle extras = getIntent().getExtras();
                if (extras != null) {
                    intent.putExtras(extras);
                }
            } else if ("dashboard".equals(nextActivity)) {
                // Navigate to appropriate dashboard based on user type
                String userType = getIntent().getStringExtra("userType");
                if (userType != null) {
                    switch (userType) {
                        case "student":
                            intent = new Intent(this, StudentDashboardActivity.class);
                            break;
                        case "teacher":
                            intent = new Intent(this, TeacherDashboardActivity.class);
                            break;
                        case "admin":
                            intent = new Intent(this, AdminDashboardActivity.class);
                            break;
                        default:
                            intent = new Intent(this, MainActivity.class);
                            break;
                    }
                } else {
                    intent = new Intent(this, MainActivity.class);
                }
                
                // Pass user data to dashboard
                intent.putExtra("userId", getIntent().getStringExtra("userId"));
                intent.putExtra("userType", getIntent().getStringExtra("userType"));
                intent.putExtra("fullName", getIntent().getStringExtra("fullName"));
                intent.putExtra("email", getIntent().getStringExtra("email"));
            } else {
                // Default to MainActivity
                intent = new Intent(this, MainActivity.class);
            }
            
            // Start activity with smooth transition
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            finish();
        }, 3000); // 3 second delay
    }
    
    private void initializeViews() {
        logoImage = findViewById(R.id.iv_logo);
        loadingText = findViewById(R.id.tv_loading);
        
        // Default logo settings
        logoImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        logoImage.setImageResource(R.drawable.laguna_logo);
    }
    
    private void startLogoAnimation() {
        // Create rotate animation
        RotateAnimation rotateAnimation = new RotateAnimation(
            0f, 360f, // From 0 to 360 degrees
            Animation.RELATIVE_TO_SELF, 0.5f, // Pivot X (center)
            Animation.RELATIVE_TO_SELF, 0.5f  // Pivot Y (center)
        );
        
        rotateAnimation.setDuration(2000); // 2 seconds per rotation
        rotateAnimation.setRepeatCount(Animation.INFINITE); // Infinite rotation
        rotateAnimation.setRepeatMode(Animation.RESTART); // Restart after completion
        rotateAnimation.setInterpolator(this, android.R.anim.accelerate_decelerate_interpolator);
        
        // Start animation
        logoImage.startAnimation(rotateAnimation);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (logoImage != null && logoImage.getAnimation() != null) {
            logoImage.getAnimation().cancel();
        }
    }
}
