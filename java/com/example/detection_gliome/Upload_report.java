package com.example.detection_gliome;
import android.Manifest;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

public class Upload_report extends AppCompatActivity implements FileUploadAdapter.OnItemClickListener {
    private static final String UPLOAD_URL = "http://10.0.2.2:90/backend_php/Patient/upload.php";
    private static final String GET_IMAGES_URL = "http://10.0.2.2:90/backend_php/model/get_patient_images.php?id_patient=";
    private static final String UPDATE_STATUT_URL = "http://10.0.2.2:90/backend_php/model/update_status.php";

    private static final String TAG = "GliomaDetection";
    private Interpreter tflite;

    private RecyclerView uploadRecyclerView;
    private List<UploadItem> uploadItems = new ArrayList<>();
    private FileUploadAdapter uploadAdapter;
    private Uri selectedFileUri;
    private String idPatient;
    private ProgressBar progressBarCircle;
    private boolean isUploading = false;
    private int completedUploads = 0;

    // RecyclerView components
    private RecyclerView imageRecyclerView;
    private ImageAdapter imageAdapter;
    private List<String> imageUrls = new ArrayList<>();
    private OkHttpClient client;
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int CAMERA_REQUEST_CODE = 101;
    private ImageView cameraIcon;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_upload_report);
        client = new OkHttpClient();

        idPatient = getIntent().getStringExtra("PATIENT_ID");
        if (idPatient == null) {
            Toast.makeText(this, "Error: No Patient ID", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Initialize views
        ImageView selectFileButton = findViewById(R.id.selectFileButton);
        Button uploadButton = findViewById(R.id.uploadButton);
        progressBarCircle = findViewById(R.id.progressBarCircle);
        imageRecyclerView = findViewById(R.id.imageRecyclerView);
        uploadRecyclerView = findViewById(R.id.uploadRecyclerView);

        // Initialize uploadAdapter
        uploadAdapter = new FileUploadAdapter(this, uploadItems, this);
        uploadRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        uploadRecyclerView.setAdapter(uploadAdapter);

        // Setup RecyclerView for images
        setupImageRecyclerView();

        // Fetch existing images
        fetchPatientImages();

        selectFileButton.setOnClickListener(v -> filePickerLauncher.launch("*/*"));

        uploadButton.setOnClickListener(v -> {
            if (isUploading) {
                Toast.makeText(this, "Upload already in progress", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!uploadItems.isEmpty()) {
                isUploading = true;
                completedUploads = 0;
                uploadAllFiles();
            } else if (selectedFileUri != null) {
                isUploading = true;
                new UploadFileTask(selectedFileUri).execute();
            } else {
                Toast.makeText(Upload_report.this, "Please select files first!", Toast.LENGTH_SHORT).show();
            }
        });

        try {
            long startTime = System.currentTimeMillis();
            tflite = new Interpreter(loadModelFile());
            long loadTime = System.currentTimeMillis() - startTime;

            Log.i(TAG, "Model loaded successfully in " + loadTime + "ms");
            Toast.makeText(this, "AI Model Ready", Toast.LENGTH_SHORT).show();

            // Print model input/output details
            if (tflite != null) {
                Log.d(TAG, "Model input tensor count: " + tflite.getInputTensorCount());
                for (int i = 0; i < tflite.getInputTensorCount(); i++) {
                    Tensor tensor = tflite.getInputTensor(i);
                    Log.d(TAG, String.format("Input %d: name=%s, shape=%s, type=%s",
                            i, tensor.name(), Arrays.toString(tensor.shape()), tensor.dataType()));
                }
                for (int i = 0; i < tflite.getOutputTensorCount(); i++) {
                    Tensor tensor = tflite.getOutputTensor(i);
                    Log.d(TAG, String.format("Output %d: name=%s, shape=%s, type=%s",
                            i, tensor.name(), Arrays.toString(tensor.shape()), tensor.dataType()));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Model loading failed", e);
            Toast.makeText(this, "Failed to load AI model", Toast.LENGTH_LONG).show();
            finish();
        }


        cameraIcon = findViewById(R.id.cameraicon);
        cameraIcon.setOnClickListener(v -> openCamera());
    }
    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // Show explanation if needed
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                Toast.makeText(this, "Camera permission is needed to take photos", Toast.LENGTH_LONG).show();
            }
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE);
        } else {
            launchCameraIntent();
        }
    }
    private void launchCameraIntent() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        List<ResolveInfo> cameraApps = getPackageManager().queryIntentActivities(cameraIntent, PackageManager.MATCH_DEFAULT_ONLY);

        if (cameraApps.isEmpty()) {
            Toast.makeText(this,
                    "No camera app detected. Please install a camera app (e.g., Google Camera).",
                    Toast.LENGTH_LONG).show();

            try {
                Intent playStoreIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=com.google.android.GoogleCamera"));
                startActivity(playStoreIntent);
            } catch (ActivityNotFoundException e) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.GoogleCamera"));
                startActivity(browserIntent);
            }
            return;
        }

        startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCameraIntent();
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK) {
            Bitmap photo = (Bitmap) data.getExtras().get("data");
            // Handle the captured image here
            // Example: cameraIcon.setImageBitmap(photo);
        }
    }
    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd("mobilenet_model.tflite");
        FileInputStream inputStream = fileDescriptor.createInputStream();
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void setupImageRecyclerView() {
        imageAdapter = new ImageAdapter(this, imageUrls);
        imageRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        imageRecyclerView.setAdapter(imageAdapter);
    }

    private void fetchPatientImages() {
        if (idPatient == null) {
            Toast.makeText(this, "Patient ID not available", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = GET_IMAGES_URL + idPatient;
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(Upload_report.this, "Failed to fetch images: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() ->
                            Toast.makeText(Upload_report.this, "Server error: " + response.code(), Toast.LENGTH_SHORT).show());
                    return;
                }

                try {
                    String responseData = response.body().string();
                    JSONObject json = new JSONObject(responseData);

                    if (json.getBoolean("success")) {
                        JSONArray images = json.getJSONArray("images");
                        imageUrls.clear();

                        for (int i = 0; i < images.length(); i++) {
                            String imageUrl = images.getString(i);
                            if (!imageUrl.startsWith("http")) {
                                imageUrl = "http://10.0.2.2:90" + imageUrl;
                            }
                            imageUrls.add(imageUrl);
                        }

                        runOnUiThread(() -> {
                            imageAdapter.notifyDataSetChanged();
                            if (imageUrls.isEmpty()) {
                                Toast.makeText(Upload_report.this, "No images found for this patient", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        String message = json.getString("message");
                        runOnUiThread(() ->
                                Toast.makeText(Upload_report.this, message, Toast.LENGTH_SHORT).show());
                    }
                } catch (JSONException e) {
                    runOnUiThread(() ->
                            Toast.makeText(Upload_report.this, "Error parsing response", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private final ActivityResultLauncher<String> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && uploadAdapter != null) {
                    String fileName = getFileName(uri);
                    uploadItems.add(new UploadItem(fileName, uri));
                    uploadAdapter.notifyItemInserted(uploadItems.size() - 1);
                    Toast.makeText(this, "File selected: " + fileName, Toast.LENGTH_SHORT).show();
                }
            });

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private void removeUploadItem(int position) {
        if (position >= 0 && position < uploadItems.size()) {
            uploadItems.remove(position);
            uploadAdapter.notifyItemRemoved(position);
            if (uploadItems.isEmpty()) {
                progressBarCircle.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onPauseClick(int position) {
        UploadItem item = uploadItems.get(position);
        if (item.isCompleted()) return;

        item.setPaused(!item.isPaused());
        if (item.isPaused()) {
            if (item.getCall() != null) {
                item.getCall().cancel();
            }
        } else {
            uploadFile(position);
        }
        uploadAdapter.notifyItemChanged(position);
    }

    @Override
    public void onCancelClick(int position) {
        UploadItem item = uploadItems.get(position);
        if (item.getCall() != null) {
            item.getCall().cancel();
        }
        removeUploadItem(position);
    }

    private static class ProgressRequestBody extends RequestBody {
        private final File file;
        private final String contentType;
        private final ProgressListener listener;
        private static final int DEFAULT_BUFFER_SIZE = 2048;

        public ProgressRequestBody(File file, String contentType, ProgressListener listener) {
            this.file = file;
            this.contentType = contentType;
            this.listener = listener;
        }

        @Override public MediaType contentType() { return MediaType.parse(contentType); }
        @Override public long contentLength() { return file.length(); }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            long fileLength = file.length();
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            long uploaded = 0;

            try (FileInputStream in = new FileInputStream(file)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    uploaded += read;
                    listener.onProgress(uploaded, fileLength);
                    sink.write(buffer, 0, read);
                }
            }
        }

        public interface ProgressListener {
            void onProgress(long bytesWritten, long contentLength);
        }
    }

    private void uploadAllFiles() {
        progressBarCircle.setVisibility(View.VISIBLE);
        for (int i = 0; i < uploadItems.size(); i++) {
            if (!uploadItems.get(i).isPaused()) {
                uploadFile(i);
            }
        }
    }

    private void uploadFile(final int position) {
        UploadItem uploadItem = uploadItems.get(position);
        File file = new File(Objects.requireNonNull(FileUtils.getPath(this, uploadItem.getFileUri())));

        String mimeType = getContentResolver().getType(uploadItem.getFileUri());
        if (mimeType == null) mimeType = "application/octet-stream";

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", uploadItem.getFileName(),
                        new ProgressRequestBody(file, mimeType,
                                (bytesWritten, contentLength) -> {
                                    int progress = (int) (100 * bytesWritten / contentLength);
                                    runOnUiThread(() -> {
                                        uploadItems.get(position).setProgress(progress);
                                        uploadAdapter.notifyItemChanged(position);
                                    });
                                }))
                .addFormDataPart("id_patient", idPatient)
                .build();

        Request request = new Request.Builder()
                .url(UPLOAD_URL)
                .post(requestBody)
                .build();

        Call call = client.newCall(request);
        uploadItem.setCall(call);

        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (call.isCanceled()) return;
                runOnUiThread(() -> {
                    Toast.makeText(Upload_report.this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    removeUploadItem(position);
                    checkAllUploadsCompleted();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String responseData = response.body().string();
                    if (responseData == null || responseData.isEmpty()) {
                        throw new JSONException("Empty response");
                    }

                    JSONObject jsonResponse = new JSONObject(responseData);
                    boolean success = jsonResponse.optBoolean("success",
                            jsonResponse.optString("status", "").equalsIgnoreCase("success"));
                    String message = jsonResponse.optString("message",
                            success ? "File uploaded successfully" : "Upload failed");

                    runOnUiThread(() -> {
                        if (success) {
                            uploadItems.get(position).setCompleted(true);
                            uploadItems.get(position).setProgress(100);
                            uploadAdapter.notifyItemChanged(position);
                            completedUploads++;

                            new Handler().postDelayed(() -> {
                                Toast.makeText(Upload_report.this, message, Toast.LENGTH_SHORT).show();
                                fetchPatientImages();
                                checkAllUploadsCompleted();
                            }, 1000);
                        } else {
                            Toast.makeText(Upload_report.this, message, Toast.LENGTH_SHORT).show();
                            removeUploadItem(position);
                            checkAllUploadsCompleted();
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        Toast.makeText(Upload_report.this,
                                "Upload completed but couldn't verify server response",
                                Toast.LENGTH_SHORT).show();
                        checkAllUploadsCompleted();
                    });
                } finally {
                    if (response != null) {
                        response.close();
                    }
                }
            }
        });
    }

    private void checkAllUploadsCompleted() {
        if (!isUploading) return;

        boolean allCompleted = true;
        for (UploadItem item : uploadItems) {
            if (!item.isCompleted() && !item.isPaused()) {
                allCompleted = false;
                break;
            }
        }

        if (allCompleted) {
            isUploading = false;

            if (completedUploads > 0) {
                if (!imageUrls.isEmpty()) {
                    new ProcessAllImagesTask().execute();
                } else if (selectedFileUri != null) {
                    new ProcessImageTask().execute();
                } else if (!uploadItems.isEmpty()) {
                    selectedFileUri = uploadItems.get(0).getFileUri();
                    new ProcessImageTask().execute();
                }
            }
        }
    }

    private class UploadFileTask extends AsyncTask<Void, Void, String> {
        private final Uri fileUri;

        UploadFileTask(Uri fileUri) {
            this.fileUri = fileUri;
        }

        @Override
        protected void onPreExecute() {
            progressBarCircle.setVisibility(View.VISIBLE);
        }

        @Override
        protected String doInBackground(Void... voids) {
            return uploadFileToServer(fileUri);
        }

        @Override
        protected void onPostExecute(String result) {
            try {
                JSONObject jsonResponse = new JSONObject(result);
                String status = jsonResponse.getString("status");
                String message = jsonResponse.getString("message");

                String toastMessage = "Status: " + status + "\n" + message;
                if (jsonResponse.has("folder_path")) {
                    toastMessage += "\nPath: " + jsonResponse.getString("folder_path");
                }

                Toast.makeText(Upload_report.this, toastMessage, Toast.LENGTH_LONG).show();

                if ("success".equals(status)) {
                    fetchPatientImages();
                    new ProcessImageTask().execute();
                }
            } catch (JSONException e) {
                Toast.makeText(Upload_report.this, "Upload response error", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String uploadFileToServer(Uri fileUri) {
        try {
            File file = new File(Objects.requireNonNull(FileUtils.getPath(this, fileUri)));
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", file.getName(),
                            RequestBody.create(MediaType.parse("application/octet-stream"), file))
                    .addFormDataPart("id_patient", idPatient)
                    .build();

            Request request = new Request.Builder()
                    .url(UPLOAD_URL)
                    .post(requestBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                return response.body() != null ? response.body().string() :
                        "{\"status\":\"error\",\"message\":\"Empty response\"}";
            }
        } catch (IOException e) {
            return "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    private class ProcessImageTask extends AsyncTask<Void, Void, String> {
        @Override
        protected void onPreExecute() {
            progressBarCircle.setVisibility(View.VISIBLE);
            Toast.makeText(Upload_report.this, "Analyzing image...", Toast.LENGTH_SHORT).show();
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                if (selectedFileUri == null) {
                    return "NO_FILE";
                }

                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedFileUri);
                if (bitmap == null) {
                    return "INVALID_IMAGE";
                }

                return classifyImage(bitmap);
            } catch (IOException e) {
                Log.e(TAG, "Image processing error", e);
                return "PROCESSING_ERROR";
            }
        }

        @Override
        protected void onPostExecute(String result) {
            progressBarCircle.setVisibility(View.GONE);
            handleClassificationResult(result);
        }
    }

    private class ProcessAllImagesTask extends AsyncTask<Void, Void, Map<String, Integer>> {
        @Override
        protected void onPreExecute() {
            progressBarCircle.setVisibility(View.VISIBLE);
            Toast.makeText(Upload_report.this, "Analyzing all images...", Toast.LENGTH_SHORT).show();
        }

        @Override
        protected Map<String, Integer> doInBackground(Void... voids) {
            Map<String, Integer> resultCounts = new HashMap<>();
            resultCounts.put("no_tumor", 0);
            resultCounts.put("pituitary_tumor", 0);
            resultCounts.put("meningioma_tumor", 0);
            resultCounts.put("glioma_tumor", 0);
            resultCounts.put("unknown", 0);

            if (imageUrls.isEmpty()) {
                return resultCounts;
            }

            for (String imageUrl : imageUrls) {
                try {
                    URL url = new URL(imageUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setDoInput(true);
                    connection.connect();
                    InputStream input = connection.getInputStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(input);

                    if (bitmap != null) {
                        String result = classifyImage(bitmap);
                        resultCounts.put(result, resultCounts.getOrDefault(result, 0) + 1);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing image from URL: " + imageUrl, e);
                }
            }

            return resultCounts;
        }

        @Override
        protected void onPostExecute(Map<String, Integer> resultCounts) {
            progressBarCircle.setVisibility(View.GONE);

            String finalResult = "unknown";
            int maxCount = 0;

            for (Map.Entry<String, Integer> entry : resultCounts.entrySet()) {
                if (entry.getValue() > maxCount) {
                    maxCount = entry.getValue();
                    finalResult = entry.getKey();
                }
            }

            handleClassificationResult(finalResult);
        }
    }

    private String classifyImage(Bitmap bitmap) {
        try {
            int inputTensorIndex = 0;
            int[] inputShape = tflite.getInputTensor(inputTensorIndex).shape();
            int imageSizeX = inputShape[1];
            int imageSizeY = inputShape[2];
            int colorChannels = inputShape[3];

            int bufferSize = 4 * imageSizeX * imageSizeY * colorChannels;
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, imageSizeX, imageSizeY, true);
            ByteBuffer inputBuffer = ByteBuffer.allocateDirect(bufferSize);
            inputBuffer.order(ByteOrder.nativeOrder());

            int[] intValues = new int[imageSizeX * imageSizeY];
            resizedBitmap.getPixels(intValues, 0, resizedBitmap.getWidth(),
                    0, 0, resizedBitmap.getWidth(), resizedBitmap.getHeight());

            int pixel = 0;
            for (int i = 0; i < imageSizeX; ++i) {
                for (int j = 0; j < imageSizeY; ++j) {
                    final int val = intValues[pixel++];
                    inputBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f);
                    inputBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f);
                    inputBuffer.putFloat((val & 0xFF) / 255.0f);
                }
            }

            int outputTensorIndex = 0;
            int[] outputShape = tflite.getOutputTensor(outputTensorIndex).shape();
            float[][] output = new float[1][outputShape[1]];

            tflite.run(inputBuffer, output);

            float[] probabilities = output[0];
            int maxIndex = 0;
            for (int i = 1; i < probabilities.length; i++) {
                if (probabilities[i] > probabilities[maxIndex]) {
                    maxIndex = i;
                }
            }

            switch(maxIndex) {
                case 0: return "no_tumor";
                case 1: return "pituitary_tumor";
                case 2: return "meningioma_tumor";
                case 3: return "glioma_tumor";
                default: return "unknown";
            }
        } catch (Exception e) {
            Log.e(TAG, "Classification error", e);
            return "classification_error";
        }
    }

    private void handleClassificationResult(String result) {
        String displayMessage;
        String databaseStatus;

        switch(result.toLowerCase()) {
            case "no_tumor":
                displayMessage = "No Tumor Detected";
                databaseStatus = "no_tumor";
                break;
            case "pituitary_tumor":
                displayMessage = "Pituitary Tumor Detected";
                databaseStatus = "pituitary_tumor";
                break;
            case "meningioma_tumor":
                displayMessage = "Meningioma Tumor Detected";
                databaseStatus = "meningioma_tumor";
                break;
            case "glioma_tumor":
                displayMessage = "Glioma Tumor Detected";
                databaseStatus = "glioma_tumor";
                break;
            case "no_file":
                displayMessage = "No file selected";
                Toast.makeText(this, displayMessage, Toast.LENGTH_SHORT).show();
                return;
            case "invalid_image":
                displayMessage = "Invalid image file";
                Toast.makeText(this, displayMessage, Toast.LENGTH_SHORT).show();
                return;
            case "processing_error":
                displayMessage = "Error processing image";
                Toast.makeText(this, displayMessage, Toast.LENGTH_SHORT).show();
                return;
            default:
                displayMessage = "Unknown result: " + result;
                databaseStatus = "unknown";
        }

        Toast.makeText(this, "Analysis Result: " + displayMessage, Toast.LENGTH_LONG).show();
        saveResultToDatabase(databaseStatus, displayMessage);
    }

    private void saveResultToDatabase(String resultType, String displayMessage) {
        new Thread(() -> {
            try {
                RequestBody requestBody = new FormBody.Builder()
                        .add("id_patient", idPatient)
                        .add("status", resultType)
                        .build();

                Request request = new Request.Builder()
                        .url(UPDATE_STATUT_URL)
                        .post(requestBody)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.body() != null) {
                        String responseBody = response.body().string();
                        runOnUiThread(() -> {
                            try {
                                JSONObject jsonResponse = new JSONObject(responseBody);
                                if (jsonResponse.getBoolean("success")) {
                                    Intent intent = new Intent(Upload_report.this, Result_Type_Gliome.class);
                                    intent.putExtra("PATIENT_ID", idPatient);
                                    intent.putExtra("RESULT", displayMessage);
                                    intent.putExtra("RESULT_TYPE", resultType);
                                    startActivity(intent);
                                    finish();
                                } else {
                                    String errorMsg = jsonResponse.getString("message");
                                    Toast.makeText(Upload_report.this,
                                            "Update failed: " + errorMsg,
                                            Toast.LENGTH_SHORT).show();
                                }
                            } catch (JSONException e) {
                                Toast.makeText(Upload_report.this,
                                        "Error parsing server response",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            } catch (IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(Upload_report.this,
                                "Network error while updating status",
                                Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tflite != null) {
            tflite.close();
        }
    }
}