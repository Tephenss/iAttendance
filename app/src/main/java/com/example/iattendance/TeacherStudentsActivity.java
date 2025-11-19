package com.example.iattendance;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TeacherStudentsActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "TeacherStudentsActivity";
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private SessionManager sessionManager;
    private Spinner classSpinner;
    private RecyclerView studentsRecyclerView;
    private ProgressBar loadingProgress;
    private TextView emptyStateText;
    
    private String teacherId;
    private List<ClassItem> teacherClasses = new ArrayList<>();
    private List<StudentItem> currentStudents = new ArrayList<>();
    private StudentsAdapter studentsAdapter;
    private String selectedClassId;
    private String selectedSection;
    private String selectedYearLevel;

    // Class item to hold class data
    private static class ClassItem {
        String id;
        String name;
        String section;
        String yearLevel;
        
        ClassItem(String id, String name, String section, String yearLevel) {
            this.id = id;
            this.name = name;
            this.section = section;
            this.yearLevel = yearLevel;
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
    
    // Student item to hold student data
    public static class StudentItem {
        String id;
        String studentId;
        String fullName;
        String email;
        String createdAt;
        String section;
        String yearLevel;
        
        StudentItem(String id, String studentId, String fullName, String email, String createdAt, String section, String yearLevel) {
            this.id = id;
            this.studentId = studentId;
            this.fullName = fullName;
            this.email = email;
            this.createdAt = createdAt;
            this.section = section;
            this.yearLevel = yearLevel;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_students);

        sessionManager = new SessionManager(this);

        // Check if user is logged in and verified
        if (!sessionManager.isLoggedIn() || !sessionManager.isVerified()) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        // Initialize views
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        classSpinner = findViewById(R.id.classSpinner);
        studentsRecyclerView = findViewById(R.id.studentsRecyclerView);
        loadingProgress = findViewById(R.id.loadingProgress);
        emptyStateText = findViewById(R.id.emptyStateText);

        // Setup drawer
        findViewById(R.id.menu_icon).setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        findViewById(R.id.more_icon).setOnClickListener(v -> onOptionsIconClick());

        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_students);

        // Get user data
        teacherId = getIntent().getStringExtra("userId");
        String fullName = getIntent().getStringExtra("fullName");

        // Update nav header
        navigationView.post(() -> {
            View headerView = navigationView.getHeaderView(0);
            if (headerView != null) {
                TextView navName = headerView.findViewById(R.id.nav_header_name);
                TextView navEmail = headerView.findViewById(R.id.nav_header_email);

                if (navName != null) navName.setText(fullName);
                if (navEmail != null) navEmail.setText("ID: " + teacherId);
            }
        });

        // Setup RecyclerView
        studentsAdapter = new StudentsAdapter(new ArrayList<>());
        studentsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        studentsRecyclerView.setAdapter(studentsAdapter);

        // Setup class spinner
        classSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0 && position <= teacherClasses.size()) {
                    ClassItem selectedClass = teacherClasses.get(position - 1);
                    selectedClassId = selectedClass.id;
                    selectedSection = selectedClass.section;
                    selectedYearLevel = selectedClass.yearLevel;
                    Log.d(TAG, "Class selected: " + selectedClass.name + " (ID: " + selectedClassId + ", Section: " + selectedSection + ", Year: " + selectedYearLevel + ")");
                    fetchStudentsForClass(selectedSection, selectedYearLevel);
                } else {
                    selectedClassId = null;
                    selectedSection = null;
                    selectedYearLevel = null;
                    currentStudents.clear();
                    studentsAdapter.updateStudents(new ArrayList<>());
                    showEmptyState();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        // Fetch teacher classes
        fetchTeacherClasses(teacherId);
    }

    private void fetchTeacherClasses(String userId) {
        loadingProgress.setVisibility(View.VISIBLE);
        emptyStateText.setVisibility(View.GONE);
        studentsRecyclerView.setVisibility(View.GONE);

        Log.d(TAG, "Fetching teacher classes for teacherId: " + userId);

        DatabaseReference classesRef = FirebaseDatabase.getInstance().getReference("attendance_system/classes");
        classesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                teacherClasses.clear();

                // Normalize teacher ID
                String normalizedTeacherId = userId;
                if (userId != null && userId.startsWith("T")) {
                    normalizedTeacherId = userId.substring(1);
                }

                Log.d(TAG, "Teacher ID: '" + userId + "', normalized: '" + normalizedTeacherId + "'");
                Log.d(TAG, "Total classes in database: " + snapshot.getChildrenCount());

                for (DataSnapshot classSnapshot : snapshot.getChildren()) {
                    try {
                        DataSnapshot dataSnapshot = classSnapshot.child("data");
                        if (!dataSnapshot.exists()) {
                            continue;
                        }

                        String classTeacherId = dataSnapshot.child("teacher_id").getValue(String.class);
                        String classId = dataSnapshot.child("id").getValue(String.class);
                        String section = dataSnapshot.child("section").getValue(String.class);
                        String yearLevel = dataSnapshot.child("year_level").getValue(String.class);

                        if (classTeacherId == null || classId == null) {
                            continue;
                        }

                        // Try different matching formats
                        String normalizedClassTeacherId = classTeacherId;
                        if (classTeacherId.startsWith("T")) {
                            normalizedClassTeacherId = classTeacherId.substring(1);
                        }

                        // Match by full ID or normalized ID
                        boolean matches = userId.equalsIgnoreCase(classTeacherId) ||
                                        normalizedTeacherId.equalsIgnoreCase(classTeacherId) ||
                                        userId.equalsIgnoreCase(normalizedClassTeacherId) ||
                                        normalizedTeacherId.equalsIgnoreCase(normalizedClassTeacherId);

                        // Also try numeric comparison if both are numeric
                        if (!matches) {
                            try {
                                int userIdNum = Integer.parseInt(normalizedTeacherId);
                                int classTeacherIdNum = Integer.parseInt(normalizedClassTeacherId);
                                if (userIdNum == classTeacherIdNum) {
                                    matches = true;
                                }
                            } catch (NumberFormatException e) {
                                // Not numeric, skip
                            }
                        }

                        if (matches) {
                            // Fetch subject info to build class name
                            String subjectId = dataSnapshot.child("subject_id").getValue(String.class);
                            fetchSubjectInfoAndAddClass(classId, subjectId, section, yearLevel);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing class " + classSnapshot.getKey() + ": " + e.getMessage());
                    }
                }

                if (teacherClasses.isEmpty()) {
                    runOnUiThread(() -> {
                        loadingProgress.setVisibility(View.GONE);
                        emptyStateText.setText("No classes found for this teacher");
                        emptyStateText.setVisibility(View.VISIBLE);
                        Toast.makeText(TeacherStudentsActivity.this, "No classes found for this teacher", Toast.LENGTH_LONG).show();
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching teacher classes: " + error.getMessage());
                runOnUiThread(() -> {
                    loadingProgress.setVisibility(View.GONE);
                    emptyStateText.setText("Error loading classes");
                    emptyStateText.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void fetchSubjectInfoAndAddClass(String classId, String subjectId, String section, String yearLevel) {
        if (subjectId == null) {
            // Add class without subject info
            String className = "Class " + classId + " - " + section + " (Year " + yearLevel + ")";
            teacherClasses.add(new ClassItem(classId, className, section, yearLevel));
            updateClassSpinner();
            return;
        }

        DatabaseReference subjectsRef = FirebaseDatabase.getInstance().getReference("attendance_system/subjects");
        subjectsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String subjectCode = null;
                String subjectName = null;

                for (DataSnapshot subjectSnapshot : snapshot.getChildren()) {
                    try {
                        DataSnapshot dataSnapshot = subjectSnapshot.child("data");
                        if (!dataSnapshot.exists()) continue;

                        String id = dataSnapshot.child("id").getValue(String.class);
                        if (subjectId.equals(id)) {
                            subjectCode = dataSnapshot.child("subject_code").getValue(String.class);
                            subjectName = dataSnapshot.child("subject_name").getValue(String.class);
                            break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing subject: " + e.getMessage());
                    }
                }

                // Build class name: "Subject Code - Section (Year Level)"
                String className;
                if (subjectCode != null && subjectName != null) {
                    className = subjectCode + " - " + section + " (Year " + yearLevel + ")";
                } else {
                    className = "Class " + classId + " - " + section + " (Year " + yearLevel + ")";
                }

                teacherClasses.add(new ClassItem(classId, className, section, yearLevel));
                updateClassSpinner();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching subject info: " + error.getMessage());
                // Add class without subject info
                String className = "Class " + classId + " - " + section + " (Year " + yearLevel + ")";
                teacherClasses.add(new ClassItem(classId, className, section, yearLevel));
                updateClassSpinner();
            }
        });
    }

    private void updateClassSpinner() {
        runOnUiThread(() -> {
            List<String> classNames = new ArrayList<>();
            classNames.add("Select Class");
            for (ClassItem classItem : teacherClasses) {
                classNames.add(classItem.name);
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, classNames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            classSpinner.setAdapter(adapter);

            // Auto-select first class if available
            if (teacherClasses.size() > 0 && classSpinner.getSelectedItemPosition() == 0) {
                classSpinner.setSelection(1);
            }
        });
    }

    private void fetchStudentsForClass(String section, String yearLevel) {
        loadingProgress.setVisibility(View.VISIBLE);
        emptyStateText.setVisibility(View.GONE);
        studentsRecyclerView.setVisibility(View.GONE);

        Log.d(TAG, "Fetching students for section: " + section + ", year level: " + yearLevel);

        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("attendance_system/students");
        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                currentStudents.clear();

                for (DataSnapshot studentSnapshot : snapshot.getChildren()) {
                    try {
                        DataSnapshot dataSnapshot = studentSnapshot.child("data");
                        if (!dataSnapshot.exists()) {
                            continue;
                        }

                        String studentSection = dataSnapshot.child("section").getValue(String.class);
                        String studentYearLevel = dataSnapshot.child("year_level").getValue(String.class);
                        String status = dataSnapshot.child("status").getValue(String.class);

                        // Filter by section and year level, exclude graduated/promoted
                        if (section != null && yearLevel != null &&
                            section.equals(studentSection) &&
                            yearLevel.equals(studentYearLevel) &&
                            (status == null || (!status.equals("graduated") && !status.equals("promoted")))) {

                            String id = dataSnapshot.child("id").getValue(String.class);
                            String studentId = dataSnapshot.child("student_id").getValue(String.class);
                            String firstName = dataSnapshot.child("first_name").getValue(String.class);
                            String lastName = dataSnapshot.child("last_name").getValue(String.class);
                            String fullName = dataSnapshot.child("full_name").getValue(String.class);
                            String email = dataSnapshot.child("email").getValue(String.class);
                            String createdAt = dataSnapshot.child("created_at").getValue(String.class);

                            if (fullName == null || fullName.isEmpty()) {
                                if (firstName != null && lastName != null) {
                                    fullName = firstName + " " + lastName;
                                } else if (firstName != null) {
                                    fullName = firstName;
                                } else if (lastName != null) {
                                    fullName = lastName;
                                } else {
                                    fullName = "Unknown";
                                }
                            }

                            if (id != null && studentId != null) {
                                currentStudents.add(new StudentItem(
                                    id,
                                    studentId,
                                    fullName,
                                    email != null ? email : "",
                                    createdAt != null ? createdAt : "",
                                    studentSection,
                                    studentYearLevel
                                ));
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing student: " + e.getMessage());
                    }
                }

                runOnUiThread(() -> {
                    loadingProgress.setVisibility(View.GONE);
                    if (currentStudents.isEmpty()) {
                        showEmptyState();
                    } else {
                        emptyStateText.setVisibility(View.GONE);
                        studentsAdapter.updateStudents(currentStudents);
                        studentsRecyclerView.setVisibility(View.VISIBLE);
                        // Show section header when students are loaded
                        TextView sectionHeader = findViewById(R.id.studentsSectionHeader);
                        if (sectionHeader != null) {
                            sectionHeader.setVisibility(View.VISIBLE);
                        }
                        Log.d(TAG, "Loaded " + currentStudents.size() + " students");
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching students: " + error.getMessage());
                runOnUiThread(() -> {
                    loadingProgress.setVisibility(View.GONE);
                    studentsRecyclerView.setVisibility(View.GONE);
                    emptyStateText.setVisibility(View.VISIBLE);
                    // Update error message in empty state
                    TextView emptyTitle = ((LinearLayout) emptyStateText).findViewById(R.id.emptyStateTitle);
                    if (emptyTitle != null) {
                        emptyTitle.setText("Error loading students");
                    }
                });
            }
        });
    }

    private void showEmptyState() {
        loadingProgress.setVisibility(View.GONE);
        studentsRecyclerView.setVisibility(View.GONE);
        emptyStateText.setVisibility(View.VISIBLE);
        // Empty state message is already set in XML
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_dashboard) {
            Intent intent = new Intent(this, TeacherDashboardActivity.class);
            intent.putExtra("userId", getIntent().getStringExtra("userId"));
            intent.putExtra("userType", getIntent().getStringExtra("userType"));
            intent.putExtra("fullName", getIntent().getStringExtra("fullName"));
            intent.putExtra("email", getIntent().getStringExtra("email"));
            startActivity(intent);
            finish();
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        } else if (id == R.id.nav_timetable) {
            Intent intent = new Intent(this, TeacherTimetableActivity.class);
            intent.putExtra("userId", getIntent().getStringExtra("userId"));
            intent.putExtra("userType", getIntent().getStringExtra("userType"));
            intent.putExtra("fullName", getIntent().getStringExtra("fullName"));
            intent.putExtra("email", getIntent().getStringExtra("email"));
            startActivity(intent);
            finish();
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        } else if (id == R.id.nav_students) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }

        return false;
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
        View moreIcon = findViewById(R.id.more_icon);
        if (moreIcon != null) {
            showPopupMenu(moreIcon);
        }
    }

    private void showPopupMenu(View view) {
        android.widget.PopupMenu popupMenu = new android.widget.PopupMenu(new android.view.ContextThemeWrapper(this, R.style.PopupMenuStyle), view);
        popupMenu.getMenuInflater().inflate(R.menu.toolbar_menu, popupMenu.getMenu());

        popupMenu.setGravity(android.view.Gravity.END);

        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.menu_logout) {
                performLogout();
                return true;
            }
            return false;
        });

        popupMenu.show();
    }

    private void performLogout() {
        sessionManager.logout();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

