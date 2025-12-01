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

import java.util.List;

public class StudentsAdapter extends RecyclerView.Adapter<StudentsAdapter.StudentViewHolder> {

    private List<TeacherStudentsActivity.StudentItem> students;
    private OnStudentClickListener listener;

    public interface OnStudentClickListener {
        void onStudentClick(TeacherStudentsActivity.StudentItem student);
    }

    public StudentsAdapter(List<TeacherStudentsActivity.StudentItem> students) {
        this.students = students;
    }

    public void setOnStudentClickListener(OnStudentClickListener listener) {
        this.listener = listener;
    }

    public void updateStudents(List<TeacherStudentsActivity.StudentItem> newStudents) {
        this.students = newStudents;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public StudentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_student, parent, false);
        return new StudentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StudentViewHolder holder, int position) {
        TeacherStudentsActivity.StudentItem student = students.get(position);
        holder.studentIdText.setText(student.studentId);
        holder.fullNameText.setText(student.fullName);
        holder.emailText.setText(student.email);
        holder.createdAtText.setText(student.createdAt != null && !student.createdAt.isEmpty() ? 
                                    formatDate(student.createdAt) : "N/A");
        
        // Load profile picture from base64 string
        if (student.profilePictureBase64 != null && !student.profilePictureBase64.isEmpty()) {
            Log.d("StudentsAdapter", "Loading profile picture for student: " + student.studentId + ", base64 length: " + student.profilePictureBase64.length());
            Bitmap bitmap = decodeBase64ToBitmap(student.profilePictureBase64);
            if (bitmap != null) {
                Log.d("StudentsAdapter", "Successfully decoded bitmap for student: " + student.studentId + ", size: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                // Convert to circular bitmap
                Bitmap circularBitmap = getCircularBitmap(bitmap);
                // Set the circular bitmap as the image source
                holder.avatarImage.setImageBitmap(circularBitmap);
                holder.avatarImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                holder.avatarImage.clearColorFilter();
                holder.avatarImage.setBackground(null); // Remove background when showing actual image
            } else {
                Log.e("StudentsAdapter", "Failed to decode bitmap for student: " + student.studentId);
                // If decoding fails, show default icon
                holder.avatarImage.setImageResource(R.drawable.ic_person);
                holder.avatarImage.setColorFilter(0xFF2196F3); // Blue color
                holder.avatarImage.setBackgroundResource(R.drawable.student_avatar_background);
            }
        } else {
            Log.d("StudentsAdapter", "No profile picture for student: " + student.studentId);
            // No profile picture, show default icon
            holder.avatarImage.setImageResource(R.drawable.ic_person);
            holder.avatarImage.setColorFilter(0xFF2196F3); // Blue color
            holder.avatarImage.setBackgroundResource(R.drawable.student_avatar_background);
        }
        
        // Make card clickable
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onStudentClick(student);
            }
        });
        
        // Add ripple effect
        holder.itemView.setClickable(true);
        holder.itemView.setFocusable(true);
    }
    
    private Bitmap decodeBase64ToBitmap(String base64String) {
        try {
            if (base64String == null || base64String.isEmpty()) {
                Log.e("StudentsAdapter", "Base64 string is null or empty");
                return null;
            }
            
            // Remove data URL prefix if present (e.g., "data:image/png;base64,")
            String base64Image = base64String.trim();
            if (base64Image.contains(",")) {
                base64Image = base64Image.substring(base64Image.indexOf(",") + 1);
            }
            
            // Remove any whitespace
            base64Image = base64Image.replaceAll("\\s", "");
            
            Log.d("StudentsAdapter", "Decoding base64 string, length: " + base64Image.length());
            
            // Decode base64 string to byte array
            byte[] decodedBytes = Base64.decode(base64Image, Base64.DEFAULT);
            
            if (decodedBytes == null || decodedBytes.length == 0) {
                Log.e("StudentsAdapter", "Decoded bytes are null or empty");
                return null;
            }
            
            Log.d("StudentsAdapter", "Decoded bytes length: " + decodedBytes.length);
            
            // Convert byte array to Bitmap
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            
            if (bitmap == null) {
                Log.e("StudentsAdapter", "Failed to decode byte array to bitmap");
            } else {
                Log.d("StudentsAdapter", "Successfully created bitmap: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            }
            
            return bitmap;
        } catch (Exception e) {
            Log.e("StudentsAdapter", "Error decoding base64: " + e.getMessage(), e);
            e.printStackTrace();
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
            
            // Draw transparent background
            canvas.drawARGB(0, 0, 0, 0);
            
            // Draw circular mask
            canvas.drawOval(rectF, paint);
            
            // Apply mask to bitmap
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            
            // Scale bitmap to fit the circle
            int x = (bitmap.getWidth() - size) / 2;
            int y = (bitmap.getHeight() - size) / 2;
            Rect srcRect = new Rect(x, y, x + size, y + size);
            canvas.drawBitmap(bitmap, srcRect, rect, paint);
            
            return output;
        } catch (Exception e) {
            Log.e("StudentsAdapter", "Error creating circular bitmap: " + e.getMessage(), e);
            return bitmap; // Return original bitmap if circular conversion fails
        }
    }

    @Override
    public int getItemCount() {
        return students != null ? students.size() : 0;
    }

    private String formatDate(String dateStr) {
        try {
            if (dateStr == null || dateStr.isEmpty()) return "N/A";
            // Try to format date string (assuming format like "2025-01-15 10:30:00")
            if (dateStr.length() >= 10) {
                return dateStr.substring(0, 10); // Return just the date part
            }
            return dateStr;
        } catch (Exception e) {
            return dateStr;
        }
    }

    static class StudentViewHolder extends RecyclerView.ViewHolder {
        ImageView avatarImage;
        TextView studentIdText;
        TextView fullNameText;
        TextView emailText;
        TextView createdAtText;

        StudentViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImage = itemView.findViewById(R.id.studentAvatarImage);
            studentIdText = itemView.findViewById(R.id.studentIdText);
            fullNameText = itemView.findViewById(R.id.fullNameText);
            emailText = itemView.findViewById(R.id.emailText);
            createdAtText = itemView.findViewById(R.id.createdAtText);
        }
    }
}

