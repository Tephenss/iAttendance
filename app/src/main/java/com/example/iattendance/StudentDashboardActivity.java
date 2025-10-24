package com.example.iattendance;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class StudentDashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_dashboard);
        
        // Get user data from intent
        String userId = getIntent().getStringExtra("userId");
        String userType = getIntent().getStringExtra("userType");
        String fullName = getIntent().getStringExtra("fullName");
        String email = getIntent().getStringExtra("email");
        
        // Set welcome message
        TextView welcomeText = findViewById(R.id.welcomeText);
        welcomeText.setText("Welcome " + fullName + "!");
        
        TextView userInfoText = findViewById(R.id.userInfoText);
        userInfoText.setText("Student ID: " + userId + "\nEmail: " + email);
    }
}




