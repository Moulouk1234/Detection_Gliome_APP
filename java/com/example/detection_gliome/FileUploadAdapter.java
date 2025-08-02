package com.example.detection_gliome;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class FileUploadAdapter extends RecyclerView.Adapter<FileUploadAdapter.UploadViewHolder> {
    private final Context context;
    private final List<UploadItem> uploadItems;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onPauseClick(int position);
        void onCancelClick(int position);
    }

    public FileUploadAdapter(Context context, List<UploadItem> uploadItems, OnItemClickListener listener) {
        this.context = context;
        this.uploadItems = uploadItems;
        this.listener = listener;
    }

    @NonNull
    @Override
    public UploadViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_file_upload, parent, false);
        return new UploadViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UploadViewHolder holder, int position) {
        UploadItem item = uploadItems.get(position);
        holder.fileName.setText(item.getFileName());
        holder.progressBar.setProgress(item.getProgress());
        holder.progressText.setText(item.getProgress() + "%");

        // Update UI based on state
        if (item.isCompleted()) {
            holder.pausePlayButton.setImageResource(R.drawable.result_normal);
            holder.progressText.setText("Upload complete");
        } else if (item.isPaused()) {
            holder.pausePlayButton.setImageResource(R.drawable.play);
            holder.progressText.setText("Paused - " + item.getProgress() + "%");
        } else {
            holder.pausePlayButton.setImageResource(R.drawable.pause);
            holder.progressText.setText("Uploading - " + item.getProgress() + "%");
        }

        // Set click listeners
        holder.pausePlayButton.setOnClickListener(v -> listener.onPauseClick(position));
        holder.deleteButton.setOnClickListener(v -> listener.onCancelClick(position));
    }

    @Override
    public int getItemCount() {
        return uploadItems.size();
    }

    static class UploadViewHolder extends RecyclerView.ViewHolder {
        TextView fileName, progressText;
        ProgressBar progressBar;
        ImageView pausePlayButton, deleteButton;

        public UploadViewHolder(@NonNull View itemView) {
            super(itemView);
            fileName = itemView.findViewById(R.id.fileName);
            progressBar = itemView.findViewById(R.id.progressBar);
            progressText = itemView.findViewById(R.id.progressText);
            pausePlayButton = itemView.findViewById(R.id.pausePlayButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
}