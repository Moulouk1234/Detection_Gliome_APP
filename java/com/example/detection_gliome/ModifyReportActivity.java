package com.example.detection_gliome;

import android.content.Intent;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

import org.json.JSONArray;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

public class ModifyReportActivity extends AppCompatActivity implements FileUploadAdapter.OnItemClickListener{
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

    // RecyclerView components
    private RecyclerView imageRecyclerView;
    private ImageAdapter imageAdapter;
    private List<String> imageUrls = new ArrayList<>();
    private OkHttpClient client; // Add this line



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_modify_report);
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
        Button upstatutButton = findViewById(R.id.editstatut);
        upstatutButton.setOnClickListener(v -> {
            if (!imageUrls.isEmpty()) {
                new ProcessAllImagesTask().execute();
            } else {
                Toast.makeText(ModifyReportActivity.this,
                        "No images available to analyze",
                        Toast.LENGTH_SHORT).show();
            }
        });
        // Initialize uploadAdapter here, before it's used anywhere
        uploadAdapter = new FileUploadAdapter(this, uploadItems, this);
        uploadRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        uploadRecyclerView.setAdapter(uploadAdapter);

        // Setup RecyclerView for images
        setupImageRecyclerView();

        // Fetch existing images
        fetchPatientImages();

        selectFileButton.setOnClickListener(v -> filePickerLauncher.launch("*/*"));

        uploadButton.setOnClickListener(v -> {
            if (!uploadItems.isEmpty()) {
                uploadAllFiles();
            } else if (selectedFileUri != null) {
                new UploadFileTask(selectedFileUri).execute();
            } else {
                Toast.makeText(ModifyReportActivity.this, "Please select files first!", Toast.LENGTH_SHORT).show();
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
                Log.d(TAG, "Model output tensor count: " + tflite.getOutputTensorCount());
                // Print detailed model info
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
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd("inceptionv3_model.tflite");
        FileInputStream inputStream = fileDescriptor.createInputStream();
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
    private class ProcessAllImagesTask extends AsyncTask<Void, Void, Map<String, Integer>> {
        @Override
        protected void onPreExecute() {
            progressBarCircle.setVisibility(View.VISIBLE);
            Toast.makeText(ModifyReportActivity.this, "Analyzing all images...", Toast.LENGTH_SHORT).show();
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
                    // Download the image
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

            // Determine the most frequent result
            String finalResult = "unknown";
            int maxCount = 0;

            for (Map.Entry<String, Integer> entry : resultCounts.entrySet()) {
                if (entry.getValue() > maxCount) {
                    maxCount = entry.getValue();
                    finalResult = entry.getKey();
                }
            }

            // Handle the final classification result
            handleClassificationResult(finalResult);
        }
    }    private void handleClassificationResult(String result) {
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

        // Show analysis result
        Toast.makeText(this, "Analysis Result: " + displayMessage, Toast.LENGTH_LONG).show();

        // Save to database
        saveResultToDatabase(databaseStatus, displayMessage);
    }

    private void saveResultToDatabase(String resultType, String displayMessage) {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                RequestBody requestBody = new FormBody.Builder()
                        .add("id_patient", idPatient)
                        .add("status", resultType)  // Changed from "tumor_type" to "status"
                        .build();

                Request request = new Request.Builder()
                        .url(UPDATE_STATUT_URL)
                        .post(requestBody)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.body() != null) {
                        String responseBody = response.body().string();
                        Log.i(TAG, "Database update response: " + responseBody);

                        runOnUiThread(() -> {
                            try {
                                JSONObject jsonResponse = new JSONObject(responseBody);
                                if (jsonResponse.getBoolean("success")) {
                                    // Removed the unused "patient" field from response handling
                                    Toast.makeText(ModifyReportActivity.this,
                                            "Patient status updated successfully!",
                                            Toast.LENGTH_SHORT).show();

                                    // Start results activity
                                    Intent intent = new Intent(ModifyReportActivity.this, Result_Type_Gliome.class);
                                    intent.putExtra("PATIENT_ID", idPatient);  // Add patient ID
                                    intent.putExtra("RESULT", displayMessage);
                                    intent.putExtra("RESULT_TYPE", resultType);
                                    startActivity(intent);
                                } else {
                                    String errorMsg = jsonResponse.getString("message");
                                    Toast.makeText(ModifyReportActivity.this,
                                            "Update failed: " + errorMsg,
                                            Toast.LENGTH_SHORT).show();
                                }
                            } catch (JSONException e) {
                                Toast.makeText(ModifyReportActivity.this,
                                        "Error parsing server response",
                                        Toast.LENGTH_SHORT).show();
                                Log.e(TAG, "JSON parsing error", e);
                            }
                        });
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Database error", e);
                runOnUiThread(() -> Toast.makeText(ModifyReportActivity.this,
                        "Network error while updating status",
                        Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private String classifyImage(Bitmap bitmap) {
        try {
            // Get model input tensor details
            int inputTensorIndex = 0;
            int[] inputShape = tflite.getInputTensor(inputTensorIndex).shape();
            int imageSizeX = inputShape[1];  // typically 224
            int imageSizeY = inputShape[2];  // typically 224
            int colorChannels = inputShape[3];  // typically 3 (RGB)

            // Calculate the required buffer size
            int bufferSize = 4 * imageSizeX * imageSizeY * colorChannels; // 4 bytes per float

            // Resize the bitmap to match model input dimensions
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(
                    bitmap, imageSizeX, imageSizeY, true);

            // Create input buffer with exact required size
            ByteBuffer inputBuffer = ByteBuffer.allocateDirect(bufferSize);
            inputBuffer.order(ByteOrder.nativeOrder());

            // Convert bitmap to byte buffer
            int[] intValues = new int[imageSizeX * imageSizeY];
            resizedBitmap.getPixels(intValues, 0, resizedBitmap.getWidth(),
                    0, 0, resizedBitmap.getWidth(), resizedBitmap.getHeight());

            // Normalize pixel values and populate buffer
            int pixel = 0;
            for (int i = 0; i < imageSizeX; ++i) {
                for (int j = 0; j < imageSizeY; ++j) {
                    final int val = intValues[pixel++];
                    // Normalize to [-1,1] or [0,1] depending on your model
                    inputBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f); // R
                    inputBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f);   // G
                    inputBuffer.putFloat((val & 0xFF) / 255.0f);         // B
                }
            }

            // Prepare output array based on model's output shape
            int outputTensorIndex = 0;
            int[] outputShape = tflite.getOutputTensor(outputTensorIndex).shape();
            float[][] output = new float[1][outputShape[1]]; // [batch_size, num_classes]

            // Run inference
            tflite.run(inputBuffer, output);

            // Process results
            float[] probabilities = output[0];
            Log.d(TAG, "Model output: " + Arrays.toString(probabilities));

            // Find the class with highest probability
            int maxIndex = 0;
            for (int i = 1; i < probabilities.length; i++) {
                if (probabilities[i] > probabilities[maxIndex]) {
                    maxIndex = i;
                }
            }

            // Return classification result
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

    private final ActivityResultLauncher<String> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && uploadAdapter != null) {  // Add null check
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
    private void fetchPatientImages() {
        if (idPatient == null) {
            Toast.makeText(this, "Patient ID not available", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = GET_IMAGES_URL + idPatient;

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(ModifyReportActivity.this,
                            "Failed to fetch images: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(ModifyReportActivity.this,
                                "Server error: " + response.code(),
                                Toast.LENGTH_SHORT).show();
                    });
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
                                Toast.makeText(ModifyReportActivity.this,
                                        "No images found for this patient",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        String message = json.getString("message");
                        runOnUiThread(() -> {
                            Toast.makeText(ModifyReportActivity.this,
                                    message,
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                } catch (JSONException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(ModifyReportActivity.this,
                                "Error parsing response",
                                Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void setupImageRecyclerView() {
        imageAdapter = new ImageAdapter(this, imageUrls);
        imageRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        imageRecyclerView.setAdapter(imageAdapter);
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

                Toast.makeText(ModifyReportActivity.this, toastMessage, Toast.LENGTH_LONG).show();

                // Refresh the image list after upload
                if ("success".equals(status)) {
                    fetchPatientImages();
                }
            } catch (JSONException e) {
                Toast.makeText(ModifyReportActivity.this, "Upload response error", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private String uploadFileToServer(Uri fileUri) {
        try {
            File file = new File(Objects.requireNonNull(FileUtils.getPath(this, fileUri)));
            OkHttpClient client = new OkHttpClient();
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
    private void uploadAllFiles() {
        progressBarCircle.setVisibility(View.VISIBLE);
        for (int i = 0; i < uploadItems.size(); i++) {
            if (!uploadItems.get(i).isPaused()) {
                uploadFile(i);  // This calls our modified method
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
                    Toast.makeText(ModifyReportActivity.this,
                            "Upload failed: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    removeUploadItem(position);
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String responseData = response.body().string();

                    // First check if response is valid JSON
                    if (responseData == null || responseData.isEmpty()) {
                        throw new JSONException("Empty response");
                    }

                    JSONObject jsonResponse = new JSONObject(responseData);

                    // Handle both possible response formats
                    boolean success = jsonResponse.optBoolean("success",
                            jsonResponse.optString("status", "").equalsIgnoreCase("success"));

                    String message = jsonResponse.optString("message",
                            success ? "File uploaded successfully" : "Upload failed");

                    runOnUiThread(() -> {
                        if (success) {
                            uploadItems.get(position).setCompleted(true);
                            uploadItems.get(position).setProgress(100);
                            uploadAdapter.notifyItemChanged(position);

                            // Delay removal to show completion state
                            new Handler().postDelayed(() -> {
                                Toast.makeText(ModifyReportActivity.this,
                                        message,
                                        Toast.LENGTH_SHORT).show();
                                fetchPatientImages();
                                removeUploadItem(position);
                            }, 1000);
                        } else {
                            Toast.makeText(ModifyReportActivity.this,
                                    message,
                                    Toast.LENGTH_SHORT).show();
                            removeUploadItem(position);
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        // Show a more user-friendly error message
                        Toast.makeText(ModifyReportActivity.this,
                                "Upload completed but couldn't verify server response",
                                Toast.LENGTH_SHORT).show();

                        // Still mark as complete and refresh
                        uploadItems.get(position).setCompleted(true);
                        uploadItems.get(position).setProgress(100);
                        uploadAdapter.notifyItemChanged(position);

                        new Handler().postDelayed(() -> {
                            fetchPatientImages();
                            removeUploadItem(position);
                        }, 1000);
                    });
                } finally {
                    if (response != null) {
                        response.close();
                    }
                }
            }
        });
    }

}

