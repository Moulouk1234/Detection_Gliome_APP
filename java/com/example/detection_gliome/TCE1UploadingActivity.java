package com.example.detection_gliome;

import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

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
import java.util.List;
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

public class TCE1UploadingActivity extends AppCompatActivity implements FileUploadAdapter.OnItemClickListener {

    private static final String UPLOAD_URL = "http://10.0.2.2:90/backend_php/Patient/uploadFiles.php";
    private static final String GET_IMAGES_URL = "http://10.0.2.2:90/backend_php/model/get_images_grades.php?id_patient=";
    private static final String UPDATE_GRADE_URL = "http://10.0.2.2:90/backend_php/model/update_grade.php";
    private static final String TAG = "GliomaDetection";

    private OkHttpClient client = new OkHttpClient();
    private Interpreter tflite;
    private boolean modelLoadedSuccessfully = false;

    private RecyclerView uploadRecyclerView;
    private List<UploadItem> uploadItems = new ArrayList<>();
    private FileUploadAdapter uploadAdapter;
    private String idPatient;
    private ProgressBar progressBar;
    private Button analyzeButton,uploadButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tce1_uploading);

        idPatient = getIntent().getStringExtra("PATIENT_ID");
        if (idPatient == null) {
            Toast.makeText(this, "Error: No Patient ID", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Initialize views
        ImageView selectFileButton = findViewById(R.id.selectFileButton);
        uploadButton = findViewById(R.id.uploadButton);
        analyzeButton = findViewById(R.id.analyzeTwoImagesButton);
        progressBar = findViewById(R.id.progressBarCircle);
        uploadRecyclerView = findViewById(R.id.uploadRecyclerView);
        analyzeButton.setVisibility(View.GONE);
        analyzeButton.setEnabled(false);

        analyzeButton.setOnClickListener(v -> analyzeImages());

        uploadAdapter = new FileUploadAdapter(this, uploadItems, this);
        uploadRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        uploadRecyclerView.setAdapter(uploadAdapter);

        selectFileButton.setOnClickListener(v -> filePickerLauncher.launch("*/*"));
        uploadButton.setOnClickListener(v -> {
            if (!uploadItems.isEmpty()) {
                uploadFile(0); // Upload first file only
            } else {
                Toast.makeText(this, "Please select a file", Toast.LENGTH_SHORT).show();
            }
        });

        try {
            tflite = new Interpreter(loadModelFile());
            modelLoadedSuccessfully = true;
            Log.i(TAG, "Model loaded successfully");
        } catch (Exception e) {
            modelLoadedSuccessfully = false;
            Log.e(TAG, "Model loading failed", e);
            Toast.makeText(this, "Failed to load AI model - will use default classification", Toast.LENGTH_LONG).show();
        }
    }

    private void analyzeImages() {
        progressBar.setVisibility(View.VISIBLE);
        analyzeButton.setEnabled(false);

        // Fetch patient images from API
        Request request = new Request.Builder()
                .url(GET_IMAGES_URL + idPatient)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(TCE1UploadingActivity.this, "Failed to fetch images", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                    analyzeButton.setEnabled(true);
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(TCE1UploadingActivity.this, "Server error", Toast.LENGTH_SHORT).show();
                        progressBar.setVisibility(View.GONE);
                        analyzeButton.setEnabled(true);
                    });
                    return;
                }

                try {
                    String responseData = response.body().string();
                    JSONObject json = new JSONObject(responseData);

                    if (json.getBoolean("success")) {
                        JSONArray files = json.getJSONArray("files");
                        if (files.length() > 0) {
                            // Process the first image
                            String imageUrl = files.getJSONObject(0).getString("url");
                            if (!imageUrl.startsWith("http")) {
                                imageUrl = "http://10.0.2.2:90" + imageUrl;
                            }

                            // Download and process image
                            processImageForClassification(imageUrl);
                        } else {
                            useDefaultClassification();
                        }
                    } else {
                        useDefaultClassification();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing response", e);
                    useDefaultClassification();
                }
            }
        });
    }

    private void processImageForClassification(String imageUrl) {
        new Thread(() -> {
            try {
                URL url = new URL(imageUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();

                try (InputStream input = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    if (bitmap != null) {
                        String result = classifyImage(bitmap);
                        bitmap.recycle();
                        updateGradeInDatabase(result);
                    } else {
                        useDefaultClassification();
                    }
                }
                connection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error processing image", e);
                useDefaultClassification();
            }
        }).start();
    }

    private String classifyImage(Bitmap bitmap) {
        if (!modelLoadedSuccessfully) {
            return "HGG"; // Default if model didn't load
        }

        try {
            // Prepare input buffer
            int[] inputShape = tflite.getInputTensor(0).shape();
            int imageSizeX = inputShape[1];
            int imageSizeY = inputShape[2];
            int colorChannels = inputShape[3];

            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, imageSizeX, imageSizeY, true);
            ByteBuffer inputBuffer = ByteBuffer.allocateDirect(4 * imageSizeX * imageSizeY * colorChannels);
            inputBuffer.order(ByteOrder.nativeOrder());

            int[] intValues = new int[imageSizeX * imageSizeY];
            resizedBitmap.getPixels(intValues, 0, resizedBitmap.getWidth(), 0, 0, resizedBitmap.getWidth(), resizedBitmap.getHeight());

            for (int val : intValues) {
                inputBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f);
                inputBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f);
                inputBuffer.putFloat((val & 0xFF) / 255.0f);
            }

            // Run model
            float[][] output = new float[1][tflite.getOutputTensor(0).shape()[1]];
            tflite.run(inputBuffer, output);

            // Interpret results (assuming output[0][0] is LGG probability, output[0][1] is HGG)
            return output[0][0] > output[0][1] ? "LGG" : "HGG";
        } catch (Exception e) {
            Log.e(TAG, "Classification error", e);
            return "HGG"; // Default on error
        }
    }

    private void updateGradeInDatabase(String grade) {
        RequestBody formBody = new FormBody.Builder()
                .add("id_patient", idPatient)
                .add("grade", grade)
                .build();

        Request request = new Request.Builder()
                .url(UPDATE_GRADE_URL)
                .post(formBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(TCE1UploadingActivity.this, "Failed to update grade", Toast.LENGTH_SHORT).show();
                    navigateToResult(grade);
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                runOnUiThread(() -> navigateToResult(grade));
            }
        });
    }

    private void useDefaultClassification() {
        runOnUiThread(() -> {
            Toast.makeText(TCE1UploadingActivity.this, "Using default classification (HGG)", Toast.LENGTH_LONG).show();
            updateGradeInDatabase("HGG");
        });
    }

    private void navigateToResult(String grade) {
        progressBar.setVisibility(View.GONE);

                Intent intent = new Intent(TCE1UploadingActivity.this, Report_Result_Danger.class);
                intent.putExtra("PATIENT_ID", idPatient);
                intent.putExtra("GRADE", grade);
                startActivity(intent);
                finish();


    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd("model3.tflite");
        FileInputStream inputStream = fileDescriptor.createInputStream();
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private final ActivityResultLauncher<String> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && uploadAdapter != null) {
                    String fileName = getFileName(uri);
                    uploadItems.add(new UploadItem(fileName, uri));
                    uploadAdapter.notifyItemInserted(uploadItems.size() - 1);
                }
            });

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
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
            if (cut != -1) result = result.substring(cut + 1);
        }
        return result;
    }

    private void uploadFile(final int position) {
        UploadItem item = uploadItems.get(position);
        File file = new File(Objects.requireNonNull(FileUtils.getPath(this, item.getFileUri())));
        String mimeType = getContentResolver().getType(item.getFileUri());
        if (mimeType == null) mimeType = "application/octet-stream";

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", item.getFileName(),
                        RequestBody.create(MediaType.parse(mimeType), file))
                .addFormDataPart("id_patient", idPatient)
                .build();

        Request request = new Request.Builder()
                .url(UPLOAD_URL)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(TCE1UploadingActivity.this, "Upload failed", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String responseData = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseData);
                    boolean success = jsonResponse.getBoolean("success");

                    runOnUiThread(() -> {
                        Toast.makeText(TCE1UploadingActivity.this,
                                "Upload successful",
                                Toast.LENGTH_SHORT).show();
                        // Enable analyze button after successful upload
                        analyzeButton.setVisibility(View.VISIBLE);
                        analyzeButton.setEnabled(true);
                        progressBar.setVisibility(View.GONE);
                        uploadButton.setVisibility(View.GONE);
                    });

                } catch (Exception e) {
                    runOnUiThread(() ->
                            Toast.makeText(TCE1UploadingActivity.this, "Upload error", Toast.LENGTH_SHORT).show());
                }
            }
        });


    }

    @Override
    public void onPauseClick(int position) {
    }

    @Override
    public void onCancelClick(int position) {
        uploadItems.remove(position);
        uploadAdapter.notifyItemRemoved(position);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tflite != null) {
            tflite.close();
        }
    }
}