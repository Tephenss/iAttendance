package com.example.iattendance;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
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
    private View emptyStateText;
    private EditText searchEditText;
    private View searchCard;
    
    private String teacherId;
    private List<ClassItem> teacherClasses = new ArrayList<>();
    private List<StudentItem> currentStudents = new ArrayList<>();
    private List<StudentItem> allStudents = new ArrayList<>(); // Store all fetched students for filtering
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
        String profilePictureBase64; // Base64 string for profile picture
        
        StudentItem(String id, String studentId, String fullName, String email, String createdAt, String section, String yearLevel, String profilePictureBase64) {
            this.id = id;
            this.studentId = studentId;
            this.fullName = fullName;
            this.email = email;
            this.createdAt = createdAt;
            this.section = section;
            this.yearLevel = yearLevel;
            this.profilePictureBase64 = profilePictureBase64;
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
        searchEditText = findViewById(R.id.searchEditText);
        searchCard = findViewById(R.id.searchCard);

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
        studentsAdapter.setOnStudentClickListener(student -> showStudentDetailsModal(student));
        studentsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        studentsRecyclerView.setAdapter(studentsAdapter);

        // Setup search functionality
        searchEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterStudents(s.toString());
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

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
                    // Clear search when new class is selected
                    searchEditText.setText("");
                    fetchStudentsForClass(selectedSection, selectedYearLevel);
                } else {
                    selectedClassId = null;
                    selectedSection = null;
                    selectedYearLevel = null;
                    currentStudents.clear();
                    allStudents.clear();
                    studentsAdapter.updateStudents(new ArrayList<>());
                    searchEditText.setText("");
                    searchCard.setVisibility(View.GONE);
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

                // Track matching classes and pending async operations
                final int[] pendingOperations = {0};
                final int[] matchedClasses = {0};

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
                            matchedClasses[0]++;
                            pendingOperations[0]++;
                            // Fetch subject info to build class name
                            String subjectId = dataSnapshot.child("subject_id").getValue(String.class);
                            fetchSubjectInfoAndAddClass(classId, subjectId, section, yearLevel, () -> {
                                // Callback when async operation completes
                                synchronized (pendingOperations) {
                                    pendingOperations[0]--;
                                    if (pendingOperations[0] == 0) {
                                        // All async operations completed, check if we have classes
                                        runOnUiThread(() -> {
                                            if (loadingProgress != null) {
                                                loadingProgress.setVisibility(View.GONE);
                                            }
                                            if (teacherClasses.isEmpty()) {
                                                if (emptyStateText != null) {
                                                    emptyStateText.setVisibility(View.VISIBLE);
                                                    TextView emptyTitle = emptyStateText.findViewById(R.id.emptyStateTitle);
                                                    if (emptyTitle != null) {
                                                        emptyTitle.setText("No classes found for this teacher");
                                                    }
                                                }
                                                Toast.makeText(TeacherStudentsActivity.this, "No classes found for this teacher", Toast.LENGTH_LONG).show();
                                            } else {
                                                if (emptyStateText != null) {
                                                    emptyStateText.setVisibility(View.GONE);
                                                }
                                            }
                                        });
                                    }
                                }
                            });
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing class " + classSnapshot.getKey() + ": " + e.getMessage());
                    }
                }

                // If no classes matched at all, show error immediately
                if (matchedClasses[0] == 0) {
                    runOnUiThread(() -> {
                        if (loadingProgress != null) {
                            loadingProgress.setVisibility(View.GONE);
                        }
                        if (emptyStateText != null) {
                            emptyStateText.setVisibility(View.VISIBLE);
                            TextView emptyTitle = emptyStateText.findViewById(R.id.emptyStateTitle);
                            if (emptyTitle != null) {
                                emptyTitle.setText("No classes found for this teacher");
                            }
                        }
                        Toast.makeText(TeacherStudentsActivity.this, "No classes found for this teacher", Toast.LENGTH_LONG).show();
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching teacher classes: " + error.getMessage());
                runOnUiThread(() -> {
                    if (loadingProgress != null) {
                        loadingProgress.setVisibility(View.GONE);
                    }
                    if (emptyStateText != null) {
                        emptyStateText.setVisibility(View.VISIBLE);
                        TextView emptyTitle = emptyStateText.findViewById(R.id.emptyStateTitle);
                        if (emptyTitle != null) {
                            emptyTitle.setText("Error loading classes");
                        }
                    }
                });
            }
        });
    }

    private void fetchSubjectInfoAndAddClass(String classId, String subjectId, String section, String yearLevel, Runnable onComplete) {
        if (subjectId == null) {
            // Add class without subject info
            String className = "Class " + classId + " - " + section + " (Year " + yearLevel + ")";
            teacherClasses.add(new ClassItem(classId, className, section, yearLevel));
            updateClassSpinner();
            if (onComplete != null) {
                onComplete.run();
            }
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
                if (onComplete != null) {
                    onComplete.run();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching subject info: " + error.getMessage());
                // Add class without subject info
                String className = "Class " + classId + " - " + section + " (Year " + yearLevel + ")";
                teacherClasses.add(new ClassItem(classId, className, section, yearLevel));
                updateClassSpinner();
                if (onComplete != null) {
                    onComplete.run();
                }
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

            // Custom adapter to make placeholder text visible
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, classNames) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView textView = (TextView) view.findViewById(android.R.id.text1);
                    if (textView != null) {
                        if (position == 0) {
                            // Placeholder text - make it dark gray for visibility
                            textView.setTextColor(0xFF424242); // Dark gray
                        } else {
                            // Regular items - black
                            textView.setTextColor(0xFF212121); // Dark gray/black
                        }
                    }
                    return view;
                }

                @Override
                public View getDropDownView(int position, View convertView, ViewGroup parent) {
                    View view = super.getDropDownView(position, convertView, parent);
                    TextView textView = (TextView) view.findViewById(android.R.id.text1);
                    if (textView != null) {
                        if (position == 0) {
                            // Placeholder text in dropdown - make it dark gray for visibility
                            textView.setTextColor(0xFF424242); // Dark gray
                        } else {
                            // Regular items in dropdown - black
                            textView.setTextColor(0xFF212121); // Dark gray/black
                        }
                    }
                    return view;
                }
            };
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
                allStudents.clear();
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
                            // Fetch profile_picture (which contains base64 string in Firebase)
                            String profilePicture = dataSnapshot.child("profile_picture").getValue(String.class);
                            
                            // Log profile picture fetch status
                            if (profilePicture != null && !profilePicture.isEmpty()) {
                                Log.d(TAG, "Fetched profile_picture for student " + studentId + ", length: " + profilePicture.length());
                            } else {
                                Log.d(TAG, "No profile_picture found for student " + studentId);
                            }

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
                                StudentItem student = new StudentItem(
                                    id,
                                    studentId,
                                    fullName,
                                    email != null ? email : "",
                                    createdAt != null ? createdAt : "",
                                    studentSection,
                                    studentYearLevel,
                                    profilePicture != null ? profilePicture : "" // Base64 string from Firebase
                                );
                                allStudents.add(student);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing student: " + e.getMessage());
                    }
                }

                runOnUiThread(() -> {
                    loadingProgress.setVisibility(View.GONE);
                    if (allStudents.isEmpty()) {
                        searchCard.setVisibility(View.GONE);
                        showEmptyState();
                    } else {
                        emptyStateText.setVisibility(View.GONE);
                        // Show search card when students are loaded
                        searchCard.setVisibility(View.VISIBLE);
                        // Apply current search filter if any
                        String searchQuery = searchEditText.getText().toString();
                        filterStudents(searchQuery);
                        // Show section header when students are loaded
                        TextView sectionHeader = findViewById(R.id.studentsSectionHeader);
                        if (sectionHeader != null) {
                            sectionHeader.setVisibility(View.VISIBLE);
                        }
                        Log.d(TAG, "Loaded " + allStudents.size() + " students");
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching students: " + error.getMessage());
                runOnUiThread(() -> {
                    if (loadingProgress != null) {
                        loadingProgress.setVisibility(View.GONE);
                    }
                    if (studentsRecyclerView != null) {
                        studentsRecyclerView.setVisibility(View.GONE);
                    }
                    if (emptyStateText != null) {
                        emptyStateText.setVisibility(View.VISIBLE);
                        // Update error message in empty state
                        TextView emptyTitle = emptyStateText.findViewById(R.id.emptyStateTitle);
                        if (emptyTitle != null) {
                            emptyTitle.setText("Error loading students");
                        }
                    }
                });
            }
        });
    }

    private void filterStudents(String query) {
        currentStudents.clear();
        
        if (query == null || query.trim().isEmpty()) {
            // No filter, show all students
            currentStudents.addAll(allStudents);
        } else {
            // Filter by name or ID (case-insensitive)
            String lowerQuery = query.toLowerCase().trim();
            for (StudentItem student : allStudents) {
                if ((student.fullName != null && student.fullName.toLowerCase().contains(lowerQuery)) ||
                    (student.studentId != null && student.studentId.toLowerCase().contains(lowerQuery))) {
                    currentStudents.add(student);
                }
            }
        }
        
        // Update adapter
        studentsAdapter.updateStudents(currentStudents);
        
        // Show/hide empty state
        if (currentStudents.isEmpty()) {
            emptyStateText.setVisibility(View.VISIBLE);
            studentsRecyclerView.setVisibility(View.GONE);
            TextView emptyTitle = emptyStateText.findViewById(R.id.emptyStateTitle);
            if (emptyTitle != null) {
                if (query == null || query.trim().isEmpty()) {
                    emptyTitle.setText("No students found");
                } else {
                    emptyTitle.setText("No students match \"" + query + "\"");
                }
            }
        } else {
            emptyStateText.setVisibility(View.GONE);
            studentsRecyclerView.setVisibility(View.VISIBLE);
        }
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
        Intent intent = null;

        if (id == R.id.nav_dashboard) {
            intent = new Intent(this, TeacherDashboardActivity.class);
        } else if (id == R.id.nav_timetable) {
            intent = new Intent(this, TeacherTimetableActivity.class);
        } else if (id == R.id.nav_students) {
            // Already on students page
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        } else if (id == R.id.nav_attendance) {
            intent = new Intent(this, RfidAttendanceActivity.class);
        }

        if (intent != null) {
            intent.putExtra("userId", getIntent().getStringExtra("userId"));
            intent.putExtra("userType", getIntent().getStringExtra("userType"));
            intent.putExtra("fullName", getIntent().getStringExtra("fullName"));
            intent.putExtra("email", getIntent().getStringExtra("email"));
            startActivity(intent);
            finish();
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

    private void showStudentDetailsModal(StudentItem student) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_student_details);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        // Initialize views
        ImageView closeButton = dialog.findViewById(R.id.closeButton);
        ImageView modalAvatar = dialog.findViewById(R.id.modalStudentAvatar);
        TextView modalName = dialog.findViewById(R.id.modalStudentName);
        TextView modalStudentId = dialog.findViewById(R.id.modalStudentId);
        TextView modalEmail = dialog.findViewById(R.id.modalStudentEmail);
        TextView modalCreatedAt = dialog.findViewById(R.id.modalStudentCreatedAt);
        TextView modalSection = dialog.findViewById(R.id.modalStudentSection);
        TextView modalYearLevel = dialog.findViewById(R.id.modalStudentYearLevel);

        // Populate data
        if (modalName != null) modalName.setText(student.fullName);
        if (modalStudentId != null) modalStudentId.setText(student.studentId);
        if (modalEmail != null) modalEmail.setText(student.email != null && !student.email.isEmpty() ? student.email : "N/A");
        if (modalCreatedAt != null) {
            String dateStr = student.createdAt != null && !student.createdAt.isEmpty() ? 
                formatDateForModal(student.createdAt) : "N/A";
            modalCreatedAt.setText(dateStr);
        }
        if (modalSection != null) modalSection.setText(student.section != null ? student.section : "N/A");
        if (modalYearLevel != null) modalYearLevel.setText(student.yearLevel != null ? "Year " + student.yearLevel : "N/A");

        // Load profile picture
        if (student.profilePictureBase64 != null && !student.profilePictureBase64.isEmpty()) {
            Bitmap bitmap = decodeBase64ToBitmap(student.profilePictureBase64);
            if (bitmap != null) {
                Bitmap circularBitmap = getCircularBitmap(bitmap);
                if (modalAvatar != null) {
                    modalAvatar.setImageBitmap(circularBitmap);
                    modalAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    modalAvatar.clearColorFilter();
                    modalAvatar.setBackground(null);
                }
            } else {
                if (modalAvatar != null) {
                    modalAvatar.setImageResource(R.drawable.ic_person);
                    modalAvatar.setColorFilter(0xFF2196F3);
                    modalAvatar.setBackgroundResource(R.drawable.student_avatar_background);
                }
            }
        } else {
            if (modalAvatar != null) {
                modalAvatar.setImageResource(R.drawable.ic_person);
                modalAvatar.setColorFilter(0xFF2196F3);
                modalAvatar.setBackgroundResource(R.drawable.student_avatar_background);
            }
        }

        // Close button
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> dialog.dismiss());
        }

        // Close on outside click
        dialog.setCanceledOnTouchOutside(true);

        dialog.show();
    }

    private String formatDateForModal(String dateStr) {
        try {
            if (dateStr == null || dateStr.isEmpty()) return "N/A";
            if (dateStr.length() >= 10) {
                return dateStr.substring(0, 10);
            }
            return dateStr;
        } catch (Exception e) {
            return dateStr;
        }
    }

    private Bitmap decodeBase64ToBitmap(String base64String) {
        try {
            if (base64String == null || base64String.isEmpty()) {
                return null;
            }
            
            String base64Image = base64String.trim();
            if (base64Image.contains(",")) {
                base64Image = base64Image.substring(base64Image.indexOf(",") + 1);
            }
            
            base64Image = base64Image.replaceAll("\\s", "");
            
            byte[] decodedBytes = Base64.decode(base64Image, Base64.DEFAULT);
            
            if (decodedBytes == null || decodedBytes.length == 0) {
                return null;
            }
            
            return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
        } catch (Exception e) {
            Log.e(TAG, "Error decoding base64: " + e.getMessage(), e);
            return null;
        }
    }

    private Bitmap getCircularBitmap(Bitmap bitmap) {
        try {
            int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
            Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            
            final Paint paint = new Paint();
            final Rect rect = new Rect(0, 0, size, size);
            final RectF rectF = new RectF(rect);
            
            paint.setAntiAlias(true);
            paint.setFilterBitmap(true);
            paint.setDither(true);
            
            canvas.drawARGB(0, 0, 0, 0);
            canvas.drawOval(rectF, paint);
            
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            
            int x = (bitmap.getWidth() - size) / 2;
            int y = (bitmap.getHeight() - size) / 2;
            Rect srcRect = new Rect(x, y, x + size, y + size);
            canvas.drawBitmap(bitmap, srcRect, rect, paint);
            
            return output;
        } catch (Exception e) {
            Log.e(TAG, "Error creating circular bitmap: " + e.getMessage(), e);
            return bitmap;
        }
    }
}

