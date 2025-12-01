package com.example.iattendance;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.PopupMenu;
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
        
        setContentView(R.layout.activity_teacher_dashboard);

        // Setup drawer
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        // Setup hamburger menu icon
        findViewById(R.id.menu_icon).setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        // Setup three dots menu icon
        findViewById(R.id.more_icon).setOnClickListener(v -> onOptionsIconClick());

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
        navigationView.setCheckedItem(R.id.nav_dashboard);

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
                if (navEmail != null) navEmail.setText("ID: " + userId);
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
        int id = item.getItemId();
        Intent intent = null;

        if (id == R.id.nav_dashboard) {
            // Already on dashboard page
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        } else if (id == R.id.nav_timetable) {
            intent = new Intent(this, TeacherTimetableActivity.class);
        } else if (id == R.id.nav_students) {
            intent = new Intent(this, TeacherStudentsActivity.class);
        } else if (id == R.id.nav_attendance) {
            intent = new Intent(this, RfidAttendanceActivity.class);
        }

        if (intent != null) {
            intent.putExtra("userId", getIntent().getStringExtra("userId"));
            intent.putExtra("userType", getIntent().getStringExtra("userType"));
            intent.putExtra("fullName", getIntent().getStringExtra("fullName"));
            intent.putExtra("email", getIntent().getStringExtra("email"));
            startActivity(intent);
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    public void onOptionsIconClick() {
        // Show popup menu from the more_icon view
        android.view.View moreIcon = findViewById(R.id.more_icon);
        if (moreIcon != null) {
            showPopupMenu(moreIcon);
        }
    }

    private void showPopupMenu(android.view.View view) {
        PopupMenu popupMenu = new PopupMenu(this, view);
        popupMenu.getMenu().add("Logout");
        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getTitle().toString().equals("Logout")) {
                sessionManager.logout();
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
            return true;
        });
        popupMenu.show();
    }
}
