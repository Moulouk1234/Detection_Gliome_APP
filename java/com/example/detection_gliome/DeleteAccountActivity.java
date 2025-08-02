package com.example.detection_gliome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class DeleteAccountActivity extends AppCompatActivity {

    private Button cancelButton, deleteButton;
    private static final String DELETE_ACCOUNT_URL = "http://10.0.2.2:90/backend_php/User/deleteAccount.php";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_delete_account);

        cancelButton = findViewById(R.id.cancel_button);
        deleteButton = findViewById(R.id.delete_button);

        cancelButton.setOnClickListener(v -> finish());

        // Delete account button
        deleteButton.setOnClickListener(v -> deleteAccount(DeleteAccountActivity.this));

    }

    public static void deleteAccount(Context context) {
        // Get user ID from singleton
        User user = User.getInstance();
        int idUser = user.getId();

        if (idUser == -1) {
            Toast.makeText(context, "User ID not found!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Send request to delete account
        StringRequest stringRequest = new StringRequest(Request.Method.POST, DELETE_ACCOUNT_URL,
                response -> {
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        String status = jsonResponse.getString("status");
                        String message = jsonResponse.getString("message");

                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();

                        if (status.equals("success")) {
                            user.clear();
                            Intent intent = new Intent(context, LoginActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            context.startActivity(intent);
                            if (context instanceof Activity) {
                                ((Activity) context).finish(); // Close the current activity
                            }
                        }
                    } catch (JSONException e) {
                        Toast.makeText(context, "Error parsing response", Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                },
                error -> Toast.makeText(context, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show()) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("id_user", String.valueOf(idUser));
                return params;
            }
        };

        RequestQueue requestQueue = Volley.newRequestQueue(context);
        requestQueue.add(stringRequest);
    }
}
