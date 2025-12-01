package com.example.iattendance;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

public class StudentDashboardActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize session manager
        sessionManager = new SessionManager(this);

        // Check if user is logged in and verified
        if (!sessionManager.isLoggedIn() || !sessionManager.isVerified()) {
            // User not logged in or not verified, redirect to login
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_student_dashboard);

        // Setup drawer
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        // Setup hamburger menu icon
        View menuIcon = findViewById(R.id.menu_icon);
        if (menuIcon != null) {
            menuIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        }

        // Setup three dots menu icon
        View moreIcon = findViewById(R.id.more_icon);
        if (moreIcon != null) {
            moreIcon.setOnClickListener(v -> onOptionsIconClick());
        }

        drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerOpened(@NonNull View drawerView) {}

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {}

            @Override
            public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {}

            @Override
            public void onDrawerStateChanged(int newState) {}
        });

        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_dashboard);

        // Get user data from intent
        String userId = getIntent().getStringExtra("userId");
        String fullName = getIntent().getStringExtra("fullName");

        // Update nav header after layout is inflated
        navigationView.post(() -> {
            View headerView = navigationView.getHeaderView(0);
            if (headerView != null) {
                TextView navName = headerView.findViewById(R.id.nav_header_name);
                TextView navEmail = headerView.findViewById(R.id.nav_header_email);

                if (navName != null) navName.setText(fullName);
                if (navEmail != null) navEmail.setText("ID: " + userId);
            }
        });

        // Set welcome message
        TextView welcomeText = findViewById(R.id.welcomeText);
        if (welcomeText != null && fullName != null) {
            welcomeText.setText("Welcome, " + fullName + "!");
        }

        TextView userInfoText = findViewById(R.id.userInfoText);
        if (userInfoText != null) {
            userInfoText.setText("Student Dashboard");
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_dashboard) {
            // Already on dashboard
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        } else if (id == R.id.nav_timetable) {
            // Navigate to Timetable
            Intent intent = new Intent(this, TimetableActivity.class);
            intent.putExtra("userId", getIntent().getStringExtra("userId"));
            intent.putExtra("userType", getIntent().getStringExtra("userType"));
            intent.putExtra("fullName", getIntent().getStringExtra("fullName"));
            intent.putExtra("email", getIntent().getStringExtra("email"));
            startActivity(intent);
            finish();
            return true;
        } else if (id == R.id.nav_my_attendance) {
            // Navigate to My Attendance
            Intent intent = new Intent(this, StudentAttendanceActivity.class);
            intent.putExtra("userId", getIntent().getStringExtra("userId"));
            intent.putExtra("userType", getIntent().getStringExtra("userType"));
            intent.putExtra("fullName", getIntent().getStringExtra("fullName"));
            intent.putExtra("email", getIntent().getStringExtra("email"));
            startActivity(intent);
            finish();
            return true;
        }

        return false;
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    public void onOptionsIconClick() {
        View moreIcon = findViewById(R.id.more_icon);
        if (moreIcon != null) {
            PopupMenu popupMenu = new PopupMenu(
                    new android.view.ContextThemeWrapper(this, R.style.PopupMenuStyle),
                    moreIcon
            );
            popupMenu.getMenuInflater().inflate(R.menu.toolbar_menu, popupMenu.getMenu());
            popupMenu.setGravity(android.view.Gravity.END);

            popupMenu.setOnMenuItemClickListener(menuItem -> {
                if (menuItem.getItemId() == R.id.menu_logout) {
                    performLogout();
                    return true;
                }
                return false;
            });

            popupMenu.show();
        }
    }

    private void performLogout() {
        if (sessionManager != null) {
            sessionManager.logout();
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}


