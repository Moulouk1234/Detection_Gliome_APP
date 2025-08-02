package com.example.detection_gliome;

import android.net.Uri;
import okhttp3.Call;

public class UploadItem {
    private final String fileName;
    private final Uri fileUri;
    private int progress;
    private boolean isPaused;
    private boolean completed;
    private boolean isRemote;
    private Call call;

    public UploadItem(String fileName, Uri fileUri) {
        this(fileName, fileUri, false);
    }

    public UploadItem(String fileName, Uri fileUri, boolean isRemote) {
        this.fileName = fileName;
        this.fileUri = fileUri;
        this.progress = 0;
        this.isPaused = false;
        this.completed = false;
        this.isRemote = isRemote;
        this.call = null;
    }

    // Getters and Setters
    public String getFileName() { return fileName; }
    public Uri getFileUri() { return fileUri; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public boolean isPaused() { return isPaused; }
    public void setPaused(boolean paused) { isPaused = paused; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public boolean isRemote() { return isRemote; }
    public void setRemote(boolean remote) { isRemote = remote; }
    public Call getCall() { return call; }
    public void setCall(Call call) { this.call = call; }

    // Updated to exclude .nii files from image processing
    public boolean isImageFile() {
        if (fileUri == null) return false;

        String path = fileUri.toString().toLowerCase();
        return path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                path.endsWith(".png") || path.endsWith(".gif") ||
                path.endsWith(".bmp") || path.endsWith(".webp");
    }

    // New method to check for NIfTI files
    public boolean isNiiFile() {
        if (fileUri == null) return false;
        String path = fileUri.toString().toLowerCase();
        return path.endsWith(".nii") || path.endsWith(".nii.gz");
    }
}