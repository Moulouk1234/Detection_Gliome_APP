package com.example.detection_gliome;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Result_Type_Gliome extends AppCompatActivity {
    private static final String TAG = "ResultActivity";
    private static final String GET_IMAGES_URL = "http://10.0.2.2:90/backend_php/model/get_patient_images.php?id_patient=";
private Boolean test=false;
    private RecyclerView imageRecyclerView;
    private ImageAdapter imageAdapter;
    private List<String> imageUrls = new ArrayList<>();
    private TextView gliomeDetectedText;
    private RadioGroup radioGroup;
    private Button checkGradeButton;
    private String patientId;
    private String result;
    private String resultType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result_type_gliome);

        initializeViews();
        getIntentData();
        setupClickListeners();
        displayResults();
        fetchPatientImages();
    }

    private void initializeViews() {
        imageRecyclerView = findViewById(R.id.imageRecyclerView);
        gliomeDetectedText = findViewById(R.id.gliomeDetectedText);
        radioGroup = findViewById(R.id.radioGroup);
        checkGradeButton = findViewById(R.id.gocheckgradeButton);


    }

    private void getIntentData() {
        Intent intent = getIntent();
        patientId = intent.getStringExtra("PATIENT_ID");
       result = intent.getStringExtra("RESULT");
        resultType = intent.getStringExtra("RESULT_TYPE");


    }

    private void setupClickListeners() {
        ImageView backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        checkGradeButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, UploadFileFlairActivity.class);
            intent.putExtra("PATIENT_ID", patientId);
            startActivity(intent);
        });
    }

    private void displayResults() {
        if (result != null) {
            gliomeDetectedText.setText(result);

            switch(resultType.toLowerCase()) {
                case "no_tumor":
                    ((RadioButton)findViewById(R.id.radioNoTumor)).setChecked(true);

                    break;
                case "pituitary_tumor":
                    ((RadioButton)findViewById(R.id.radioPituitary)).setChecked(true);

                    break;
                case "meningioma_tumor":

                    ((RadioButton)findViewById(R.id.radioMeningioma)).setChecked(true);
                    break;
                case "glioma_tumor":
                    test=true;
                    ((RadioButton)findViewById(R.id.radioGlioma)).setChecked(true);
                    break;
            }
            if(!test)
            {
                checkGradeButton.setVisibility(View.GONE);
            }

        }
    }

    private void fetchPatientImages() {
        if (patientId == null) {
            Toast.makeText(this, "Patient ID not available", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = GET_IMAGES_URL + patientId; // Ensure the URL has the patientId as a query parameter
        Log.d(TAG, "Fetching images from: " + url);

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(Result_Type_Gliome.this,
                            "Failed to fetch images: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "API call failed", e);
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(Result_Type_Gliome.this,
                                "Server error: " + response.code(),
                                Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                try {
                    String responseData = response.body().string();
                    Log.d(TAG, "Response: " + responseData); // Log the raw response to see the data

                    JSONObject json = new JSONObject(responseData);

                    if (json.getBoolean("success")) {
                        JSONArray images = json.getJSONArray("images");
                        imageUrls.clear(); // Clear the existing list before adding new images

                        for (int i = 0; i < images.length(); i++) {
                            String imageUrl = images.getString(i);
                            Log.d(TAG, "Image URL: " + imageUrl); // Check if the API is returning a valid image URL

                            // Check if the URL is relative and convert it to absolute
                            if (!imageUrl.startsWith("http")) {
                                imageUrl = "http://10.0.2.2:90" + imageUrl; // Correct relative URL to absolute URL
                            }

                            imageUrls.add(imageUrl); // Add the image URL to the list
                        }

                        runOnUiThread(() -> setupImageRecyclerView()); // Update the UI on the main thread
                    } else {
                        String message = json.getString("message");
                        runOnUiThread(() -> {
                            Toast.makeText(Result_Type_Gliome.this,
                                    message,
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                } catch (JSONException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(Result_Type_Gliome.this,
                                "Error parsing response",
                                Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "JSON parsing error", e);
                    });
                }
            }
        });
    }

    private void setupImageRecyclerView() {
        if (imageUrls.isEmpty()) {
            Toast.makeText(this, "No images found for this patient", Toast.LENGTH_SHORT).show();
            return;
        }

        imageAdapter = new ImageAdapter(this, imageUrls);
        imageRecyclerView.setLayoutManager(
                new GridLayoutManager(this, 2));
        imageRecyclerView.setAdapter(imageAdapter);
    }
}
