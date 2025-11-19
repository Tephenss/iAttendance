package com.example.iattendance;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import at.favre.lib.crypto.bcrypt.BCrypt;

public class FirebaseAuthHelper {
    private static final String TAG = "FirebaseAuthHelper";
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private Context context;
    
    public interface AuthCallback {
        void onSuccess(String userType, String userId, String fullName, String email);
        void onError(String errorMessage);
    }
    
    public FirebaseAuthHelper(Context context) {
        this.context = context;
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
    }
    
    public void signInWithIdAndPassword(String id, String password, AuthCallback callback) {
        Log.d(TAG, "=== LOGIN ATTEMPT ===");
        Log.d(TAG, "ID: " + id);
        Log.d(TAG, "Password: " + password);
        
        // Test Firebase connection first
        testFirebaseConnection(callback);
        
        // Search for user in Firebase database using ID
        searchUserInDatabase(id, password, callback);
    }
    
    private void testFirebaseConnection(AuthCallback callback) {
        Log.d(TAG, "=== TESTING FIREBASE CONNECTION ===");
        Log.d(TAG, "Database URL: " + FirebaseDatabase.getInstance().getReference().toString());
        
        // Try to read from root
        mDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d(TAG, "Firebase connection successful!");
                Log.d(TAG, "Root data exists: " + snapshot.exists());
                Log.d(TAG, "Root children count: " + snapshot.getChildrenCount());
                
                if (snapshot.exists()) {
                    Log.d(TAG, "Root data: " + snapshot.getValue());
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase connection failed: " + error.getMessage());
                Log.e(TAG, "Error code: " + error.getCode());
                Log.e(TAG, "Error details: " + error.getDetails());
            }
        });
    }
    
    private void checkFirebaseStructure(AuthCallback callback) {
        // Check the root structure
        mDatabase.child("attendance_system").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d(TAG, "Firebase root structure: " + snapshot.getChildrenCount() + " children");
                for (DataSnapshot child : snapshot.getChildren()) {
                    Log.d(TAG, "Child: " + child.getKey() + " - " + child.getChildrenCount() + " records");
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error checking Firebase structure: " + error.getMessage());
            }
        });
    }
    
    private void searchUserInDatabase(String id, String password, AuthCallback callback) {
        Log.d(TAG, "=== SEARCHING USER IN DATABASE ===");
        
        // Search in students first
        searchInStudents(id, password, callback);
    }
    
    private void searchInStudents(String id, String password, AuthCallback callback) {
        Log.d(TAG, "Searching in students table...");
        
        mDatabase.child("attendance_system").child("students").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d(TAG, "Students data received: " + snapshot.getChildrenCount() + " records");
                
                // Look through all student records
                for (DataSnapshot studentSnapshot : snapshot.getChildren()) {
                    Log.d(TAG, "Checking student record: " + studentSnapshot.getKey());
                    
                    // Check if data exists under 'data' child (based on PHP backup structure)
                    DataSnapshot dataSnapshot = studentSnapshot.child("data");
                    if (dataSnapshot.exists()) {
                        Log.d(TAG, "Found 'data' child in record");
                        
                        // Debug: Print all fields in data child
                        for (DataSnapshot field : dataSnapshot.getChildren()) {
                            Log.d(TAG, "Data field: " + field.getKey() + " = " + field.getValue());
                        }
                        
                        // Try different possible ID field names based on PHP structure
                        String[] idFields = {"student_id", "id", "user_id", "username"};
                        String[] passwordFields = {"password", "pass", "pwd"};
                        
                        for (String idField : idFields) {
                            String foundId = dataSnapshot.child(idField).getValue(String.class);
                            Log.d(TAG, "Checking ID field '" + idField + "': " + foundId);
                            
                            if (foundId != null && id.equals(foundId)) {
                                Log.d(TAG, "Found matching ID: " + foundId + " in field: " + idField);
                                
                                // Check password
                                for (String passwordField : passwordFields) {
                                    String foundPassword = dataSnapshot.child(passwordField).getValue(String.class);
                                    Log.d(TAG, "Checking password field '" + passwordField + "': " + foundPassword);

                                    if (foundPassword != null) {
                                        // Check if password is hashed (starts with $2y$ for bcrypt)
                                        boolean isPasswordValid;
                                        if (foundPassword.startsWith("$2y$") || foundPassword.startsWith("$2a$") || foundPassword.startsWith("$2b$")) {
                                            // Password is hashed, use BCrypt verification
                                            isPasswordValid = BCrypt.verifyer().verify(password.toCharArray(), foundPassword).verified;
                                            Log.d(TAG, "Using BCrypt verification for hashed password");
                                        } else {
                                            // Password is plain text, use direct comparison
                                            isPasswordValid = password.equals(foundPassword);
                                            Log.d(TAG, "Using plain text comparison");
                                        }

                                        if (isPasswordValid) {
                                            Log.d(TAG, "Found matching password in field: " + passwordField);
                                            
                                            // Check if account is soft deleted - check both data child and root level
                                            Object isDeletedObj = dataSnapshot.child("is_deleted").getValue();
                                            if (isDeletedObj == null) {
                                                // Also check at root level of student record
                                                isDeletedObj = studentSnapshot.child("is_deleted").getValue();
                                            }
                                            
                                            boolean isDeleted = false;
                                            if (isDeletedObj != null) {
                                                Log.d(TAG, "is_deleted value found: " + isDeletedObj + " (type: " + isDeletedObj.getClass().getSimpleName() + ")");
                                                if (isDeletedObj instanceof Boolean) {
                                                    isDeleted = (Boolean) isDeletedObj;
                                                } else if (isDeletedObj instanceof Number) {
                                                    isDeleted = ((Number) isDeletedObj).intValue() == 1;
                                                } else if (isDeletedObj instanceof String) {
                                                    isDeleted = "1".equals(isDeletedObj) || "true".equalsIgnoreCase((String) isDeletedObj);
                                                }
                                                Log.d(TAG, "is_deleted parsed as: " + isDeleted);
                                            } else {
                                                Log.d(TAG, "is_deleted field not found - assuming account is active");
                                            }
                                            
                                            if (isDeleted) {
                                                Log.e(TAG, "STUDENT ACCOUNT IS DELETED - Login blocked");
                                                callback.onError("This account has been deleted. Please contact the administrator to restore your account.");
                                                return;
                                            }

                                            String firstName = dataSnapshot.child("first_name").getValue(String.class);
                                            String lastName = dataSnapshot.child("last_name").getValue(String.class);
                                            String email = dataSnapshot.child("email").getValue(String.class);
                                            String fullName = (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "").trim();

                                            Log.d(TAG, "STUDENT LOGIN SUCCESS: " + fullName);
                                            callback.onSuccess("student", id, fullName, email);
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Log.d(TAG, "No 'data' child found in record: " + studentSnapshot.getKey());
                    }
                }
                
                Log.d(TAG, "Student not found, searching teachers...");
                // If not found in students, search in teachers
                searchInTeachers(id, password, callback);
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error searching students: " + error.getMessage());
                callback.onError("Database error: " + error.getMessage());
            }
        });
    }
    
    
    private void searchInTeachers(String id, String password, AuthCallback callback) {
        Log.d(TAG, "Searching in teachers table...");
        
        mDatabase.child("attendance_system").child("teachers").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d(TAG, "Teachers data received: " + snapshot.getChildrenCount() + " records");
                
                // Look through all teacher records
                for (DataSnapshot teacherSnapshot : snapshot.getChildren()) {
                    Log.d(TAG, "Checking teacher record: " + teacherSnapshot.getKey());
                    
                    // Check if data exists under 'data' child (based on PHP backup structure)
                    DataSnapshot dataSnapshot = teacherSnapshot.child("data");
                    if (dataSnapshot.exists()) {
                        Log.d(TAG, "Found 'data' child in teacher record");
                        
                        // Debug: Print all fields in data child
                        for (DataSnapshot field : dataSnapshot.getChildren()) {
                            Log.d(TAG, "Teacher data field: " + field.getKey() + " = " + field.getValue());
                        }
                        
                        // Try different possible ID field names based on PHP structure
                        String[] idFields = {"teacher_id", "id", "user_id", "username"};
                        String[] passwordFields = {"password", "pass", "pwd"};
                        
                        for (String idField : idFields) {
                            String foundId = dataSnapshot.child(idField).getValue(String.class);
                            Log.d(TAG, "Checking teacher ID field '" + idField + "': " + foundId);
                            
                            if (foundId != null && id.equals(foundId)) {
                                Log.d(TAG, "Found matching teacher ID: " + foundId + " in field: " + idField);
                                
                                // Check password
                                for (String passwordField : passwordFields) {
                                    String foundPassword = dataSnapshot.child(passwordField).getValue(String.class);
                                    Log.d(TAG, "Checking teacher password field '" + passwordField + "': " + foundPassword);

                                    if (foundPassword != null) {
                                        // Check if password is hashed (starts with $2y$ for bcrypt)
                                        boolean isPasswordValid;
                                        if (foundPassword.startsWith("$2y$") || foundPassword.startsWith("$2a$") || foundPassword.startsWith("$2b$")) {
                                            // Password is hashed, use BCrypt verification
                                            isPasswordValid = BCrypt.verifyer().verify(password.toCharArray(), foundPassword).verified;
                                            Log.d(TAG, "Using BCrypt verification for hashed teacher password");
                                        } else {
                                            // Password is plain text, use direct comparison
                                            isPasswordValid = password.equals(foundPassword);
                                            Log.d(TAG, "Using plain text comparison for teacher password");
                                        }

                                        if (isPasswordValid) {
                                            Log.d(TAG, "Found matching teacher password in field: " + passwordField);
                                            
                                            // Check if account is soft deleted - check both data child and root level
                                            Object isDeletedObj = dataSnapshot.child("is_deleted").getValue();
                                            if (isDeletedObj == null) {
                                                // Also check at root level of teacher record
                                                isDeletedObj = teacherSnapshot.child("is_deleted").getValue();
                                            }
                                            
                                            boolean isDeleted = false;
                                            if (isDeletedObj != null) {
                                                Log.d(TAG, "is_deleted value found: " + isDeletedObj + " (type: " + isDeletedObj.getClass().getSimpleName() + ")");
                                                if (isDeletedObj instanceof Boolean) {
                                                    isDeleted = (Boolean) isDeletedObj;
                                                } else if (isDeletedObj instanceof Number) {
                                                    isDeleted = ((Number) isDeletedObj).intValue() == 1;
                                                } else if (isDeletedObj instanceof String) {
                                                    isDeleted = "1".equals(isDeletedObj) || "true".equalsIgnoreCase((String) isDeletedObj);
                                                }
                                                Log.d(TAG, "is_deleted parsed as: " + isDeleted);
                                            } else {
                                                Log.d(TAG, "is_deleted field not found - assuming account is active");
                                            }
                                            
                                            if (isDeleted) {
                                                Log.e(TAG, "TEACHER ACCOUNT IS DELETED - Login blocked");
                                                callback.onError("This account has been deleted. Please contact the administrator to restore your account.");
                                                return;
                                            }

                                            String firstName = dataSnapshot.child("first_name").getValue(String.class);
                                            String lastName = dataSnapshot.child("last_name").getValue(String.class);
                                            String email = dataSnapshot.child("email").getValue(String.class);
                                            String fullName = (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "").trim();

                                            Log.d(TAG, "TEACHER LOGIN SUCCESS: " + fullName);
                                            callback.onSuccess("teacher", id, fullName, email);
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Log.d(TAG, "No 'data' child found in teacher record: " + teacherSnapshot.getKey());
                    }
                }
                
                Log.d(TAG, "Teacher not found, searching admins...");
                // If not found in teachers, search in admins
                searchInAdmins(id, password, callback);
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error searching teachers: " + error.getMessage());
                callback.onError("Database error: " + error.getMessage());
            }
        });
    }
    
    private void searchInAdmins(String id, String password, AuthCallback callback) {
        Log.d(TAG, "Searching in admins table...");
        
        mDatabase.child("attendance_system").child("admins").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d(TAG, "Admins data received: " + snapshot.getChildrenCount() + " records");
                
                // Look through all admin records
                for (DataSnapshot adminSnapshot : snapshot.getChildren()) {
                    Log.d(TAG, "Checking admin record: " + adminSnapshot.getKey());
                    
                    // Check if data exists under 'data' child (based on PHP backup structure)
                    DataSnapshot dataSnapshot = adminSnapshot.child("data");
                    if (dataSnapshot.exists()) {
                        Log.d(TAG, "Found 'data' child in admin record");
                        
                        // Debug: Print all fields in data child
                        for (DataSnapshot field : dataSnapshot.getChildren()) {
                            Log.d(TAG, "Admin data field: " + field.getKey() + " = " + field.getValue());
                        }
                        
                        // Try different possible ID field names based on PHP structure
                        String[] idFields = {"admin_id", "id", "user_id", "username"};
                        String[] passwordFields = {"password", "pass", "pwd"};
                        
                        for (String idField : idFields) {
                            String foundId = dataSnapshot.child(idField).getValue(String.class);
                            Log.d(TAG, "Checking admin ID field '" + idField + "': " + foundId);
                            
                            if (foundId != null && id.equals(foundId)) {
                                Log.d(TAG, "Found matching admin ID: " + foundId + " in field: " + idField);
                                
                                // Check password
                                for (String passwordField : passwordFields) {
                                    String foundPassword = dataSnapshot.child(passwordField).getValue(String.class);
                                    Log.d(TAG, "Checking admin password field '" + passwordField + "': " + foundPassword);

                                    if (foundPassword != null) {
                                        // Check if password is hashed (starts with $2y$ for bcrypt)
                                        boolean isPasswordValid;
                                        if (foundPassword.startsWith("$2y$") || foundPassword.startsWith("$2a$") || foundPassword.startsWith("$2b$")) {
                                            // Password is hashed, use BCrypt verification
                                            isPasswordValid = BCrypt.verifyer().verify(password.toCharArray(), foundPassword).verified;
                                            Log.d(TAG, "Using BCrypt verification for hashed admin password");
                                        } else {
                                            // Password is plain text, use direct comparison
                                            isPasswordValid = password.equals(foundPassword);
                                            Log.d(TAG, "Using plain text comparison for admin password");
                                        }

                                        if (isPasswordValid) {
                                            Log.d(TAG, "Found matching admin password in field: " + passwordField);

                                            String firstName = dataSnapshot.child("first_name").getValue(String.class);
                                            String lastName = dataSnapshot.child("last_name").getValue(String.class);
                                            String email = dataSnapshot.child("email").getValue(String.class);
                                            String fullName = (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "").trim();

                                            Log.d(TAG, "ADMIN LOGIN SUCCESS: " + fullName);
                                            callback.onSuccess("admin", id, fullName, email);
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Log.d(TAG, "No 'data' child found in admin record: " + adminSnapshot.getKey());
                    }
                }
                
                Log.d(TAG, "User not found in any table");
                // User not found in any table
                callback.onError("Invalid ID or password");
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error searching admins: " + error.getMessage());
                callback.onError("Database error: " + error.getMessage());
            }
        });
    }
    
    public void signOut() {
        mAuth.signOut();
    }
    
    public FirebaseUser getCurrentUser() {
        return mAuth.getCurrentUser();
    }
}
