package com.example.iattendance;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class SessionManager {
    private static final String TAG = "SessionManager";
    private static final String PREF_NAME = "iAttendanceSession";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_IS_VERIFIED = "isVerified";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_TYPE = "userType";
    private static final String KEY_FULL_NAME = "fullName";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_LAST_ACTIVE_TIME = "lastActiveTime";
    private static final String KEY_LOGIN_TIME = "loginTime"; // Time when user logged in
    private static final long SESSION_TIMEOUT = 24 * 60 * 60 * 1000; // 24 hours (1 day) in milliseconds

    private SharedPreferences pref;
    private SharedPreferences.Editor editor;
    private Context context;

    public SessionManager(Context context) {
        this.context = context;
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
    }

    public void createLoginSession(String userId, String userType, String fullName, String email) {
        Log.d(TAG, "Creating login session for: " + fullName);
        long currentTime = System.currentTimeMillis();
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putBoolean(KEY_IS_VERIFIED, false); // Not verified yet
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_USER_TYPE, userType);
        editor.putString(KEY_FULL_NAME, fullName);
        editor.putString(KEY_EMAIL, email);
        editor.putLong(KEY_LOGIN_TIME, currentTime); // Store login time for 24-hour expiration
        editor.putLong(KEY_LAST_ACTIVE_TIME, currentTime);
        editor.commit();
        Log.d(TAG, "Session created. Will expire in 24 hours.");
    }
    
    public void setVerified(boolean verified) {
        Log.d(TAG, "Setting verification status to: " + verified);
        editor.putBoolean(KEY_IS_VERIFIED, verified);
        editor.commit();
    }
    
    public boolean isVerified() {
        return pref.getBoolean(KEY_IS_VERIFIED, false);
    }

    public boolean isLoggedIn() {
        boolean isLoggedIn = pref.getBoolean(KEY_IS_LOGGED_IN, false);
        if (isLoggedIn) {
            long loginTime = pref.getLong(KEY_LOGIN_TIME, 0);
            long currentTime = System.currentTimeMillis();
            
            // Check if 24 hours have passed since login
            if (loginTime == 0 || currentTime - loginTime > SESSION_TIMEOUT) {
                Log.d(TAG, "Session expired after 24 hours. Login time: " + loginTime + ", Current time: " + currentTime);
                logout();
                return false;
            }
            
            // Update last active time for activity tracking (doesn't affect expiration)
            updateLastActiveTime();
        }
        return isLoggedIn;
    }

    public String getUserId() {
        return pref.getString(KEY_USER_ID, null);
    }

    public String getUserType() {
        return pref.getString(KEY_USER_TYPE, null);
    }

    public String getFullName() {
        return pref.getString(KEY_FULL_NAME, null);
    }

    public String getEmail() {
        return pref.getString(KEY_EMAIL, null);
    }

    public void logout() {
        Log.d(TAG, "Logging out user");
        editor.clear();
        editor.commit();
    }

    public void updateLastActiveTime() {
        // Update last active time to current time
        editor.putLong(KEY_LAST_ACTIVE_TIME, System.currentTimeMillis());
        editor.commit();
    }
}