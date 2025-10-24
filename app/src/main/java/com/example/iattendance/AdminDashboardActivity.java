package com.example.iattendance;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AdminDashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);
        
        // Get user data from intent
        String userId = getIntent().getStringExtra("userId");
        String userType = getIntent().getStringExtra("userType");
        String fullName = getIntent().getStringExtra("fullName");
        String email = getIntent().getStringExtra("email");
        
        // Set welcome message
        TextView welcomeText = findViewById(R.id.welcomeText);
        welcomeText.setText("Welcome " + fullName + "!");
        
        TextView userInfoText = findViewById(R.id.userInfoText);
        userInfoText.setText("Admin ID: " + userId + "\nEmail: " + email);
    }
}




