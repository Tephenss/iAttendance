package com.example.iattendance;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AttendanceListAdapter extends RecyclerView.Adapter<AttendanceListAdapter.AttendanceViewHolder> {

    private List<StudentAttendanceActivity.AttendanceItem> attendanceItems;

    public AttendanceListAdapter(List<StudentAttendanceActivity.AttendanceItem> attendanceItems) {
        this.attendanceItems = attendanceItems;
    }

    @NonNull
    @Override
    public AttendanceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_student_attendance, parent, false);
        return new AttendanceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AttendanceViewHolder holder, int position) {
        StudentAttendanceActivity.AttendanceItem item = attendanceItems.get(position);
        
        holder.subjectCodeText.setText(item.subjectCode);
        holder.subjectNameText.setText(item.subjectName);
        holder.sectionText.setText("Section " + item.section);
        holder.timeText.setText(item.timeDisplay);
        
        // Set status badge
        if (item.status == null) {
            holder.statusBadge.setText("Not Recorded");
            holder.statusBadge.setBackgroundColor(Color.parseColor("#9E9E9E")); // Gray
            holder.statusBadge.setTextColor(Color.WHITE);
        } else if (item.status.equalsIgnoreCase("present")) {
            holder.statusBadge.setText("Present");
            holder.statusBadge.setBackgroundColor(Color.parseColor("#4CAF50")); // Green
            holder.statusBadge.setTextColor(Color.WHITE);
        } else if (item.status.equalsIgnoreCase("late")) {
            holder.statusBadge.setText("Late");
            holder.statusBadge.setBackgroundColor(Color.parseColor("#FF9800")); // Orange
            holder.statusBadge.setTextColor(Color.WHITE);
        } else if (item.status.equalsIgnoreCase("absent")) {
            holder.statusBadge.setText("Absent");
            holder.statusBadge.setBackgroundColor(Color.parseColor("#F44336")); // Red
            holder.statusBadge.setTextColor(Color.WHITE);
        } else if (item.status.equalsIgnoreCase("excused")) {
            holder.statusBadge.setText("Excused");
            holder.statusBadge.setBackgroundColor(Color.parseColor("#2196F3")); // Blue
            holder.statusBadge.setTextColor(Color.WHITE);
        } else {
            holder.statusBadge.setText(item.status);
            holder.statusBadge.setBackgroundColor(Color.parseColor("#757575")); // Gray
            holder.statusBadge.setTextColor(Color.WHITE);
        }
    }

    @Override
    public int getItemCount() {
        return attendanceItems.size();
    }

    public void updateAttendanceList(List<StudentAttendanceActivity.AttendanceItem> newItems) {
        this.attendanceItems = newItems;
        notifyDataSetChanged();
    }

    static class AttendanceViewHolder extends RecyclerView.ViewHolder {
        TextView subjectCodeText;
        TextView subjectNameText;
        TextView sectionText;
        TextView timeText;
        TextView statusBadge;
        CardView cardView;

        AttendanceViewHolder(@NonNull View itemView) {
            super(itemView);
            subjectCodeText = itemView.findViewById(R.id.subjectCodeText);
            subjectNameText = itemView.findViewById(R.id.subjectNameText);
            sectionText = itemView.findViewById(R.id.sectionText);
            timeText = itemView.findViewById(R.id.timeText);
            statusBadge = itemView.findViewById(R.id.statusBadge);
            cardView = itemView.findViewById(R.id.attendanceCard);
        }
    }
}

