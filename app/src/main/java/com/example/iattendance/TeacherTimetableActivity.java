package com.example.iattendance;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TeacherTimetableActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "TeacherTimetableActivity";
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private SessionManager sessionManager;
    private TableLayout timetableTable;
    private ProgressBar loadingProgress;
    private TextView emptyStateText;
    private CardView timetableCard;
    private TextView currentDayIndicator;

    private String teacherId;
    private ValueEventListener timetableListener;
    private DatabaseReference timetableRef;
    private List<String> teacherClassIds; // Store class IDs for real-time updates

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_timetable);

        sessionManager = new SessionManager(this);

        // Initialize views
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        timetableTable = findViewById(R.id.timetableTable);
        loadingProgress = findViewById(R.id.loadingProgress);
        emptyStateText = findViewById(R.id.emptyStateText);
        timetableCard = findViewById(R.id.timetableCard);
        currentDayIndicator = findViewById(R.id.currentDayIndicator);

        // Set current day indicator immediately
        Calendar calendar = Calendar.getInstance();
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        String[] daysArray = {"", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        String todayDay;
        if (dayOfWeek == Calendar.SUNDAY) {
            todayDay = daysArray[7];
        } else {
            todayDay = daysArray[dayOfWeek - 1];
        }
        if (currentDayIndicator != null) {
            currentDayIndicator.setText(todayDay);
        }

        // Setup drawer
        findViewById(R.id.menu_icon).setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        findViewById(R.id.more_icon).setOnClickListener(v -> onOptionsIconClick());

        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_timetable);

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

        // Setup blue theme
        View toolbarContainer = findViewById(R.id.toolbar_container);
        if (toolbarContainer != null) {
            toolbarContainer.setBackgroundColor(Color.parseColor("#2196F3")); // Blue
        }
        if (currentDayIndicator != null) {
            currentDayIndicator.setTextColor(Color.parseColor("#2196F3")); // Blue
        }

        // Fetch teacher timetable
        fetchTeacherTimetable(teacherId);
    }

    private void fetchTeacherTimetable(String userId) {
        loadingProgress.setVisibility(View.VISIBLE);
        timetableCard.setVisibility(View.GONE);

        Log.d(TAG, "Fetching teacher timetable for teacherId: " + userId);

        // First, find teacher's classes
        DatabaseReference classesRef = FirebaseDatabase.getInstance().getReference("attendance_system/classes");
        classesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> teacherClassIds = new ArrayList<>();

                // Normalize teacher ID (handle T0001 format)
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
                                    Log.d(TAG, "Match found via numeric comparison: " + userIdNum);
                                }
                            } catch (NumberFormatException e) {
                                // Not numeric, skip
                            }
                        }

                        if (matches) {
                            teacherClassIds.add(classId);
                            Log.d(TAG, "✓ Found class " + classId + " for teacher " + userId);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing class " + classSnapshot.getKey() + ": " + e.getMessage());
                    }
                }

                Log.d(TAG, "Teacher has " + teacherClassIds.size() + " classes: " + teacherClassIds);
                if (teacherClassIds.isEmpty()) {
                    runOnUiThread(() -> {
                        loadingProgress.setVisibility(View.GONE);
                        showEmptyState();
                        Toast.makeText(TeacherTimetableActivity.this, "No classes found for this teacher", Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                // Store class IDs for real-time updates
                TeacherTimetableActivity.this.teacherClassIds = teacherClassIds;
                
                // Now fetch timetable entries for these classes
                fetchTeacherTimetableEntries(teacherClassIds);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching teacher classes: " + error.getMessage());
                runOnUiThread(() -> {
                    loadingProgress.setVisibility(View.GONE);
                    showEmptyState();
                });
            }
        });
    }

    private void fetchTeacherTimetableEntries(List<String> classIds) {
        // Remove previous listener if exists
        if (timetableRef != null && timetableListener != null) {
            timetableRef.removeEventListener(timetableListener);
        }
        
        timetableRef = FirebaseDatabase.getInstance().getReference("attendance_system/timetable");
        timetableListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Use stored classIds or the provided ones
                List<String> currentClassIds = teacherClassIds != null ? teacherClassIds : classIds;
                
                List<TimetableEntry> entries = new ArrayList<>();
                int totalEntries = (int) snapshot.getChildrenCount();
                final int[] processedCount = {0};
                
                if (totalEntries == 0) {
                    runOnUiThread(() -> {
                        loadingProgress.setVisibility(View.GONE);
                        showEmptyState();
                    });
                    return;
                }

                for (DataSnapshot entrySnapshot : snapshot.getChildren()) {
                    try {
                        DataSnapshot dataSnapshot = entrySnapshot.child("data");
                        if (!dataSnapshot.exists()) {
                            processedCount[0]++;
                            checkIfComplete(entries, totalEntries, processedCount[0]);
                            continue;
                        }

                        String classId = dataSnapshot.child("class_id").getValue(String.class);
                        String dayOfWeek = dataSnapshot.child("day_of_week").getValue(String.class);
                        String startTime = dataSnapshot.child("start_time").getValue(String.class);
                        String endTime = dataSnapshot.child("end_time").getValue(String.class);
                        String room = dataSnapshot.child("room").getValue(String.class);

                        Log.d(TAG, "Timetable entry - classId: " + classId + ", day: " + dayOfWeek);

                        // Only process entries for teacher's classes
                        if (classId != null && currentClassIds.contains(classId)) {
                            Log.d(TAG, "✓ Processing timetable entry for classId: " + classId);
                            fetchTeacherClassDetails(classId, dayOfWeek, startTime, endTime, room, entries, totalEntries, processedCount);
                        } else {
                            Log.d(TAG, "✗ Skipping timetable entry - classId: " + classId + " not in teacher's classes");
                            processedCount[0]++;
                            checkIfComplete(entries, totalEntries, processedCount[0]);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing timetable entry: " + e.getMessage());
                        processedCount[0]++;
                        checkIfComplete(entries, totalEntries, processedCount[0]);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching timetable: " + error.getMessage());
                runOnUiThread(() -> {
                    loadingProgress.setVisibility(View.GONE);
                    showEmptyState();
                });
            }
        };
        
        // Use addValueEventListener for real-time updates
        timetableRef.addValueEventListener(timetableListener);
    }

    private void fetchTeacherClassDetails(String classId, String dayOfWeek, String startTime, String endTime, 
                                         String room, List<TimetableEntry> entries, int totalEntries, int[] processedCount) {
        DatabaseReference classesRef = FirebaseDatabase.getInstance().getReference("attendance_system/classes");
        classesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean found = false;
                
                for (DataSnapshot classSnapshot : snapshot.getChildren()) {
                    try {
                        DataSnapshot dataSnapshot = classSnapshot.child("data");
                        if (!dataSnapshot.exists()) continue;
                        
                        String id = dataSnapshot.child("id").getValue(String.class);
                        
                        if (classId.equals(id)) {
                            found = true;
                            String section = dataSnapshot.child("section").getValue(String.class);
                            String yearLevel = dataSnapshot.child("year_level").getValue(String.class);
                            String subjectId = dataSnapshot.child("subject_id").getValue(String.class);
                            
                            Log.d(TAG, "Found class - section: " + section + ", yearLevel: " + yearLevel + ", subjectId: " + subjectId);
                            
                            // Fetch subject details
                            fetchTeacherSubjectDetails(subjectId, yearLevel, section, dayOfWeek, startTime, endTime, room, entries, totalEntries, processedCount);
                            break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing class: " + e.getMessage());
                    }
                }
                
                if (!found) {
                    Log.d(TAG, "Class " + classId + " not found in classes");
                    processedCount[0]++;
                    checkIfComplete(entries, totalEntries, processedCount[0]);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching class details: " + error.getMessage());
                processedCount[0]++;
                checkIfComplete(entries, totalEntries, processedCount[0]);
            }
        });
    }

    private void fetchTeacherSubjectDetails(String subjectId, String yearLevel, String section, String dayOfWeek,
                                           String startTime, String endTime, String room, List<TimetableEntry> entries,
                                           int totalEntries, int[] processedCount) {
        Log.d(TAG, "Fetching subject details for subjectId: " + subjectId);
        
        if (subjectId == null || subjectId.trim().isEmpty()) {
            // Create entry even if subjectId is missing
            TimetableEntry entry = new TimetableEntry();
            entry.dayOfWeek = dayOfWeek;
            entry.startTime = startTime;
            entry.endTime = endTime;
            entry.room = room;
            entry.subjectCode = "N/A";
            entry.subjectName = "No Subject";
            entry.teacherName = yearLevel + "-" + section; // Store year-section for teacher view
            entries.add(entry);
            processedCount[0]++;
            checkIfComplete(entries, totalEntries, processedCount[0]);
            return;
        }
        
        DatabaseReference subjectsRef = FirebaseDatabase.getInstance().getReference("attendance_system/subjects");
        subjectsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean found = false;
                
                for (DataSnapshot subjectSnapshot : snapshot.getChildren()) {
                    try {
                        DataSnapshot dataSnapshot = subjectSnapshot.child("data");
                        if (!dataSnapshot.exists()) continue;
                        
                        String id = dataSnapshot.child("id").getValue(String.class);
                        
                        if (subjectId.equals(id)) {
                            found = true;
                            
                            String subjectCode = dataSnapshot.child("code").getValue(String.class);
                            String subjectName = dataSnapshot.child("name").getValue(String.class);
                            
                            if (subjectCode == null || subjectCode.isEmpty()) {
                                subjectCode = dataSnapshot.child("subject_code").getValue(String.class);
                            }
                            if (subjectName == null || subjectName.isEmpty()) {
                                subjectName = dataSnapshot.child("subject_name").getValue(String.class);
                            }
                            
                            // Create entry for teacher (shows subject, year, section)
                            TimetableEntry entry = new TimetableEntry();
                            entry.dayOfWeek = dayOfWeek;
                            entry.startTime = startTime;
                            entry.endTime = endTime;
                            entry.room = room;
                            entry.subjectCode = subjectCode != null && !subjectCode.isEmpty() ? subjectCode : "N/A";
                            entry.subjectName = subjectName != null && !subjectName.isEmpty() ? subjectName : "Subject ID: " + subjectId;
                            entry.teacherName = yearLevel + "-" + section; // Store year-section in teacherName field for teacher view
                            
                            entries.add(entry);
                            Log.d(TAG, "✓ Added teacher entry: " + entry.subjectCode + " Year " + yearLevel + " Section " + section + " on " + dayOfWeek);
                            break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing subject: " + e.getMessage());
                    }
                }
                
                if (!found) {
                    // Create entry even if subject not found
                    TimetableEntry entry = new TimetableEntry();
                    entry.dayOfWeek = dayOfWeek;
                    entry.startTime = startTime;
                    entry.endTime = endTime;
                    entry.room = room;
                    entry.subjectCode = "N/A";
                    entry.subjectName = "Subject ID: " + subjectId;
                    entry.teacherName = yearLevel + "-" + section;
                    entries.add(entry);
                    Log.d(TAG, "Added teacher entry (subject not found): Year " + yearLevel + " Section " + section + " on " + dayOfWeek);
                }
                
                processedCount[0]++;
                checkIfComplete(entries, totalEntries, processedCount[0]);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching subject details: " + error.getMessage());
                // Create entry even on error
                TimetableEntry entry = new TimetableEntry();
                entry.dayOfWeek = dayOfWeek;
                entry.startTime = startTime;
                entry.endTime = endTime;
                entry.room = room;
                entry.subjectCode = "Error";
                entry.subjectName = "Error fetching subject";
                entry.teacherName = yearLevel + "-" + section;
                entries.add(entry);
                processedCount[0]++;
                checkIfComplete(entries, totalEntries, processedCount[0]);
            }
        });
    }

    private void checkIfComplete(List<TimetableEntry> entries, int totalEntries, int processedCount) {
        if (processedCount >= totalEntries) {
            runOnUiThread(() -> {
                loadingProgress.setVisibility(View.GONE);

                if (entries.isEmpty()) {
                    showEmptyState();
                    return;
                }

                Log.d(TAG, "Building teacher table with " + entries.size() + " entries");
                buildTimetableTable(entries);
                timetableCard.setVisibility(View.VISIBLE);
            });
        }
    }

    private void buildTimetableTable(List<TimetableEntry> entries) {
        timetableTable.removeAllViews();
        
        // Get current day
        Calendar calendar = Calendar.getInstance();
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        String[] daysArray = {"", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        String todayDay;
        if (dayOfWeek == Calendar.SUNDAY) {
            todayDay = daysArray[7];
        } else {
            todayDay = daysArray[dayOfWeek - 1];
        }
        
        // Filter entries for today only
        List<TimetableEntry> todayEntries = new ArrayList<>();
        for (TimetableEntry entry : entries) {
            String entryDay = convertDayToString(entry.dayOfWeek);
            if (entryDay.equals(todayDay)) {
                todayEntries.add(entry);
            }
        }
        
        // Sort by start time
        Collections.sort(todayEntries, new Comparator<TimetableEntry>() {
            @Override
            public int compare(TimetableEntry e1, TimetableEntry e2) {
                int time1 = parseTimeToMinutes(e1.startTime);
                int time2 = parseTimeToMinutes(e2.startTime);
                return Integer.compare(time1, time2);
            }
        });
        
        // Create header
        TableRow headerRow = new TableRow(this);
        headerRow.setBackgroundColor(Color.parseColor("#f8f9fa"));
        
        TextView timeHeader = createHeaderCell("TIME");
        timeHeader.setMinWidth(dpToPx(150));
        headerRow.addView(timeHeader);
        
        TextView subjectHeader = createHeaderCell("SUBJECT");
        subjectHeader.setMinWidth(dpToPx(150));
        headerRow.addView(subjectHeader);
        
        TextView yearHeader = createHeaderCell("YEAR");
        yearHeader.setMinWidth(dpToPx(100));
        headerRow.addView(yearHeader);
        
        TextView sectionHeader = createHeaderCell("SECTION");
        sectionHeader.setMinWidth(dpToPx(100));
        headerRow.addView(sectionHeader);
        
        timetableTable.addView(headerRow);
        
        // Add entries
        for (TimetableEntry entry : todayEntries) {
            TableRow row = new TableRow(this);
            
            // Time column
            String timeDisplay = formatTime(entry.startTime) + "-" + formatTime(entry.endTime);
            TextView timeCell = createCell(timeDisplay);
            timeCell.setMinWidth(dpToPx(150));
            row.addView(timeCell);
            
            // Subject column
            TextView subjectCell = createCell(entry.subjectCode);
            subjectCell.setMinWidth(dpToPx(150));
            row.addView(subjectCell);
            
            // Year and Section from teacherName field (format: "year-section")
            String yearSection = entry.teacherName;
            String year = "";
            String section = "";
            if (yearSection != null && yearSection.contains("-")) {
                String[] parts = yearSection.split("-", 2);
                year = parts.length > 0 ? parts[0] : "";
                section = parts.length > 1 ? parts[1] : "";
            }
            
            TextView yearCell = createCell(year);
            yearCell.setMinWidth(dpToPx(100));
            row.addView(yearCell);
            
            TextView sectionCell = createCell(section);
            sectionCell.setMinWidth(dpToPx(100));
            row.addView(sectionCell);
            
            timetableTable.addView(row);
        }
    }

    private TextView createHeaderCell(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#333333"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        tv.setBackgroundColor(Color.parseColor("#f8f9fa"));
        return tv;
    }

    private TextView createCell(String text) {
        TextView tv = new TextView(this);
        tv.setText(text != null ? text : "");
        tv.setTextColor(Color.parseColor("#333333"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        tv.setBackgroundColor(Color.WHITE);
        
        TableRow.LayoutParams params = new TableRow.LayoutParams();
        params.setMargins(dpToPx(1), dpToPx(1), dpToPx(1), dpToPx(1));
        tv.setLayoutParams(params);
        
        return tv;
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private String convertDayToString(String day) {
        if (day == null) return "";
        try {
            int dayNum = Integer.parseInt(day);
            String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
            if (dayNum >= 1 && dayNum <= 6) {
                return days[dayNum - 1];
            }
        } catch (NumberFormatException e) {
            return day;
        }
        return day;
    }

    private int parseTimeToMinutes(String timeStr) {
        try {
            String cleanTime = timeStr.trim();
            if (cleanTime.contains("AM") || cleanTime.contains("PM")) {
                String hourMin = cleanTime.replace("AM", "").replace("PM", "").trim();
                String[] parts = hourMin.split(":");
                int hour = Integer.parseInt(parts[0]);
                int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                
                if (cleanTime.contains("PM") && hour != 12) {
                    hour += 12;
                } else if (cleanTime.contains("AM") && hour == 12) {
                    hour = 0;
                }
                
                return hour * 60 + minute;
            } else {
                String[] parts = cleanTime.split(":");
                int hour = Integer.parseInt(parts[0]);
                int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                return hour * 60 + minute;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private String formatTime(String timeStr) {
        try {
            if (timeStr == null || timeStr.isEmpty()) return "";
            
            // Try parsing as HH:mm:ss or HH:mm
            SimpleDateFormat inputFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
            
            if (timeStr.length() == 5) {
                inputFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            }
            
            Date date = inputFormat.parse(timeStr);
            if (date != null) {
                return outputFormat.format(date);
            }
        } catch (ParseException e) {
            // If parsing fails, return as is
            return timeStr;
        }
        return timeStr;
    }

    private void showEmptyState() {
        runOnUiThread(() -> {
            loadingProgress.setVisibility(View.GONE);
            timetableCard.setVisibility(View.GONE);
            emptyStateText.setVisibility(View.VISIBLE);
            emptyStateText.setText("No schedule found for today.");
        });
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_dashboard) {
            Intent intent = new Intent(this, TeacherDashboardActivity.class);
            intent.putExtra("userId", teacherId);
            intent.putExtra("userType", getIntent().getStringExtra("userType"));
            intent.putExtra("fullName", getIntent().getStringExtra("fullName"));
            intent.putExtra("email", getIntent().getStringExtra("email"));
            startActivity(intent);
            finish();
            return true;
        } else if (id == R.id.nav_timetable) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }

        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove real-time listener when activity is destroyed
        if (timetableRef != null && timetableListener != null) {
            timetableRef.removeEventListener(timetableListener);
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

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

    // Inner class for timetable entry
    private static class TimetableEntry {
        String dayOfWeek;
        String startTime;
        String endTime;
        String room;
        String subjectCode;
        String subjectName;
        String teacherName;
    }
}
