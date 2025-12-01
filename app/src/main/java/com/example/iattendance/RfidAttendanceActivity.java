package com.example.iattendance;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import androidx.appcompat.widget.AppCompatButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RfidAttendanceActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "RfidAttendanceActivity";
    
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private SessionManager sessionManager;
    private Spinner classSpinner;
    private AppCompatButton startScannerBtn;
    private Button acceptBtn, declineBtn;
    private TextView dateText, studentName, studentId, studentCourse, 
                     studentYearSection, studentRfidUid, attendanceStatus;
    private ImageView studentPhoto, studentPhotoPlaceholder;
    private CardView studentInfoCard, scannerCard, studentsListCard;
    private View emptyStateText;
    private RecyclerView studentsRecyclerView;
    private View studentsEmptyState;
    
    // Dialog views
    private AlertDialog scannerDialog;
    private TextView dialogScannerStatusBadge, dialogAlertText;
    private LinearLayout dialogWaitingArea;
    private ProgressBar dialogLoadingProgress;
    private CardView dialogStudentInfoCard;
    private TextView dialogStudentName, dialogStudentId, dialogStudentCourse, 
                     dialogStudentYearSection, dialogStudentRfidUid, dialogAttendanceStatus;
    private ImageView dialogStudentPhoto, dialogStudentPhotoPlaceholder;
    private AppCompatButton dialogAcceptBtn, dialogDeclineBtn;
    
    private String teacherId;
    private final List<ClassItem> teacherClasses = new ArrayList<>(); // Thread-safe list operations
    private String selectedClassId;
    private String attendanceDate;
    private DatabaseReference rfidScanRef;
    private ValueEventListener rfidScanListener;
    private boolean isScanning = false;
    private String currentStudentId;
    private String currentAttendanceStatus;
    private String currentRfidUid; // Store current RFID UID for tracking
    private Handler mainHandler;
    private boolean isFirstScan = true; // Track first scan to ignore initial null snapshot
    private String lastProcessedRfidUid = null; // Track last processed RFID to avoid duplicate processing
    private long scannerStartTime = 0; // Track when scanner started
    private AttendanceStudentsAdapter studentsAdapter;
    private List<AttendanceStudentsAdapter.AttendanceStudentItem> attendanceStudents = new ArrayList<>();
    private List<AttendanceStudentsAdapter.AttendanceStudentItem> allAttendanceStudents = new ArrayList<>(); // Store all fetched students for filtering
    private EditText searchEditText;
    private View searchCard;
    private boolean isProcessingStudent = false; // Track if currently processing a student to prevent spam
    private String currentlyProcessingRfidUid = null; // Track which RFID is currently being processed
    private Handler alertHandler = new Handler(Looper.getMainLooper()); // Handler for auto-hiding alerts
    private Runnable alertHideRunnable; // Runnable to hide alert after 5 seconds
    private ValueEventListener attendanceRealtimeListener; // Real-time listener for attendance changes
    private DatabaseReference attendanceRealtimeRef; // Reference for real-time attendance updates
    private boolean isFirstListenerTrigger = true; // Track first trigger to ignore initial load

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_rfid_attendance);

            sessionManager = new SessionManager(this);
            mainHandler = new Handler(Looper.getMainLooper());

            // Check if user is logged in and verified
            if (!sessionManager.isLoggedIn() || !sessionManager.isVerified()) {
                finish();
                return;
            }

            // Initialize views
            initializeViews();

        // Setup drawer
        View menuIcon = findViewById(R.id.menu_icon);
        if (menuIcon != null) {
            menuIcon.setOnClickListener(v -> {
                if (drawerLayout != null) {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            });
        }
        
        View moreIcon = findViewById(R.id.more_icon);
        if (moreIcon != null) {
            moreIcon.setOnClickListener(v -> onOptionsIconClick());
        }

        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(this);
            // Set checked item after menu is inflated
            navigationView.post(() -> {
                try {
                    navigationView.setCheckedItem(R.id.nav_attendance);
                } catch (Exception e) {
                    Log.e(TAG, "Error setting checked item: " + e.getMessage());
                }
            });
        }

        // Using Firebase directly - no server URL needed
        Log.d(TAG, "Using Firebase for attendance management");

        // Get user data
        teacherId = getIntent().getStringExtra("userId");
        String fullName = getIntent().getStringExtra("fullName");

        // Update nav header
        if (navigationView != null) {
            navigationView.post(() -> {
                try {
                    View headerView = navigationView.getHeaderView(0);
                    if (headerView != null) {
                        TextView navName = headerView.findViewById(R.id.nav_header_name);
                        TextView navEmail = headerView.findViewById(R.id.nav_header_email);

                        if (navName != null && fullName != null) navName.setText(fullName);
                        if (navEmail != null && teacherId != null) navEmail.setText("ID: " + teacherId);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error updating nav header: " + e.getMessage());
                }
            });
        }

        // Set current date
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        attendanceDate = sdf.format(new Date());
        if (dateText != null) {
            dateText.setText("Date: " + attendanceDate);
        }

        // Setup buttons
        if (startScannerBtn != null) {
            startScannerBtn.setOnClickListener(v -> startScanner());
        }
        if (acceptBtn != null) {
            acceptBtn.setOnClickListener(v -> {
                try {
                    // Prevent any default behavior
                    v.setEnabled(false);
                    acceptAttendance();
                } catch (Exception e) {
                    Log.e(TAG, "Error in accept button click: " + e.getMessage(), e);
                    v.setEnabled(true);
                    showAlert("Error: " + e.getMessage(), "#FF9800");
                }
            });
        }
        if (declineBtn != null) {
            declineBtn.setOnClickListener(v -> {
                try {
                    declineAttendance();
                } catch (Exception e) {
                    Log.e(TAG, "Error in decline button click: " + e.getMessage(), e);
                    showAlert("Error: " + e.getMessage(), "#FF9800");
                }
            });
        }

        // Setup class spinner
        if (classSpinner != null) {
            classSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                try {
                    // Thread-safe access to teacherClasses
                    ClassItem selectedClass = null;
                    synchronized (teacherClasses) {
                        if (position > 0 && position <= teacherClasses.size()) {
                            selectedClass = teacherClasses.get(position - 1);
                        }
                    }
                    
                    if (selectedClass != null) {
                        selectedClassId = selectedClass.id;
                        Log.d(TAG, "Class selected: " + selectedClass.name + " (ID: " + selectedClassId + ")");
                        if (isScanning) {
                            stopScanner();
                        }
                        // Clear search when new class is selected
                        if (searchEditText != null) {
                            searchEditText.setText("");
                        }
                        // Fetch students for the selected class
                        fetchStudentsForClass(selectedClass.section, selectedClass.yearLevel);
                    } else {
                        selectedClassId = null;
                        if (position == 0) {
                            Log.d(TAG, "Placeholder 'Select Class' selected");
                        } else {
                            Log.w(TAG, "Selected class is null at position: " + position);
                        }
                        if (isScanning) {
                            stopScanner();
                        }
                        // Clear search and hide search card when no class is selected
                        if (searchEditText != null) {
                            searchEditText.setText("");
                        }
                        if (searchCard != null) {
                            searchCard.setVisibility(View.GONE);
                        }
                        // Clear student lists
                        allAttendanceStudents.clear();
                        attendanceStudents.clear();
                        if (studentsAdapter != null) {
                            studentsAdapter.updateStudents(attendanceStudents);
                        }
                        // Remove real-time listener when no class is selected
                        if (attendanceRealtimeListener != null && attendanceRealtimeRef != null) {
                            try {
                                attendanceRealtimeRef.removeEventListener(attendanceRealtimeListener);
                                attendanceRealtimeListener = null;
                                attendanceRealtimeRef = null;
                                Log.d(TAG, "Real-time listener removed - no class selected");
                            } catch (Exception e) {
                                Log.e(TAG, "Error removing listener: " + e.getMessage());
                            }
                        }
                        // Hide students list when no class is selected
                        if (studentsListCard != null) {
                            studentsListCard.setVisibility(View.GONE);
                        }
                    }
                } catch (IndexOutOfBoundsException e) {
                    Log.e(TAG, "Index out of bounds in class selection: " + e.getMessage());
                    selectedClassId = null;
                } catch (Exception e) {
                    Log.e(TAG, "Error in class selection: " + e.getMessage(), e);
                    selectedClassId = null;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
        }

        // Fetch teacher classes
        if (teacherId != null && !teacherId.isEmpty()) {
            fetchTeacherClasses(teacherId);
        } else {
            Log.e(TAG, "Teacher ID is null or empty");
            Toast.makeText(this, "Error: Teacher ID not found", Toast.LENGTH_LONG).show();
            finish();
        }
        
        
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            e.printStackTrace();
            try {
                Toast.makeText(this, "Error initializing activity. Please try again.", Toast.LENGTH_LONG).show();
            } catch (Exception toastEx) {
                Log.e(TAG, "Error showing toast: " + toastEx.getMessage());
            }
            finish();
        }
    }

    private void initializeViews() {
        try {
            drawerLayout = findViewById(R.id.drawer_layout);
            navigationView = findViewById(R.id.nav_view);
            classSpinner = findViewById(R.id.classSpinner);
            startScannerBtn = findViewById(R.id.startScannerBtn);
            // Force blue background - completely override Material3 purple theme
            if (startScannerBtn != null) {
                // Create blue StateListDrawable programmatically
                android.content.res.ColorStateList buttonStates = new android.content.res.ColorStateList(
                    new int[][]{
                        new int[]{android.R.attr.state_pressed},
                        new int[]{android.R.attr.state_enabled},
                        new int[]{}
                    },
                    new int[]{
                        0xFF1976D2, // Darker blue when pressed
                        0xFF2196F3, // Blue when enabled
                        0xFF2196F3  // Blue default
                    }
                );
                
                // Create blue background drawable
                android.graphics.drawable.StateListDrawable stateListDrawable = new android.graphics.drawable.StateListDrawable();
                float cornerRadius = 12f * getResources().getDisplayMetrics().density; // Convert dp to pixels
                
                // Normal state - blue
                android.graphics.drawable.GradientDrawable normalBg = new android.graphics.drawable.GradientDrawable();
                normalBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                normalBg.setColor(0xFF2196F3); // Blue
                normalBg.setCornerRadius(cornerRadius);
                stateListDrawable.addState(new int[]{android.R.attr.state_enabled}, normalBg);
                
                // Pressed state - darker blue
                android.graphics.drawable.GradientDrawable pressedBg = new android.graphics.drawable.GradientDrawable();
                pressedBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                pressedBg.setColor(0xFF1976D2); // Darker blue
                pressedBg.setCornerRadius(cornerRadius);
                stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, pressedBg);
                
                // Default state - blue
                stateListDrawable.addState(new int[]{}, normalBg);
                
                // Apply the blue background
                startScannerBtn.setBackground(stateListDrawable);
                startScannerBtn.setBackgroundTintList(null);
                startScannerBtn.setTextColor(0xFFFFFFFF); // White text
                
                // Force apply again after layout to override any theme
                startScannerBtn.post(() -> {
                    startScannerBtn.setBackground(stateListDrawable);
                    startScannerBtn.setBackgroundTintList(null);
                    startScannerBtn.setTextColor(0xFFFFFFFF);
                });
            }
            acceptBtn = findViewById(R.id.acceptBtn);
            declineBtn = findViewById(R.id.declineBtn);
            dateText = findViewById(R.id.dateText);
            studentName = findViewById(R.id.studentName);
            studentId = findViewById(R.id.studentId);
            studentCourse = findViewById(R.id.studentCourse);
            studentYearSection = findViewById(R.id.studentYearSection);
            studentRfidUid = findViewById(R.id.studentRfidUid);
            attendanceStatus = findViewById(R.id.attendanceStatus);
            studentPhoto = findViewById(R.id.studentPhoto);
            studentPhotoPlaceholder = findViewById(R.id.studentPhotoPlaceholder);
            studentInfoCard = findViewById(R.id.studentInfoCard);
            scannerCard = findViewById(R.id.scannerCard);
            studentsListCard = findViewById(R.id.studentsListCard);
            emptyStateText = findViewById(R.id.emptyStateText);
            studentsRecyclerView = findViewById(R.id.studentsRecyclerView);
            studentsEmptyState = findViewById(R.id.studentsEmptyState);
            searchEditText = findViewById(R.id.searchEditText);
            searchCard = findViewById(R.id.searchCard);
        
        // Setup search functionality
        if (searchEditText != null) {
            searchEditText.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterAttendanceStudents(s.toString());
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }
        
        // Setup RecyclerView for students list
        if (studentsRecyclerView != null) {
            studentsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            studentsAdapter = new AttendanceStudentsAdapter(attendanceStudents);
            // Set click listener for manual attendance update
            studentsAdapter.setOnStudentClickListener(student -> {
                showUpdateAttendanceDialog(student);
            });
            studentsRecyclerView.setAdapter(studentsAdapter);
        }
        
        // Initialize scanner dialog
        initializeScannerDialog();
            
            // Log if critical views are missing
            if (drawerLayout == null) Log.w(TAG, "drawerLayout is null");
            if (navigationView == null) Log.w(TAG, "navigationView is null");
            if (classSpinner == null) Log.w(TAG, "classSpinner is null");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views: " + e.getMessage(), e);
            throw e; // Re-throw to be caught by onCreate
        }
    }
    
    private void initializeScannerDialog() {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_scanner_status, null);
            builder.setView(dialogView);
            builder.setCancelable(true);
            
            scannerDialog = builder.create();
            if (scannerDialog.getWindow() != null) {
                scannerDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                // Set dialog to take up most of the screen width
                android.view.WindowManager.LayoutParams layoutParams = scannerDialog.getWindow().getAttributes();
                layoutParams.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
                scannerDialog.getWindow().setAttributes(layoutParams);
            }
            
            // Auto-stop scanner when dialog is dismissed
            scannerDialog.setOnDismissListener(dialog -> {
                if (isScanning) {
                    stopScanner();
                }
            });
            
            // Initialize dialog views
            dialogScannerStatusBadge = dialogView.findViewById(R.id.dialogScannerStatusBadge);
            dialogAlertText = dialogView.findViewById(R.id.dialogAlertText);
            dialogWaitingArea = dialogView.findViewById(R.id.dialogWaitingArea);
            dialogLoadingProgress = dialogView.findViewById(R.id.dialogLoadingProgress);
            dialogStudentInfoCard = dialogView.findViewById(R.id.dialogStudentInfoCard);
            dialogStudentName = dialogView.findViewById(R.id.dialogStudentName);
            dialogStudentId = dialogView.findViewById(R.id.dialogStudentId);
            dialogStudentCourse = dialogView.findViewById(R.id.dialogStudentCourse);
            dialogStudentYearSection = dialogView.findViewById(R.id.dialogStudentYearSection);
            dialogStudentRfidUid = dialogView.findViewById(R.id.dialogStudentRfidUid);
            dialogAttendanceStatus = dialogView.findViewById(R.id.dialogAttendanceStatus);
            dialogStudentPhoto = dialogView.findViewById(R.id.dialogStudentPhoto);
            dialogStudentPhotoPlaceholder = dialogView.findViewById(R.id.dialogStudentPhotoPlaceholder);
            dialogAcceptBtn = dialogView.findViewById(R.id.dialogAcceptBtn);
            dialogDeclineBtn = dialogView.findViewById(R.id.dialogDeclineBtn);
            
            // Setup dialog buttons - force colors to prevent Material3 purple theme
            if (dialogAcceptBtn != null) {
                // Force green background - override Material3 theme
                dialogAcceptBtn.setBackgroundTintList(null);
                android.graphics.drawable.GradientDrawable acceptBtnBg = new android.graphics.drawable.GradientDrawable();
                acceptBtnBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                acceptBtnBg.setColor(0xFF4CAF50); // Green color
                acceptBtnBg.setCornerRadius(12f * getResources().getDisplayMetrics().density);
                dialogAcceptBtn.setBackground(acceptBtnBg);
                dialogAcceptBtn.setTextColor(0xFFFFFFFF); // White text
                
                // Re-apply after layout to ensure it sticks
                dialogAcceptBtn.post(() -> {
                    dialogAcceptBtn.setBackground(acceptBtnBg);
                    dialogAcceptBtn.setBackgroundTintList(null);
                    dialogAcceptBtn.setTextColor(0xFFFFFFFF);
                });
                
                dialogAcceptBtn.setOnClickListener(v -> {
                    try {
                        acceptAttendance();
                    } catch (Exception e) {
                        Log.e(TAG, "Error in dialog accept button click: " + e.getMessage(), e);
                        showAlert("Error: " + e.getMessage(), "#FF9800");
                    }
                });
            }
            if (dialogDeclineBtn != null) {
                // Force red background - override Material3 theme
                dialogDeclineBtn.setBackgroundTintList(null);
                android.graphics.drawable.GradientDrawable declineBtnBg = new android.graphics.drawable.GradientDrawable();
                declineBtnBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                declineBtnBg.setColor(0xFFF44336); // Red color
                declineBtnBg.setCornerRadius(12f * getResources().getDisplayMetrics().density);
                dialogDeclineBtn.setBackground(declineBtnBg);
                dialogDeclineBtn.setTextColor(0xFFFFFFFF); // White text
                
                // Re-apply after layout to ensure it sticks
                dialogDeclineBtn.post(() -> {
                    dialogDeclineBtn.setBackground(declineBtnBg);
                    dialogDeclineBtn.setBackgroundTintList(null);
                    dialogDeclineBtn.setTextColor(0xFFFFFFFF);
                });
                
                dialogDeclineBtn.setOnClickListener(v -> {
                    try {
                        declineAttendance();
                    } catch (Exception e) {
                        Log.e(TAG, "Error in dialog decline button click: " + e.getMessage(), e);
                        showAlert("Error: " + e.getMessage(), "#FF9800");
                    }
                });
            }
            
            AppCompatButton dialogCloseBtn = dialogView.findViewById(R.id.dialogCloseBtn);
            if (dialogCloseBtn != null) {
                // Force blue background - override Material3 purple theme
                dialogCloseBtn.setBackgroundTintList(null);
                android.graphics.drawable.GradientDrawable closeBtnBg = new android.graphics.drawable.GradientDrawable();
                closeBtnBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                closeBtnBg.setColor(0xFF2196F3); // Blue color
                closeBtnBg.setCornerRadius(12f * getResources().getDisplayMetrics().density);
                dialogCloseBtn.setBackground(closeBtnBg);
                dialogCloseBtn.setTextColor(0xFFFFFFFF); // White text
                
                // Re-apply after layout to ensure it sticks
                dialogCloseBtn.post(() -> {
                    dialogCloseBtn.setBackground(closeBtnBg);
                    dialogCloseBtn.setBackgroundTintList(null);
                    dialogCloseBtn.setTextColor(0xFFFFFFFF);
                });
                
                dialogCloseBtn.setOnClickListener(v -> {
                    if (scannerDialog != null && scannerDialog.isShowing()) {
                        scannerDialog.dismiss();
                    }
                    if (isScanning) {
                        stopScanner();
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing scanner dialog: " + e.getMessage(), e);
        }
    }

    private void startScanner() {
        try {
            if (selectedClassId == null || selectedClassId.isEmpty()) {
                showAlert("Please select a class first", "#FF9800");
                return;
            }

            // Stop any existing scanner first
            if (isScanning && rfidScanListener != null && rfidScanRef != null) {
                try {
                    rfidScanRef.removeEventListener(rfidScanListener);
                } catch (Exception e) {
                    Log.w(TAG, "Error removing existing listener: " + e.getMessage());
                }
            }

            isScanning = true;
            isFirstScan = true; // Reset first scan flag
            lastProcessedRfidUid = null; // Reset last processed RFID
            scannerStartTime = System.currentTimeMillis(); // Record scanner start time
            isProcessingStudent = false; // Reset processing flag
            currentlyProcessingRfidUid = null; // Reset current processing RFID
            currentRfidUid = null; // Reset current RFID UID
            
            // Update UI safely
            if (startScannerBtn != null) {
                startScannerBtn.setVisibility(View.GONE);
            }
            runOnUiThread(() -> {
                if (studentInfoCard != null) {
                    studentInfoCard.setVisibility(View.GONE);
                }
                showScannerDialog();
                updateDialogStatus("Listening", 0xFF4CAF50);
                showDialogWaitingArea(true);
                hideDialogAlert();
            });

            // Listen to Firebase RFID scans
            try {
                rfidScanRef = FirebaseDatabase.getInstance().getReference("attendance_system/rfid_scans/latest");
                
                if (rfidScanRef == null) {
                    throw new Exception("Failed to get Firebase reference");
                }
                
                rfidScanListener = new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        try {
                            // Check if activity is still valid (safe check for all Android versions)
                            try {
                                if (isFinishing() || (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
                                    Log.d(TAG, "Activity is finishing/destroyed, ignoring RFID scan");
                                    return;
                                }
                            } catch (Exception e) {
                                // If check fails, continue anyway
                                Log.w(TAG, "Could not check activity state: " + e.getMessage());
                            }
                            
                            if (!isScanning) {
                                Log.d(TAG, "Scanner stopped, ignoring RFID scan");
                                return;
                            }
                            
                            if (selectedClassId == null || selectedClassId.isEmpty()) {
                                Log.d(TAG, "No class selected, ignoring RFID scan");
                                return;
                            }
                            
                            // Skip if snapshot is null or empty
                            if (snapshot == null) {
                                Log.d(TAG, "Snapshot is null");
                                return;
                            }
                            
                            // Ignore the first snapshot - it's the current value when listener is attached
                            if (isFirstScan) {
                                isFirstScan = false;
                                Log.d(TAG, "Ignoring initial snapshot (current Firebase value)");
                                
                                // Store the initial RFID UID to compare against future scans
                                Object initialUidObj = null;
                                try {
                                    initialUidObj = snapshot.child("uid").getValue();
                                    if (initialUidObj == null) {
                                        initialUidObj = snapshot.child("UID").getValue();
                                        if (initialUidObj == null) {
                                            initialUidObj = snapshot.child("card").getValue();
                                            if (initialUidObj == null) {
                                                initialUidObj = snapshot.child("tag").getValue();
                                                if (initialUidObj == null) {
                                                    initialUidObj = snapshot.child("value").getValue();
                                                    if (initialUidObj == null) {
                                                        initialUidObj = snapshot.child("rfid").getValue();
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    if (initialUidObj != null) {
                                        String initialRfidUid = String.valueOf(initialUidObj).replaceAll("[^A-Za-z0-9]", "").toUpperCase();
                                        if (initialRfidUid != null && !initialRfidUid.isEmpty()) {
                                            lastProcessedRfidUid = initialRfidUid;
                                            Log.d(TAG, "Stored initial RFID UID: " + initialRfidUid + " (will ignore until new scan)");
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error reading initial RFID UID: " + e.getMessage());
                                }
                                return; // Don't process the initial snapshot
                            }
                            
                            if (!snapshot.exists()) {
                                Log.d(TAG, "Snapshot does not exist");
                                return;
                            }
                            
                            Object uidObj = null;
                            try {
                                // Try to get UID from various possible locations
                                // First, try direct value (if snapshot itself is the UID)
                                Object directValue = snapshot.getValue();
                                if (directValue != null && directValue instanceof String) {
                                    String directStr = String.valueOf(directValue).trim();
                                    if (!directStr.isEmpty() && directStr.length() >= 4) {
                                        uidObj = directValue;
                                        Log.d(TAG, "Found RFID UID as direct value: " + directStr);
                                    }
                                }
                                
                                // Try child fields
                                if (uidObj == null) {
                                    uidObj = snapshot.child("uid").getValue();
                                    if (uidObj == null) {
                                        uidObj = snapshot.child("UID").getValue();
                                        if (uidObj == null) {
                                            uidObj = snapshot.child("card").getValue();
                                            if (uidObj == null) {
                                                uidObj = snapshot.child("tag").getValue();
                                                if (uidObj == null) {
                                                    uidObj = snapshot.child("value").getValue();
                                                    if (uidObj == null) {
                                                        uidObj = snapshot.child("rfid").getValue();
                                                        if (uidObj == null) {
                                                            uidObj = snapshot.child("rfid_uid").getValue();
                                                            if (uidObj == null) {
                                                                uidObj = snapshot.child("card_id").getValue();
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                // Log snapshot structure for debugging
                                if (uidObj == null) {
                                    Log.w(TAG, "No RFID UID found. Snapshot structure: " + snapshot.toString());
                                    Log.w(TAG, "Snapshot children: " + snapshot.getChildrenCount());
                                    for (DataSnapshot child : snapshot.getChildren()) {
                                        Log.w(TAG, "Child key: " + child.getKey() + ", value: " + child.getValue());
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error reading RFID UID from snapshot: " + e.getMessage(), e);
                            }
                            
                            if (uidObj != null) {
                                String rfidUid = String.valueOf(uidObj).replaceAll("[^A-Za-z0-9]", "").toUpperCase();
                                if (rfidUid != null && !rfidUid.isEmpty() && rfidUid.length() >= 4) {
                                    Log.d(TAG, "Extracted RFID UID: " + rfidUid + " (length: " + rfidUid.length() + ")");
                                    // Only process if not currently processing the same RFID
                                    // Don't check lastProcessedRfidUid here - we'll set it only after successful validation
                                    if (!isProcessingStudent || 
                                        (currentlyProcessingRfidUid != null && !rfidUid.equals(currentlyProcessingRfidUid))) {
                                        Log.d(TAG, "New RFID scan detected: " + rfidUid);
                                        isProcessingStudent = true; // Mark as processing
                                        currentlyProcessingRfidUid = rfidUid; // Track current processing
                                        
                                        // Ensure we're on main thread before calling fetchStudentByRfid
                                        final String finalRfidUid = rfidUid;
                                        // Always use runOnUiThread to ensure we're on main thread
                                        runOnUiThread(() -> {
                                            try {
                                                if (isScanning && selectedClassId != null && !selectedClassId.isEmpty()) {
                                                    Log.d(TAG, "Calling fetchStudentByRfid with: " + finalRfidUid);
                                                    currentRfidUid = finalRfidUid; // Store current RFID UID
                                                    fetchStudentByRfid(finalRfidUid);
                                                } else {
                                                    Log.w(TAG, "Cannot fetch student - isScanning: " + isScanning + ", selectedClassId: " + selectedClassId);
                                                    // Reset processing flags if we can't process
                                                    isProcessingStudent = false;
                                                    currentlyProcessingRfidUid = null;
                                                    currentRfidUid = null;
                                                }
                                            } catch (Exception e) {
                                                Log.e(TAG, "Error calling fetchStudentByRfid: " + e.getMessage(), e);
                                                // Reset processing flags on error
                                                isProcessingStudent = false;
                                                currentlyProcessingRfidUid = null;
                                                currentRfidUid = null;
                                                showDialogLoadingProgress(false);
                                                showAlert("Error processing RFID scan: " + e.getMessage(), "#FF9800");
                                            }
                                        });
                                    } else {
                                        // This RFID is currently being processed - ignore silently
                                        Log.d(TAG, "RFID UID is currently being processed, ignoring duplicate: " + rfidUid);
                                    }
                                } else {
                                    Log.w(TAG, "RFID UID is empty or invalid (length: " + (rfidUid != null ? rfidUid.length() : 0) + ")");
                                }
                            } else {
                                Log.w(TAG, "No RFID UID found in snapshot. Snapshot exists: " + snapshot.exists());
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing RFID scan: " + e.getMessage(), e);
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "RFID scan listener cancelled: " + error.getMessage());
                        if (isScanning) {
                            runOnUiThread(() -> {
                                showAlert("Error listening for RFID scans: " + error.getMessage(), "#FF9800");
                            });
                        }
                    }
                };
                
                if (rfidScanListener != null) {
                    rfidScanRef.addValueEventListener(rfidScanListener);
                    Log.d(TAG, "Scanner started, listening for RFID scans at: attendance_system/rfid_scans/latest");
                } else {
                    throw new Exception("Failed to create listener");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error setting up Firebase listener: " + e.getMessage(), e);
                isScanning = false;
                runOnUiThread(() -> {
                    showAlert("Error starting scanner: " + e.getMessage(), "#FF9800");
                    if (startScannerBtn != null) {
                        startScannerBtn.setVisibility(View.VISIBLE);
                    }
                });
                return; // Don't throw, just return gracefully
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting scanner: " + e.getMessage(), e);
            isScanning = false;
            runOnUiThread(() -> {
                try {
                    showAlert("Error starting scanner: " + e.getMessage(), "#FF9800");
                    if (startScannerBtn != null) {
                        startScannerBtn.setVisibility(View.VISIBLE);
                    }
                    showDialogWaitingArea(false);
                } catch (Exception uiException) {
                    Log.e(TAG, "Error updating UI after scanner error: " + uiException.getMessage());
                }
            });
        }
    }

    private void stopScanner() {
        try {
            isScanning = false;
            // Reset processing flags when stopping scanner
            isProcessingStudent = false;
            currentlyProcessingRfidUid = null;
            lastProcessedRfidUid = null;
            currentRfidUid = null;
            
            if (startScannerBtn != null) {
                startScannerBtn.setVisibility(View.VISIBLE);
            }
            if (studentInfoCard != null) {
                studentInfoCard.setVisibility(View.GONE);
            }
            dismissScannerDialog();
            hideAlert();

            if (rfidScanListener != null && rfidScanRef != null) {
                try {
                    rfidScanRef.removeEventListener(rfidScanListener);
                    rfidScanListener = null;
                } catch (Exception e) {
                    Log.e(TAG, "Error removing RFID listener: " + e.getMessage());
                }
            }
            
            Log.d(TAG, "Scanner stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping scanner: " + e.getMessage(), e);
        }
    }

    private void fetchStudentByRfid(String rfidUid) {
        try {
            if (rfidUid == null || rfidUid.isEmpty()) {
                Log.w(TAG, "RFID UID is null or empty");
                return;
            }
            
            if (selectedClassId == null || selectedClassId.isEmpty()) {
                showAlert("Please select a class first", "#FF9800");
                return;
            }

            showDialogLoadingProgress(true);
            hideAlert();

        // Fetch student from Firebase by RFID UID
        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("attendance_system/students");
        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    showDialogLoadingProgress(false);
                
                boolean studentFound = false;
                for (DataSnapshot studentSnapshot : snapshot.getChildren()) {
                    try {
                        DataSnapshot dataSnapshot = studentSnapshot.child("data");
                        if (!dataSnapshot.exists()) continue;

                        String studentRfidUid = dataSnapshot.child("rfid_uid").getValue(String.class);
                        if (studentRfidUid == null || !studentRfidUid.equalsIgnoreCase(rfidUid)) {
                            continue;
                        }

                        // Found student with matching RFID
                        studentFound = true;
                        
                        // Make final copies for nested callbacks
                        final DataSnapshot finalStudentData = dataSnapshot;
                        String studentId = dataSnapshot.child("id").getValue(String.class);
                        Object studentSectionObj = dataSnapshot.child("section").getValue();
                        Object studentYearLevelObj = dataSnapshot.child("year_level").getValue();
                        String studentSection = studentSectionObj != null ? String.valueOf(studentSectionObj) : null;
                        String studentYearLevel = studentYearLevelObj != null ? String.valueOf(studentYearLevelObj) : null;
                        
                        Log.d(TAG, "Student found - ID: " + studentId + ", Section: " + studentSection + ", Year: " + studentYearLevel);
                        
                        if (studentId == null || studentId.isEmpty()) {
                            Log.e(TAG, "Student ID is null or empty");
                            runOnUiThread(() -> {
                                showAlert("Error: Student ID not found", "#FF9800");
                                showDialogWaitingArea(true);
                                // Hide dialog student info card
                                if (dialogStudentInfoCard != null) {
                                    dialogStudentInfoCard.setVisibility(View.GONE);
                                }
                                // Also hide main screen student info card
                                if (studentInfoCard != null) {
                                    studentInfoCard.setVisibility(View.GONE);
                                }
                            });
                            return;
                        }
                        
                        final String finalStudentId = studentId;
                        final String finalStudentSection = studentSection;
                        final String finalStudentYearLevel = studentYearLevel;
                        
                        // Get selected class details first (like website does)
                        ClassItem selectedClass = null;
                        synchronized (teacherClasses) {
                            for (ClassItem cls : teacherClasses) {
                                if (cls.id.equals(selectedClassId)) {
                                    selectedClass = cls;
                                    break;
                                }
                            }
                        }
                        
                        if (selectedClass == null) {
                            Log.e(TAG, "Selected class not found in teacherClasses list");
                            runOnUiThread(() -> {
                                showAlert("Error: Selected class not found", "#FF9800");
                                showDialogWaitingArea(true);
                            });
                            return;
                        }
                        
                        String selectedClassSection = selectedClass.section;
                        String selectedClassYearLevel = selectedClass.yearLevel;
                        
                        Log.d(TAG, "Selected class - Section: " + selectedClassSection + ", Year: " + selectedClassYearLevel);
                        Log.d(TAG, "Student - Section: " + finalStudentSection + ", Year: " + finalStudentYearLevel);
                        
                        // STRICT VALIDATION (like website): Check if student's section and year_level match the class
                        // Normalize year level for comparison
                        String normalizedStudentYear = finalStudentYearLevel != null ? String.valueOf(finalStudentYearLevel).trim() : null;
                        String normalizedClassYear = selectedClassYearLevel != null ? String.valueOf(selectedClassYearLevel).trim() : null;
                        
                        // Extract numeric values for comparison
                        String studentYearNum = "";
                        String classYearNum = "";
                        if (normalizedStudentYear != null) {
                            studentYearNum = normalizedStudentYear.replaceAll("[^0-9]", "");
                        }
                        if (normalizedClassYear != null) {
                            classYearNum = normalizedClassYear.replaceAll("[^0-9]", "");
                        }
                        
                        // Check if section matches (case-insensitive, trim whitespace)
                        boolean sectionMatch = false;
                        if (finalStudentSection != null && selectedClassSection != null) {
                            String studentSec = finalStudentSection.trim();
                            String classSec = selectedClassSection.trim();
                            sectionMatch = studentSec.equalsIgnoreCase(classSec);
                            Log.d(TAG, "Section comparison - Student: '" + studentSec + "' vs Class: '" + classSec + "' = " + sectionMatch);
                        }
                        
                        // Check if year matches
                        boolean yearMatch = false;
                        if (!studentYearNum.isEmpty() && !classYearNum.isEmpty()) {
                            try {
                                int studentYearInt = Integer.parseInt(studentYearNum);
                                int classYearInt = Integer.parseInt(classYearNum);
                                yearMatch = studentYearInt == classYearInt;
                                Log.d(TAG, "Year comparison (numeric) - Student: " + studentYearInt + " vs Class: " + classYearInt + " = " + yearMatch);
                            } catch (NumberFormatException e) {
                                yearMatch = normalizedStudentYear != null && normalizedClassYear != null && 
                                           normalizedStudentYear.equals(normalizedClassYear);
                                Log.d(TAG, "Year comparison (string fallback) - Student: '" + normalizedStudentYear + "' vs Class: '" + normalizedClassYear + "' = " + yearMatch);
                            }
                        } else if (normalizedStudentYear != null && normalizedClassYear != null) {
                            yearMatch = normalizedStudentYear.equals(normalizedClassYear);
                            Log.d(TAG, "Year comparison (direct string) - Student: '" + normalizedStudentYear + "' vs Class: '" + normalizedClassYear + "' = " + yearMatch);
                        }
                        
                        Log.d(TAG, "Section match: " + sectionMatch + ", Year match: " + yearMatch);
                        
                        // REJECT IMMEDIATELY if section/year doesn't match (like website)
                        if (!sectionMatch || !yearMatch) {
                            Log.w(TAG, "✗ Student section/year does NOT match selected class");
                            String studentInfo = "Year " + finalStudentYearLevel + " - Section " + finalStudentSection;
                            String classInfo = "Year " + selectedClassYearLevel + " - Section " + selectedClassSection;
                            runOnUiThread(() -> {
                                // Reset processing flags - allow re-scanning of this RFID
                                // Reset processing flags - allow re-scanning of this RFID
                                isProcessingStudent = false;
                                currentlyProcessingRfidUid = null;
                                currentRfidUid = null; // Clear current RFID UID
                                // Don't set lastProcessedRfidUid - allow re-scanning
                                showAlert("This student (" + studentInfo + ") is NOT in the selected class (" + classInfo + "). Please select the correct class.", "#FF9800");
                                showDialogWaitingArea(true);
                                // Hide dialog student info card
                                if (dialogStudentInfoCard != null) {
                                    dialogStudentInfoCard.setVisibility(View.GONE);
                                }
                                // Also hide main screen student info card
                                if (studentInfoCard != null) {
                                    studentInfoCard.setVisibility(View.GONE);
                                }
                            });
                            return;
                        }
                        
                        // If section/year matches, now check enrollment in class_students table
                        DatabaseReference classStudentsRef = FirebaseDatabase.getInstance()
                                .getReference("attendance_system/class_students");
                        classStudentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                try {
                                    boolean isEnrolled = false;
                                    
                                    // Check if student is enrolled in the selected class
                                    if (snapshot.exists()) {
                                        Log.d(TAG, "Checking " + snapshot.getChildrenCount() + " enrollment records");
                                        for (DataSnapshot enrollmentSnapshot : snapshot.getChildren()) {
                                            try {
                                                DataSnapshot enrollData = enrollmentSnapshot.child("data");
                                                if (!enrollData.exists()) {
                                                    // Try direct access (some records might not have nested "data")
                                                    Object directClassIdObj = enrollmentSnapshot.child("class_id").getValue();
                                                    Object directStudentIdObj = enrollmentSnapshot.child("student_id").getValue();
                                                    String directStatus = enrollmentSnapshot.child("status").getValue(String.class);
                                                    
                                                    String directClassId = directClassIdObj != null ? String.valueOf(directClassIdObj) : null;
                                                    String directStudentId = directStudentIdObj != null ? String.valueOf(directStudentIdObj) : null;
                                                    
                                                    // Check if class_id and student_id match (handle both string and integer)
                                                    boolean classIdMatch = false;
                                                    boolean studentIdMatch = false;
                                                    
                                                    if (selectedClassId != null && directClassId != null) {
                                                        classIdMatch = selectedClassId.equals(directClassId);
                                                        if (!classIdMatch) {
                                                            try {
                                                                int selectedClassIdInt = Integer.parseInt(selectedClassId);
                                                                int directClassIdInt = Integer.parseInt(directClassId);
                                                                classIdMatch = selectedClassIdInt == directClassIdInt;
                                                            } catch (NumberFormatException e) {
                                                                // Not numeric, keep false
                                                            }
                                                        }
                                                    }
                                                    
                                                    if (finalStudentId != null && directStudentId != null) {
                                                        studentIdMatch = finalStudentId.equals(directStudentId);
                                                        if (!studentIdMatch) {
                                                            try {
                                                                int finalStudentIdInt = Integer.parseInt(finalStudentId);
                                                                int directStudentIdInt = Integer.parseInt(directStudentId);
                                                                studentIdMatch = finalStudentIdInt == directStudentIdInt;
                                                            } catch (NumberFormatException e) {
                                                                // Not numeric, keep false
                                                            }
                                                        }
                                                    }
                                                    
                                                    if (classIdMatch && studentIdMatch && "active".equalsIgnoreCase(directStatus)) {
                                                        isEnrolled = true;
                                                        Log.d(TAG, "✓ Found enrollment (direct structure) - Class ID and Student ID match");
                                                        break;
                                                    }
                                                    continue;
                                                }
                                                
                                                Object enrollClassIdObj = enrollData.child("class_id").getValue();
                                                Object enrollStudentIdObj = enrollData.child("student_id").getValue();
                                                String enrollStatus = enrollData.child("status").getValue(String.class);
                                                
                                                String enrollClassId = enrollClassIdObj != null ? String.valueOf(enrollClassIdObj) : null;
                                                String enrollStudentId = enrollStudentIdObj != null ? String.valueOf(enrollStudentIdObj) : null;
                                                
                                                Log.d(TAG, "Checking enrollment - Class: " + enrollClassId + ", Student: " + enrollStudentId + ", Status: " + enrollStatus);
                                                Log.d(TAG, "Comparing - Selected Class: " + selectedClassId + ", Student ID: " + finalStudentId);
                                                
                                                // Check if class_id and student_id match (handle both string and integer)
                                                boolean classIdMatch = false;
                                                boolean studentIdMatch = false;
                                                
                                                if (selectedClassId != null && enrollClassId != null) {
                                                    // Try exact string match first
                                                    classIdMatch = selectedClassId.equals(enrollClassId);
                                                    // If not match, try numeric comparison
                                                    if (!classIdMatch) {
                                                        try {
                                                            int selectedClassIdInt = Integer.parseInt(selectedClassId);
                                                            int enrollClassIdInt = Integer.parseInt(enrollClassId);
                                                            classIdMatch = selectedClassIdInt == enrollClassIdInt;
                                                        } catch (NumberFormatException e) {
                                                            // Not numeric, keep false
                                                        }
                                                    }
                                                }
                                                
                                                if (finalStudentId != null && enrollStudentId != null) {
                                                    // Try exact string match first
                                                    studentIdMatch = finalStudentId.equals(enrollStudentId);
                                                    // If not match, try numeric comparison
                                                    if (!studentIdMatch) {
                                                        try {
                                                            int finalStudentIdInt = Integer.parseInt(finalStudentId);
                                                            int enrollStudentIdInt = Integer.parseInt(enrollStudentId);
                                                            studentIdMatch = finalStudentIdInt == enrollStudentIdInt;
                                                        } catch (NumberFormatException e) {
                                                            // Not numeric, keep false
                                                        }
                                                    }
                                                }
                                                
                                                if (classIdMatch && studentIdMatch && "active".equalsIgnoreCase(enrollStatus)) {
                                                    isEnrolled = true;
                                                    Log.d(TAG, "✓ Found active enrollment - Class ID and Student ID match");
                                                    break;
                                                }
                                            } catch (Exception e) {
                                                Log.e(TAG, "Error parsing enrollment: " + e.getMessage());
                                            }
                                        }
                                    } else {
                                        Log.d(TAG, "No enrollment records found in Firebase");
                                    }
                                    
                                    Log.d(TAG, "Enrollment check result: " + (isEnrolled ? "ENROLLED" : "NOT ENROLLED"));
                                    
                                    // PRIMARY CHECK: If enrolled in class_students with active status, proceed immediately
                                    if (isEnrolled) {
                                        Log.d(TAG, "✓ Student is enrolled - proceeding with attendance");
                                        runOnUiThread(() -> {
                                            // Reset processing flags before displaying
                                            isProcessingStudent = false;
                                            currentlyProcessingRfidUid = null;
                                            checkExistingAttendanceAndDisplay(finalStudentId, finalStudentData);
                                        });
                                        return;
                                    }
                                    
                                    // SECONDARY CHECK: If not enrolled, reject immediately (like website - strict validation)
                                    // Website rejects if not enrolled, even if section/year matches
                                    Log.w(TAG, "✗ Student is NOT enrolled in the selected class");
                                    runOnUiThread(() -> {
                                        // Reset processing flags - allow re-scanning of this RFID
                                        isProcessingStudent = false;
                                        currentlyProcessingRfidUid = null;
                                        currentRfidUid = null; // Clear current RFID UID
                                        // Don't set lastProcessedRfidUid - allow re-scanning
                                        showAlert("This student is NOT enrolled in the selected class. Only students enrolled in this class can scan their RFID card.", "#FF9800");
                                        showDialogWaitingArea(true);
                                        // Hide dialog student info card
                                        if (dialogStudentInfoCard != null) {
                                            dialogStudentInfoCard.setVisibility(View.GONE);
                                        }
                                        // Also hide main screen student info card
                                        if (studentInfoCard != null) {
                                            studentInfoCard.setVisibility(View.GONE);
                                        }
                                    });
                                    return;
                                } catch (Exception e) {
                                    Log.e(TAG, "Error in enrollment check: " + e.getMessage(), e);
                                    runOnUiThread(() -> {
                                        // Reset processing flags on error - allow re-scanning
                                        isProcessingStudent = false;
                                        currentlyProcessingRfidUid = null;
                                        currentRfidUid = null; // Clear current RFID UID
                                        showAlert("Error checking enrollment: " + e.getMessage(), "#FF9800");
                                        showDialogWaitingArea(true);
                                    });
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                Log.e(TAG, "Error checking enrollment: " + error.getMessage());
                                runOnUiThread(() -> {
                                    // Reset processing flags on error - allow re-scanning
                                    isProcessingStudent = false;
                                    currentlyProcessingRfidUid = null;
                                    currentRfidUid = null; // Clear current RFID UID
                                    showAlert("Error checking student enrollment: " + error.getMessage(), "#FF9800");
                                    showDialogWaitingArea(true);
                                    // Hide dialog student info card
                                    if (dialogStudentInfoCard != null) {
                                        dialogStudentInfoCard.setVisibility(View.GONE);
                                    }
                                    // Also hide main screen student info card
                                    if (studentInfoCard != null) {
                                        studentInfoCard.setVisibility(View.GONE);
                                    }
                                });
                            }
                        });
                        
                        break;
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing student: " + e.getMessage());
                    }
                }
                
                    if (!studentFound) {
                        // Reset processing flags - allow re-scanning of this RFID
                        isProcessingStudent = false;
                        currentlyProcessingRfidUid = null;
                        currentRfidUid = null; // Clear current RFID UID
                        // Don't set lastProcessedRfidUid - allow re-scanning
                        showAlert("Student not found with this RFID card", "#FF9800");
                        showDialogWaitingArea(true);
                        // Hide dialog student info card
                        if (dialogStudentInfoCard != null) {
                            dialogStudentInfoCard.setVisibility(View.GONE);
                        }
                        // Also hide main screen student info card
                        if (studentInfoCard != null) {
                            studentInfoCard.setVisibility(View.GONE);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in onDataChange for students: " + e.getMessage(), e);
                    // Reset processing flags on error - allow re-scanning
                    isProcessingStudent = false;
                    currentlyProcessingRfidUid = null;
                    currentRfidUid = null; // Clear current RFID UID
                    showDialogLoadingProgress(false);
                    showAlert("Error processing student data: " + e.getMessage(), "#FF9800");
                    showDialogWaitingArea(true);
                    // Hide dialog student info card
                    if (dialogStudentInfoCard != null) {
                        dialogStudentInfoCard.setVisibility(View.GONE);
                    }
                    // Also hide main screen student info card
                    if (studentInfoCard != null) {
                        studentInfoCard.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                try {
                    // Reset processing flags on error - allow re-scanning
                    isProcessingStudent = false;
                    currentlyProcessingRfidUid = null;
                    currentRfidUid = null; // Clear current RFID UID
                    showDialogLoadingProgress(false);
                    Log.e(TAG, "Error fetching students: " + error.getMessage());
                    showAlert("Error fetching student data: " + error.getMessage(), "#FF9800");
                    showDialogWaitingArea(true);
                    // Hide dialog student info card
                    if (dialogStudentInfoCard != null) {
                        dialogStudentInfoCard.setVisibility(View.GONE);
                    }
                    // Also hide main screen student info card
                    if (studentInfoCard != null) {
                        studentInfoCard.setVisibility(View.GONE);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in onCancelled: " + e.getMessage(), e);
                }
            }
        });
        } catch (Exception e) {
            Log.e(TAG, "Error in fetchStudentByRfid: " + e.getMessage(), e);
            // Reset processing flags on error
            isProcessingStudent = false;
            currentlyProcessingRfidUid = null;
            showDialogLoadingProgress(false);
            showAlert("Error fetching student: " + e.getMessage(), "#FF9800");
        }
    }
    
    private void validateBySectionYearAndProceed(String studentId, String studentSection, String studentYearLevel, DataSnapshot studentData) {
        // Validate by section/year and proceed with attendance
        DatabaseReference classRef = FirebaseDatabase.getInstance()
                .getReference("attendance_system/classes");
        classRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot classSnapshot) {
                try {
                    boolean sectionYearMatch = false;
                    
                    if (classSnapshot.exists()) {
                        for (DataSnapshot clsSnapshot : classSnapshot.getChildren()) {
                            try {
                                DataSnapshot clsData = clsSnapshot.child("data");
                                if (!clsData.exists()) continue;
                                
                                String classId = clsData.child("id").getValue(String.class);
                                String classSection = clsData.child("section").getValue(String.class);
                                Object classYearLevelObj = clsData.child("year_level").getValue();
                                String classYearLevel = classYearLevelObj != null ? String.valueOf(classYearLevelObj) : null;
                                
                                                                // Normalize for comparison
                                                                String normalizedStudentYear = studentYearLevel != null ? String.valueOf(studentYearLevel).trim() : null;
                                                                String normalizedClassYear = classYearLevel != null ? String.valueOf(classYearLevel).trim() : null;
                                                                
                                                                // Extract numeric values for comparison
                                                                String studentYearNum = "";
                                                                String classYearNum = "";
                                                                if (normalizedStudentYear != null) {
                                                                    studentYearNum = normalizedStudentYear.replaceAll("[^0-9]", "");
                                                                }
                                                                if (normalizedClassYear != null) {
                                                                    classYearNum = normalizedClassYear.replaceAll("[^0-9]", "");
                                                                }
                                                                
                                                                // Check if section matches (case-insensitive, trim whitespace)
                                                                boolean sectionMatch = false;
                                                                if (studentSection != null && classSection != null) {
                                                                    sectionMatch = studentSection.trim().equalsIgnoreCase(classSection.trim());
                                                                }
                                                                
                                                                // Check if year matches (convert to integers for reliable comparison)
                                                                boolean yearMatch = false;
                                                                if (!studentYearNum.isEmpty() && !classYearNum.isEmpty()) {
                                                                    try {
                                                                        int studentYearInt = Integer.parseInt(studentYearNum);
                                                                        int classYearInt = Integer.parseInt(classYearNum);
                                                                        yearMatch = studentYearInt == classYearInt;
                                                                    } catch (NumberFormatException e) {
                                                                        // Fallback to string comparison
                                                                        yearMatch = normalizedStudentYear != null && normalizedClassYear != null && 
                                                                                   normalizedStudentYear.equals(normalizedClassYear);
                                                                    }
                                                                } else if (normalizedStudentYear != null && normalizedClassYear != null) {
                                                                    // Direct string comparison if no numbers found
                                                                    yearMatch = normalizedStudentYear.equals(normalizedClassYear);
                                                                }
                                
                                if (selectedClassId != null && selectedClassId.equals(classId) && sectionMatch && yearMatch) {
                                    sectionYearMatch = true;
                                    Log.d(TAG, "Section/Year validated - proceeding with attendance");
                                    autoEnrollStudent(studentId, selectedClassId, null);
                                    runOnUiThread(() -> {
                                        checkExistingAttendanceAndDisplay(studentId, studentData);
                                    });
                                    return;
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error in section/year validation: " + e.getMessage());
                            }
                        }
                    }
                    
                    if (!sectionYearMatch) {
                        runOnUiThread(() -> {
                            showAlert("Student section/year does not match the selected class", "#FF9800");
                            showDialogWaitingArea(true);
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in section/year validation: " + e.getMessage(), e);
                    runOnUiThread(() -> {
                        showAlert("Error validating student: " + e.getMessage(), "#FF9800");
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error validating section/year: " + error.getMessage());
                runOnUiThread(() -> {
                    showAlert("Error validating student", "#FF9800");
                });
            }
        });
    }
    
    private void autoEnrollStudent(String studentId, String classId, Runnable onComplete) {
        // Auto-enroll student in class_students table in Firebase
        DatabaseReference classStudentsRef = FirebaseDatabase.getInstance()
                .getReference("attendance_system/class_students");
        
        // Generate unique key (using format that matches PHP backup structure)
        String enrollmentKey = "class_students_" + classId + "_" + studentId + "_" + System.currentTimeMillis();
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        
        Map<String, Object> enrollmentData = new HashMap<>();
        enrollmentData.put("class_id", classId);
        enrollmentData.put("student_id", studentId);
        enrollmentData.put("status", "active");
        enrollmentData.put("enrolled_at", timestamp);
        
        // Match the structure used by PHP BackupHooks
        Map<String, Object> backupData = new HashMap<>();
        backupData.put("table", "class_students");
        backupData.put("operation", "insert");
        backupData.put("data", enrollmentData);
        backupData.put("timestamp", timestamp);
        backupData.put("server_time", System.currentTimeMillis() / 1000);
        
        Log.d(TAG, "Auto-enrolling student " + studentId + " in class " + classId);
        
        classStudentsRef.child(enrollmentKey).setValue(backupData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Student auto-enrolled successfully in Firebase");
                    // Wait a moment for Firebase to update, then proceed
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    }, 500); // 500ms delay to ensure Firebase has updated
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error auto-enrolling student: " + e.getMessage(), e);
                    // Still proceed with attendance even if enrollment backup fails
                    runOnUiThread(() -> {
                        Log.w(TAG, "Enrollment backup failed, but proceeding with attendance");
                    });
                    if (onComplete != null) {
                        onComplete.run(); // Still proceed with attendance
                    }
                });
    }
    
    private void checkExistingAttendanceAndDisplay(String studentId, DataSnapshot studentData) {
        // Check existing attendance
        DatabaseReference attendanceRef = FirebaseDatabase.getInstance()
                .getReference("attendance_system/attendance");
        attendanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String existingStatus = null;
                for (DataSnapshot attSnapshot : snapshot.getChildren()) {
                    try {
                        DataSnapshot attData = attSnapshot.child("data");
                        if (!attData.exists()) continue;
                        
                        // Handle both Long and String types for class_id and student_id
                        Object attClassIdObj = attData.child("class_id").getValue();
                        Object attStudentIdObj = attData.child("student_id").getValue();
                        String attDate = attData.child("date").getValue(String.class);
                        
                        String attClassId = attClassIdObj != null ? String.valueOf(attClassIdObj) : null;
                        String attStudentId = attStudentIdObj != null ? String.valueOf(attStudentIdObj) : null;
                        
                        if (selectedClassId != null && attClassId != null && 
                            studentId != null && attStudentId != null &&
                            selectedClassId.equals(attClassId) && 
                            studentId.equals(attStudentId) && 
                            attendanceDate != null && attendanceDate.equals(attDate)) {
                            existingStatus = attData.child("status").getValue(String.class);
                            break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing attendance record: " + e.getMessage());
                        continue;
                    }
                }
                
                // Create final copy for lambda expression
                final String finalExistingStatus = existingStatus;
                
                // Determine attendance status based on schedule
                // Ensure we're on UI thread for Firebase callbacks
                runOnUiThread(() -> {
                    determineAttendanceStatusAndDisplay(studentId, studentData, finalExistingStatus);
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error checking existing attendance: " + error.getMessage());
                // Continue anyway - ensure on UI thread
                runOnUiThread(() -> {
                    determineAttendanceStatusAndDisplay(studentId, studentData, null);
                });
            }
        });
    }
    
    private void determineAttendanceStatusAndDisplay(String studentId, DataSnapshot studentData, String existingStatus) {
        // STEP 4: Check if there's an ONGOING schedule for this class today (like PHP backend)
        // Get current day of week
        SimpleDateFormat sdfDay = new SimpleDateFormat("EEEE", Locale.US);
        String currentDay = sdfDay.format(new Date());
        
        // Get current time in 24-hour format (HH:mm:ss)
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String currentTime = timeFormat.format(new Date());
        
        Log.d(TAG, "=== RFID Attendance Check ===");
        Log.d(TAG, "Current Day: " + currentDay);
        Log.d(TAG, "Current Time: " + currentTime);
        Log.d(TAG, "Class ID: " + selectedClassId);
        Log.d(TAG, "Date: " + attendanceDate);
        
        DatabaseReference timetableRef = FirebaseDatabase.getInstance()
                .getReference("attendance_system/timetable");
        timetableRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    // Get ALL timetables for this class and day (multiple sessions possible)
                    List<TimetableSchedule> schedules = new ArrayList<>();
                    
                    Log.d(TAG, "Total timetable entries in Firebase: " + snapshot.getChildrenCount());
                    
                    for (DataSnapshot ttSnapshot : snapshot.getChildren()) {
                        // Try nested "data" structure first (Firebase backup format)
                        DataSnapshot ttData = ttSnapshot.child("data");
                        if (!ttData.exists()) {
                            // If no nested "data", use the snapshot directly
                            ttData = ttSnapshot;
                        }
                        
                        if (!ttData.exists()) continue;
                        
                        // Handle class_id comparison (both string and number types)
                        Object ttClassIdObj = ttData.child("class_id").getValue();
                        String ttClassId = ttClassIdObj != null ? String.valueOf(ttClassIdObj) : null;
                        String dayOfWeek = ttData.child("day_of_week").getValue(String.class);
                        
                        Log.d(TAG, "Checking timetable - Class ID: " + ttClassId + " (selected: " + selectedClassId + "), Day: " + dayOfWeek + " (current: " + currentDay + ")");
                        
                        // Compare class IDs (handle both string and numeric)
                        boolean classIdMatch = false;
                        if (selectedClassId != null && ttClassId != null) {
                            classIdMatch = selectedClassId.equals(ttClassId);
                            // Try numeric comparison if string match fails
                            if (!classIdMatch) {
                                try {
                                    int selectedId = Integer.parseInt(selectedClassId);
                                    int ttId = Integer.parseInt(ttClassId);
                                    classIdMatch = (selectedId == ttId);
                                } catch (NumberFormatException e) {
                                    // Not numeric, keep false
                                }
                            }
                        }
                        
                        boolean dayMatch = (dayOfWeek != null && currentDay.equalsIgnoreCase(dayOfWeek));
                        
                        if (classIdMatch && dayMatch) {
                            String startTime = ttData.child("start_time").getValue(String.class);
                            String endTime = ttData.child("end_time").getValue(String.class);
                            
                            Log.d(TAG, "✓ Schedule matched! Start: " + startTime + ", End: " + endTime);
                            
                            if (startTime != null && endTime != null) {
                                TimetableSchedule schedule = new TimetableSchedule();
                                schedule.startTime = startTime;
                                schedule.endTime = endTime;
                                schedules.add(schedule);
                                Log.d(TAG, "Added schedule - Start: " + startTime + ", End: " + endTime);
                            }
                        }
                    }
                    
                    Log.d(TAG, "Total schedules found for today: " + schedules.size());
                    
                    // Parse current time
                    int currentMinutes = parseTimeToMinutes(currentTime);
                    if (currentMinutes == -1) {
                        Log.e(TAG, "Error parsing current time: " + currentTime);
                        runOnUiThread(() -> {
                            isProcessingStudent = false;
                            currentlyProcessingRfidUid = null;
                            showAlert("Error parsing current time.", "#FF9800");
                            showDialogWaitingArea(true);
                            if (dialogStudentInfoCard != null) {
                                dialogStudentInfoCard.setVisibility(View.GONE);
                            }
                        });
                        return;
                    }
                    
                    // Check if current time falls within ANY ONGOING schedule for this day
                    TimetableSchedule matchedSchedule = null;
                    boolean isTooEarly = true;
                    Integer earliestStartTime = null;
                    Integer latestEndTime = null;
                    
                    for (TimetableSchedule schedule : schedules) {
                        int startMinutes = parseTimeToMinutes(schedule.startTime);
                        int endMinutes = parseTimeToMinutes(schedule.endTime);
                        
                        if (startMinutes == -1 || endMinutes == -1) {
                            Log.w(TAG, "Error parsing schedule times - Start: " + schedule.startTime + ", End: " + schedule.endTime);
                            continue; // Skip invalid schedule
                        }
                        
                        // Track earliest start and latest end for error messages
                        if (earliestStartTime == null || startMinutes < earliestStartTime) {
                            earliestStartTime = startMinutes;
                        }
                        if (latestEndTime == null || endMinutes > latestEndTime) {
                            latestEndTime = endMinutes;
                        }
                        
                        Log.d(TAG, "Schedule - Start: " + schedule.startTime + " (" + startMinutes + " min), End: " + schedule.endTime + " (" + endMinutes + " min)");
                        
                        // Check if current time is within this schedule's window (ONGOING schedule)
                        if (currentMinutes >= startMinutes && currentMinutes <= endMinutes) {
                            matchedSchedule = schedule;
                            isTooEarly = false;
                            Log.d(TAG, "MATCHED Ongoing Schedule - Start: " + schedule.startTime + ", End: " + schedule.endTime);
                            break; // Found an ongoing schedule
                        }
                        
                        // Check if we're before any schedule starts
                        if (currentMinutes < startMinutes) {
                            isTooEarly = true;
                        } else {
                            isTooEarly = false; // We're past at least one schedule start
                        }
                    }
                    
                    // If no ongoing schedule found, show error (like PHP backend)
                    if (matchedSchedule == null) {
                        if (schedules.isEmpty()) {
                            // No schedule found for today - reject attendance (must have schedule)
                            Log.w(TAG, "❌ No schedule found for today - Class ID: " + selectedClassId + ", Day: " + currentDay);
                            Log.w(TAG, "Please check if schedule exists in Firebase for this class and day");
                            runOnUiThread(() -> {
                                isProcessingStudent = false;
                                currentlyProcessingRfidUid = null;
                                showAlert("No schedule found for today. Attendance can only be recorded during scheduled class time.", "#FF9800");
                                showDialogWaitingArea(true);
                                if (dialogStudentInfoCard != null) {
                                    dialogStudentInfoCard.setVisibility(View.GONE);
                                }
                            });
                            return;
                        } else {
                            Log.w(TAG, "⚠️ Schedules exist but no ongoing schedule found");
                            Log.w(TAG, "Current time: " + currentTime + " (" + currentMinutes + " min)");
                            for (TimetableSchedule s : schedules) {
                                int sMin = parseTimeToMinutes(s.startTime);
                                int eMin = parseTimeToMinutes(s.endTime);
                                Log.w(TAG, "  - Schedule: " + s.startTime + " (" + sMin + " min) to " + s.endTime + " (" + eMin + " min)");
                            }
                            // Schedules exist but no ongoing schedule (current time not within any schedule window)
                            String errorMessage;
                            if (isTooEarly && earliestStartTime != null) {
                                String earliestStart = formatMinutesToTime(earliestStartTime);
                                errorMessage = "No ongoing schedule. Class starts at " + earliestStart + ".";
                            } else if (latestEndTime != null) {
                                String latestEnd = formatMinutesToTime(latestEndTime);
                                errorMessage = "No ongoing schedule. Class ended at " + latestEnd + ".";
                            } else {
                                errorMessage = "No ongoing schedule. Please check the class schedule.";
                            }
                            
                            Log.w(TAG, errorMessage);
                            runOnUiThread(() -> {
                                isProcessingStudent = false;
                                currentlyProcessingRfidUid = null;
                                showAlert(errorMessage, "#FF9800");
                                showDialogWaitingArea(true);
                                if (dialogStudentInfoCard != null) {
                                    dialogStudentInfoCard.setVisibility(View.GONE);
                                }
                            });
                            return;
                        }
                    }
                    
                    // Determine attendance status based on current time vs matched ongoing schedule
                    AttendanceStatusInfo statusInfo = determineAttendanceStatus(
                            matchedSchedule.startTime, matchedSchedule.endTime, currentTime);
                    
                    // VALIDATION: Only allow attendance if status is 'present' or 'late'
                    // Reject 'too_early', 'too_late', or 'class_ended' statuses (like PHP backend)
                    if (statusInfo.status.equals("too_early") || 
                        statusInfo.status.equals("too_late") || 
                        statusInfo.status.equals("class_ended")) {
                        Log.w(TAG, "Attendance rejected: " + statusInfo.message);
                        runOnUiThread(() -> {
                            isProcessingStudent = false;
                            currentlyProcessingRfidUid = null;
                            showAlert(statusInfo.message, "#FF9800");
                            showDialogWaitingArea(true);
                            if (dialogStudentInfoCard != null) {
                                dialogStudentInfoCard.setVisibility(View.GONE);
                            }
                        });
                        return;
                    }
                    
                    // Use existing status if available
                    boolean isAlreadyRecorded = (existingStatus != null);
                    String attendanceStatus = statusInfo.status;
                    String statusMessage = statusInfo.message;
                    
                    if (existingStatus != null) {
                        attendanceStatus = existingStatus;
                        statusMessage = "Already recorded: " + existingStatus;
                    }
                    
                    currentStudentId = studentId;
                    currentAttendanceStatus = attendanceStatus;
                    
                    // Create final copies for lambda expression
                    final boolean finalIsAlreadyRecorded = isAlreadyRecorded;
                    final String finalAttendanceStatus = attendanceStatus;
                    final String finalStatusMessage = statusMessage;
                    
                    // Ensure UI updates are on main thread
                    runOnUiThread(() -> {
                        Log.d(TAG, "Displaying student info - isAlreadyRecorded: " + finalIsAlreadyRecorded + ", status: " + finalAttendanceStatus);
                        
                        // Show alert message if attendance is already recorded (before displaying student info)
                        if (finalIsAlreadyRecorded) {
                            String alertMessage = "Attendance already recorded for this student today.";
                            showAlert(alertMessage, "#FF9800");
                            Log.d(TAG, "Showing alert for already recorded attendance");
                        } else {
                            hideAlert();
                        }
                        
                        // Convert DataSnapshot to JSON-like structure for display (on UI thread)
                        displayStudentInfoFromSnapshot(studentData, finalAttendanceStatus, finalStatusMessage, finalIsAlreadyRecorded);
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error in determineAttendanceStatusAndDisplay: " + e.getMessage(), e);
                    runOnUiThread(() -> {
                        isProcessingStudent = false;
                        currentlyProcessingRfidUid = null;
                        showAlert("Error processing schedule: " + e.getMessage(), "#FF9800");
                        showDialogWaitingArea(true);
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching timetable: " + error.getMessage());
                runOnUiThread(() -> {
                    isProcessingStudent = false;
                    currentlyProcessingRfidUid = null;
                    showAlert("Error fetching schedule: " + error.getMessage(), "#FF9800");
                    showDialogWaitingArea(true);
                    if (dialogStudentInfoCard != null) {
                        dialogStudentInfoCard.setVisibility(View.GONE);
                    }
                });
            }
        });
    }
    
    // Helper class for timetable schedule
    private static class TimetableSchedule {
        String startTime;
        String endTime;
    }
    
    // Helper class for attendance status info
    private static class AttendanceStatusInfo {
        String status;
        String message;
        
        AttendanceStatusInfo(String status, String message) {
            this.status = status;
            this.message = message;
        }
    }
    
    // Parse time string (supports both 24-hour format HH:mm:ss/HH:mm and 12-hour format like "1:30 PM")
    private int parseTimeToMinutes(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return -1;
        }
        
        try {
            // Trim whitespace
            timeStr = timeStr.trim();
            
            // Check if it's 12-hour format (contains AM/PM)
            boolean is12Hour = timeStr.toUpperCase().contains("AM") || timeStr.toUpperCase().contains("PM");
            
            if (is12Hour) {
                // Parse 12-hour format (e.g., "1:30 PM", "11:45 AM")
                String upperTime = timeStr.toUpperCase();
                boolean isPM = upperTime.contains("PM");
                
                // Remove AM/PM
                String timeOnly = upperTime.replace("AM", "").replace("PM", "").trim();
                
                String[] parts = timeOnly.split(":");
                if (parts.length < 2) {
                    Log.e(TAG, "Invalid 12-hour time format: " + timeStr);
                    return -1;
                }
                
                int hours = Integer.parseInt(parts[0].trim());
                int minutes = Integer.parseInt(parts[1].trim());
                
                // Convert to 24-hour format
                if (isPM && hours != 12) {
                    hours += 12;
                } else if (!isPM && hours == 12) {
                    hours = 0;
                }
                
                return hours * 60 + minutes;
            } else {
                // Parse 24-hour format (e.g., "13:30:00", "13:30")
                String[] parts = timeStr.split(":");
                if (parts.length < 2) {
                    Log.e(TAG, "Invalid 24-hour time format: " + timeStr);
                    return -1;
                }
                
                int hours = Integer.parseInt(parts[0].trim());
                int minutes = Integer.parseInt(parts[1].trim());
                
                return hours * 60 + minutes;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing time: " + timeStr, e);
            return -1;
        }
    }
    
    // Format minutes since midnight to time string (12-hour format with AM/PM)
    private String formatMinutesToTime(int minutes) {
        int hours = minutes / 60;
        int mins = minutes % 60;
        
        // Convert to 12-hour format with AM/PM
        String period = (hours >= 12) ? "PM" : "AM";
        int displayHours = hours;
        if (hours > 12) {
            displayHours = hours - 12;
        } else if (hours == 0) {
            displayHours = 12;
        }
        
        return String.format(Locale.getDefault(), "%d:%02d %s", displayHours, mins, period);
    }
    
    // Helper function to determine attendance status based on schedule (like PHP backend)
    private AttendanceStatusInfo determineAttendanceStatus(String startTime, String endTime, String currentTime) {
        try {
            int startMinutes = parseTimeToMinutes(startTime);
            int endMinutes = parseTimeToMinutes(endTime);
            int currentMinutes = parseTimeToMinutes(currentTime);
            
            if (startMinutes == -1 || endMinutes == -1 || currentMinutes == -1) {
                Log.e(TAG, "Error parsing times - Start: " + startTime + ", End: " + endTime + ", Current: " + currentTime);
                return new AttendanceStatusInfo("error", "Error parsing schedule times.");
            }
            
            Log.d(TAG, "Time Comparison - Start: " + startTime + " (" + startMinutes + " min), End: " + endTime + " (" + endMinutes + " min), Current: " + currentTime + " (" + currentMinutes + " min)");
            
            // Calculate time difference from start
            int minutesDiff = currentMinutes - startMinutes;
            
            // VALIDATION: Check if current time is within the scheduled time window
            if (currentMinutes < startMinutes) {
                // Tapped before schedule - too early
                String startTimeFormatted = formatMinutesToTime(startMinutes);
                
                Log.d(TAG, "TOO EARLY - Current: " + currentMinutes + " min < Start: " + startMinutes + " min");
                return new AttendanceStatusInfo("too_early", "Too early. Schedule has not started yet. Class starts at " + startTimeFormatted + ".");
            } else if (currentMinutes > endMinutes) {
                // Tapped after schedule - class has ended
                String endTimeFormatted = formatMinutesToTime(endMinutes);
                
                Log.d(TAG, "CLASS ENDED - Current: " + currentMinutes + " min > End: " + endMinutes + " min");
                return new AttendanceStatusInfo("class_ended", "Class has already ended. Class ended at " + endTimeFormatted + ".");
            }
            
            // STATUS DETERMINATION (within scheduled time window):
            // 0 to 15 minutes after start → Present
            // 16 to 30 minutes after start → Late
            // After 30 minutes but before end_time → Too late (attendance window closed)
            
            if (minutesDiff >= 0 && minutesDiff <= 15) {
                // Within 15 minutes grace period → Present
                Log.d(TAG, "PRESENT - Minutes diff: " + minutesDiff);
                return new AttendanceStatusInfo("present", "On time - Marked as Present");
            } else if (minutesDiff >= 16 && minutesDiff <= 30) {
                // 16-30 minutes late → Late
                Log.d(TAG, "LATE - Minutes diff: " + minutesDiff);
                return new AttendanceStatusInfo("late", "Late arrival - Marked as Late");
            } else {
                // More than 30 minutes late but still within class time → Too late to scan
                Log.d(TAG, "TOO LATE - Minutes diff: " + minutesDiff);
                return new AttendanceStatusInfo("too_late", "Too late. Attendance window has closed. You can only scan within 30 minutes after class starts.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in determineAttendanceStatus: " + e.getMessage(), e);
            return new AttendanceStatusInfo("error", "Error determining attendance status.");
        }
    }
    
    private void displayStudentInfoFromSnapshot(DataSnapshot studentData, String status, String statusMessage, boolean isAlreadyRecorded) {
        try {
            // Build full name
            String firstName = studentData.child("first_name").getValue(String.class);
            String middleName = studentData.child("middle_name").getValue(String.class);
            String lastName = studentData.child("last_name").getValue(String.class);
            String suffixName = studentData.child("suffix_name").getValue(String.class);
            
            StringBuilder fullNameBuilder = new StringBuilder();
            if (firstName != null && !firstName.isEmpty()) fullNameBuilder.append(firstName);
            if (middleName != null && !middleName.isEmpty()) fullNameBuilder.append(" ").append(middleName);
            if (lastName != null && !lastName.isEmpty()) fullNameBuilder.append(" ").append(lastName);
            if (suffixName != null && !suffixName.isEmpty()) fullNameBuilder.append(" ").append(suffixName);
            
            // Update dialog views only (inside modal)
            if (dialogStudentName != null) {
                dialogStudentName.setText(fullNameBuilder.toString().trim());
            }
            if (dialogStudentId != null) {
                dialogStudentId.setText(studentData.child("student_id").getValue(String.class));
            }
            if (dialogStudentCourse != null) {
                dialogStudentCourse.setText(studentData.child("course").getValue(String.class));
            }
            
            String yearLevel = studentData.child("year_level").getValue(String.class);
            String section = studentData.child("section").getValue(String.class);
            if (dialogStudentYearSection != null) {
                dialogStudentYearSection.setText("Year " + yearLevel + " - " + section);
            }
            
            if (dialogStudentRfidUid != null) {
                dialogStudentRfidUid.setText(studentData.child("rfid_uid").getValue(String.class));
            }

            // Set attendance status
            int statusColor;
            String statusText;
            if (isAlreadyRecorded && statusMessage != null && statusMessage.startsWith("Already recorded:")) {
                // Format the already recorded message nicely
                String recordedStatus = statusMessage.replace("Already recorded: ", "");
                switch (recordedStatus.toLowerCase()) {
                    case "present":
                        statusColor = 0xFF4CAF50;
                        statusText = "Already Recorded - Present";
                        break;
                    case "late":
                        statusColor = 0xFFFF9800;
                        statusText = "Already Recorded - Late";
                        break;
                    case "absent":
                        statusColor = 0xFFF44336;
                        statusText = "Already Recorded - Absent";
                        break;
                    default:
                        statusColor = 0xFF2196F3;
                        statusText = "Already Recorded - " + recordedStatus;
                        break;
                }
            } else {
                switch (status) {
                    case "present":
                        statusColor = 0xFF4CAF50;
                        statusText = "On Time - Marked as Present";
                        break;
                    case "late":
                        statusColor = 0xFFFF9800;
                        statusText = "Late Arrival - Marked as Late";
                        break;
                    case "too_early":
                        statusColor = 0xFF2196F3;
                        statusText = "Too Early - Schedule not started";
                        break;
                    case "too_late":
                        statusColor = 0xFFF44336;
                        statusText = "Too Late - Attendance window closed";
                        break;
                    case "class_ended":
                        statusColor = 0xFFF44336;
                        statusText = "Class Ended - Attendance recording closed";
                        break;
                    default:
                        statusColor = 0xFF2196F3;
                        statusText = statusMessage != null && !statusMessage.isEmpty() ? statusMessage : "Manual Selection Required";
                        break;
                }
            }
            
            // Update dialog attendance status
            if (dialogAttendanceStatus != null) {
                dialogAttendanceStatus.setText("Status: " + statusText);
                dialogAttendanceStatus.setBackgroundColor(statusColor);
                dialogAttendanceStatus.setTextColor(0xFFFFFFFF);
            }
            
            // Load profile picture - update dialog views only
            String profilePictureBase64 = studentData.child("profile_picture").getValue(String.class);
            if (profilePictureBase64 != null && !profilePictureBase64.isEmpty()) {
                Bitmap bitmap = decodeBase64ToBitmap(profilePictureBase64);
                if (bitmap != null) {
                    Bitmap circularBitmap = getCircularBitmap(bitmap);
                    // Update dialog photo
                    if (dialogStudentPhoto != null) {
                        dialogStudentPhoto.setImageBitmap(circularBitmap);
                        dialogStudentPhoto.setVisibility(View.VISIBLE);
                    }
                    if (dialogStudentPhotoPlaceholder != null) {
                        dialogStudentPhotoPlaceholder.setVisibility(View.GONE);
                    }
                } else {
                    // Update dialog photo placeholder
                    if (dialogStudentPhoto != null) {
                        dialogStudentPhoto.setVisibility(View.GONE);
                    }
                    if (dialogStudentPhotoPlaceholder != null) {
                        dialogStudentPhotoPlaceholder.setVisibility(View.VISIBLE);
                    }
                }
            } else {
                // Update dialog photo placeholder
                if (dialogStudentPhoto != null) {
                    dialogStudentPhoto.setVisibility(View.GONE);
                }
                if (dialogStudentPhotoPlaceholder != null) {
                    dialogStudentPhotoPlaceholder.setVisibility(View.VISIBLE);
                }
            }

            // Show/hide buttons based on whether attendance is already recorded
            // Update dialog buttons only
            if (isAlreadyRecorded) {
                // If already recorded, hide Accept button completely, show only Decline
                if (dialogAcceptBtn != null) {
                    dialogAcceptBtn.setVisibility(View.GONE);
                }
                if (dialogDeclineBtn != null) {
                    dialogDeclineBtn.setVisibility(View.VISIBLE);
                }
            } else {
                // If not recorded yet, show both buttons
                if (dialogAcceptBtn != null) {
                    dialogAcceptBtn.setVisibility(View.VISIBLE);
                }
                if (dialogDeclineBtn != null) {
                    dialogDeclineBtn.setVisibility(View.VISIBLE);
                }
            }
            
            // Reset processing flags when displaying student info
            isProcessingStudent = false;
            currentlyProcessingRfidUid = null;
            
            // Only mark as processed AFTER successful validation and display
            // This allows re-scanning if validation fails
            // Note: We don't set lastProcessedRfidUid here because we want to allow
            // the same student to scan again if they decline or if there's an error
            
            // Note: Alert message is now shown in determineAttendanceStatusAndDisplay before calling this method
            // This ensures the alert is displayed on the UI thread
            
            // Show student info card in dialog only (hide waiting area)
            showDialogWaitingArea(false);
            if (dialogStudentInfoCard != null) {
                dialogStudentInfoCard.setVisibility(View.VISIBLE);
            }
            // Ensure main screen student info card stays hidden
            if (studentInfoCard != null) {
                studentInfoCard.setVisibility(View.GONE);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error displaying student info: " + e.getMessage());
            showAlert("Error displaying student information", "#FF9800");
        }
    }


    private void acceptAttendance() {
        try {
            if (currentStudentId == null || currentStudentId.isEmpty()) {
                showAlert("No student selected", "#FF9800");
                return;
            }

            if (selectedClassId == null || selectedClassId.isEmpty()) {
                showAlert("No class selected", "#FF9800");
                return;
            }

            if (teacherId == null || teacherId.isEmpty()) {
                showAlert("Teacher ID not found", "#FF9800");
                return;
            }

            // VALIDATION: Only allow attendance if status is 'present' or 'late'
            // Reject 'too_early', 'too_late', or 'class_ended' statuses (like PHP backend)
            String statusValue = currentAttendanceStatus;
            if (statusValue == null || statusValue.isEmpty() || 
                statusValue.equals("too_early") || 
                statusValue.equals("too_late") || 
                statusValue.equals("class_ended") ||
                statusValue.equals("manual") ||
                statusValue.equals("error")) {
                showAlert("Cannot record attendance. Please check the schedule and try again.", "#FF9800");
                showDialogLoadingProgress(false);
                // Re-enable dialog buttons
                if (dialogAcceptBtn != null) {
                    dialogAcceptBtn.setEnabled(true);
                }
                if (dialogDeclineBtn != null) {
                    dialogDeclineBtn.setEnabled(true);
                }
                return;
            }
            
            final String finalStatus = statusValue;

            showDialogLoadingProgress(true);
            // Disable dialog buttons
            if (dialogAcceptBtn != null) {
                dialogAcceptBtn.setEnabled(false);
            }
            if (dialogDeclineBtn != null) {
                dialogDeclineBtn.setEnabled(false);
            }
            // Also disable main screen buttons (for backward compatibility)
            if (acceptBtn != null) {
                acceptBtn.setEnabled(false);
            }
            if (declineBtn != null) {
                declineBtn.setEnabled(false);
            }

        // Check if attendance already exists
        DatabaseReference attendanceRef = FirebaseDatabase.getInstance()
                .getReference("attendance_system/attendance");
        attendanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    String existingKey = null;
                    if (snapshot.exists()) {
                        for (DataSnapshot attSnapshot : snapshot.getChildren()) {
                            try {
                                DataSnapshot attData = attSnapshot.child("data");
                                if (!attData.exists()) continue;
                                
                                // Handle both Long and String types
                                Object attClassIdObj = attData.child("class_id").getValue();
                                Object attStudentIdObj = attData.child("student_id").getValue();
                                String attDate = attData.child("date").getValue(String.class);
                                
                                String attClassId = attClassIdObj != null ? String.valueOf(attClassIdObj) : null;
                                String attStudentId = attStudentIdObj != null ? String.valueOf(attStudentIdObj) : null;
                                
                                if (selectedClassId != null && attClassId != null && 
                                    currentStudentId != null && attStudentId != null &&
                                    attendanceDate != null && attDate != null &&
                                    selectedClassId.equals(attClassId) && 
                                    currentStudentId.equals(attStudentId) && 
                                    attendanceDate.equals(attDate)) {
                                    existingKey = attSnapshot.getKey();
                                    break;
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing attendance record: " + e.getMessage());
                                continue;
                            }
                        }
                    }
                
                // Prepare attendance data
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                String timestamp = sdf.format(new Date());
                
                Map<String, Object> attendanceData = new HashMap<>();
                attendanceData.put("class_id", selectedClassId);
                attendanceData.put("student_id", currentStudentId);
                attendanceData.put("date", attendanceDate);
                attendanceData.put("status", finalStatus);
                attendanceData.put("recorded_by", teacherId);
                attendanceData.put("created_at", timestamp);
                
                // Make final copies for inner class
                final String finalExistingKey = existingKey;
                final Map<String, Object> finalAttendanceData = new HashMap<>(attendanceData);
                
                // If updating, get the existing ID
                if (finalExistingKey != null) {
                    attendanceRef.child(finalExistingKey).child("data").addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            String existingId = snapshot.child("id").getValue(String.class);
                            if (existingId != null) {
                                finalAttendanceData.put("id", existingId);
                            }
                            saveAttendanceToFirebase(finalExistingKey, finalAttendanceData, true);
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            saveAttendanceToFirebase(finalExistingKey, finalAttendanceData, true);
                        }
                    });
                } else {
                    // Generate CONSISTENT key for attendance (class_id + student_id + date)
                    // This ensures updates replace existing records instead of creating duplicates
                    String newKey = "attendance_" + selectedClassId + "_" + currentStudentId + "_" + attendanceDate.replace("-", "");
                    // Generate a temporary ID (will be replaced by server when syncing)
                    finalAttendanceData.put("id", "temp_" + System.currentTimeMillis());
                    saveAttendanceToFirebase(newKey, finalAttendanceData, false);
                }
                } catch (Exception e) {
                    Log.e(TAG, "Error in onDataChange for attendance check: " + e.getMessage(), e);
                    runOnUiThread(() -> {
                                showDialogLoadingProgress(false);
                        if (acceptBtn != null) {
                            acceptBtn.setEnabled(true);
                        }
                        if (declineBtn != null) {
                            declineBtn.setEnabled(true);
                        }
                        showAlert("Error processing attendance: " + e.getMessage(), "#FF9800");
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error checking existing attendance: " + error.getMessage());
                runOnUiThread(() -> {
                    showDialogLoadingProgress(false);
                    // Re-enable dialog buttons
                    if (dialogAcceptBtn != null) {
                        dialogAcceptBtn.setEnabled(true);
                    }
                    if (dialogDeclineBtn != null) {
                        dialogDeclineBtn.setEnabled(true);
                    }
                    // Also re-enable main screen buttons
                    if (acceptBtn != null) {
                        acceptBtn.setEnabled(true);
                    }
                    if (declineBtn != null) {
                        declineBtn.setEnabled(true);
                    }
                    showAlert("Error checking attendance: " + error.getMessage(), "#FF9800");
                });
            }
        });
        } catch (Exception e) {
            Log.e(TAG, "Error in acceptAttendance: " + e.getMessage(), e);
            showDialogLoadingProgress(false);
            // Re-enable dialog buttons
            if (dialogAcceptBtn != null) {
                dialogAcceptBtn.setEnabled(true);
            }
            if (dialogDeclineBtn != null) {
                dialogDeclineBtn.setEnabled(true);
            }
            // Also re-enable main screen buttons
            if (acceptBtn != null) {
                acceptBtn.setEnabled(true);
            }
            if (declineBtn != null) {
                declineBtn.setEnabled(true);
            }
            showAlert("Error accepting attendance: " + e.getMessage(), "#FF9800");
        }
    }
    
    private void saveAttendanceToFirebase(String key, Map<String, Object> attendanceData, boolean isUpdate) {
        // Save directly to Firebase - real-time sync for both website and app
        DatabaseReference attendanceRef = FirebaseDatabase.getInstance()
                .getReference("attendance_system/attendance");
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        
        Map<String, Object> backupData = new HashMap<>();
        backupData.put("table", "attendance");
        backupData.put("operation", isUpdate ? "update" : "insert");
        backupData.put("data", attendanceData);
        backupData.put("timestamp", timestamp);
        backupData.put("server_time", System.currentTimeMillis() / 1000);
        
        DatabaseReference recordRef = attendanceRef.child(key);
        recordRef.setValue(backupData)
                .addOnSuccessListener(aVoid -> {
                    runOnUiThread(() -> {
                        try {
                                showDialogLoadingProgress(false);
                            // Re-enable dialog buttons
                            if (dialogAcceptBtn != null) {
                                dialogAcceptBtn.setEnabled(true);
                            }
                            if (dialogDeclineBtn != null) {
                                dialogDeclineBtn.setEnabled(true);
                            }
                            // Also re-enable main screen buttons
                            if (acceptBtn != null) {
                                acceptBtn.setEnabled(true);
                            }
                            if (declineBtn != null) {
                                declineBtn.setEnabled(true);
                            }
                            showAlert("Attendance recorded successfully!", "#4CAF50");
                            Log.d(TAG, "Attendance saved to Firebase: " + key);
                            
                            // Reset UI after 2 seconds
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                try {
                                    // Hide dialog student info card
                                    if (dialogStudentInfoCard != null) {
                                        dialogStudentInfoCard.setVisibility(View.GONE);
                                    }
                                    // Also hide main screen student info card
                                    if (studentInfoCard != null) {
                                        studentInfoCard.setVisibility(View.GONE);
                                    }
                                    showDialogWaitingArea(true);
                                    // Clear current student data
                                    currentStudentId = null;
                                    currentAttendanceStatus = null;
                                    // Mark this RFID as processed only after successful attendance
                                    if (currentRfidUid != null) {
                                        lastProcessedRfidUid = currentRfidUid;
                                        Log.d(TAG, "Marked RFID as processed after successful attendance: " + currentRfidUid);
                                    }
                                    currentRfidUid = null;
                                    hideAlert();
                                    
                                    // Refresh students list to update status
                                    if (selectedClassId != null && !selectedClassId.isEmpty()) {
                                        ClassItem selectedClass = null;
                                        synchronized (teacherClasses) {
                                            for (ClassItem cls : teacherClasses) {
                                                if (cls.id.equals(selectedClassId)) {
                                                    selectedClass = cls;
                                                    break;
                                                }
                                            }
                                        }
                                        if (selectedClass != null) {
                                            fetchStudentsForClass(selectedClass.section, selectedClass.yearLevel);
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error resetting UI: " + e.getMessage());
                                }
                            }, 2000);
                        } catch (Exception e) {
                            Log.e(TAG, "Error in success callback: " + e.getMessage(), e);
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    runOnUiThread(() -> {
                        try {
                                showDialogLoadingProgress(false);
                            // Re-enable dialog buttons
                            if (dialogAcceptBtn != null) {
                                dialogAcceptBtn.setEnabled(true);
                            }
                            if (dialogDeclineBtn != null) {
                                dialogDeclineBtn.setEnabled(true);
                            }
                            // Also re-enable main screen buttons
                            if (acceptBtn != null) {
                                acceptBtn.setEnabled(true);
                            }
                            if (declineBtn != null) {
                                declineBtn.setEnabled(true);
                            }
                            Log.e(TAG, "Error saving attendance to Firebase: " + e.getMessage(), e);
                            showAlert("Error saving attendance: " + e.getMessage(), "#FF9800");
                        } catch (Exception ex) {
                            Log.e(TAG, "Error in failure callback: " + ex.getMessage(), ex);
                        }
                    });
                });
    }

    private void declineAttendance() {
        try {
            // Reset processing flags - allow re-scanning
            isProcessingStudent = false;
            currentlyProcessingRfidUid = null;
            currentRfidUid = null; // Clear current RFID UID - don't mark as processed
            // Also clear lastProcessedRfidUid to allow re-scanning
            lastProcessedRfidUid = null;
            
            // Hide dialog student info card
            if (dialogStudentInfoCard != null) {
                dialogStudentInfoCard.setVisibility(View.GONE);
            }
            // Also hide main screen student info card
            if (studentInfoCard != null) {
                studentInfoCard.setVisibility(View.GONE);
            }
            // Show waiting area - ready for next scan
            showDialogWaitingArea(true);
            hideDialogAlert();
            currentStudentId = null;
            currentAttendanceStatus = null;
            
            Log.d(TAG, "Attendance declined - ready for next scan");
        } catch (Exception e) {
            Log.e(TAG, "Error in declineAttendance: " + e.getMessage(), e);
            // Reset processing flags on error
            isProcessingStudent = false;
            currentlyProcessingRfidUid = null;
            currentRfidUid = null;
            lastProcessedRfidUid = null;
            showAlert("Error declining attendance: " + e.getMessage(), "#FF9800");
        }
    }
    
    private void fetchStudentsForClass(String section, String yearLevel) {
        if (selectedClassId == null || selectedClassId.isEmpty()) {
            if (studentsListCard != null) {
                studentsListCard.setVisibility(View.GONE);
            }
            return;
        }
        
        Log.d(TAG, "Fetching students for class - Section: " + section + ", Year: " + yearLevel);
        
        if (studentsListCard != null) {
            studentsListCard.setVisibility(View.VISIBLE);
        }
        if (studentsEmptyState != null) {
            studentsEmptyState.setVisibility(View.GONE);
        }
        
        // First, get enrolled students from class_students
        DatabaseReference classStudentsRef = FirebaseDatabase.getInstance()
                .getReference("attendance_system/class_students");
        classStudentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    List<String> enrolledStudentIds = new ArrayList<>();
                    
                    if (snapshot.exists()) {
                        for (DataSnapshot enrollmentSnapshot : snapshot.getChildren()) {
                            try {
                                DataSnapshot enrollData = enrollmentSnapshot.child("data");
                                if (!enrollData.exists()) {
                                    // Try direct access
                                    Object enrollClassIdObj = enrollmentSnapshot.child("class_id").getValue();
                                    Object enrollStudentIdObj = enrollmentSnapshot.child("student_id").getValue();
                                    String enrollStatus = enrollmentSnapshot.child("status").getValue(String.class);
                                    
                                    String enrollClassId = enrollClassIdObj != null ? String.valueOf(enrollClassIdObj) : null;
                                    String enrollStudentId = enrollStudentIdObj != null ? String.valueOf(enrollStudentIdObj) : null;
                                    
                                    if (selectedClassId != null && enrollClassId != null && 
                                        selectedClassId.equals(enrollClassId) && 
                                        "active".equalsIgnoreCase(enrollStatus) &&
                                        enrollStudentId != null) {
                                        enrolledStudentIds.add(enrollStudentId);
                                    }
                                    continue;
                                }
                                
                                Object enrollClassIdObj = enrollData.child("class_id").getValue();
                                Object enrollStudentIdObj = enrollData.child("student_id").getValue();
                                String enrollStatus = enrollData.child("status").getValue(String.class);
                                
                                String enrollClassId = enrollClassIdObj != null ? String.valueOf(enrollClassIdObj) : null;
                                String enrollStudentId = enrollStudentIdObj != null ? String.valueOf(enrollStudentIdObj) : null;
                                
                                if (selectedClassId != null && enrollClassId != null && 
                                    selectedClassId.equals(enrollClassId) && 
                                    "active".equalsIgnoreCase(enrollStatus) &&
                                    enrollStudentId != null) {
                                    enrolledStudentIds.add(enrollStudentId);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing enrollment: " + e.getMessage());
                            }
                        }
                    }
                    
                    Log.d(TAG, "Found " + enrolledStudentIds.size() + " enrolled students");
                    
                    // Now fetch student details and attendance status
                    fetchStudentDetailsWithAttendance(enrolledStudentIds, section, yearLevel);
                } catch (Exception e) {
                    Log.e(TAG, "Error fetching enrolled students: " + e.getMessage(), e);
                    runOnUiThread(() -> {
                        if (studentsEmptyState != null) {
                            studentsEmptyState.setVisibility(View.VISIBLE);
                            TextView emptyTitle = studentsEmptyState.findViewById(R.id.studentsEmptyStateTitle);
                            if (emptyTitle != null) {
                                emptyTitle.setText("Error loading students");
                            }
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching enrolled students: " + error.getMessage());
                runOnUiThread(() -> {
                    if (studentsEmptyState != null) {
                        studentsEmptyState.setVisibility(View.VISIBLE);
                        TextView emptyTitle = studentsEmptyState.findViewById(R.id.studentsEmptyStateTitle);
                        if (emptyTitle != null) {
                            emptyTitle.setText("Error loading students");
                        }
                    }
                });
            }
        });
    }
    
    private void fetchStudentDetailsWithAttendance(List<String> enrolledStudentIds, String section, String yearLevel) {
        if (enrolledStudentIds.isEmpty()) {
            runOnUiThread(() -> {
                attendanceStudents.clear();
                if (studentsAdapter != null) {
                    studentsAdapter.updateStudents(attendanceStudents);
                }
                if (studentsEmptyState != null) {
                    studentsEmptyState.setVisibility(View.VISIBLE);
                }
            });
            return;
        }
        
        // Fetch all students
        DatabaseReference studentsRef = FirebaseDatabase.getInstance()
                .getReference("attendance_system/students");
        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    // Fetch attendance records for today
                    DatabaseReference attendanceRef = FirebaseDatabase.getInstance()
                            .getReference("attendance_system/attendance");
                    attendanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot attendanceSnapshot) {
                            try {
                                // Build map of student attendance status (latest per student)
                                Map<String, String> attendanceStatusMap = new HashMap<>();
                                Map<String, Long> attendanceTimestampMap = new HashMap<>();
                                
                                Log.d(TAG, "📦 Fetching attendance for class: " + selectedClassId + ", date: " + attendanceDate);
                                
                                if (attendanceSnapshot.exists()) {
                                    int totalRecords = (int) attendanceSnapshot.getChildrenCount();
                                    int matchedRecords = 0;
                                    
                                    Log.d(TAG, "📦 Total attendance records in Firebase: " + totalRecords);
                                    
                                    for (DataSnapshot attSnapshot : attendanceSnapshot.getChildren()) {
                                        try {
                                            // Handle both nested {data: {...}} and direct {...} structures
                                            DataSnapshot attData = attSnapshot.child("data");
                                            boolean hasNestedData = attData.exists() && attData.hasChildren();
                                            
                                            // Try to get values from nested data first, then direct
                                            Object attClassIdObj = null;
                                            Object attStudentIdObj = null;
                                            String attDate = null;
                                            String attStatus = null;
                                            
                                            if (hasNestedData) {
                                                // Nested structure: {data: {class_id, student_id, date, status}}
                                                attClassIdObj = attData.child("class_id").getValue();
                                                attStudentIdObj = attData.child("student_id").getValue();
                                                attDate = attData.child("date").getValue(String.class);
                                                attStatus = attData.child("status").getValue(String.class);
                                            }
                                            
                                            // If nested didn't work, try direct access
                                            if (attClassIdObj == null) {
                                                attClassIdObj = attSnapshot.child("class_id").getValue();
                                            }
                                            if (attStudentIdObj == null) {
                                                attStudentIdObj = attSnapshot.child("student_id").getValue();
                                            }
                                            if (attDate == null) {
                                                attDate = attSnapshot.child("date").getValue(String.class);
                                            }
                                            if (attStatus == null) {
                                                attStatus = attSnapshot.child("status").getValue(String.class);
                                            }
                                            
                                            // Get timestamp for latest record tracking
                                            Long ts = attSnapshot.child("server_time").getValue(Long.class);
                                            if (ts == null) {
                                                ts = attSnapshot.child("timestamp").getValue(Long.class);
                                            }
                                            if (ts == null && hasNestedData) {
                                                ts = attData.child("timestamp").getValue(Long.class);
                                            }
                                            if (ts == null) {
                                                // Try parsing timestamp string
                                                String timestampStr = attSnapshot.child("timestamp").getValue(String.class);
                                                if (timestampStr == null && hasNestedData) {
                                                    timestampStr = attData.child("created_at").getValue(String.class);
                                                }
                                                ts = timestampStr != null ? (long) timestampStr.hashCode() : System.currentTimeMillis();
                                            }
                                            
                                            String attClassId = attClassIdObj != null ? String.valueOf(attClassIdObj) : null;
                                            String attStudentId = attStudentIdObj != null ? String.valueOf(attStudentIdObj) : null;
                                            
                                            // Normalize dates for comparison (handle "2025-12-01" vs "20251201")
                                            String normalizedAttDate = attDate != null ? attDate.replace("-", "").replace("/", "").trim() : null;
                                            String normalizedCurrentDate = attendanceDate != null ? attendanceDate.replace("-", "").replace("/", "").trim() : null;
                                            
                                            // Normalize class IDs
                                            String normalizedAttClassId = attClassId != null ? attClassId.trim() : null;
                                            String normalizedSelectedClassId = selectedClassId != null ? selectedClassId.trim() : null;
                                            
                                            // Match by class_id and date
                                            boolean classMatches = normalizedSelectedClassId != null && normalizedAttClassId != null &&
                                                normalizedSelectedClassId.equals(normalizedAttClassId);
                                            boolean dateMatches = normalizedCurrentDate != null && normalizedAttDate != null &&
                                                normalizedCurrentDate.equals(normalizedAttDate);
                                            
                                            // Debug logging for first few records
                                            Log.d(TAG, "🔍 Record check - ClassID: " + attClassId + " vs " + selectedClassId + 
                                                " (match=" + classMatches + "), Date: " + attDate + " vs " + attendanceDate + 
                                                " (match=" + dateMatches + "), Student: " + attStudentId + ", Status: " + attStatus);
                                            
                                            if (classMatches && dateMatches && attStudentId != null && attStatus != null) {
                                                // Track latest record by timestamp
                                                Long existingTs = attendanceTimestampMap.get(attStudentId);
                                                if (existingTs == null || ts > existingTs) {
                                                    attendanceStatusMap.put(attStudentId, attStatus.toLowerCase());
                                                    attendanceTimestampMap.put(attStudentId, ts);
                                                    matchedRecords++;
                                                    Log.d(TAG, "✅ Matched attendance: Student " + attStudentId + " → " + attStatus);
                                                }
                                            }
                                        } catch (Exception e) {
                                            Log.e(TAG, "Error parsing attendance: " + e.getMessage());
                                        }
                                    }
                                    
                                    Log.d(TAG, "📊 Matched " + matchedRecords + " attendance records for current class/date");
                                } else {
                                    Log.d(TAG, "⚠️ No attendance data in Firebase");
                                }
                                
                                Log.d(TAG, "📋 Attendance status map: " + attendanceStatusMap.toString());
                                
                                // Build student list
                                List<AttendanceStudentsAdapter.AttendanceStudentItem> studentList = new ArrayList<>();
                                
                                for (DataSnapshot studentSnapshot : snapshot.getChildren()) {
                                    try {
                                        DataSnapshot dataSnapshot = studentSnapshot.child("data");
                                        if (!dataSnapshot.exists()) continue;
                                        
                                        String studentId = dataSnapshot.child("id").getValue(String.class);
                                        if (studentId == null || !enrolledStudentIds.contains(studentId)) {
                                            continue;
                                        }
                                        
                                        // Check section and year level match
                                        Object studentSectionObj = dataSnapshot.child("section").getValue();
                                        Object studentYearLevelObj = dataSnapshot.child("year_level").getValue();
                                        String studentSection = studentSectionObj != null ? String.valueOf(studentSectionObj) : null;
                                        String studentYearLevel = studentYearLevelObj != null ? String.valueOf(studentYearLevelObj) : null;
                                        
                                        // Normalize for comparison
                                        String normalizedStudentYear = studentYearLevel != null ? String.valueOf(studentYearLevel).trim() : null;
                                        String normalizedClassYear = yearLevel != null ? String.valueOf(yearLevel).trim() : null;
                                        String studentYearNum = normalizedStudentYear != null ? normalizedStudentYear.replaceAll("[^0-9]", "") : "";
                                        String classYearNum = normalizedClassYear != null ? normalizedClassYear.replaceAll("[^0-9]", "") : "";
                                        
                                        boolean sectionMatch = section != null && studentSection != null && 
                                                section.trim().equalsIgnoreCase(studentSection.trim());
                                        boolean yearMatch = false;
                                        if (!studentYearNum.isEmpty() && !classYearNum.isEmpty()) {
                                            try {
                                                int studentYearInt = Integer.parseInt(studentYearNum);
                                                int classYearInt = Integer.parseInt(classYearNum);
                                                yearMatch = studentYearInt == classYearInt;
                                            } catch (NumberFormatException e) {
                                                yearMatch = normalizedStudentYear != null && normalizedClassYear != null && 
                                                           normalizedStudentYear.equals(normalizedClassYear);
                                            }
                                        } else if (normalizedStudentYear != null && normalizedClassYear != null) {
                                            yearMatch = normalizedStudentYear.equals(normalizedClassYear);
                                        }
                                        
                                        if (!sectionMatch || !yearMatch) {
                                            continue;
                                        }
                                        
                                        // Get student details
                                        String studentIdStr = dataSnapshot.child("student_id").getValue(String.class);
                                        String firstName = dataSnapshot.child("first_name").getValue(String.class);
                                        String middleName = dataSnapshot.child("middle_name").getValue(String.class);
                                        String lastName = dataSnapshot.child("last_name").getValue(String.class);
                                        String suffixName = dataSnapshot.child("suffix_name").getValue(String.class);
                                        String profilePicture = dataSnapshot.child("profile_picture").getValue(String.class);
                                        
                                        // Build full name
                                        StringBuilder fullNameBuilder = new StringBuilder();
                                        if (firstName != null && !firstName.isEmpty()) fullNameBuilder.append(firstName);
                                        if (middleName != null && !middleName.isEmpty()) fullNameBuilder.append(" ").append(middleName);
                                        if (lastName != null && !lastName.isEmpty()) fullNameBuilder.append(" ").append(lastName);
                                        if (suffixName != null && !suffixName.isEmpty()) fullNameBuilder.append(" ").append(suffixName);
                                        String fullName = fullNameBuilder.toString().trim();
                                        
                                        // Get attendance status (only if recorded, otherwise null)
                                        String attendanceStatus = attendanceStatusMap.get(studentId); // null if not recorded
                                        
                                        AttendanceStudentsAdapter.AttendanceStudentItem studentItem = 
                                            new AttendanceStudentsAdapter.AttendanceStudentItem(
                                                studentId,
                                                studentIdStr != null ? studentIdStr : "",
                                                fullName,
                                                studentSection,
                                                studentYearLevel,
                                                profilePicture != null ? profilePicture : "",
                                                attendanceStatus
                                            );
                                        studentList.add(studentItem);
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error parsing student: " + e.getMessage());
                                    }
                                }
                                
                                // Sort by name
                                studentList.sort((a, b) -> a.fullName.compareToIgnoreCase(b.fullName));
                                
                                // Update UI
                                runOnUiThread(() -> {
                                    // Store all students for filtering
                                    allAttendanceStudents.clear();
                                    allAttendanceStudents.addAll(studentList);
                                    
                                    // Apply current search filter if any
                                    String searchQuery = searchEditText != null ? searchEditText.getText().toString() : "";
                                    filterAttendanceStudents(searchQuery);
                                    
                                    if (studentList.isEmpty()) {
                                        if (studentsEmptyState != null) {
                                            studentsEmptyState.setVisibility(View.VISIBLE);
                                        }
                                        if (searchCard != null) {
                                            searchCard.setVisibility(View.GONE);
                                        }
                                    } else {
                                        if (studentsEmptyState != null) {
                                            studentsEmptyState.setVisibility(View.GONE);
                                        }
                                        // Show search card when students are loaded
                                        if (searchCard != null) {
                                            searchCard.setVisibility(View.VISIBLE);
                                        }
                                    }
                                    
                                    // Setup real-time listener for attendance updates AFTER initial data is loaded
                                    setupRealtimeAttendanceListener();
                                });
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing attendance data: " + e.getMessage(), e);
                                runOnUiThread(() -> {
                                    if (studentsEmptyState != null) {
                                        studentsEmptyState.setVisibility(View.VISIBLE);
                                        TextView emptyTitle = studentsEmptyState.findViewById(R.id.studentsEmptyStateTitle);
                                        if (emptyTitle != null) {
                                            emptyTitle.setText("Error loading attendance");
                                        }
                                    }
                                });
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e(TAG, "Error fetching attendance: " + error.getMessage());
                            runOnUiThread(() -> {
                                if (studentsEmptyState != null) {
                                    studentsEmptyState.setVisibility(View.VISIBLE);
                                    TextView emptyTitle = studentsEmptyState.findViewById(R.id.studentsEmptyStateTitle);
                                    if (emptyTitle != null) {
                                        emptyTitle.setText("Error loading attendance");
                                    }
                                }
                            });
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error fetching students: " + e.getMessage(), e);
                    runOnUiThread(() -> {
                        if (studentsEmptyState != null) {
                            studentsEmptyState.setVisibility(View.VISIBLE);
                            TextView emptyTitle = studentsEmptyState.findViewById(R.id.studentsEmptyStateTitle);
                            if (emptyTitle != null) {
                                emptyTitle.setText("Error loading students");
                            }
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching students: " + error.getMessage());
                runOnUiThread(() -> {
                    if (studentsEmptyState != null) {
                        studentsEmptyState.setVisibility(View.VISIBLE);
                        TextView emptyTitle = studentsEmptyState.findViewById(R.id.studentsEmptyStateTitle);
                        if (emptyTitle != null) {
                            emptyTitle.setText("Error loading students");
                        }
                    }
                });
            }
        });
    }

    private void fetchTeacherClasses(String userId) {
        showDialogLoadingProgress(true);
        emptyStateText.setVisibility(View.GONE);

        Log.d(TAG, "Fetching teacher classes for teacherId: " + userId);

        DatabaseReference classesRef = FirebaseDatabase.getInstance().getReference("attendance_system/classes");
        classesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                synchronized (teacherClasses) {
                    teacherClasses.clear();
                }

                String normalizedTeacherId = userId;
                if (userId != null && userId.startsWith("T")) {
                    normalizedTeacherId = userId.substring(1);
                }

                final int[] pendingOperations = {0};
                final int[] matchedClasses = {0};

                for (DataSnapshot classSnapshot : snapshot.getChildren()) {
                    try {
                        DataSnapshot dataSnapshot = classSnapshot.child("data");
                        if (!dataSnapshot.exists()) continue;

                        String classTeacherId = dataSnapshot.child("teacher_id").getValue(String.class);
                        String classId = dataSnapshot.child("id").getValue(String.class);
                        String section = dataSnapshot.child("section").getValue(String.class);
                        String yearLevel = dataSnapshot.child("year_level").getValue(String.class);

                        if (classTeacherId == null || classId == null) continue;

                        String normalizedClassTeacherId = classTeacherId;
                        if (classTeacherId.startsWith("T")) {
                            normalizedClassTeacherId = classTeacherId.substring(1);
                        }

                        boolean matches = userId.equalsIgnoreCase(classTeacherId) ||
                                        normalizedTeacherId.equalsIgnoreCase(classTeacherId) ||
                                        userId.equalsIgnoreCase(normalizedClassTeacherId) ||
                                        normalizedTeacherId.equalsIgnoreCase(normalizedClassTeacherId);

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
                            String subjectId = dataSnapshot.child("subject_id").getValue(String.class);
                            fetchSubjectInfoAndAddClass(classId, subjectId, section, yearLevel, () -> {
                                synchronized (pendingOperations) {
                                    pendingOperations[0]--;
                                    if (pendingOperations[0] == 0) {
                                        runOnUiThread(() -> {
                                            showDialogLoadingProgress(false);
                                            synchronized (teacherClasses) {
                                                if (teacherClasses.isEmpty()) {
                                                    emptyStateText.setVisibility(View.VISIBLE);
                                                    TextView emptyTitle = emptyStateText.findViewById(R.id.emptyStateTitle);
                                                    if (emptyTitle != null) {
                                                        emptyTitle.setText("No classes found for this teacher");
                                                    }
                                                } else {
                                                    emptyStateText.setVisibility(View.GONE);
                                                    updateClassSpinner();
                                                }
                                            }
                                        });
                                    }
                                }
                            });
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing class: " + e.getMessage());
                    }
                }

                if (matchedClasses[0] == 0) {
                    runOnUiThread(() -> {
                        showDialogLoadingProgress(false);
                        emptyStateText.setVisibility(View.VISIBLE);
                        TextView emptyTitle = emptyStateText.findViewById(R.id.emptyStateTitle);
                        if (emptyTitle != null) {
                            emptyTitle.setText("No classes found for this teacher");
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching teacher classes: " + error.getMessage());
                runOnUiThread(() -> {
                    showDialogLoadingProgress(false);
                    emptyStateText.setVisibility(View.VISIBLE);
                    TextView emptyTitle = emptyStateText.findViewById(R.id.emptyStateTitle);
                    if (emptyTitle != null) {
                        emptyTitle.setText("Error loading classes");
                    }
                });
            }
        });
    }

    private void fetchSubjectInfoAndAddClass(String classId, String subjectId, String section, String yearLevel, Runnable onComplete) {
        if (subjectId == null) {
            String className = "Class " + classId + " - " + section + " (Year " + yearLevel + ")";
            synchronized (teacherClasses) {
                teacherClasses.add(new ClassItem(classId, className, section, yearLevel));
            }
            if (onComplete != null) onComplete.run();
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

                String className;
                if (subjectCode != null && subjectName != null) {
                    className = subjectCode + " - " + section + " (Year " + yearLevel + ")";
                } else {
                    className = "Class " + classId + " - " + section + " (Year " + yearLevel + ")";
                }

                synchronized (teacherClasses) {
                    teacherClasses.add(new ClassItem(classId, className, section, yearLevel));
                }
                if (onComplete != null) onComplete.run();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching subject info: " + error.getMessage());
                String className = "Class " + classId + " - " + section + " (Year " + yearLevel + ")";
                synchronized (teacherClasses) {
                    teacherClasses.add(new ClassItem(classId, className, section, yearLevel));
                }
                if (onComplete != null) onComplete.run();
            }
        });
    }

    private void updateClassSpinner() {
        try {
            // Temporarily disable listener to prevent triggering during update
            AdapterView.OnItemSelectedListener listener = classSpinner.getOnItemSelectedListener();
            classSpinner.setOnItemSelectedListener(null);
            
            List<String> classNames = new ArrayList<>();
            classNames.add("Select Class");
            
            // Thread-safe access to teacherClasses
            List<ClassItem> classesCopy;
            synchronized (teacherClasses) {
                classesCopy = new ArrayList<>(teacherClasses);
            }
            
            for (ClassItem classItem : classesCopy) {
                if (classItem != null && classItem.name != null) {
                    classNames.add(classItem.name);
                }
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.spinner_item_modern, classNames) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView textView = (TextView) view.findViewById(android.R.id.text1);
                    if (textView != null) {
                        if (position == 0) {
                            textView.setTextColor(0xFF9E9E9E);
                            textView.setTextSize(16);
                        } else {
                            textView.setTextColor(0xFF212121);
                            textView.setTextSize(16);
                        }
                        textView.setPadding(16, 16, 16, 16);
                    }
                    return view;
                }

                @Override
                public View getDropDownView(int position, View convertView, ViewGroup parent) {
                    View view = super.getDropDownView(position, convertView, parent);
                    TextView textView = (TextView) view.findViewById(android.R.id.text1);
                    if (textView != null) {
                        if (position == 0) {
                            textView.setTextColor(0xFF9E9E9E);
                        } else {
                            textView.setTextColor(0xFF212121);
                        }
                        textView.setPadding(20, 20, 20, 20);
                    }
                    return view;
                }
            };
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_modern);
            classSpinner.setAdapter(adapter);

            // Re-enable listener
            if (listener != null) {
                classSpinner.setOnItemSelectedListener(listener);
            }

            // Auto-select first class if available and no selection
            synchronized (teacherClasses) {
                if (teacherClasses.size() > 0 && classSpinner.getSelectedItemPosition() == 0) {
                    // Use post to avoid triggering during adapter update
                    classSpinner.post(() -> {
                        try {
                            synchronized (teacherClasses) {
                                if (classSpinner.getSelectedItemPosition() == 0 && teacherClasses.size() > 0) {
                                    classSpinner.setSelection(1, false); // false = don't trigger listener
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error auto-selecting class: " + e.getMessage());
                        }
                    });
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating class spinner: " + e.getMessage(), e);
        }
    }

    private Bitmap decodeBase64ToBitmap(String base64String) {
        try {
            // Remove data URL prefix if present
            if (base64String.contains(",")) {
                base64String = base64String.split(",")[1];
            }
            byte[] decodedBytes = Base64.decode(base64String, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
        } catch (Exception e) {
            Log.e(TAG, "Error decoding base64 image: " + e.getMessage());
            return null;
        }
    }

    private Bitmap getCircularBitmap(Bitmap bitmap) {
        if (bitmap == null) return null;
        
        int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        
        Paint paint = new Paint();
        Rect rect = new Rect(0, 0, size, size);
        RectF rectF = new RectF(rect);
        
        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(0xFF2196F3);
        canvas.drawOval(rectF, paint);
        
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        
        return output;
    }

    private void showAlert(String message, String color) {
        runOnUiThread(() -> {
            try {
                showDialogAlert(message, color);
            } catch (Exception e) {
                Log.e(TAG, "Error showing alert: " + e.getMessage(), e);
            }
        });
    }

    private void hideAlert() {
        runOnUiThread(() -> {
            try {
                hideDialogAlert();
            } catch (Exception e) {
                Log.e(TAG, "Error hiding alert: " + e.getMessage(), e);
            }
        });
    }
    
    // Dialog helper methods
    private void showScannerDialog() {
        runOnUiThread(() -> {
            try {
                if (isFinishing() || isDestroyed()) {
                    return; // Don't show dialog if activity is finishing
                }
                if (scannerDialog != null && !scannerDialog.isShowing()) {
                    scannerDialog.show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error showing scanner dialog: " + e.getMessage(), e);
            }
        });
    }
    
    private void dismissScannerDialog() {
        runOnUiThread(() -> {
            try {
                if (scannerDialog != null && scannerDialog.isShowing()) {
                    scannerDialog.dismiss();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error dismissing scanner dialog: " + e.getMessage(), e);
            }
        });
    }
    
    private void updateDialogStatus(String status, int color) {
        runOnUiThread(() -> {
            try {
                if (dialogScannerStatusBadge != null) {
                    dialogScannerStatusBadge.setText(status);
                    dialogScannerStatusBadge.setBackgroundColor(color);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating dialog status: " + e.getMessage(), e);
            }
        });
    }
    
    private void showDialogWaitingArea(boolean show) {
        runOnUiThread(() -> {
            try {
                if (dialogWaitingArea != null) {
                    dialogWaitingArea.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error showing dialog waiting area: " + e.getMessage(), e);
            }
        });
    }
    
    private void showDialogLoadingProgress(boolean show) {
        runOnUiThread(() -> {
            try {
                if (dialogLoadingProgress != null) {
                    dialogLoadingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error showing dialog loading progress: " + e.getMessage(), e);
            }
        });
    }
    
    private void showDialogAlert(String message, String color) {
        runOnUiThread(() -> {
            try {
                // Cancel any existing hide runnable
                if (alertHideRunnable != null) {
                    alertHandler.removeCallbacks(alertHideRunnable);
                }
                
                if (dialogAlertText != null) {
                    dialogAlertText.setText(message);
                    dialogAlertText.setBackgroundColor(android.graphics.Color.parseColor(color));
                    dialogAlertText.setVisibility(View.VISIBLE);
                    Log.d(TAG, "Dialog alert shown: " + message + " (color: " + color + ")");
                    
                    // Auto-hide after 5 seconds
                    alertHideRunnable = () -> {
                        hideDialogAlert();
                    };
                    alertHandler.postDelayed(alertHideRunnable, 5000); // 5 seconds
                } else {
                    Log.e(TAG, "dialogAlertText is null, cannot show alert");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error showing dialog alert: " + e.getMessage(), e);
            }
        });
    }
    
    private void hideDialogAlert() {
        runOnUiThread(() -> {
            try {
                // Cancel any pending hide runnable
                if (alertHideRunnable != null) {
                    alertHandler.removeCallbacks(alertHideRunnable);
                    alertHideRunnable = null;
                }
                
                if (dialogAlertText != null) {
                    dialogAlertText.setVisibility(View.GONE);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error hiding dialog alert: " + e.getMessage(), e);
            }
        });
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
            intent = new Intent(this, TeacherStudentsActivity.class);
        } else if (id == R.id.nav_attendance) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return true; // Already on this page
        }

        if (intent != null) {
            intent.putExtra("userId", teacherId);
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
        try {
            stopScanner();
            // Remove real-time attendance listener
            if (attendanceRealtimeListener != null && attendanceRealtimeRef != null) {
                attendanceRealtimeRef.removeEventListener(attendanceRealtimeListener);
                attendanceRealtimeListener = null;
                attendanceRealtimeRef = null;
            }
            // Cancel any pending alert hide runnable
            if (alertHideRunnable != null) {
                alertHandler.removeCallbacks(alertHideRunnable);
                alertHideRunnable = null;
            }
            // Dismiss dialog if showing
            if (scannerDialog != null && scannerDialog.isShowing()) {
                scannerDialog.dismiss();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy: " + e.getMessage());
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        try {
            // Don't stop scanner on pause, just log
            Log.d(TAG, "Activity paused, scanner state: " + isScanning);
            // Dismiss dialog when activity is paused to prevent buffer issues
            if (scannerDialog != null && scannerDialog.isShowing()) {
                scannerDialog.dismiss();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onPause: " + e.getMessage());
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        try {
            Log.d(TAG, "Activity resumed, scanner state: " + isScanning);
            // Re-setup real-time listener if class is selected (in case it was removed during pause)
            if (selectedClassId != null && !selectedClassId.isEmpty() && 
                attendanceDate != null && !attendanceDate.isEmpty() &&
                (attendanceRealtimeListener == null || attendanceRealtimeRef == null)) {
                Log.d(TAG, "Re-setting up real-time listener on resume");
                setupRealtimeAttendanceListener();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume: " + e.getMessage());
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    public void onOptionsIconClick() {
        // Show popup menu
        View moreIcon = findViewById(R.id.more_icon);
        if (moreIcon != null) {
            android.widget.PopupMenu popupMenu = new android.widget.PopupMenu(this, moreIcon);
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
    
    /**
     * Setup real-time listener for attendance updates from Firebase
     * This will detect changes made by the website in real-time
     */
    private void setupRealtimeAttendanceListener() {
        // Remove existing listener if any
        if (attendanceRealtimeListener != null && attendanceRealtimeRef != null) {
            try {
                attendanceRealtimeRef.removeEventListener(attendanceRealtimeListener);
            } catch (Exception e) {
                Log.e(TAG, "Error removing existing listener: " + e.getMessage());
            }
        }
        
        // Don't setup listener if no class is selected
        if (selectedClassId == null || selectedClassId.isEmpty() || attendanceDate == null || attendanceDate.isEmpty()) {
            Log.d(TAG, "Cannot setup real-time listener - class or date not selected");
            return;
        }
        
        // Setup new real-time listener
        attendanceRealtimeRef = FirebaseDatabase.getInstance()
                .getReference("attendance_system/attendance");
        
        attendanceRealtimeListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    if (selectedClassId == null || selectedClassId.isEmpty() || attendanceDate == null) {
                        return;
                    }
                    
                    // Log every trigger for debugging
                    if (isFirstListenerTrigger) {
                        Log.d(TAG, "⏭️ First listener trigger (initial load) - will still process");
                        isFirstListenerTrigger = false;
                        // Don't return - let it process to ensure initial data is correct
                    }
                    
                    Log.d(TAG, "🔄🔄🔄 REAL-TIME UPDATE DETECTED FROM FIREBASE! 🔄🔄🔄");
                    Log.d(TAG, "   Class ID: " + selectedClassId);
                    Log.d(TAG, "   Date: " + attendanceDate);
                    
                    // Build map of current attendance status (latest per student)
                    Map<String, String> attendanceStatusMap = new HashMap<>();
                    Map<String, String> attendanceKeyMap = new HashMap<>();
                    Map<String, Long> attendanceTimestampMap = new HashMap<>();
                    
                    int totalRecords = 0;
                    int matchedRecords = 0;
                    
                    if (snapshot.exists()) {
                        totalRecords = (int) snapshot.getChildrenCount();
                        Log.d(TAG, "📦 Total Firebase records: " + totalRecords);
                        
                        for (DataSnapshot attSnapshot : snapshot.getChildren()) {
                            try {
                                // Handle both nested {data: {...}} and direct {...} structures
                                DataSnapshot attData = attSnapshot.child("data");
                                boolean hasNestedData = attData.exists() && attData.hasChildren();
                                
                                // Try to get values from nested data first, then direct
                                Object attClassIdObj = null;
                                Object attStudentIdObj = null;
                                String attDate = null;
                                String attStatus = null;
                                
                                if (hasNestedData) {
                                    // Nested structure: {data: {class_id, student_id, date, status}}
                                    attClassIdObj = attData.child("class_id").getValue();
                                    attStudentIdObj = attData.child("student_id").getValue();
                                    attDate = attData.child("date").getValue(String.class);
                                    attStatus = attData.child("status").getValue(String.class);
                                }
                                
                                // If nested didn't work, try direct access
                                if (attClassIdObj == null) {
                                    attClassIdObj = attSnapshot.child("class_id").getValue();
                                }
                                if (attStudentIdObj == null) {
                                    attStudentIdObj = attSnapshot.child("student_id").getValue();
                                }
                                if (attDate == null) {
                                    attDate = attSnapshot.child("date").getValue(String.class);
                                }
                                if (attStatus == null) {
                                    attStatus = attSnapshot.child("status").getValue(String.class);
                                }
                                
                                // Get timestamp for latest record tracking
                                Long ts = attSnapshot.child("server_time").getValue(Long.class);
                                if (ts == null) {
                                    ts = attSnapshot.child("timestamp").getValue(Long.class);
                                }
                                if (ts == null && hasNestedData) {
                                    ts = attData.child("timestamp").getValue(Long.class);
                                }
                                if (ts == null) {
                                    // Try parsing timestamp string
                                    String timestampStr = attSnapshot.child("timestamp").getValue(String.class);
                                    if (timestampStr == null && hasNestedData) {
                                        timestampStr = attData.child("created_at").getValue(String.class);
                                    }
                                    ts = timestampStr != null ? (long) timestampStr.hashCode() : System.currentTimeMillis();
                                }
                                
                                String attClassId = attClassIdObj != null ? String.valueOf(attClassIdObj) : null;
                                String attStudentId = attStudentIdObj != null ? String.valueOf(attStudentIdObj) : null;
                                
                                // Normalize dates for comparison (handle "2025-12-01" vs "20251201" vs "2025/12/01")
                                String normalizedAttDate = attDate != null ? attDate.replace("-", "").replace("/", "").trim() : null;
                                String normalizedCurrentDate = attendanceDate != null ? attendanceDate.replace("-", "").replace("/", "").trim() : null;
                                
                                // Normalize class IDs (handle string vs number)
                                String normalizedAttClassId = attClassId != null ? String.valueOf(attClassId).trim() : null;
                                String normalizedSelectedClassId = selectedClassId != null ? String.valueOf(selectedClassId).trim() : null;
                                
                                // Match by class_id and date (normalized)
                                boolean classMatches = normalizedSelectedClassId != null && normalizedAttClassId != null &&
                                    normalizedSelectedClassId.equals(normalizedAttClassId);
                                boolean dateMatches = normalizedCurrentDate != null && normalizedAttDate != null &&
                                    normalizedCurrentDate.equals(normalizedAttDate);
                                
                                if (classMatches && dateMatches && attStudentId != null && attStatus != null) {
                                    matchedRecords++;
                                    // Use composite key studentId to track latest status by timestamp
                                    Long existingTs = attendanceTimestampMap.get(attStudentId);
                                    if (existingTs == null || ts > existingTs) {
                                        attendanceStatusMap.put(attStudentId, attStatus.toLowerCase());
                                        attendanceKeyMap.put(attStudentId, attSnapshot.getKey());
                                        attendanceTimestampMap.put(attStudentId, ts);
                                        
                                        Log.d(TAG, "✅ REAL-TIME MATCH (latest) Student " + attStudentId + " → Status: " + attStatus + " (ts=" + ts + ")");
                                    } else {
                                        Log.d(TAG, "⏭️ Skipping older record for student " + attStudentId + " (ts=" + ts + ", latestTs=" + existingTs + ")");
                                    }
                                } else {
                                    // Log why it didn't match (for debugging)
                                    if (attClassId != null || attDate != null) {
                                        Log.d(TAG, "❌ No match - Class: " + (classMatches ? "✓" : "✗") + 
                                              ", Date: " + (dateMatches ? "✓" : "✗") +
                                              ", AttDate: " + attDate + ", CurrentDate: " + attendanceDate);
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing attendance in real-time: " + e.getMessage());
                            }
                        }
                        
                        Log.d(TAG, "📊 Firebase sync complete: " + matchedRecords + "/" + totalRecords + " records matched for current class/date");
                    } else {
                        Log.d(TAG, "⚠️ No attendance data in Firebase");
                    }
                    
                    // Update attendance status for existing students in the list
                    runOnUiThread(() -> {
                        try {
                            boolean hasChanges = false;
                            int updateCount = 0;
                            int deleteCount = 0;
                            
                            // Create a copy to avoid concurrent modification
                            List<AttendanceStudentsAdapter.AttendanceStudentItem> studentsCopy = new ArrayList<>(allAttendanceStudents.isEmpty() ? attendanceStudents : allAttendanceStudents);
                            
                            Log.d(TAG, "🔍 Processing " + studentsCopy.size() + " students for updates");
                            Log.d(TAG, "🔍 Firebase has " + attendanceStatusMap.size() + " attendance records");
                            
                            for (AttendanceStudentsAdapter.AttendanceStudentItem student : studentsCopy) {
                                String newStatus = attendanceStatusMap.get(student.id);
                                String newKey = attendanceKeyMap.get(student.id);
                                String oldStatus = student.attendanceStatus;
                                
                                // Check if status changed or added
                                if (newStatus != null && (oldStatus == null || !newStatus.equals(oldStatus))) {
                                    Log.d(TAG, "📝 REAL-TIME UPDATE: Student " + student.id + " (" + student.fullName + ") status: " + oldStatus + " → " + newStatus);
                                    student.attendanceStatus = newStatus;
                                    student.attendanceKey = newKey;
                                    hasChanges = true;
                                    updateCount++;
                                } else if (newStatus == null && oldStatus != null) {
                                    // Attendance was deleted (e.g., reset)
                                    Log.d(TAG, "🗑️ REAL-TIME DELETE: Removing attendance for student " + student.id + " (" + student.fullName + ")");
                                    student.attendanceStatus = null;
                                    student.attendanceKey = null;
                                    hasChanges = true;
                                    deleteCount++;
                                } else if (newStatus != null && newStatus.equals(oldStatus)) {
                                    Log.d(TAG, "✓ No change for student " + student.id + " - already " + newStatus);
                                }
                            }
                            
                            if (hasChanges || true) {  // Always update to ensure UI is fresh
                                String action = deleteCount > 0 ? "deleted" : (updateCount > 0 ? "updated" : "synced");
                                int count = deleteCount > 0 ? deleteCount : updateCount;
                                Log.d(TAG, "🔄 REAL-TIME: Refreshing UI with " + count + " " + action + " attendance record(s)");
                                
                                // Update the allAttendanceStudents list (for filtering)
                                allAttendanceStudents.clear();
                                allAttendanceStudents.addAll(studentsCopy);
                                
                                // Update the main attendanceStudents list
                                attendanceStudents.clear();
                                attendanceStudents.addAll(studentsCopy);
                                
                                // Apply current search filter if any and update adapter
                                String searchQuery = searchEditText != null ? searchEditText.getText().toString() : "";
                                if (searchQuery != null && !searchQuery.isEmpty()) {
                                    Log.d(TAG, "📝 Applying search filter: " + searchQuery);
                                    filterAttendanceStudents(searchQuery);
                                } else {
                                    // No filter, directly update adapter
                                    if (studentsAdapter != null) {
                                        Log.d(TAG, "📝 Updating adapter with " + attendanceStudents.size() + " students");
                                        studentsAdapter.updateStudents(new ArrayList<>(attendanceStudents));
                                        Log.d(TAG, "✅ Adapter updated successfully");
                                    } else {
                                        Log.w(TAG, "⚠️ Adapter is null, cannot update");
                                    }
                                }
                                
                                // Show toast notification to user only if actual changes
                                if (hasChanges) {
                                    String message;
                                    if (deleteCount > 0) {
                                        message = "Attendance reset - " + deleteCount + " record(s) cleared";
                                    } else {
                                        message = "Attendance updated - " + updateCount + " change(s)";
                                    }
                                    Toast.makeText(RfidAttendanceActivity.this, message, Toast.LENGTH_SHORT).show();
                                    Log.d(TAG, "✅ REAL-TIME: UI refresh complete! Message: " + message);
                                } else {
                                    Log.d(TAG, "ℹ️ REAL-TIME: Sync complete, no visible changes");
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error updating UI from real-time listener: " + e.getMessage(), e);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error in real-time attendance listener: " + e.getMessage(), e);
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Real-time attendance listener cancelled: " + error.getMessage());
            }
        };
        
        // Reset first trigger flag when setting up new listener
        isFirstListenerTrigger = true;
        
        attendanceRealtimeRef.addValueEventListener(attendanceRealtimeListener);
        Log.d(TAG, "✅ Real-time attendance listener setup complete for class " + selectedClassId + ", date " + attendanceDate);
        Log.d(TAG, "✅ Listening to Firebase path: attendance_system/attendance");
    }
    
    /**
     * Filter attendance students by search query (name or ID)
     */
    private void filterAttendanceStudents(String query) {
        attendanceStudents.clear();
        
        if (query == null || query.trim().isEmpty()) {
            // No filter, show all students
            attendanceStudents.addAll(allAttendanceStudents);
        } else {
            // Filter by name or ID (case-insensitive)
            String lowerQuery = query.toLowerCase().trim();
            for (AttendanceStudentsAdapter.AttendanceStudentItem student : allAttendanceStudents) {
                if ((student.fullName != null && student.fullName.toLowerCase().contains(lowerQuery)) ||
                    (student.studentId != null && student.studentId.toLowerCase().contains(lowerQuery))) {
                    attendanceStudents.add(student);
                }
            }
        }
        
        // Update adapter
        if (studentsAdapter != null) {
            studentsAdapter.updateStudents(attendanceStudents);
        }
        
        // Show/hide empty state
        if (attendanceStudents.isEmpty()) {
            if (studentsEmptyState != null) {
                studentsEmptyState.setVisibility(View.VISIBLE);
                TextView emptyTitle = studentsEmptyState.findViewById(R.id.studentsEmptyStateTitle);
                if (emptyTitle != null) {
                    if (query != null && !query.trim().isEmpty()) {
                        emptyTitle.setText("No students found matching \"" + query + "\"");
                    } else {
                        emptyTitle.setText("No students found");
                    }
                }
            }
        } else {
            if (studentsEmptyState != null) {
                studentsEmptyState.setVisibility(View.GONE);
            }
        }
    }
    
    /**
     * Show dialog for manual attendance update
     */
    private void showUpdateAttendanceDialog(AttendanceStudentsAdapter.AttendanceStudentItem student) {
        if (student == null || selectedClassId == null || attendanceDate == null) {
            Toast.makeText(this, "Please select a class first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_update_attendance, null);
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        // Get views
        ImageView studentPhoto = dialogView.findViewById(R.id.dialogStudentPhoto);
        ImageView studentPhotoPlaceholder = dialogView.findViewById(R.id.dialogStudentPhotoPlaceholder);
        TextView studentName = dialogView.findViewById(R.id.dialogStudentName);
        TextView studentIdText = dialogView.findViewById(R.id.dialogStudentId);
        TextView studentSection = dialogView.findViewById(R.id.dialogStudentSection);
        TextView currentStatusBadge = dialogView.findViewById(R.id.currentStatusBadge);
        
        // Status buttons are now CardViews
        View btnPresent = dialogView.findViewById(R.id.btnPresent);
        View btnLate = dialogView.findViewById(R.id.btnLate);
        View btnAbsent = dialogView.findViewById(R.id.btnAbsent);
        View btnExcused = dialogView.findViewById(R.id.btnExcused);
        View btnCancel = dialogView.findViewById(R.id.btnCancel);
        
        // Set student info
        studentName.setText(student.fullName);
        studentIdText.setText(student.studentId);
        studentSection.setText("Year " + student.yearLevel + " - Section " + student.section);
        
        // Set profile picture
        if (student.profilePictureBase64 != null && !student.profilePictureBase64.isEmpty()) {
            try {
                String base64 = student.profilePictureBase64;
                if (base64.contains(",")) {
                    base64 = base64.split(",")[1];
                }
                byte[] decodedBytes = Base64.decode(base64, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                if (bitmap != null) {
                    studentPhoto.setImageBitmap(bitmap);
                    studentPhoto.setVisibility(View.VISIBLE);
                    studentPhotoPlaceholder.setVisibility(View.GONE);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading profile picture", e);
            }
        }
        
        // Set current status badge with rounded background
        String currentStatus = student.attendanceStatus;
        android.graphics.drawable.GradientDrawable badgeBackground = new android.graphics.drawable.GradientDrawable();
        badgeBackground.setCornerRadius(40f);
        
        if (currentStatus != null && !currentStatus.isEmpty()) {
            currentStatusBadge.setText(currentStatus.substring(0, 1).toUpperCase() + currentStatus.substring(1));
            switch (currentStatus.toLowerCase()) {
                case "present":
                    badgeBackground.setColor(0xFF4CAF50);
                    break;
                case "late":
                    badgeBackground.setColor(0xFFFF9800);
                    break;
                case "absent":
                    badgeBackground.setColor(0xFFF44336);
                    break;
                case "excused":
                    badgeBackground.setColor(0xFF2196F3);
                    break;
                default:
                    currentStatusBadge.setText("Not Recorded");
                    badgeBackground.setColor(0xFF9E9E9E);
                    break;
            }
        } else {
            currentStatusBadge.setText("Not Recorded");
            badgeBackground.setColor(0xFF9E9E9E);
        }
        currentStatusBadge.setBackground(badgeBackground);
        
        // Button click listeners
        View.OnClickListener statusClickListener = v -> {
            String newStatus = "";
            if (v.getId() == R.id.btnPresent) {
                newStatus = "present";
            } else if (v.getId() == R.id.btnLate) {
                newStatus = "late";
            } else if (v.getId() == R.id.btnAbsent) {
                newStatus = "absent";
            } else if (v.getId() == R.id.btnExcused) {
                newStatus = "excused";
            }
            
            if (!newStatus.isEmpty()) {
                saveManualAttendance(student, newStatus, dialog);
            }
        };
        
        btnPresent.setOnClickListener(statusClickListener);
        btnLate.setOnClickListener(statusClickListener);
        btnAbsent.setOnClickListener(statusClickListener);
        btnExcused.setOnClickListener(statusClickListener);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    /**
     * Save manual attendance update to Firebase
     */
    private void saveManualAttendance(AttendanceStudentsAdapter.AttendanceStudentItem student, String newStatus, AlertDialog dialog) {
        if (student == null || selectedClassId == null || attendanceDate == null) {
            Toast.makeText(this, "Error: Missing required data", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show loading
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Updating attendance...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        // Generate consistent key (same format as website)
        String attendanceKey = "attendance_" + selectedClassId + "_" + student.id + "_" + attendanceDate.replace("-", "");
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        
        // Build attendance data
        Map<String, Object> attendanceData = new HashMap<>();
        attendanceData.put("id", student.attendanceKey != null ? student.attendanceKey : "manual_" + System.currentTimeMillis());
        attendanceData.put("class_id", selectedClassId);
        attendanceData.put("student_id", student.id);
        attendanceData.put("date", attendanceDate);
        attendanceData.put("status", newStatus);
        attendanceData.put("recorded_by", teacherId);
        attendanceData.put("created_at", timestamp);
        
        // Build backup structure (same as website)
        Map<String, Object> backupData = new HashMap<>();
        backupData.put("table", "attendance");
        backupData.put("operation", student.attendanceStatus != null && !student.attendanceStatus.isEmpty() ? "update" : "insert");
        backupData.put("data", attendanceData);
        backupData.put("timestamp", timestamp);
        backupData.put("server_time", System.currentTimeMillis() / 1000);
        
        // Save to Firebase
        DatabaseReference attendanceRef = FirebaseDatabase.getInstance()
                .getReference("attendance_system/attendance");
        
        attendanceRef.child(attendanceKey).setValue(backupData)
                .addOnSuccessListener(aVoid -> {
                    progressDialog.dismiss();
                    dialog.dismiss();
                    
                    // Update local data
                    student.attendanceStatus = newStatus;
                    student.attendanceKey = attendanceKey;
                    
                    // Refresh adapter
                    if (studentsAdapter != null) {
                        studentsAdapter.notifyDataSetChanged();
                    }
                    
                    Toast.makeText(this, "Attendance updated to " + newStatus.toUpperCase(), Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "✅ Manual attendance saved: " + student.fullName + " -> " + newStatus);
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Error updating attendance: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error saving manual attendance", e);
                });
    }
    
}

