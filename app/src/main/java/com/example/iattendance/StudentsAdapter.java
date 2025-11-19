package com.example.iattendance;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class StudentsAdapter extends RecyclerView.Adapter<StudentsAdapter.StudentViewHolder> {

    private List<TeacherStudentsActivity.StudentItem> students;

    public StudentsAdapter(List<TeacherStudentsActivity.StudentItem> students) {
        this.students = students;
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
        TextView studentIdText;
        TextView fullNameText;
        TextView emailText;
        TextView createdAtText;

        StudentViewHolder(@NonNull View itemView) {
            super(itemView);
            studentIdText = itemView.findViewById(R.id.studentIdText);
            fullNameText = itemView.findViewById(R.id.fullNameText);
            emailText = itemView.findViewById(R.id.emailText);
            createdAtText = itemView.findViewById(R.id.createdAtText);
        }
    }
}

