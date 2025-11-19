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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TimetableActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "TimetableActivity";
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private SessionManager sessionManager;
    private TableLayout timetableTable;
    private ProgressBar loadingProgress;
    private TextView emptyStateText;
    private CardView timetableCard;
    private TextView currentDayIndicator;

    private String studentYearLevel;
    private String studentSection;
    private String studentCourse; // Course code like "BSIT"
    private String studentCourseId; // Course ID from courses table
    
    private long currentSnapshotTimestamp = 0; // Track snapshot timestamps to ignore stale async operations
    private ValueEventListener timetableListener; // Store listener reference to prevent garbage collection
    private DatabaseReference timetableRef; // Store reference to prevent detachment

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_timetable);

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
            todayDay = daysArray[7]; // Sunday
        } else {
            todayDay = daysArray[dayOfWeek - 1]; // Monday(2-1=1), Tuesday(3-1=2), etc.
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
        String userId = getIntent().getStringExtra("userId");
        String fullName = getIntent().getStringExtra("fullName");

        // Update nav header
        navigationView.post(() -> {
            View headerView = navigationView.getHeaderView(0);
            if (headerView != null) {
                TextView navName = headerView.findViewById(R.id.nav_header_name);
                TextView navEmail = headerView.findViewById(R.id.nav_header_email);

                if (navName != null) navName.setText(fullName);
                if (navEmail != null) navEmail.setText("ID: " + userId);
            }
        });

        // Fetch student info and timetable
        fetchStudentInfo(userId);
    }

    private void fetchStudentInfo(String userId) {
        loadingProgress.setVisibility(View.VISIBLE);
        timetableCard.setVisibility(View.GONE);

        Log.d(TAG, "Fetching student info for userId: " + userId);

        // First, let's check what root nodes exist
        DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference();
        rootRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d(TAG, "Root nodes available:");
                for (DataSnapshot child : snapshot.getChildren()) {
                    Log.d(TAG, "- " + child.getKey());
                }
                
                // Now try to find student data
                tryFetchStudentFromDifferentNodes(userId);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching root: " + error.getMessage());
                // Still try to fetch student data
                tryFetchStudentFromDifferentNodes(userId);
            }
        });
    }

    private void tryFetchStudentFromDifferentNodes(String userId) {
        // Based on the logs, data is likely under attendance_system node
        // Try different possible paths
        String[] possiblePaths = {
            "attendance_system/students", 
            "attendance_system/student",
            "students", 
            "student", 
            "users", 
            "user_data"
        };
        
        tryNextStudentNode(userId, possiblePaths, 0);
    }

    private void tryNextStudentNode(String userId, String[] paths, int index) {
        if (index >= paths.length) {
            // If no student data found, let's try to use the first available student for testing
            Log.d(TAG, "Student ID " + userId + " not found, trying to use first available student");
            tryUseFirstAvailableStudent();
            return;
        }

        String path = paths[index];
        Log.d(TAG, "Trying to fetch student from path: " + path);

        DatabaseReference nodeRef = FirebaseDatabase.getInstance().getReference(path);
        nodeRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d(TAG, path + " snapshot exists: " + snapshot.exists());
                Log.d(TAG, path + " children count: " + snapshot.getChildrenCount());

                boolean found = false;
                for (DataSnapshot studentSnapshot : snapshot.getChildren()) {
                    try {
                        Log.d(TAG, "Processing " + path + " key: " + studentSnapshot.getKey());
                        
                        // Check if data is nested under "data" node
                        DataSnapshot dataSnapshot = studentSnapshot.child("data");
                        if (dataSnapshot.exists()) {
                            // First try student_id field (format: "2025-008")
                            String studentId = dataSnapshot.child("student_id").getValue(String.class);
                            // Fallback to id field (numeric MySQL ID)
                            String id = dataSnapshot.child("id").getValue(String.class);
                            
                            Log.d(TAG, "Checking nested student - student_id: " + studentId + ", id: " + id + " against userId: " + userId);
                            
                            // Match by student_id first (preferred), then by id
                            if ((studentId != null && userId.equals(studentId)) || (id != null && userId.equals(id))) {
                                found = true;
                                studentYearLevel = dataSnapshot.child("year_level").getValue(String.class);
                                studentSection = dataSnapshot.child("section").getValue(String.class);
                                studentCourse = dataSnapshot.child("course").getValue(String.class);
                                
                                Log.d(TAG, "✓ Found student in " + path + " - Year: " + studentYearLevel + ", Section: " + studentSection + ", Course: " + studentCourse);
                                
                                // Fetch course_id from courses table
                                if (studentCourse != null) {
                                    fetchCourseIdFromCourses(studentCourse);
                                } else {
                                    Log.e(TAG, "Student found but course is null");
                                    showEmptyState();
                                }
                                break;
                            }
                        } else {
                            // Try direct access
                            String studentId = studentSnapshot.child("student_id").getValue(String.class);
                            String id = studentSnapshot.child("id").getValue(String.class);
                            if (id == null) {
                                // Maybe the key itself is the ID
                                id = studentSnapshot.getKey();
                            }
                            
                            Log.d(TAG, "Checking direct student - student_id: " + studentId + ", id: " + id + " against userId: " + userId);
                            
                            // Match by student_id first (preferred), then by id
                            if ((studentId != null && userId.equals(studentId)) || (id != null && userId.equals(id))) {
                                found = true;
                                studentYearLevel = studentSnapshot.child("year_level").getValue(String.class);
                                studentSection = studentSnapshot.child("section").getValue(String.class);
                                studentCourse = studentSnapshot.child("course").getValue(String.class);
                                
                                Log.d(TAG, "✓ Found student (direct) in " + path + " - Year: " + studentYearLevel + ", Section: " + studentSection + ", Course: " + studentCourse);
                                
                                // Fetch course_id from courses table
                                if (studentCourse != null) {
                                    fetchCourseIdFromCourses(studentCourse);
                                } else {
                                    Log.e(TAG, "Student found but course is null");
                                    showEmptyState();
                                }
                                break;
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing student from " + path + ": " + e.getMessage());
                    }
                }

                if (found && studentYearLevel != null && studentSection != null) {
                    Toast.makeText(TimetableActivity.this, 
                        "Student found: Year " + studentYearLevel + ", Section " + studentSection + ", Course: " + studentCourse, 
                        Toast.LENGTH_LONG).show();
                    
                    // Wait for course_id to be fetched before fetching timetable
                    if (studentCourseId != null) {
                        fetchTimetableFromAttendanceSystem();
                    } else if (studentCourse != null) {
                        // course_id will be fetched, timetable will be fetched in fetchCourseIdFromCourses callback
                    } else {
                        // No course, try next path or show error
                        tryNextStudentNode(userId, paths, index + 1);
                    }
                } else {
                    // Try next path
                    tryNextStudentNode(userId, paths, index + 1);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching from " + path + ": " + error.getMessage());
                // Try next path
                tryNextStudentNode(userId, paths, index + 1);
            }
        });
    }

    private void fetchCourseIdFromCourses(String courseCode) {
        Log.d(TAG, "Fetching course_id for course code: " + courseCode);
        
        DatabaseReference coursesRef = FirebaseDatabase.getInstance().getReference("attendance_system/courses");
        
        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean found = false;
                for (DataSnapshot courseSnapshot : snapshot.getChildren()) {
                    try {
                        DataSnapshot dataSnapshot = courseSnapshot.child("data");
                        if (!dataSnapshot.exists()) continue;
                        
                        String code = dataSnapshot.child("code").getValue(String.class);
                        if (courseCode.equals(code)) {
                            found = true;
                            studentCourseId = dataSnapshot.child("id").getValue(String.class);
                            Log.d(TAG, "Found course_id: " + studentCourseId + " for course: " + courseCode);
                            
                            // Now fetch timetable
                            fetchTimetableFromAttendanceSystem();
                            break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing course: " + e.getMessage());
                    }
                }
                
                if (!found) {
                    Log.e(TAG, "Course not found: " + courseCode);
                    runOnUiThread(() -> {
                        loadingProgress.setVisibility(View.GONE);
                        Toast.makeText(TimetableActivity.this, "Course not found in database", Toast.LENGTH_LONG).show();
                    });
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching courses: " + error.getMessage());
                runOnUiThread(() -> {
                    loadingProgress.setVisibility(View.GONE);
                    Toast.makeText(TimetableActivity.this, "Error fetching course data", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void fetchTimetableFromAttendanceSystem() {
        Log.d(TAG, "========================================");
        Log.d(TAG, "Starting to fetch timetable for student:");
        Log.d(TAG, "  Year Level: " + studentYearLevel);
        Log.d(TAG, "  Section: " + studentSection);
        Log.d(TAG, "  Course: " + studentCourse);
        Log.d(TAG, "  Course ID: " + studentCourseId);
        Log.d(TAG, "========================================");
        
        // Try different possible paths for timetable data
        String[] timetablePaths = {
            "attendance_system/timetable",
            "timetable"
        };
        
        tryFetchTimetableFromPath(timetablePaths, 0);
    }

    private void tryFetchTimetableFromPath(String[] paths, int index) {
        if (index >= paths.length) {
            Log.d(TAG, "No timetable found in any path");
            loadingProgress.setVisibility(View.GONE);
            showEmptyState();
            return;
        }

        String path = paths[index];
        Log.d(TAG, "Trying to fetch timetable from path: " + path);
        
        // Remove old listener if exists to prevent multiple listeners
        if (timetableRef != null && timetableListener != null) {
            Log.d(TAG, "Removing old timetable listener");
            timetableRef.removeEventListener(timetableListener);
        }
        
        timetableRef = FirebaseDatabase.getInstance().getReference(path);

        // Use addValueEventListener for real-time updates instead of addListenerForSingleValueEvent
        timetableListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Generate unique timestamp for this snapshot to track async operations
                final long snapshotTimestamp = System.currentTimeMillis();
                currentSnapshotTimestamp = snapshotTimestamp;
                
                Log.d(TAG, "========================================");
                Log.d(TAG, "Firebase onDataChange triggered (timestamp: " + snapshotTimestamp + ")");
                Log.d(TAG, path + " snapshot exists: " + snapshot.exists());
                Log.d(TAG, path + " children count: " + snapshot.getChildrenCount());
                Log.d(TAG, "========================================");

                // Note: We just set currentSnapshotTimestamp = snapshotTimestamp above,
                // so this check will always pass. But we keep it for consistency with async operations.

                if (!snapshot.exists() || snapshot.getChildrenCount() == 0) {
                    // Show empty state if no entries
                    Log.d(TAG, "No timetable entries found in Firebase snapshot");
                    runOnUiThread(() -> {
                        // Double-check snapshot is still current before updating UI
                        if (currentSnapshotTimestamp == snapshotTimestamp) {
                            loadingProgress.setVisibility(View.GONE);
                            showEmptyState();
                        } else {
                            Log.d(TAG, "⚠ Ignoring empty state update - snapshot changed");
                        }
                    });
                    return;
                }

                // Clear existing entries list for fresh fetch on each update
                final List<TimetableEntry> entries = new ArrayList<>();
                int totalEntries = (int) snapshot.getChildrenCount();
                final int[] processedCount = {0};
                final Object lock = new Object(); // Lock for thread-safe operations
                
                // Store snapshot timestamp in final variable for async operations to use
                final long thisSnapshotTimestamp = snapshotTimestamp;
                
                Log.d(TAG, "🔄 Starting to process " + totalEntries + " timetable entries from Firebase (snapshot: " + thisSnapshotTimestamp + ")");
                Log.d(TAG, "Current snapshot timestamp: " + currentSnapshotTimestamp);

                // Collect all entry keys and IDs first for logging and validation
                List<String> entryKeys = new ArrayList<>();
                List<String> entryIds = new ArrayList<>();
                for (DataSnapshot entrySnapshot : snapshot.getChildren()) {
                    entryKeys.add(entrySnapshot.getKey());
                    DataSnapshot dataSnap = entrySnapshot.child("data");
                    if (dataSnap.exists()) {
                        String entryId = dataSnap.child("id").getValue(String.class);
                        if (entryId != null) {
                            entryIds.add(entryId);
                        }
                    }
                }
                Log.d(TAG, "📋 Entry keys in snapshot: " + entryKeys.toString());
                Log.d(TAG, "📋 Entry IDs in snapshot: " + entryIds.toString());

                for (DataSnapshot entrySnapshot : snapshot.getChildren()) {
                    // Check if snapshot is still current before processing each entry
                    if (currentSnapshotTimestamp != thisSnapshotTimestamp) {
                        Log.d(TAG, "⚠ Snapshot changed during processing - stopping entry processing (current: " + currentSnapshotTimestamp + ", this: " + thisSnapshotTimestamp + ")");
                        return;
                    }
                    try {
                        Log.d(TAG, "Processing timetable key: " + entrySnapshot.getKey());
                        
                        // Get data from the nested "data" object
                        DataSnapshot dataSnapshot = entrySnapshot.child("data");
                        
                        if (!dataSnapshot.exists()) {
                            Log.w(TAG, "⚠ No data node found for entry: " + entrySnapshot.getKey() + " - skipping");
                            synchronized (lock) {
                                processedCount[0]++;
                                checkIfComplete(entries, totalEntries, processedCount[0], thisSnapshotTimestamp);
                            }
                            continue;
                        }

                        String timetableId = dataSnapshot.child("id").getValue(String.class);
                        String classId = dataSnapshot.child("class_id").getValue(String.class);
                        String courseId = dataSnapshot.child("course_id").getValue(String.class);
                        String dayOfWeek = dataSnapshot.child("day_of_week").getValue(String.class);
                        String startTime = dataSnapshot.child("start_time").getValue(String.class);
                        String endTime = dataSnapshot.child("end_time").getValue(String.class);
                        String room = dataSnapshot.child("room").getValue(String.class);

                        Log.d(TAG, "📋 Processing timetable entry - ID: " + timetableId + ", classId: " + classId + ", courseId: " + courseId + 
                              ", day: " + dayOfWeek + ", time: " + startTime + "-" + endTime + ", room: " + room);

                        // STRICT FILTER: Course ID must match (if both are present)
                        // If student has course_id, timetable entry MUST have matching course_id
                        if (studentCourseId != null) {
                            if (courseId == null || !courseId.equals(studentCourseId)) {
                                Log.d(TAG, "✗ Course ID filter failed - timetable: " + courseId + ", student: " + studentCourseId + ", skipping");
                            synchronized (lock) {
                                processedCount[0]++;
                                checkIfComplete(entries, totalEntries, processedCount[0], thisSnapshotTimestamp);
                            }
                            continue;
                        } else {
                            Log.d(TAG, "✓ Course ID match - timetable: " + courseId + ", student: " + studentCourseId);
                        }
                        } else if (courseId != null) {
                            // If timetable has course_id but student doesn't, skip (strict matching)
                            Log.d(TAG, "✗ Course ID filter failed - timetable has course_id: " + courseId + " but student has no course_id, skipping");
                            synchronized (lock) {
                                processedCount[0]++;
                                checkIfComplete(entries, totalEntries, processedCount[0], thisSnapshotTimestamp);
                            }
                            continue;
                        } else {
                            // Both are null, proceed to section/year_level check
                            Log.d(TAG, "⚠ Both course_id are null, will check section/year_level match");
                        }

                        if (classId != null) {
                            // Fetch class details from attendance_system
                            fetchClassDetailsFromAttendanceSystem(classId, dayOfWeek, startTime, endTime, room, entries, 
                                    totalEntries, processedCount, lock, thisSnapshotTimestamp);
                        } else {
                            Log.w(TAG, "⚠ ClassId is null for entry: " + entrySnapshot.getKey() + " - skipping");
                            synchronized (lock) {
                                processedCount[0]++;
                                checkIfComplete(entries, totalEntries, processedCount[0], thisSnapshotTimestamp);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "✗ Error parsing timetable entry: " + entrySnapshot.getKey() + " - " + e.getMessage());
                        e.printStackTrace();
                        synchronized (lock) {
                            processedCount[0]++;
                            checkIfComplete(entries, totalEntries, processedCount[0], thisSnapshotTimestamp);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching timetable from " + path + ": " + error.getMessage());
                // Try next path
                tryFetchTimetableFromPath(paths, index + 1);
            }
        };
        
        // Attach listener and store reference
        timetableRef.addValueEventListener(timetableListener);
        Log.d(TAG, "Timetable listener attached to path: " + path);
    }

    private void fetchClassDetailsFromAttendanceSystem(String classId, String dayOfWeek, String startTime, String endTime, 
                                   String room, List<TimetableEntry> entries, int totalEntries, 
                                   int[] processedCount, Object lock, long snapshotTimestamp) {
        Log.d(TAG, "Fetching class details for classId: " + classId + " (snapshot: " + snapshotTimestamp + ")");
        
        // Skip class validation - directly fetch subject and teacher from classes
        // Using a simplified approach to match by year level and section
        DatabaseReference classesRef = FirebaseDatabase.getInstance().getReference("attendance_system/classes");
        
        classesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Check if this is still the current snapshot (ignore if a new one has arrived)
                if (currentSnapshotTimestamp != snapshotTimestamp) {
                    Log.d(TAG, "Ignoring stale class fetch (current: " + currentSnapshotTimestamp + ", this: " + snapshotTimestamp + ")");
                    return;
                }
                
                boolean found = false;
                
                for (DataSnapshot classSnapshot : snapshot.getChildren()) {
                    try {
                        Log.d(TAG, "Checking class: " + classSnapshot.getKey());
                        
                        DataSnapshot dataSnapshot = classSnapshot.child("data");
                        if (!dataSnapshot.exists()) continue;
                        
                        String id = dataSnapshot.child("id").getValue(String.class);
                        Log.d(TAG, "Comparing classId: '" + id + "' with target: '" + classId + "'");
                        
                        if (classId.equals(id)) {
                            found = true;
                            String section = dataSnapshot.child("section").getValue(String.class);
                            String classYearLevel = dataSnapshot.child("year_level").getValue(String.class);
                            String subjectId = dataSnapshot.child("subject_id").getValue(String.class);
                            String teacherId = dataSnapshot.child("teacher_id").getValue(String.class);
                            
                            Log.d(TAG, "Found matching class - section: " + section + ", year_level: " + classYearLevel + ", subjectId: " + subjectId);
                            
                            // STRICT FILTERING: Both section AND year_level must match student's data
                            boolean sectionMatches = (section != null && section.equals(studentSection));
                            boolean yearLevelMatches = (classYearLevel != null && studentYearLevel != null && classYearLevel.equals(studentYearLevel));
                            
                            Log.d(TAG, "Filter check - Section match: " + sectionMatches + " (class: " + section + ", student: " + studentSection + 
                                  "), Year level match: " + yearLevelMatches + " (class: " + classYearLevel + ", student: " + studentYearLevel + ")");
                            
                            // Only show schedules that match BOTH section AND year_level
                            if (!sectionMatches || !yearLevelMatches) {
                                if (!sectionMatches) {
                                    Log.d(TAG, "✗ Section mismatch - skipping entry (class: " + section + " != student: " + studentSection + ")");
                                }
                                if (!yearLevelMatches) {
                                    Log.d(TAG, "✗ Year level mismatch - skipping entry (class: " + classYearLevel + " != student: " + studentYearLevel + ")");
                                }
                                synchronized (lock) {
                                    processedCount[0]++;
                                    checkIfComplete(entries, totalEntries, processedCount[0], snapshotTimestamp);
                                }
                                return;
                            }
                            
                            Log.d(TAG, "✓ All filters passed - section and year_level match, proceeding to fetch subject/teacher");
                            
                            // Fetch subject and teacher details
                            Log.d(TAG, "Fetching subject and teacher details");
                            fetchSubjectAndTeacherFromAttendanceSystem(subjectId, teacherId, section, dayOfWeek, 
                                    startTime, endTime, room, entries, totalEntries, processedCount, lock, snapshotTimestamp);
                            break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing class: " + e.getMessage());
                    }
                }
                
                if (!found) {
                    Log.d(TAG, "Class " + classId + " not found, skipping entry");
                    synchronized (lock) {
                        processedCount[0]++;
                        checkIfComplete(entries, totalEntries, processedCount[0], snapshotTimestamp);
                    }
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching classes: " + error.getMessage());
                synchronized (lock) {
                    processedCount[0]++;
                    checkIfComplete(entries, totalEntries, processedCount[0], snapshotTimestamp);
                }
            }
        });
    }


    private void fetchSubjectAndTeacherFromAttendanceSystem(String subjectId, String teacherId, String section, 
                                       String dayOfWeek, String startTime, String endTime, String room,
                                       List<TimetableEntry> entries, int totalEntries, int[] processedCount, Object lock, long snapshotTimestamp) {
        final String[] subjectCode = {null};
        final String[] subjectName = {null};
        final String[] teacherName = {null};
        final String[] yearLevel = {null};
        final int[] fetchCount = {0};

        // Try different paths for subjects
        String[] subjectPaths = {"attendance_system/subjects", "subjects"};
        String[] teacherPaths = {"attendance_system/teachers", "teachers"};

        // Fetch subject
        tryFetchSubject(subjectPaths, 0, subjectId, subjectCode, subjectName, yearLevel, fetchCount, 
                       teacherName, section, dayOfWeek, startTime, endTime, room, entries, totalEntries, processedCount, lock, snapshotTimestamp);

        // Fetch teacher
        tryFetchTeacher(teacherPaths, 0, teacherId, teacherName, fetchCount, 
                       subjectCode, subjectName, yearLevel, section, dayOfWeek, startTime, endTime, room, entries, totalEntries, processedCount, lock, snapshotTimestamp);
    }

    private void tryFetchSubject(String[] paths, int index, String subjectId, String[] subjectCode, String[] subjectName, String[] yearLevel,
                                int[] fetchCount, String[] teacherName, String section, String dayOfWeek, String startTime, String endTime, 
                                String room, List<TimetableEntry> entries, int totalEntries, int[] processedCount, Object lock, long snapshotTimestamp) {
        
        if (index >= paths.length) {
            Log.d(TAG, "Subject not found in any path for subjectId: " + subjectId);
            fetchCount[0]++;
            if (fetchCount[0] == 2) {
                synchronized (lock) {
                    // Check if still current snapshot
                    if (currentSnapshotTimestamp == snapshotTimestamp) {
                        processedCount[0]++;
                        checkIfComplete(entries, totalEntries, processedCount[0], snapshotTimestamp);
                    } else {
                        Log.d(TAG, "Ignoring stale subject fetch completion (current: " + currentSnapshotTimestamp + ", this: " + snapshotTimestamp + ")");
                    }
                }
            }
            return;
        }

        String path = paths[index];
        DatabaseReference subjectsRef = FirebaseDatabase.getInstance().getReference(path);

        subjectsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Check if this is still the current snapshot
                if (currentSnapshotTimestamp != snapshotTimestamp) {
                    Log.d(TAG, "Ignoring stale subject fetch (current: " + currentSnapshotTimestamp + ", this: " + snapshotTimestamp + ")");
                    return;
                }
                
                boolean found = false;
                for (DataSnapshot subjectSnapshot : snapshot.getChildren()) {
                    try {
                        DataSnapshot dataSnapshot = subjectSnapshot.child("data");
                        String id = dataSnapshot.child("id").getValue(String.class);

                        if (subjectId.equals(id)) {
                            found = true;
                            subjectCode[0] = dataSnapshot.child("subject_code").getValue(String.class);
                            subjectName[0] = dataSnapshot.child("subject_name").getValue(String.class);
                            yearLevel[0] = dataSnapshot.child("year_level").getValue(String.class);
                            Log.d(TAG, "Found subject: " + subjectCode[0] + ", year: " + yearLevel[0]);
                            break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing subject: " + e.getMessage());
                    }
                }

                if (found) {
                    fetchCount[0]++;
                    if (fetchCount[0] == 2) {
                        // Double-check snapshot is still current before creating entry
                        if (currentSnapshotTimestamp == snapshotTimestamp) {
                            createTimetableEntry(subjectCode[0], subjectName[0], teacherName[0], yearLevel[0],
                                    section, dayOfWeek, startTime, endTime, room, entries, totalEntries, processedCount, lock, snapshotTimestamp);
                        } else {
                            Log.d(TAG, "Ignoring stale subject/teacher fetch completion (current: " + currentSnapshotTimestamp + ", this: " + snapshotTimestamp + ")");
                        }
                    }
                } else {
                    // Try next path (only if still current snapshot)
                    if (currentSnapshotTimestamp == snapshotTimestamp) {
                        tryFetchSubject(paths, index + 1, subjectId, subjectCode, subjectName, yearLevel, fetchCount, 
                                       teacherName, section, dayOfWeek, startTime, endTime, room, entries, totalEntries, processedCount, lock, snapshotTimestamp);
                    } else {
                        Log.d(TAG, "Skipping next subject path - snapshot changed (current: " + currentSnapshotTimestamp + ", this: " + snapshotTimestamp + ")");
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching subject from " + path + ": " + error.getMessage());
                // Try next path (only if still current snapshot)
                if (currentSnapshotTimestamp == snapshotTimestamp) {
                    tryFetchSubject(paths, index + 1, subjectId, subjectCode, subjectName, yearLevel, fetchCount, 
                                   teacherName, section, dayOfWeek, startTime, endTime, room, entries, totalEntries, processedCount, lock, snapshotTimestamp);
                } else {
                    Log.d(TAG, "Skipping next subject path on error - snapshot changed (current: " + currentSnapshotTimestamp + ", this: " + snapshotTimestamp + ")");
                }
            }
        });
    }

    private void tryFetchTeacher(String[] paths, int index, String teacherId, String[] teacherName, int[] fetchCount,
                                String[] subjectCode, String[] subjectName, String[] yearLevel, String section, 
                                String dayOfWeek, String startTime, String endTime, String room, 
                                List<TimetableEntry> entries, int totalEntries, int[] processedCount, Object lock, long snapshotTimestamp) {
        
        if (index >= paths.length) {
            Log.d(TAG, "Teacher not found in any path for teacherId: " + teacherId);
            fetchCount[0]++;
            if (fetchCount[0] == 2) {
                synchronized (lock) {
                    // Check if still current snapshot
                    if (currentSnapshotTimestamp == snapshotTimestamp) {
                        processedCount[0]++;
                        checkIfComplete(entries, totalEntries, processedCount[0], snapshotTimestamp);
                    } else {
                        Log.d(TAG, "Ignoring stale teacher fetch completion (current: " + currentSnapshotTimestamp + ", this: " + snapshotTimestamp + ")");
                    }
                }
            }
            return;
        }

        String path = paths[index];
        DatabaseReference teachersRef = FirebaseDatabase.getInstance().getReference(path);

        teachersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Check if this is still the current snapshot
                if (currentSnapshotTimestamp != snapshotTimestamp) {
                    Log.d(TAG, "Ignoring stale teacher fetch (current: " + currentSnapshotTimestamp + ", this: " + snapshotTimestamp + ")");
                    return;
                }
                
                boolean found = false;
                for (DataSnapshot teacherSnapshot : snapshot.getChildren()) {
                    try {
                        DataSnapshot dataSnapshot = teacherSnapshot.child("data");
                        String id = dataSnapshot.child("id").getValue(String.class);

                        if (teacherId.equals(id)) {
                            found = true;
                            teacherName[0] = dataSnapshot.child("full_name").getValue(String.class);
                            Log.d(TAG, "Found teacher: " + teacherName[0]);
                            break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing teacher: " + e.getMessage());
                    }
                }

                if (found) {
                    fetchCount[0]++;
                    if (fetchCount[0] == 2) {
                        // Double-check snapshot is still current before creating entry
                        if (currentSnapshotTimestamp == snapshotTimestamp) {
                            createTimetableEntry(subjectCode[0], subjectName[0], teacherName[0], yearLevel[0],
                                    section, dayOfWeek, startTime, endTime, room, entries, totalEntries, processedCount, lock, snapshotTimestamp);
                        } else {
                            Log.d(TAG, "Ignoring stale teacher fetch completion (current: " + currentSnapshotTimestamp + ", this: " + snapshotTimestamp + ")");
                        }
                    }
                } else {
                    // Try next path (only if still current snapshot)
                    if (currentSnapshotTimestamp == snapshotTimestamp) {
                        tryFetchTeacher(paths, index + 1, teacherId, teacherName, fetchCount, subjectCode, subjectName, yearLevel, 
                                       section, dayOfWeek, startTime, endTime, room, entries, totalEntries, processedCount, lock, snapshotTimestamp);
                    } else {
                        Log.d(TAG, "Skipping next teacher path - snapshot changed (current: " + currentSnapshotTimestamp + ", this: " + snapshotTimestamp + ")");
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching teacher from " + path + ": " + error.getMessage());
                // Try next path (only if still current snapshot)
                if (currentSnapshotTimestamp == snapshotTimestamp) {
                    tryFetchTeacher(paths, index + 1, teacherId, teacherName, fetchCount, subjectCode, subjectName, yearLevel, 
                                   section, dayOfWeek, startTime, endTime, room, entries, totalEntries, processedCount, lock, snapshotTimestamp);
                } else {
                    Log.d(TAG, "Skipping next teacher path on error - snapshot changed (current: " + currentSnapshotTimestamp + ", this: " + snapshotTimestamp + ")");
                }
            }
        });
    }

    private void createTimetableEntry(String subjectCode, String subjectName, String teacherName,
                                     String yearLevel, String section, String dayOfWeek, 
                                     String startTime, String endTime, String room,
                                     List<TimetableEntry> entries, int totalEntries, int[] processedCount, Object lock, long snapshotTimestamp) {
        // Entry is already filtered by course_id, section, and year_level
        TimetableEntry entry = new TimetableEntry();
        entry.dayOfWeek = dayOfWeek;
        entry.startTime = startTime;
        entry.endTime = endTime;
        entry.room = room;
        entry.subjectCode = subjectCode;
        entry.subjectName = subjectName;
        entry.teacherName = teacherName;

        synchronized (lock) {
            entries.add(entry);
            Log.d(TAG, "✅ Added filtered entry: " + subjectCode + " on " + dayOfWeek + " at " + startTime + 
                  " (Year: " + yearLevel + ", Section: " + section + ", snapshot: " + snapshotTimestamp + ")");
            processedCount[0]++;
            checkIfComplete(entries, totalEntries, processedCount[0], snapshotTimestamp);
        }
    }

    private void checkIfComplete(List<TimetableEntry> entries, int totalEntries, int processedCount, long snapshotTimestamp) {
        Log.d(TAG, "checkIfComplete called - processedCount: " + processedCount + ", totalEntries: " + totalEntries + 
              ", entries.size(): " + entries.size() + ", snapshot: " + snapshotTimestamp + ", current: " + currentSnapshotTimestamp);
        
        // Check if this is still the current snapshot (ignore if a new snapshot has arrived)
        if (currentSnapshotTimestamp != snapshotTimestamp) {
            Log.d(TAG, "⚠ Ignoring checkIfComplete - snapshot changed (current: " + currentSnapshotTimestamp + ", this: " + snapshotTimestamp + ")");
            return;
        }
        
        if (processedCount >= totalEntries) {
            runOnUiThread(() -> {
                // Double-check snapshot is still current before updating UI
                if (currentSnapshotTimestamp != snapshotTimestamp) {
                    Log.d(TAG, "⚠ Ignoring UI update - snapshot changed during UI thread execution");
                    return;
                }
                
                loadingProgress.setVisibility(View.GONE);

                if (entries.isEmpty()) {
                    Log.w(TAG, "⚠ No timetable entries found after processing all " + totalEntries + " entries");
                    Log.w(TAG, "Student filters - Year: " + studentYearLevel + ", Section: " + studentSection + ", Course: " + studentCourse + ", CourseID: " + studentCourseId);
                    showEmptyState();
                    return;
                }

                Log.d(TAG, "✅ Successfully processed " + entries.size() + " filtered entries out of " + totalEntries + " total entries (snapshot: " + snapshotTimestamp + ")");
                buildTimetableTable(entries);
                timetableCard.setVisibility(View.VISIBLE);
                emptyStateText.setVisibility(View.GONE); // Hide empty state when entries are found
            });
        } else {
            Log.d(TAG, "⏳ Still processing... " + processedCount + "/" + totalEntries + " entries processed");
        }
    }


    private String convertTimeTo12Hour(String time) {
        // If already in 12-hour format (contains AM/PM), return as is
        if (time.contains("AM") || time.contains("PM")) {
            return time;
        }
        
        // Otherwise convert from 24-hour format
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
            
            String timeWithoutSeconds = time.length() >= 8 ? time.substring(0, 8) : time; // Get HH:mm:ss
            Date date = inputFormat.parse(timeWithoutSeconds);
            
            if (date != null) {
                String result = outputFormat.format(date);
                // Remove leading zero from hour if present
                if (result.startsWith("0")) {
                    result = result.substring(1);
                }
                return result;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error converting time: " + e.getMessage());
        }
        return time;
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
            Log.e(TAG, "Error parsing time: " + timeStr + " - " + e.getMessage());
            return 0;
        }
    }
    
    private void buildTimetableTable(List<TimetableEntry> entries) {
        timetableTable.removeAllViews();
        
        Log.d(TAG, "Building timetable with " + entries.size() + " entries");
        
        // Get current day - Calendar.DAY_OF_WEEK: Sunday=1, Monday=2, ..., Saturday=7
        Calendar calendar = Calendar.getInstance();
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        
        // Convert to our format: Monday=1, Tuesday=2, ..., Sunday=7
        // Calendar: Sunday=1, Monday=2 -> Our: Monday=1, Sunday=7
        String[] daysArray = {"", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        String todayDay;
        if (dayOfWeek == Calendar.SUNDAY) {
            todayDay = daysArray[7]; // Sunday
        } else {
            todayDay = daysArray[dayOfWeek - 1]; // Monday(2-1=1), Tuesday(3-1=2), etc.
        }
        
        Log.d(TAG, "Current Calendar dayOfWeek: " + dayOfWeek + ", Today is: " + todayDay);
        
        // Update current day indicator - show actual current day
        if (currentDayIndicator != null) {
            currentDayIndicator.setText(todayDay);
            Log.d(TAG, "Updated current day indicator to: " + todayDay);
        }
        
        // Filter entries for today only
        List<TimetableEntry> todayEntries = new ArrayList<>();
        Log.d(TAG, "Total entries received: " + entries.size());
        Log.d(TAG, "Filtering for today: " + todayDay);
        
        for (TimetableEntry entry : entries) {
            String entryDay = convertDayToString(entry.dayOfWeek);
            Log.d(TAG, "Entry day: '" + entry.dayOfWeek + "' -> converted: '" + entryDay + "', matches today (" + todayDay + "): " + entryDay.equals(todayDay));
            
            if (entryDay.equals(todayDay)) {
                todayEntries.add(entry);
                Log.d(TAG, "✓ Today's entry added: " + entry.subjectCode + " at " + entry.startTime);
            } else {
                Log.d(TAG, "✗ Entry not for today - Entry: '" + entryDay + "', Today: '" + todayDay + "'");
            }
        }
        
        Log.d(TAG, "Total entries for today: " + todayEntries.size());
        
        // Create header
        TableRow headerRow = new TableRow(this);
        headerRow.setBackgroundColor(Color.parseColor("#f8f9fa"));
        
        TextView timeHeader = createHeaderCell("TIME");
        timeHeader.setMinWidth(dpToPx(120));
        timeHeader.setMaxWidth(dpToPx(120));
        headerRow.addView(timeHeader);
        
        TextView subjectHeader = createHeaderCell("SUBJECT");
        subjectHeader.setMinWidth(dpToPx(150));
        subjectHeader.setMaxWidth(dpToPx(150));
        headerRow.addView(subjectHeader);
        
        TextView teacherHeader = createHeaderCell("TEACHER");
        teacherHeader.setMinWidth(dpToPx(120));
        teacherHeader.setMaxWidth(dpToPx(120));
        headerRow.addView(teacherHeader);
        
        TextView roomHeader = createHeaderCell("ROOM");
        roomHeader.setMinWidth(dpToPx(100));
        roomHeader.setMaxWidth(dpToPx(100));
        headerRow.addView(roomHeader);
        
        timetableTable.addView(headerRow);
        
        // Add entries for today
        if (todayEntries.isEmpty()) {
            TableRow emptyRow = new TableRow(this);
            TextView emptyCell = new TextView(this);
            emptyCell.setText("No schedule for " + todayDay);
            emptyCell.setGravity(Gravity.CENTER);
            emptyCell.setPadding(dpToPx(16), dpToPx(32), dpToPx(16), dpToPx(32));
            emptyCell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            emptyCell.setTextColor(Color.parseColor("#666666"));
            
            TableRow.LayoutParams params = new TableRow.LayoutParams();
            params.span = 4; // Span all columns
            emptyCell.setLayoutParams(params);
            emptyRow.addView(emptyCell);
            timetableTable.addView(emptyRow);
            
            // Hide empty state text below (table already shows empty message)
            emptyStateText.setVisibility(View.GONE);
        } else {
            // Hide empty state text when entries are found
            emptyStateText.setVisibility(View.GONE);
            // Sort by time - earliest first
            Log.d(TAG, "Before sorting - entry times:");
            for (TimetableEntry entry : todayEntries) {
                Log.d(TAG, "Entry: " + entry.startTime + " (raw)");
            }
            
            todayEntries.sort((a, b) -> {
                try {
                    Log.d(TAG, "Comparing: " + a.startTime + " vs " + b.startTime);
                    
                    // Parse times to minutes since midnight for easy comparison
                    int minutesA = parseTimeToMinutes(a.startTime);
                    int minutesB = parseTimeToMinutes(b.startTime);
                    
                    Log.d(TAG, "Parsed minutes: " + minutesA + " vs " + minutesB);
                    int result = Integer.compare(minutesA, minutesB);
                    Log.d(TAG, "Result: " + result);
                    
                    return result;
                } catch (Exception e) {
                    Log.e(TAG, "Error sorting: " + e.getMessage());
                    return 0;
                }
            });
            
            Log.d(TAG, "After sorting - entry times:");
            for (TimetableEntry entry : todayEntries) {
                Log.d(TAG, "Entry: " + entry.startTime);
            }
            
            for (TimetableEntry entry : todayEntries) {
                TableRow row = new TableRow(this);
                
                // Time column - convert to 12-hour format
                String startTime12 = convertTimeTo12Hour(entry.startTime);
                String endTime12 = convertTimeTo12Hour(entry.endTime);
                TextView timeCell = createCell(startTime12 + "-" + endTime12);
                timeCell.setMinWidth(dpToPx(120));
                timeCell.setMaxWidth(dpToPx(120));
                row.addView(timeCell);
                
                // Subject column
                TextView subjectCell = createCell(entry.subjectCode);
                subjectCell.setMinWidth(dpToPx(150));
                subjectCell.setMaxWidth(dpToPx(150));
                row.addView(subjectCell);
                
                // Teacher column
                String teacherShort = formatTeacherName(entry.teacherName);
                TextView teacherCell = createCell(teacherShort);
                teacherCell.setMinWidth(dpToPx(120));
                teacherCell.setMaxWidth(dpToPx(120));
                row.addView(teacherCell);
                
                // Room column
                TextView roomCell = createCell(entry.room);
                roomCell.setMinWidth(dpToPx(100));
                roomCell.setMaxWidth(dpToPx(100));
                row.addView(roomCell);
                
                timetableTable.addView(row);
            }
        }
    }
    
    private TextView createCell(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTextColor(Color.parseColor("#333333"));
        tv.setBackgroundColor(Color.WHITE);
        tv.setMinHeight(dpToPx(60));
        tv.setSingleLine(false);
        tv.setMaxLines(3);
        tv.setEllipsize(null); // Don't ellipsize
        tv.setHorizontalScrollBarEnabled(false);
        
        TableRow.LayoutParams params = new TableRow.LayoutParams();
        params.setMargins(dpToPx(1), dpToPx(1), dpToPx(1), dpToPx(1));
        tv.setLayoutParams(params);
        
        return tv;
    }

    private TextView createHeaderCell(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dpToPx(15), dpToPx(15), dpToPx(15), dpToPx(15));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(Color.parseColor("#333333"));
        tv.setBackgroundColor(Color.parseColor("#f8f9fa"));
        tv.setSingleLine(true);
        tv.setMaxLines(1);

        TableRow.LayoutParams params = new TableRow.LayoutParams();
        params.setMargins(dpToPx(1), dpToPx(1), dpToPx(1), dpToPx(1));
        tv.setLayoutParams(params);

        return tv;
    }

    private TextView createTimeCell(String time) {
        TextView tv = new TextView(this);
        tv.setText(time);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dpToPx(15), dpToPx(15), dpToPx(15), dpToPx(15));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTextColor(Color.parseColor("#666666"));
        tv.setBackgroundColor(Color.WHITE);
        tv.setMinWidth(dpToPx(120));
        tv.setMinHeight(dpToPx(50));
        tv.setSingleLine(true);
        tv.setMaxLines(1);

        TableRow.LayoutParams params = new TableRow.LayoutParams();
        params.setMargins(dpToPx(1), dpToPx(1), dpToPx(1), dpToPx(1));
        tv.setLayoutParams(params);

        return tv;
    }

    private TextView createEmptyCell() {
        TextView tv = new TextView(this);
        tv.setMinWidth(dpToPx(150));
        tv.setMinHeight(dpToPx(50));
        tv.setBackgroundColor(Color.WHITE);

        TableRow.LayoutParams params = new TableRow.LayoutParams();
        params.setMargins(dpToPx(1), dpToPx(1), dpToPx(1), dpToPx(1));
        tv.setLayoutParams(params);

        return tv;
    }


    private TextView createScheduleCell(TimetableEntry entry) {
        TextView tv = new TextView(this);

        // Format teacher name (A.Bautista style) - matching web version
        String teacherShort = formatTeacherName(entry.teacherName);

        // Create styled text to match web design exactly
        android.text.SpannableStringBuilder styledText = new android.text.SpannableStringBuilder();
        
        // Subject code - Bold, larger font (1.1em equivalent) - matching web version
        styledText.append(entry.subjectCode);
        styledText.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 
                          0, entry.subjectCode.length(), 
                          android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        styledText.setSpan(new android.text.style.AbsoluteSizeSpan(dpToPx(18), true), 
                          0, entry.subjectCode.length(), 
                          android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        // Teacher name - Green color (#4caf50), smaller font - matching web version
        styledText.append("\n");
        int teacherStart = styledText.length();
        styledText.append(teacherShort);
        styledText.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#4caf50")), 
                          teacherStart, styledText.length(), 
                          android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        styledText.setSpan(new android.text.style.AbsoluteSizeSpan(dpToPx(16), true), 
                          teacherStart, styledText.length(), 
                          android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        // Room - Gray color (#333), smallest font - matching web version
        styledText.append("\n");
        int roomStart = styledText.length();
        styledText.append(entry.room);
        styledText.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#333333")), 
                          roomStart, styledText.length(), 
                          android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        styledText.setSpan(new android.text.style.AbsoluteSizeSpan(dpToPx(14), true), 
                          roomStart, styledText.length(), 
                          android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        tv.setText(styledText);
        tv.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        tv.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));

        // Green background with left border (matching website design exactly)
        tv.setBackgroundColor(Color.parseColor("#e8f5e9"));
        tv.setCompoundDrawablesWithIntrinsicBounds(
                createLeftBorder(),
                null, null, null
        );

        tv.setMinWidth(dpToPx(150));
        tv.setMinHeight(dpToPx(50));

        TableRow.LayoutParams params = new TableRow.LayoutParams();
        params.setMargins(dpToPx(1), dpToPx(1), dpToPx(1), dpToPx(1));
        tv.setLayoutParams(params);

        return tv;
    }

    private android.graphics.drawable.Drawable createLeftBorder() {
        android.graphics.drawable.GradientDrawable border = new android.graphics.drawable.GradientDrawable();
        border.setColor(Color.parseColor("#4caf50"));
        border.setSize(dpToPx(4), 0);
        return border;
    }

    private String formatTeacherName(String fullName) {
        if (fullName == null || fullName.isEmpty()) return "";

        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 0) return "";

        String initial = parts[0].substring(0, 1).toUpperCase();
        String lastName = parts[parts.length - 1];
        lastName = lastName.substring(0, 1).toUpperCase() + lastName.substring(1).toLowerCase();

        return initial + "." + lastName;
    }

    private String convertDayToAbbreviation(String day) {
        if (day == null) return "";

        // Handle numeric day (1-6)
        try {
            int dayNum = Integer.parseInt(day);
            String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
            if (dayNum >= 1 && dayNum <= 6) {
                return days[dayNum - 1];
            }
        } catch (NumberFormatException e) {
            // Handle string day names
            if (day.equalsIgnoreCase("Monday")) return "Mon";
            if (day.equalsIgnoreCase("Tuesday")) return "Tue";
            if (day.equalsIgnoreCase("Wednesday")) return "Wed";
            if (day.equalsIgnoreCase("Thursday")) return "Thu";
            if (day.equalsIgnoreCase("Friday")) return "Fri";
            if (day.equalsIgnoreCase("Saturday")) return "Sat";
            return day;
        }

        return day;
    }

    private String convertDayToString(String day) {
        if (day == null) return "";

        // Handle numeric day (1-6)
        try {
            int dayNum = Integer.parseInt(day);
            String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
            if (dayNum >= 1 && dayNum <= 6) {
                return days[dayNum - 1];
            }
        } catch (NumberFormatException e) {
            // It's already a string, return as is
            return day;
        }

        return day;
    }

    private String getTimeFromSlot(String slot) {
        // Convert "7:00-7:30" to "07:00:00"
        String startTime = slot.split("-")[0].trim();

        // Ensure proper format
        if (!startTime.contains(":")) {
            startTime = startTime + ":00";
        }

        // Add leading zero if needed
        String[] parts = startTime.split(":");
        if (parts[0].length() == 1) {
            startTime = "0" + startTime;
        }

        return startTime + ":00";
    }

    private int calculateDuration(String startTime, String endTime) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            Date start = sdf.parse(startTime);
            Date end = sdf.parse(endTime);

            if (start != null && end != null) {
                long diffInMillis = end.getTime() - start.getTime();
                long diffInMinutes = diffInMillis / (60 * 1000);
                return (int) Math.ceil(diffInMinutes / 30.0);
            }
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing time: " + e.getMessage());
        }
        return 1;
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }

    private void showEmptyState() {
        runOnUiThread(() -> {
            loadingProgress.setVisibility(View.GONE);
            timetableCard.setVisibility(View.GONE);
            emptyStateText.setVisibility(View.VISIBLE);
            emptyStateText.setText("No schedule found for your section.");
        });
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_dashboard) {
            Intent intent = new Intent(this, StudentDashboardActivity.class);
            intent.putExtra("userId", getIntent().getStringExtra("userId"));
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
        android.widget.PopupMenu popupMenu = new android.widget.PopupMenu(
                new android.view.ContextThemeWrapper(this, R.style.PopupMenuStyle), view);
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
    private void tryUseFirstAvailableStudent() {
        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("attendance_system/students");
        
        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean found = false;
                
                for (DataSnapshot studentSnapshot : snapshot.getChildren()) {
                    try {
                        DataSnapshot dataSnapshot = studentSnapshot.child("data");
                        if (dataSnapshot.exists()) {
                            String id = dataSnapshot.child("id").getValue(String.class);
                            String yearLevel = dataSnapshot.child("year_level").getValue(String.class);
                            String section = dataSnapshot.child("section").getValue(String.class);
                            String course = dataSnapshot.child("course").getValue(String.class);
                            
                            if (yearLevel != null && section != null && course != null) {
                                found = true;
                                studentYearLevel = yearLevel;
                                studentSection = section;
                                studentCourse = course;
                                
                                Log.d(TAG, "Using first available student - ID: " + id + ", Year: " + yearLevel + ", Section: " + section + ", Course: " + course);
                                Toast.makeText(TimetableActivity.this, 
                                    "Using student data: Year " + yearLevel + ", Section " + section + ", Course: " + course, 
                                    Toast.LENGTH_LONG).show();
                                
                                // Fetch course_id and then timetable
                                fetchCourseIdFromCourses(course);
                                break;
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing first available student: " + e.getMessage());
                    }
                }
                
                if (!found) {
                    Log.d(TAG, "No valid student data found, showing empty state");
                    showEmptyState();
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching first available student: " + error.getMessage());
                showEmptyState();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove Firebase listener to prevent memory leaks
        if (timetableRef != null && timetableListener != null) {
            Log.d(TAG, "Removing timetable listener in onDestroy");
            timetableRef.removeEventListener(timetableListener);
            timetableListener = null;
            timetableRef = null;
        }
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