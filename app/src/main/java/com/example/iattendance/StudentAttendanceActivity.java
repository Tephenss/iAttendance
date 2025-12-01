package com.example.iattendance;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StudentAttendanceActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "StudentAttendance";
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private SessionManager sessionManager;
    private RecyclerView attendanceRecyclerView;
    private ProgressBar loadingProgress;
    private View emptyStateText;
    private CardView attendanceListCard;
    private TextView dateText;
    private TextView emptyStateTitle;

    private String studentId;
    private String studentYearLevel;
    private String studentSection;
    private String studentCourse;
    private String studentCourseId;
    private String attendanceDate;

    private List<AttendanceItem> attendanceList = new ArrayList<>();
    private List<AttendanceItem> workingList = new ArrayList<>(); // Shared list for building
    private AttendanceListAdapter attendanceAdapter;
    private int totalItemsToProcess = 0;
    private int processedItemsCount = 0;

    // Realtime attendance listener
    private DatabaseReference attendanceRealtimeRef;
    private ValueEventListener attendanceRealtimeListener;

    // Helper class for attendance items
    public static class AttendanceItem {
        String classId;
        String subjectName;
        String subjectCode;
        String section;
        String startTime;
        String endTime;
        String timeDisplay;
        String status; // "present", "late", "absent", null (not recorded)
        int startTimeMinutes; // For sorting

        AttendanceItem(String classId, String subjectName, String subjectCode, String section, 
                      String startTime, String endTime, String status) {
            this.classId = classId;
            this.subjectName = subjectName;
            this.subjectCode = subjectCode;
            this.section = section;
            this.startTime = startTime;
            this.endTime = endTime;
            this.status = status;
            
            // Parse time for sorting and display
            this.startTimeMinutes = parseTimeToMinutes(startTime);
            this.timeDisplay = formatTimeDisplay(startTime, endTime);
        }

        private int parseTimeToMinutes(String timeStr) {
            if (timeStr == null || timeStr.isEmpty()) return 0;
            try {
                String[] parts = timeStr.split(":");
                if (parts.length >= 2) {
                    int hours = Integer.parseInt(parts[0]);
                    int minutes = Integer.parseInt(parts[1]);
                    return hours * 60 + minutes;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing time: " + timeStr);
            }
            return 0;
        }

        private String formatTimeDisplay(String start, String end) {
            if (start == null || end == null) return "";
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                SimpleDateFormat outputFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
                
                Date startDate = inputFormat.parse(start.length() >= 8 ? start.substring(0, 8) : start);
                Date endDate = inputFormat.parse(end.length() >= 8 ? end.substring(0, 8) : end);
                
                if (startDate != null && endDate != null) {
                    return outputFormat.format(startDate) + " - " + outputFormat.format(endDate);
                }
            } catch (Exception e) {
                // Fallback to original format
                return start + " - " + end;
            }
            return start + " - " + end;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_attendance);

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
        attendanceRecyclerView = findViewById(R.id.attendanceRecyclerView);
        loadingProgress = findViewById(R.id.loadingProgress);
        emptyStateText = findViewById(R.id.emptyStateText);
        attendanceListCard = findViewById(R.id.attendanceListCard);
        dateText = findViewById(R.id.dateText);
        emptyStateTitle = findViewById(R.id.emptyStateTitle);

        // Setup drawer
        findViewById(R.id.menu_icon).setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        findViewById(R.id.more_icon).setOnClickListener(v -> onOptionsIconClick());

        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_my_attendance);

        // Get user data
        studentId = getIntent().getStringExtra("userId");
        String fullName = getIntent().getStringExtra("fullName");

        // Set current date
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        attendanceDate = sdf.format(new Date());
        if (dateText != null) {
            SimpleDateFormat displayFormat = new SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault());
            dateText.setText(displayFormat.format(new Date()));
        }

        // Update nav header
        navigationView.post(() -> {
            View headerView = navigationView.getHeaderView(0);
            if (headerView != null) {
                TextView navName = headerView.findViewById(R.id.nav_header_name);
                TextView navEmail = headerView.findViewById(R.id.nav_header_email);

                if (navName != null) navName.setText(fullName);
                if (navEmail != null) navEmail.setText("ID: " + studentId);
            }
        });

        // Setup RecyclerView
        attendanceAdapter = new AttendanceListAdapter(attendanceList);
        attendanceRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        attendanceRecyclerView.setAdapter(attendanceAdapter);

        // Fetch student info and attendance
        fetchStudentInfo(studentId);

        // Setup realtime listener for attendance updates
        setupRealtimeAttendanceListener();
    }

    private void fetchStudentInfo(String userId) {
        loadingProgress.setVisibility(View.VISIBLE);
        attendanceListCard.setVisibility(View.GONE);
        emptyStateText.setVisibility(View.GONE);

        Log.d(TAG, "Fetching student info for userId: " + userId);

        DatabaseReference studentsRef = FirebaseDatabase.getInstance()
                .getReference("attendance_system/students");
        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean found = false;
                for (DataSnapshot studentSnapshot : snapshot.getChildren()) {
                    try {
                        DataSnapshot dataSnapshot = studentSnapshot.child("data");
                        if (!dataSnapshot.exists()) continue;

                        String studentIdFromDb = dataSnapshot.child("student_id").getValue(String.class);
                        String idFromDb = dataSnapshot.child("id").getValue(String.class);

                        if ((studentIdFromDb != null && userId.equals(studentIdFromDb)) || 
                            (idFromDb != null && userId.equals(idFromDb))) {
                            found = true;
                            studentYearLevel = dataSnapshot.child("year_level").getValue(String.class);
                            studentSection = dataSnapshot.child("section").getValue(String.class);
                            studentCourse = dataSnapshot.child("course").getValue(String.class);

                            Log.d(TAG, "✓ Found student - Year: " + studentYearLevel + ", Section: " + studentSection + ", Course: " + studentCourse);

                            if (studentCourse != null) {
                                fetchCourseIdFromCourses(studentCourse);
                            } else {
                                fetchTodayAttendance();
                            }
                            break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing student: " + e.getMessage());
                    }
                }

                if (!found) {
                    Log.e(TAG, "Student not found");
                    runOnUiThread(() -> {
                        loadingProgress.setVisibility(View.GONE);
                        if (emptyStateText != null) {
                            emptyStateText.setVisibility(View.VISIBLE);
                        }
                        if (emptyStateTitle != null) {
                            emptyStateTitle.setText("Student information not found");
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching student: " + error.getMessage());
                runOnUiThread(() -> {
                    loadingProgress.setVisibility(View.GONE);
                    Toast.makeText(StudentAttendanceActivity.this, "Error loading attendance", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void fetchCourseIdFromCourses(String courseCode) {
        Log.d(TAG, "Fetching course_id for course: " + courseCode);

        DatabaseReference coursesRef = FirebaseDatabase.getInstance()
                .getReference("attendance_system/courses");
        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot courseSnapshot : snapshot.getChildren()) {
                    try {
                        DataSnapshot dataSnapshot = courseSnapshot.child("data");
                        if (!dataSnapshot.exists()) continue;

                        String code = dataSnapshot.child("code").getValue(String.class);
                        if (courseCode.equals(code)) {
                            studentCourseId = dataSnapshot.child("id").getValue(String.class);
                            Log.d(TAG, "Found course_id: " + studentCourseId);
                            break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing course: " + e.getMessage());
                    }
                }
                fetchTodayAttendance();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching courses: " + error.getMessage());
                fetchTodayAttendance();
            }
        });
    }

    private void fetchTodayAttendance() {
        Log.d(TAG, "Fetching today's attendance...");

        // Reset counters
        totalItemsToProcess = 0;
        processedItemsCount = 0;

        // Get today's day name
        Calendar calendar = Calendar.getInstance();
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        String[] daysArray = {"", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        String todayDay;
        if (dayOfWeek == Calendar.SUNDAY) {
            todayDay = daysArray[7];
        } else {
            todayDay = daysArray[dayOfWeek - 1];
        }

        Log.d(TAG, "Today is: " + todayDay);

        // Fetch timetable entries for today
        DatabaseReference timetableRef = FirebaseDatabase.getInstance()
                .getReference("attendance_system/timetable");
        timetableRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                workingList.clear(); // Reset working list

                for (DataSnapshot entrySnapshot : snapshot.getChildren()) {
                    try {
                        DataSnapshot dataSnapshot = entrySnapshot.child("data");
                        if (!dataSnapshot.exists()) continue;

                        String classId = dataSnapshot.child("class_id").getValue(String.class);
                        String dayOfWeek = dataSnapshot.child("day_of_week").getValue(String.class);
                        String startTime = dataSnapshot.child("start_time").getValue(String.class);
                        String endTime = dataSnapshot.child("end_time").getValue(String.class);

                        // Check if it's today's schedule
                        if (dayOfWeek == null || !dayOfWeek.equalsIgnoreCase(todayDay)) {
                            continue;
                        }

                        if (classId == null || startTime == null || endTime == null) {
                            continue;
                        }

                        // Fetch class details to verify student enrollment
                        totalItemsToProcess++;
                        fetchClassDetailsAndAddToList(classId, startTime, endTime);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing timetable entry: " + e.getMessage());
                    }
                }

                // Check if no entries found at all
                if (totalItemsToProcess == 0) {
                    runOnUiThread(() -> {
                        loadingProgress.setVisibility(View.GONE);
                        attendanceListCard.setVisibility(View.GONE);
                        emptyStateText.setVisibility(View.VISIBLE);
                        if (emptyStateTitle != null) {
                            emptyStateTitle.setText("No classes scheduled for today");
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching timetable: " + error.getMessage());
                runOnUiThread(() -> {
                    loadingProgress.setVisibility(View.GONE);
                    Toast.makeText(StudentAttendanceActivity.this, "Error loading timetable", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void fetchClassDetailsAndAddToList(String classId, String startTime, String endTime) {
        DatabaseReference classesRef = FirebaseDatabase.getInstance()
                .getReference("attendance_system/classes");
        classesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot classSnapshot : snapshot.getChildren()) {
                    try {
                        DataSnapshot dataSnapshot = classSnapshot.child("data");
                        if (!dataSnapshot.exists()) continue;

                        String classIdFromDb = dataSnapshot.child("id").getValue(String.class);
                        if (classId == null || !classId.equals(classIdFromDb)) {
                            continue;
                        }

                        String section = dataSnapshot.child("section").getValue(String.class);
                        String yearLevel = dataSnapshot.child("year_level").getValue(String.class);
                        String subjectId = dataSnapshot.child("subject_id").getValue(String.class);

                        // Verify student belongs to this class (section and year match)
                        if (section == null || yearLevel == null || 
                            !section.equals(studentSection) || !yearLevel.equals(studentYearLevel)) {
                            continue;
                        }

                        // Fetch subject details
                        fetchSubjectAndCreateItem(subjectId, classId, section, startTime, endTime);
                        return;
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing class: " + e.getMessage());
                    }
                }
                // Class not found or student not enrolled
                processedItemsCount++;
                checkAndDisplayAttendance();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching class: " + error.getMessage());
                processedItemsCount++;
                checkAndDisplayAttendance();
            }
        });
    }

    private void fetchSubjectAndCreateItem(String subjectId, String classId, String section, 
                                          String startTime, String endTime) {
        if (subjectId == null) {
            processedItemsCount++;
            checkAndDisplayAttendance();
            return;
        }

        DatabaseReference subjectsRef = FirebaseDatabase.getInstance()
                .getReference("attendance_system/subjects");
        subjectsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot subjectSnapshot : snapshot.getChildren()) {
                    try {
                        DataSnapshot dataSnapshot = subjectSnapshot.child("data");
                        if (!dataSnapshot.exists()) continue;

                        String subjectIdFromDb = dataSnapshot.child("id").getValue(String.class);
                        if (subjectId == null || !subjectId.equals(subjectIdFromDb)) {
                            continue;
                        }

                        String subjectName = dataSnapshot.child("subject_name").getValue(String.class);
                        String subjectCode = dataSnapshot.child("subject_code").getValue(String.class);

                        // Create attendance item (status will be fetched later)
                        AttendanceItem item = new AttendanceItem(classId, subjectName, subjectCode, section, 
                                                                 startTime, endTime, null);
                        workingList.add(item);

                        // Fetch attendance status
                        fetchAttendanceStatus(item);
                        return;
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing subject: " + e.getMessage());
                    }
                }
                // Subject not found
                processedItemsCount++;
                checkAndDisplayAttendance();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching subject: " + error.getMessage());
                processedItemsCount++;
                checkAndDisplayAttendance();
            }
        });
    }

    private void fetchAttendanceStatus(AttendanceItem item) {
        DatabaseReference attendanceRef = FirebaseDatabase.getInstance()
                .getReference("attendance_system/attendance");
        attendanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean found = false;

                for (DataSnapshot attSnapshot : snapshot.getChildren()) {
                    try {
                        // Support both nested \"data\" structure and direct fields
                        DataSnapshot attData = attSnapshot.child("data");
                        if (!attData.exists()) {
                            attData = attSnapshot;
                        }

                        Object attClassIdObj = attData.child("class_id").getValue();
                        Object attStudentIdObj = attData.child("student_id").getValue();
                        String attDate = attData.child("date").getValue(String.class);
                        String attStatus = attData.child("status").getValue(String.class);

                        String attClassId = attClassIdObj != null ? String.valueOf(attClassIdObj) : null;
                        String attStudentId = attStudentIdObj != null ? String.valueOf(attStudentIdObj) : null;

                        // Normalize dates
                        String normalizedAttDate = attDate != null ? attDate.replace("-", "").replace("/", "").trim() : null;
                        String normalizedCurrentDate = attendanceDate != null ? attendanceDate.replace("-", "").replace("/", "").trim() : null;

                        // Match by class_id, student_id, and date
                        if (attClassId != null && attClassId.equals(item.classId) &&
                            attStudentId != null && attStudentId.equals(studentId) &&
                            normalizedAttDate != null && normalizedCurrentDate != null &&
                            normalizedAttDate.equals(normalizedCurrentDate) &&
                            attStatus != null) {
                            item.status = attStatus.toLowerCase();
                            found = true;
                            Log.d(TAG, "Found attendance for " + item.subjectCode + ": " + item.status);
                            break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing attendance: " + e.getMessage());
                    }
                }

                if (!found) {
                    item.status = null; // No attendance recorded yet
                }

                // Increment processed count
                processedItemsCount++;
                
                // Check if all items have been processed
                checkAndDisplayAttendance();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching attendance: " + error.getMessage());
                processedItemsCount++;
                checkAndDisplayAttendance();
            }
        });
    }

    private synchronized void checkAndDisplayAttendance() {
        // Wait until all items have been processed
        if (processedItemsCount < totalItemsToProcess) {
            Log.d(TAG, "Waiting for more items... Processed: " + processedItemsCount + "/" + totalItemsToProcess);
            return;
        }

        // Sort by start time (morning first)
        workingList.sort((a, b) -> Integer.compare(a.startTimeMinutes, b.startTimeMinutes));

        runOnUiThread(() -> {
            attendanceList.clear();
            attendanceList.addAll(workingList);
            attendanceAdapter.notifyDataSetChanged();

            loadingProgress.setVisibility(View.GONE);
            if (attendanceList.isEmpty()) {
                attendanceListCard.setVisibility(View.GONE);
                emptyStateText.setVisibility(View.VISIBLE);
                if (emptyStateTitle != null) {
                    emptyStateTitle.setText("No classes scheduled for today");
                }
            } else {
                attendanceListCard.setVisibility(View.VISIBLE);
                emptyStateText.setVisibility(View.GONE);
            }
        });
    }

    /**
     * Realtime listener: listens to Firebase attendance node and updates statuses
     * for current student and date whenever there is a change.
     */
    private void setupRealtimeAttendanceListener() {
        // Clean up old listener if any
        if (attendanceRealtimeRef != null && attendanceRealtimeListener != null) {
            attendanceRealtimeRef.removeEventListener(attendanceRealtimeListener);
        }

        attendanceRealtimeRef = FirebaseDatabase.getInstance()
                .getReference("attendance_system/attendance");

        attendanceRealtimeListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (workingList.isEmpty()) {
                    // No timetable entries yet, nothing to update
                    return;
                }

                // Build latest status map per classId for this student & date
                Map<String, String> latestStatusByClass = new HashMap<>();
                Map<String, Long> latestTimestampByClass = new HashMap<>();

                for (DataSnapshot attSnapshot : snapshot.getChildren()) {
                    try {
                        DataSnapshot dataSnap = attSnapshot.child("data");
                        if (!dataSnap.exists()) {
                            dataSnap = attSnapshot;
                        }

                        Object attClassIdObj = dataSnap.child("class_id").getValue();
                        Object attStudentIdObj = dataSnap.child("student_id").getValue();
                        String attDate = dataSnap.child("date").getValue(String.class);
                        String attStatus = dataSnap.child("status").getValue(String.class);

                        // Try to get timestamp (top-level or nested)
                        Long ts = attSnapshot.child("timestamp").getValue(Long.class);
                        if (ts == null) {
                            ts = dataSnap.child("timestamp").getValue(Long.class);
                        }
                        if (ts == null) {
                            String createdAt = dataSnap.child("created_at").getValue(String.class);
                            ts = createdAt != null ? (long) createdAt.hashCode() : 0L;
                        }

                        String attClassId = attClassIdObj != null ? String.valueOf(attClassIdObj) : null;
                        String attStudentId = attStudentIdObj != null ? String.valueOf(attStudentIdObj) : null;

                        String normalizedAttDate = attDate != null ? attDate.replace("-", "").replace("/", "").trim() : null;
                        String normalizedCurrentDate = attendanceDate != null ? attendanceDate.replace("-", "").replace("/", "").trim() : null;

                        if (attClassId == null || attStudentId == null || attStatus == null ||
                                normalizedAttDate == null || normalizedCurrentDate == null) {
                            continue;
                        }

                        if (!attStudentId.equals(studentId)) continue;
                        if (!normalizedAttDate.equals(normalizedCurrentDate)) continue;
                        
                        // Keep only latest per classId
                        Long existingTs = latestTimestampByClass.get(attClassId);
                        if (existingTs == null || ts > existingTs) {
                            latestStatusByClass.put(attClassId, attStatus.toLowerCase());
                            latestTimestampByClass.put(attClassId, ts);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in realtime attendance parsing: " + e.getMessage());
                    }
                }

                // Apply statuses to workingList and refresh UI
                for (AttendanceItem item : workingList) {
                    String status = latestStatusByClass.get(item.classId);
                    item.status = status != null ? status : null;
                }

                runOnUiThread(() -> {
                    attendanceList.clear();
                    attendanceList.addAll(workingList);
                    attendanceAdapter.notifyDataSetChanged();
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Realtime attendance listener cancelled: " + error.getMessage());
            }
        };

        attendanceRealtimeRef.addValueEventListener(attendanceRealtimeListener);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        Intent intent = null;

        if (id == R.id.nav_dashboard) {
            intent = new Intent(this, StudentDashboardActivity.class);
        } else if (id == R.id.nav_timetable) {
            intent = new Intent(this, TimetableActivity.class);
        } else if (id == R.id.nav_my_attendance) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }

        if (intent != null) {
            intent.putExtra("userId", studentId);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (attendanceRealtimeRef != null && attendanceRealtimeListener != null) {
            attendanceRealtimeRef.removeEventListener(attendanceRealtimeListener);
        }
    }

    public void onOptionsIconClick() {
        View moreIcon = findViewById(R.id.more_icon);
        if (moreIcon != null) {
            android.widget.PopupMenu popupMenu = new android.widget.PopupMenu(new android.view.ContextThemeWrapper(this, R.style.PopupMenuStyle), moreIcon);
            popupMenu.getMenuInflater().inflate(R.menu.toolbar_menu, popupMenu.getMenu());
            popupMenu.setGravity(android.view.Gravity.END);
            popupMenu.setOnMenuItemClickListener(menuItem -> {
                if (menuItem.getItemId() == R.id.menu_logout) {
                    sessionManager.logout();
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                    return true;
                }
                return false;
            });
            popupMenu.show();
        }
    }
}

