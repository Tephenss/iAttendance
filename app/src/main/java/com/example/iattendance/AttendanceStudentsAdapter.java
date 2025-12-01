package com.example.iattendance;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AttendanceStudentsAdapter extends RecyclerView.Adapter<AttendanceStudentsAdapter.StudentViewHolder> {
    private static final String TAG = "AttendanceStudentsAdapter";
    private List<AttendanceStudentItem> students;
    private OnStudentClickListener onStudentClickListener;
    
    public interface OnStudentClickListener {
        void onStudentClick(AttendanceStudentItem student);
    }
    
    public AttendanceStudentsAdapter(List<AttendanceStudentItem> students) {
        this.students = students != null ? students : new ArrayList<>();
    }
    
    public void setOnStudentClickListener(OnStudentClickListener listener) {
        this.onStudentClickListener = listener;
    }
    
    public void updateStudents(List<AttendanceStudentItem> newStudents) {
        this.students = newStudents != null ? newStudents : new ArrayList<>();
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public StudentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attendance_student, parent, false);
        return new StudentViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull StudentViewHolder holder, int position) {
        AttendanceStudentItem student = students.get(position);
        
        holder.studentName.setText(student.fullName);
        holder.studentId.setText(student.studentId);
        
        // Set status badge - only show if attendance was recorded
        if (student.attendanceStatus != null && !student.attendanceStatus.isEmpty()) {
            String status = student.attendanceStatus.toLowerCase();
            holder.statusBadge.setVisibility(View.VISIBLE);
            switch (status) {
                case "present":
                    holder.statusBadge.setText("Present");
                    holder.statusBadge.setBackgroundColor(0xFF4CAF50);
                    break;
                case "late":
                    holder.statusBadge.setText("Late");
                    holder.statusBadge.setBackgroundColor(0xFFFF9800);
                    break;
                case "absent":
                    holder.statusBadge.setText("Absent");
                    holder.statusBadge.setBackgroundColor(0xFFF44336);
                    break;
                case "excused":
                    holder.statusBadge.setText("Excused");
                    holder.statusBadge.setBackgroundColor(0xFF2196F3);
                    break;
                default:
                    holder.statusBadge.setVisibility(View.GONE);
                    break;
            }
        } else {
            // No attendance recorded yet - hide badge
            holder.statusBadge.setVisibility(View.GONE);
        }
        
        // Load profile picture
        if (student.profilePictureBase64 != null && !student.profilePictureBase64.isEmpty()) {
            Bitmap bitmap = decodeBase64ToBitmap(student.profilePictureBase64);
            if (bitmap != null) {
                Bitmap circularBitmap = getCircularBitmap(bitmap);
                holder.avatarImage.setImageBitmap(circularBitmap);
                holder.avatarImage.setVisibility(View.VISIBLE);
                holder.avatarPlaceholder.setVisibility(View.GONE);
            } else {
                holder.avatarImage.setVisibility(View.GONE);
                holder.avatarPlaceholder.setVisibility(View.VISIBLE);
            }
        } else {
            holder.avatarImage.setVisibility(View.GONE);
            holder.avatarPlaceholder.setVisibility(View.VISIBLE);
        }
        
        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            if (onStudentClickListener != null) {
                onStudentClickListener.onStudentClick(student);
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return students.size();
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
        paint.setColor(0xFF424242);
        canvas.drawOval(rectF, paint);
        
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        
        return output;
    }
    
    static class StudentViewHolder extends RecyclerView.ViewHolder {
        TextView studentName, studentId, statusBadge;
        ImageView avatarImage, avatarPlaceholder;
        
        StudentViewHolder(@NonNull View itemView) {
            super(itemView);
            studentName = itemView.findViewById(R.id.studentName);
            studentId = itemView.findViewById(R.id.studentId);
            statusBadge = itemView.findViewById(R.id.statusBadge);
            avatarImage = itemView.findViewById(R.id.avatarImage);
            avatarPlaceholder = itemView.findViewById(R.id.avatarPlaceholder);
        }
    }
    
    public static class AttendanceStudentItem {
        String id;
        String studentId;
        String fullName;
        String section;
        String yearLevel;
        String profilePictureBase64;
        String attendanceStatus; // "present", "late", "absent"
        String attendanceKey; // Firebase key for the attendance record
        
        AttendanceStudentItem(String id, String studentId, String fullName, String section, 
                             String yearLevel, String profilePictureBase64, String attendanceStatus) {
            this.id = id;
            this.studentId = studentId;
            this.fullName = fullName;
            this.section = section;
            this.yearLevel = yearLevel;
            this.profilePictureBase64 = profilePictureBase64;
            this.attendanceStatus = attendanceStatus;
            this.attendanceKey = null;
        }
        
        AttendanceStudentItem(String id, String studentId, String fullName, String section, 
                             String yearLevel, String profilePictureBase64, String attendanceStatus, String attendanceKey) {
            this.id = id;
            this.studentId = studentId;
            this.fullName = fullName;
            this.section = section;
            this.yearLevel = yearLevel;
            this.profilePictureBase64 = profilePictureBase64;
            this.attendanceStatus = attendanceStatus;
            this.attendanceKey = attendanceKey;
        }
    }
}

