package com.example.detection_gliome;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SignUpActivity extends AppCompatActivity {

    private static final String TAG = "SignUpActivity";
    private static final String REGISTER_URL = "http://10.0.2.2:90/backend_php/User/register.php";

    private EditText fullNameEditText, emailEditText, passwordEditText, mobileEditText,
            specialtyEditText, profileDescEditText, startYearEditText, workplaceEditText;
    private ImageView profileImageView;
    private Button signUpButton, uploadImageButton;

    private Uri selectedImageUri;
    private Bitmap selectedBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        // Initialize UI components
        fullNameEditText = findViewById(R.id.fullNameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        mobileEditText = findViewById(R.id.mobileEditText);
        specialtyEditText = findViewById(R.id.specialtyEditText);
        profileDescEditText = findViewById(R.id.profileDescEditText);
        startYearEditText = findViewById(R.id.startYearEditText);
        workplaceEditText = findViewById(R.id.workplaceEditText);
        profileImageView = findViewById(R.id.profileImageView);
        signUpButton = findViewById(R.id.signUpButton);
        uploadImageButton = findViewById(R.id.uploadImageButton);


        // Select Image
        uploadImageButton.setOnClickListener(v -> openGallery());

        // Register User
        signUpButton.setOnClickListener(v -> registerUser());
    }

    // Opens Gallery to Select Image
    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, 100);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            selectedImageUri = data.getData();
            try {
                selectedBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImageUri);
                profileImageView.setImageBitmap(selectedBitmap);
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Convert Image to Base64 String
    private String encodeImageToBase64(Bitmap bitmap) {
        if (bitmap == null) return "";
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
        byte[] imageBytes = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(imageBytes, Base64.DEFAULT);
    }

    // Register User Function
    private void registerUser() {

        StringRequest stringRequest = new StringRequest(Request.Method.POST, REGISTER_URL,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d(TAG, "Raw Response: " + response);

                        try {
                            // Try to parse the response as JSON
                            JSONObject jsonResponse = new JSONObject(response);
                            String status = jsonResponse.getString("status");
                            String message = jsonResponse.getString("message");

                            if ("success".equals(status)) {
                                Toast.makeText(SignUpActivity.this, "Registration Successful", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(SignUpActivity.this, LoginActivity.class));
                                finish();
                            } else {
                                Toast.makeText(SignUpActivity.this, "Registration Failed: " + message, Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Invalid JSON Format", e);
                            Toast.makeText(SignUpActivity.this, "Invalid response format from server", Toast.LENGTH_SHORT).show();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, "Volley Error: ", error);
                        Toast.makeText(SignUpActivity.this, "Network error occurred", Toast.LENGTH_SHORT).show();
                    }
                }) {
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> params = new HashMap<>();
                params.put("full_name", fullNameEditText.getText().toString().trim());
                params.put("email", emailEditText.getText().toString().trim());
                params.put("password", passwordEditText.getText().toString().trim());
                params.put("mobile", mobileEditText.getText().toString().trim());
                params.put("specialty", specialtyEditText.getText().toString().trim());
                params.put("profile_description", profileDescEditText.getText().toString().trim());
                params.put("start_year", startYearEditText.getText().toString().trim());
                params.put("workplace", workplaceEditText.getText().toString().trim());

                if (selectedBitmap != null) {
                    params.put("profile_image", encodeImageToBase64(selectedBitmap));
                }

                return params;
            }
        };

// Add Request to Queue
        RequestQueue requestQueue = Volley.newRequestQueue(this);
        requestQueue.add(stringRequest);

    }
}
