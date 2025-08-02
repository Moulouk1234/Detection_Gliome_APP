package com.example.detection_gliome;

import static com.example.detection_gliome.DeleteAccountActivity.deleteAccount;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {
    private ImageView backButton;
    TextView txt;
    LinearLayout profile, logout,deleteaccount;
    private static final String DELETE_ACCOUNT_URL = "http://10.0.2.2:90/backend_php/User/deleteAccount.php";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

         txt = findViewById(R.id.profile_name);
        User user = User.getInstance();
        System.out.println("username"+user.getFullName());
        txt.setText(user.getFullName());
        ImageView imgUser = findViewById(R.id.imguser);
        String baseUrl = "http://10.0.2.2:90/backend_php/User/";
        String imagePath = user.getProfileImage();
        String fullImageUrl = baseUrl + imagePath;
        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
            Glide.with(this)
                    .load(fullImageUrl)
                    .placeholder(R.drawable.iconuser)
                    .transform(new CircleCrop())

                    .error(R.drawable.iconuser)
                    .into(imgUser);
        } else {
            imgUser.setImageResource(R.drawable.iconuser);
        }

        profile = findViewById(R.id.goprofile);

        logout = findViewById(R.id.gologout);
        deleteaccount = findViewById(R.id.deleteaccount);

        // Set click listeners
        profile.setOnClickListener(v -> goToProfile());

        logout.setOnClickListener(v -> showLogoutDialog());
        deleteaccount.setOnClickListener(v -> showdeleteaccountDialog());

        backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> goBack());

    }

    private void goToProfile() {
        Intent i = new Intent(ProfileActivity.this, EditProfileActivity.class);
        startActivity(i);
        finish();

    }



    private void showLogoutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View customView = getLayoutInflater().inflate(R.layout.logout_dialog, null);
        builder.setView(customView);

        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        Button cancelButton = customView.findViewById(R.id.cancel_button);
        Button logoutButton = customView.findViewById(R.id.logout_button);

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        logoutButton.setOnClickListener(v -> {

            dialog.dismiss();
            User.setInstance(null);
            Intent i = new Intent(ProfileActivity.this, SplachActivity.class);

            startActivity(i);

            finishAffinity();

        });

        dialog.show();
    }

    private void showdeleteaccountDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View customView = getLayoutInflater().inflate(R.layout.activity_delete_account, null);
        builder.setView(customView);

        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        Button cancelButton = customView.findViewById(R.id.cancel_button);
        Button deleteButton = customView.findViewById(R.id.delete_button);

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        deleteButton.setOnClickListener(v -> {
            deleteAccount();
            dialog.dismiss();
        });

        dialog.show();
    }
    private void goBack() {
        finish();
    }

    public void deleteAccount() {
        // Get user ID from singleton
        User user = User.getInstance();
        int idUser = user.getId();

        if (idUser == -1) {
            Toast.makeText(ProfileActivity.this, "User ID not found!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Send request to delete account
        StringRequest stringRequest = new StringRequest(Request.Method.POST, DELETE_ACCOUNT_URL,
                response -> {
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        String status = jsonResponse.getString("status");
                        String message = jsonResponse.getString("message");

                        Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_SHORT).show();

                        if (status.equals("success")) {
                            user.clear();
                            Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            ProfileActivity.this.startActivity(intent);
                                finish(); // Close the current activity

                        }
                    } catch (JSONException e) {
                        Toast.makeText(ProfileActivity.this, "Error parsing response", Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                },
                error -> Toast.makeText(ProfileActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show()) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("id_user", String.valueOf(idUser));
                return params;
            }
        };

        RequestQueue requestQueue = Volley.newRequestQueue(ProfileActivity.this);
        requestQueue.add(stringRequest);
    }


}
