package com.example.detection_gliome;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UploadFileFlairActivity extends AppCompatActivity implements FileUploadAdapter.OnItemClickListener {

    private static final String UPLOAD_URL = "http://10.0.2.2:90/backend_php/Patient/uploadFiles.php";
    private final OkHttpClient client = new OkHttpClient();

    private RecyclerView uploadRecyclerView;
    private List<UploadItem> uploadItems = new ArrayList<>();
    private FileUploadAdapter uploadAdapter;
    private String idPatient;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_file_flair);

        idPatient = getIntent().getStringExtra("PATIENT_ID");


        ImageView selectFileButton = findViewById(R.id.selectFileButton);
        Button uploadButton = findViewById(R.id.uploadButton);
        uploadRecyclerView = findViewById(R.id.uploadRecyclerView);

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
                        Toast.makeText(UploadFileFlairActivity.this, "Upload failed", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String responseData = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseData);
                    boolean success = jsonResponse.getBoolean("success");

                    runOnUiThread(() -> {

                            Toast.makeText(UploadFileFlairActivity.this, "Upload successful", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(UploadFileFlairActivity.this, TCE1UploadingActivity.class);

                        intent.putExtra("PATIENT_ID", idPatient);
                        startActivity(intent);
                            finish();

                    });

                } catch (Exception e) {

                        startActivity(new Intent(UploadFileFlairActivity.this, TCE1UploadingActivity.class));
                        finish();
                }
            }
        });
    }

    @Override
    public void onPauseClick(int position) {
        // Optional: remove pause functionality or keep it no-op
    }

    @Override
    public void onCancelClick(int position) {
        uploadItems.remove(position);
        uploadAdapter.notifyItemRemoved(position);
    }
}
