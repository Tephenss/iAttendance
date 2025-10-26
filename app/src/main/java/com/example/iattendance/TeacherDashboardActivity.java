package com.example.iattendance;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

public class TeacherDashboardActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private SessionManager sessionManager;
    private android.os.Handler inactivityHandler;
    private Runnable logoutRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_dashboard);

        // Initialize session manager
        sessionManager = new SessionManager(this);

        // Setup inactivity timer
        setupInactivityTimer();

        // Setup drawer
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        // Setup hamburger menu icon
        findViewById(R.id.menu_icon).setOnClickListener(v -> {
            resetInactivityTimer();
            drawerLayout.openDrawer(GravityCompat.START);
        });

        // Setup three dots menu icon
        findViewById(R.id.more_icon).setOnClickListener(v -> {
            resetInactivityTimer();
            onOptionsIconClick();
        });

        // Don't use ActionBarDrawerToggle since we're using custom toolbar
        // Just setup the drawer listener
        drawerLayout.addDrawerListener(new androidx.drawerlayout.widget.DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerOpened(@androidx.annotation.NonNull android.view.View drawerView) {}

            @Override
            public void onDrawerClosed(@androidx.annotation.NonNull android.view.View drawerView) {}

            @Override
            public void onDrawerSlide(@androidx.annotation.NonNull android.view.View drawerView, float slideOffset) {}

            @Override
            public void onDrawerStateChanged(int newState) {}
        });

        navigationView.setNavigationItemSelectedListener(this);

        // Get user data from intent
        String userId = getIntent().getStringExtra("userId");
        String userType = getIntent().getStringExtra("userType");
        String fullName = getIntent().getStringExtra("fullName");
        String email = getIntent().getStringExtra("email");

        // Update nav header after layout is inflated
        navigationView.post(() -> {
            android.view.View headerView = navigationView.getHeaderView(0);
            if (headerView != null) {
                TextView navName = headerView.findViewById(R.id.nav_header_name);
                TextView navEmail = headerView.findViewById(R.id.nav_header_email);

                if (navName != null) navName.setText(fullName);
                if (navEmail != null) navEmail.setText(email);
            }
        });

        // Set welcome message
        TextView welcomeText = findViewById(R.id.welcomeText);
        if (welcomeText != null) {
            welcomeText.setText("Welcome, " + fullName + "!");
        }

        TextView userInfoText = findViewById(R.id.userInfoText);
        if (userInfoText != null) {
            userInfoText.setText("Teacher Dashboard");
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        resetInactivityTimer();

        int id = item.getItemId();

        if (id == R.id.nav_dashboard) {
            // Dashboard selected
            Toast.makeText(this, "Dashboard", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.nav_logout) {
            // Logout selected
            performLogout();
            return true;
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        resetInactivityTimer();

        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        resetInactivityTimer();
        return super.onTouchEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resetInactivityTimer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopInactivityTimer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopInactivityTimer();
    }

    @SuppressWarnings("StatementWithEmptyBody")
    public void onOptionsIconClick() {
        // Handle three dots menu click if needed
        Toast.makeText(this, "Options", Toast.LENGTH_SHORT).show();
    }

    private void setupInactivityTimer() {
        inactivityHandler = new android.os.Handler();
        logoutRunnable = () -> {
            Toast.makeText(this, "Session expired due to inactivity", Toast.LENGTH_LONG).show();
            performLogout();
        };
        resetInactivityTimer();
    }

    private void resetInactivityTimer() {
        inactivityHandler.removeCallbacks(logoutRunnable);
        inactivityHandler.postDelayed(logoutRunnable, 5 * 60 * 1000); // 5 minutes
        sessionManager.updateActivity();
    }

    private void stopInactivityTimer() {
        if (inactivityHandler != null) {
            inactivityHandler.removeCallbacks(logoutRunnable);
        }
    }

    private void performLogout() {
        sessionManager.logout();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}





