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
import android.widget.TextView;
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
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {
    private ImageView backButton, imgUser;
    private EditText txtpass, txtworkplace, txtemail, txtstartyear, txtdesc, txtspecialty, txtusername, txtmobile;
    private User user;
    private Uri selectedImageUri;
    private Bitmap selectedBitmap;
    private TextView txtname;
    private Button savechanges;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);
        user = User.getInstance();

        // Initialize UI components
        txtname = findViewById(R.id.profilname);
        txtname.setText(user.getFullName());
        txtusername = findViewById(R.id.editname);
        txtusername.setText(user.getFullName());
        txtpass = findViewById(R.id.passedit);
        txtemail = findViewById(R.id.emailedit);
        txtemail.setText(user.getEmail());
        txtmobile = findViewById(R.id.mobileedit);
        txtmobile.setText(user.getMobile());
        txtspecialty = findViewById(R.id.specialtyedit);
        txtspecialty.setText(user.getSpecialty());
        txtdesc = findViewById(R.id.descedit);
        txtdesc.setText(user.getProfileDescription());
        txtstartyear = findViewById(R.id.yearstartedit);
        txtstartyear.setText(user.getStartYear());
        txtworkplace = findViewById(R.id.workedit);
        txtworkplace.setText(user.getWorkplace());

        imgUser = findViewById(R.id.imguser);
        String baseUrl = "http://10.0.2.2:90/backend_php/User/";
        String imagePath = user.getProfileImage();
        String fullImageUrl = baseUrl + imagePath;
        if (imagePath != null && !imagePath.isEmpty()) {
            Glide.with(this)
                    .load(fullImageUrl)
                    .placeholder(R.drawable.iconuser)
                    .transform(new CircleCrop())
                    .error(R.drawable.iconuser)
                    .into(imgUser);
        } else {
            imgUser.setImageResource(R.drawable.iconuser);
        }

        // Set up back button
        backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> goBack());

        // Set up save button
        savechanges = findViewById(R.id.savechanges);
        savechanges.setOnClickListener(v -> saveChanges());
    }

    private void goBack() {
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            selectedImageUri = data.getData();
            try {
                // Convert the URI to a Bitmap and display it in the ImageView
                selectedBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImageUri);
                imgUser.setImageBitmap(selectedBitmap);
                Toast.makeText(this, "Image selected successfully", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveChanges() {
        String fullName = txtusername.getText().toString().trim();
        String email = txtemail.getText().toString().trim();
        String password = txtpass.getText().toString().trim();

        // Validate the required fields
        if (fullName.isEmpty() || email.isEmpty()) {
            Toast.makeText(EditProfileActivity.this, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get the user id (make sure it's accessible in the User instance)
        String userId = String.valueOf(user.getId());  // Assuming `getId()` is a method in the `User` class

        String url = "http://10.0.2.2:90/backend_php/User/updateProfil.php";  // Adjust your URL as needed

        // Prepare the request
        StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d("EditProfileActivity", "Raw Response: " + response);
                        try {
                            JSONObject jsonResponse = new JSONObject(response);
                            String status = jsonResponse.getString("status");
                            String message = jsonResponse.getString("message");

                            if ("success".equals(status)) {
                                Toast.makeText(EditProfileActivity.this, "Profile Update Successful", Toast.LENGTH_LONG).show();
                                user.refreshUserData();
                                startActivity(new Intent(EditProfileActivity.this, Landing_Activity.class));
                                finish();
                            } else {
                                Toast.makeText(EditProfileActivity.this, "Update Failed: " + message, Toast.LENGTH_LONG).show();
                            }
                        } catch (JSONException e) {
                            Log.e("EditProfileActivity", "Invalid JSON Format", e);
                            Toast.makeText(EditProfileActivity.this, "Invalid response format from server", Toast.LENGTH_SHORT).show();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("EditProfileActivity", "Volley Error: ", error);
                        Toast.makeText(EditProfileActivity.this, "Network error occurred", Toast.LENGTH_SHORT).show();
                    }
                }) {
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {

                Log.d("UserID", "User ID: " + user.getId());

                Map<String, String> params = new HashMap<>();
                params.put("user_id", String.valueOf(user.getId()));
                params.put("full_name", fullName);
                params.put("email", email);
                params.put("password", password);
                params.put("mobile", txtmobile.getText().toString().trim());
                params.put("specialty", txtspecialty.getText().toString().trim());
                params.put("profile_description", txtdesc.getText().toString().trim());
                params.put("start_year", txtstartyear.getText().toString().trim());
                params.put("workplace", txtworkplace.getText().toString().trim());

                if (selectedBitmap != null) {
                    String encodedImage = encodeImageToBase64(selectedBitmap);
                    params.put("profile_image", encodedImage);  // Sending base64 encoded image string
                }

                return params;
            }
        };

        // Add the request to the Volley queue
        RequestQueue requestQueue = Volley.newRequestQueue(this);
        requestQueue.add(stringRequest);
    }


    private String encodeImageToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }

    public void onEditProfilePictureClick(View view) {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, 100);  // Request code 100 for image selection
    }
}
